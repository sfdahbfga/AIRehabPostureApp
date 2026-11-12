package com.example.airehabpostureapp.pose;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * 유지 시간형 측정기 (Android 의존성 없는 순수 Java). 예: 하체 스트레칭
 *
 * 판정 값 = 앞으로 숙인 상체 기울기 (클수록 많이 숙임, ExerciseCriteria.higherIsDeeper)
 * 1회 = 판정 값(평균)이 기준 이상이고 무릎(더 펴진 다리)이 minKneeAngle 이상인 상태를 holdDurationMs 동안 유지
 * - 판정에는 최근 smoothingWindow 프레임의 평균을 쓰고, 연속성 검사도 RepetitionCounter와 같다.
 * - 유지 시작은 bentAngle(20도) 이상, 유지 중에는 holdKeepAngle(17도) 이상이면 이어짐 (기준 근처에서 시작 / 끊김 반복 방지)
 * - 무릎도 여유 범위: 160도 아래면 굽힘, 굽힌 뒤에는 165도 이상이 되어야 다시 편 것으로 봄
 * - 유지 시간은 기준 안에 있는 프레임 사이의 시간만 더한다. (기준 밖 / 안 보이는 동안은 멈춤)
 * - 기준 밖으로 holdGraceMs 넘게 벗어나거나 관절이 maxInvisibleMs 넘게 안 보이면 그 유지는 끝(세지 않음).
 *   기준 밖이어도 holdGraceMs 안이면 안내는 "유지하세요" 그대로 (잠깐 끊겼다 이어져도 안내가 바뀌지 않음)
 * - 1회가 끝나면 자세를 다시 세워야 다음 유지를 시작한다.
 *   세운 것으로 보는 값 = max(extendedAngle, 직전 유지 평균 - releaseRiseDegrees) 이하
 * - "조금 더 숙여 주세요"(BEND_MORE)는 숙이는 중에는 내지 않고, 기준에 못 미친 채 STALL_HINT_MS 넘게
 *   더 숙이지 않고 멈춰 있을 때 동작 하나에 한 번만 (의자 스쿼트의 멈춤 안내와 같은 방식)
 * 1회 결과는 RepResult로 돌려준다: minJointAngle = 유지 중 평균 값, durationMs = 실제 유지 시간
 *
 * 한 스레드에서만 호출해야 한다. (ExerciseActivity에서는 UI 스레드)
 */
public class HoldCounter implements ExerciseCounter {

    // 이 시간 이상 버틴 뒤 놓친 유지만 "버려진 동작"으로 기록 (기준값 조정용)
    private static final long MIN_ATTEMPT_MS = 3000;
    // 기준 안 프레임 사이 시간을 더할 때 최대값 (건너뛴 프레임이 있어도 한 번에 이보다 많이 더하지 않음)
    private static final long MAX_FRAME_GAP_MS = 200;
    // 이만큼 앞으로 숙이면 "숙이는 동작" 시작으로 봄 (멈춤 안내 / 동작 단위 안내의 기준)
    static final double LEAN_STARTED_DEGREES = 5;
    // 기준에 못 미친 채 이 시간 넘게 더 숙이지 않으면 "조금 더 숙여 주세요" 한 번
    static final long STALL_HINT_MS = 1500;
    // 이만큼 더 숙여야 "더 숙이는 중"으로 봄 (멈춤 시간을 처음부터 다시 잼)
    static final double STALL_PROGRESS_DEGREES = 2;
    // 무릎을 굽힌 것으로 본 뒤에는 minKneeAngle + 이 값 이상이 되어야 다시 편 것으로 봄
    static final double KNEE_STRAIGHT_MARGIN_DEGREES = 5;

    private final ExerciseCriteria criteria;
    private final Deque<Double> window = new ArrayDeque<>();
    private final List<RepetitionCounter.RepResult> results = new ArrayList<>();

    private double lastAcceptedAngle = Double.NaN;
    private long lastVisibleAtMs = -1;
    private int skippedFrameCount;

    // 1회를 마친 뒤 자세를 다시 세우기를 기다리는 중
    private boolean needRelease;
    private double releaseAngle;

    // 진행 중인 유지 (-1이면 유지 중 아님)
    private long holdStartedAtMs = -1;
    private long heldMs;
    // 직전 프레임이 기준 안이었으면 그 시각 (-1이면 아님)
    private long lastInRangeAtMs = -1;
    private boolean previousInRange;
    private double angleSum;
    private int angleCount;
    private double minKneeAngle = Double.NaN;
    private int attemptAccepted;
    private int attemptSkipped;
    private int attemptInvisible;
    private double lastHoldMinKneeAngle = Double.NaN;

    // 무릎을 굽힌 상태로 보고 있는지 (여유 범위)
    private boolean kneeBent;
    // 숙이는 동작 중 (LEAN_STARTED_DEGREES 이상 숙임, 유지 중 포함)
    private boolean leaning;
    // 멈춤 안내: 이번 동작에서 이미 안내했는지 / 기준 각도와 그 시각
    private boolean stallHintUsed;
    private double stallRefAngle;
    private long stallRefAtMs;
    // 1회를 마친 뒤 다시 세움 기준은 지났지만 아직 LEAN_STARTED_DEGREES 아래로 내려가지 않음 (세우는 중)
    private boolean awaitingUpright;

    private double smoothedAngle = Double.NaN;
    private RepetitionCounter.Feedback feedback = RepetitionCounter.Feedback.NOT_VISIBLE;
    private RepetitionCounter.RejectedAttempt pendingRejection;

    public HoldCounter(ExerciseCriteria criteria) {
        this.criteria = criteria;
    }

    /**
     * @param jointAngle 판정 값 (하체 스트레칭: 앞으로 숙인 상체 기울기). 안 보이면 NaN
     * @param trunkLean  쓰지 않음
     * @param kneeAngle  잘 보이는 다리 중 더 펴진 다리의 무릎 각도. 안 보이면 NaN (그때는 무릎 조건을 검사하지 않음)
     */
    @Override
    public RepetitionCounter.RepResult onFrame(long timestampMs, double jointAngle, double trunkLean,
                                               double kneeAngle) {
        if (Double.isNaN(jointAngle)) {
            if (holdStartedAtMs >= 0) {
                attemptInvisible++;
            }
            handleInvisible(timestampMs);
            return null;
        }
        long gapMs = timestampMs - lastVisibleAtMs;
        if (holdStartedAtMs >= 0) {
            gapMs = Math.min(gapMs, criteria.inRepMaxGapMs);
        }
        if (!Double.isNaN(lastAcceptedAngle)
                && Math.abs(jointAngle - lastAcceptedAngle) > criteria.maxAngleChange(gapMs)) {
            skippedFrameCount++;
            if (holdStartedAtMs >= 0) {
                attemptSkipped++;
            }
            handleInvisible(timestampMs);
            return null;
        }
        lastAcceptedAngle = jointAngle;
        lastVisibleAtMs = timestampMs;
        smoothedAngle = pushAndAverage(jointAngle);
        updateKneeBent(kneeAngle);

        if (needRelease) {
            if (smoothedAngle <= releaseAngle) {
                needRelease = false;
                endLeaning();
                // 세우는 중(예: 15도 → 0도)을 새로 숙이는 동작으로 보지 않도록, 한 번 LEAN_STARTED_DEGREES 아래로 내려갈 때까지 기다림
                awaitingUpright = true;
                feedback = RepetitionCounter.Feedback.START_BENDING;
            } else {
                feedback = RepetitionCounter.Feedback.RELEASE;
            }
            return null;
        }

        boolean holding = holdStartedAtMs >= 0;
        double threshold = holding ? criteria.holdKeepAngle : criteria.bentAngle;
        boolean inRange = smoothedAngle >= threshold && !kneeBent;

        if (inRange) {
            leaning = true;
            if (!holding) {
                startHold(timestampMs);
            } else if (previousInRange) {
                heldMs += Math.min(timestampMs - lastInRangeAtMs, MAX_FRAME_GAP_MS);
            }
            previousInRange = true;
            lastInRangeAtMs = timestampMs;
            attemptAccepted++;
            angleSum += smoothedAngle;
            angleCount++;
            if (!Double.isNaN(kneeAngle)) {
                minKneeAngle = Double.isNaN(minKneeAngle) ? kneeAngle : Math.min(minKneeAngle, kneeAngle);
            }
            if (heldMs >= criteria.holdDurationMs) {
                return completeHold();
            }
            feedback = RepetitionCounter.Feedback.HOLD;
            return null;
        }

        // 기준 밖: 유지 중이면 시간은 멈추고, holdGraceMs 넘게 이어지면 유지 끝
        previousInRange = false;
        if (holding) {
            attemptAccepted++;
            if (timestampMs - lastInRangeAtMs > criteria.holdGraceMs) {
                abortHold(RepetitionCounter.RejectReason.RELEASED_EARLY);
            }
        }
        if (kneeBent) {
            feedback = RepetitionCounter.Feedback.STRAIGHTEN_KNEE;
        } else if (holdStartedAtMs >= 0) {
            // 잠깐 기준 밖 (holdGraceMs 안): 안내는 그대로
            feedback = RepetitionCounter.Feedback.HOLD;
        } else {
            feedback = leaningFeedback(timestampMs);
        }
        return null;
    }

    // 무릎 여유 범위: 160도 아래면 굽힘, 굽힌 뒤에는 165도 이상이어야 편 것. 안 보이면 검사하지 않음
    private void updateKneeBent(double kneeAngle) {
        if (Double.isNaN(kneeAngle) || criteria.minKneeAngle <= 0) {
            kneeBent = false;
        } else if (kneeBent) {
            kneeBent = kneeAngle < criteria.minKneeAngle + KNEE_STRAIGHT_MARGIN_DEGREES;
        } else {
            kneeBent = criteria.isKneeBent(kneeAngle);
        }
    }

    // 유지 전 숙이는 중 안내: 기본은 "숙여 주세요", 기준에 못 미친 채 멈추면 "조금 더 숙여 주세요" 한 번
    private RepetitionCounter.Feedback leaningFeedback(long timestampMs) {
        if (smoothedAngle < LEAN_STARTED_DEGREES) {
            endLeaning();
            awaitingUpright = false;
            return RepetitionCounter.Feedback.START_BENDING;
        }
        if (awaitingUpright) {
            // 1회를 마치고 세우는 중: 아직 새 동작이 아님
            return RepetitionCounter.Feedback.START_BENDING;
        }
        if (!leaning) {
            leaning = true;
            stallRefAngle = smoothedAngle;
            stallRefAtMs = timestampMs;
        }
        if (smoothedAngle > stallRefAngle + STALL_PROGRESS_DEGREES) {
            stallRefAngle = smoothedAngle;
            stallRefAtMs = timestampMs;
        }
        if (!stallHintUsed && timestampMs - stallRefAtMs >= STALL_HINT_MS) {
            stallHintUsed = true;
            return RepetitionCounter.Feedback.BEND_MORE;
        }
        return feedback == RepetitionCounter.Feedback.BEND_MORE
                ? RepetitionCounter.Feedback.BEND_MORE : RepetitionCounter.Feedback.START_BENDING;
    }

    // 숙이는 동작 하나가 끝남 (다시 세움): 멈춤 안내를 다음 동작에서 다시 할 수 있음
    private void endLeaning() {
        leaning = false;
        stallHintUsed = false;
        stallRefAngle = Double.NaN;
        stallRefAtMs = -1;
    }

    private void startHold(long timestampMs) {
        holdStartedAtMs = timestampMs;
        heldMs = 0;
        angleSum = 0;
        angleCount = 0;
        minKneeAngle = Double.NaN;
        attemptAccepted = 0;
        attemptSkipped = 0;
        attemptInvisible = 0;
    }

    private RepetitionCounter.RepResult completeHold() {
        double average = angleSum / angleCount;
        RepetitionCounter.RepResult result = new RepetitionCounter.RepResult(
                results.size() + 1,
                average,
                Double.NaN,
                Double.NaN,
                criteria.jointError(average),
                Double.NaN,
                criteria.jointScore(average),
                heldMs,
                currentFrameStats());
        results.add(result);
        lastHoldMinKneeAngle = minKneeAngle;
        needRelease = true;
        releaseAngle = Math.max(criteria.extendedAngle, average - criteria.releaseRiseDegrees);
        clearHold();
        feedback = RepetitionCounter.Feedback.RELEASE;
        return result;
    }

    private void abortHold(RepetitionCounter.RejectReason reason) {
        if (heldMs >= MIN_ATTEMPT_MS && angleCount > 0) {
            pendingRejection = new RepetitionCounter.RejectedAttempt(reason, angleSum / angleCount,
                    Double.NaN, Double.NaN, heldMs, currentFrameStats());
        }
        clearHold();
        // 유지가 끝나도 계속 숙이고 있으면 같은 동작: 멈춤 안내 시간은 지금부터 다시 잼
        stallRefAngle = smoothedAngle;
        stallRefAtMs = lastVisibleAtMs;
    }

    private void clearHold() {
        holdStartedAtMs = -1;
        heldMs = 0;
        lastInRangeAtMs = -1;
        previousInRange = false;
    }

    private RepetitionCounter.FrameStats currentFrameStats() {
        return new RepetitionCounter.FrameStats(attemptAccepted, attemptSkipped, attemptInvisible);
    }

    private void handleInvisible(long timestampMs) {
        boolean neverVisible = lastVisibleAtMs < 0;
        if (neverVisible || timestampMs - lastVisibleAtMs > criteria.maxInvisibleMs) {
            if (holdStartedAtMs >= 0) {
                abortHold(RepetitionCounter.RejectReason.LOST_TRACKING);
            }
            window.clear();
            smoothedAngle = Double.NaN;
            lastAcceptedAngle = Double.NaN;
            endLeaning();
            feedback = RepetitionCounter.Feedback.NOT_VISIBLE;
        }
        // 짧게 안 보인 프레임은 건너뛰고 상태 유지 (유지 시간은 늘지 않음)
    }

    private double pushAndAverage(double value) {
        window.addLast(value);
        while (window.size() > criteria.smoothingWindow) {
            window.removeFirst();
        }
        double sum = 0;
        for (double v : window) {
            sum += v;
        }
        return sum / window.size();
    }

    @Override
    public void restoreResults(List<RepetitionCounter.RepResult> savedResults) {
        reset();
        results.addAll(savedResults);
    }

    @Override
    public void reset() {
        results.clear();
        window.clear();
        lastAcceptedAngle = Double.NaN;
        lastVisibleAtMs = -1;
        skippedFrameCount = 0;
        needRelease = false;
        awaitingUpright = false;
        kneeBent = false;
        clearHold();
        endLeaning();
        smoothedAngle = Double.NaN;
        lastHoldMinKneeAngle = Double.NaN;
        feedback = RepetitionCounter.Feedback.NOT_VISIBLE;
        pendingRejection = null;
    }

    @Override
    public RepetitionCounter.RejectedAttempt takeRejectedAttempt() {
        RepetitionCounter.RejectedAttempt rejected = pendingRejection;
        pendingRejection = null;
        return rejected;
    }

    /** 유지형은 "천천히" 안내를 쓰지 않음 */
    @Override
    public boolean takeMoveSlowlyHint() {
        return false;
    }

    /**
     * 동작 진행 중: 유지 중이거나, 유지 전후로 앞으로 숙인 동작 중 (다시 세우면 끝)
     * (같은 동작 안에서 "유지하세요"를 한 번만 읽고, 목표 안내는 다음 동작을 시작하면 내리는 기준)
     */
    @Override
    public boolean isRepInProgress() {
        return holdStartedAtMs >= 0 || (leaning && !needRelease);
    }

    /** 유지 중인지 (진행 중인 유지 포함) */
    public boolean isHolding() {
        return holdStartedAtMs >= 0;
    }

    @Override
    public RepetitionCounter.Feedback getFeedback() {
        return feedback;
    }

    @Override
    public int getCompletedReps() {
        return results.size();
    }

    @Override
    public List<RepetitionCounter.RepResult> getResults() {
        return Collections.unmodifiableList(results);
    }

    @Override
    public int getSkippedFrameCount() {
        return skippedFrameCount;
    }

    /** 진행 중인 유지의 누적 시간 (유지 중이 아니면 0). 화면의 "N/20초" 표시용 */
    public long getHeldMs() {
        return holdStartedAtMs >= 0 ? heldMs : 0;
    }

    /** 마지막으로 끝난 유지 동안 가장 작았던 무릎 각도 (디버그 기록용, 안 보였으면 NaN) */
    public double getLastHoldMinKneeAngle() {
        return lastHoldMinKneeAngle;
    }

    public double getSmoothedJointAngle() {
        return smoothedAngle;
    }
}

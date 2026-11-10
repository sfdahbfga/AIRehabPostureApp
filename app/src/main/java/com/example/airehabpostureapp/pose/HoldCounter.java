package com.example.airehabpostureapp.pose;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * 유지 시간형 측정기 (Android 의존성 없는 순수 Java). 예: 하체 스트레칭
 *
 * 1회 = 판정 각도(평균)가 bentAngle 이하이고 펴야 하는 무릎이 minKneeAngle 이상인 상태를 holdDurationMs 동안 유지
 * - 판정에는 최근 smoothingWindow 프레임의 평균 각도를 쓰고, 각도 연속성 검사도 RepetitionCounter와 같다.
 * - 유지 시간은 기준 안에 있는 프레임 사이의 시간만 더한다. (기준 밖 / 안 보이는 동안은 멈춤)
 * - 기준 밖으로 holdGraceMs 넘게 벗어나거나 관절이 maxInvisibleMs 넘게 안 보이면 그 유지는 끝(세지 않음).
 * - 1회가 끝나면 자세를 다시 세워야 다음 유지를 시작한다.
 *   세운 것으로 보는 각도 = min(extendedAngle, 직전 유지 평균 + releaseRiseDegrees)
 *   (앉은 자세의 엉덩이 각도는 다리 모양에 따라 달라서, 고정값만 쓰면 다시 세운 것을 알아채지 못할 수 있음)
 * 1회 결과는 RepResult로 돌려준다: minJointAngle = 유지 중 평균 각도, durationMs = 실제 유지 시간
 *
 * 한 스레드에서만 호출해야 한다. (ExerciseActivity에서는 UI 스레드)
 */
public class HoldCounter implements ExerciseCounter {

    // 이 시간 이상 버틴 뒤 놓친 유지만 "버려진 동작"으로 기록 (기준값 조정용)
    private static final long MIN_ATTEMPT_MS = 3000;
    // 기준 안 프레임 사이 시간을 더할 때 최대값 (건너뛴 프레임이 있어도 한 번에 이보다 많이 더하지 않음)
    private static final long MAX_FRAME_GAP_MS = 200;
    // 세운 자세보다 이만큼 숙여야 "숙이는 중"으로 보고 "조금 더 숙여 주세요" 안내 (그 전에는 "숙여 주세요")
    private static final double LEAN_STARTED_DEGREES = 5;

    private final ExerciseCriteria criteria;
    private final Deque<Double> window = new ArrayDeque<>();
    private final List<RepetitionCounter.RepResult> results = new ArrayList<>();

    private double lastAcceptedAngle = Double.NaN;
    private long lastVisibleAtMs = -1;
    private int skippedFrameCount;

    // 1회를 마친 뒤 자세를 다시 세우기를 기다리는 중
    private boolean needRelease;
    private double releaseAngle;
    // 세운 자세로 보는 각도 (처음에는 extendedAngle, 1회 뒤에는 그때 쓴 다시 세움 각도)
    private double uprightAngle = Double.NaN;

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

    private double smoothedAngle = Double.NaN;
    private RepetitionCounter.Feedback feedback = RepetitionCounter.Feedback.NOT_VISIBLE;
    private RepetitionCounter.RejectedAttempt pendingRejection;

    public HoldCounter(ExerciseCriteria criteria) {
        this.criteria = criteria;
    }

    /**
     * @param jointAngle 판정 각도 (하체 스트레칭: 엉덩이 각도). 안 보이면 NaN
     * @param trunkLean  쓰지 않음 (유지형은 상체 점수를 따로 매기지 않음)
     * @param kneeAngle  펴야 하는 다리의 무릎 각도. 안 보이면 NaN (그때는 무릎 조건을 검사하지 않음)
     */
    @Override
    public RepetitionCounter.RepResult onFrame(long timestampMs, double jointAngle, double trunkLean,
                                               double kneeAngle) {
        if (Double.isNaN(jointAngle)) {
            if (isRepInProgress()) {
                attemptInvisible++;
            }
            handleInvisible(timestampMs);
            return null;
        }
        long gapMs = timestampMs - lastVisibleAtMs;
        if (isRepInProgress()) {
            gapMs = Math.min(gapMs, criteria.inRepMaxGapMs);
        }
        if (!Double.isNaN(lastAcceptedAngle)
                && Math.abs(jointAngle - lastAcceptedAngle) > criteria.maxAngleChange(gapMs)) {
            skippedFrameCount++;
            if (isRepInProgress()) {
                attemptSkipped++;
            }
            handleInvisible(timestampMs);
            return null;
        }
        lastAcceptedAngle = jointAngle;
        lastVisibleAtMs = timestampMs;
        smoothedAngle = pushAndAverage(jointAngle);

        if (needRelease) {
            if (smoothedAngle >= releaseAngle) {
                needRelease = false;
                feedback = RepetitionCounter.Feedback.START_BENDING;
            } else {
                feedback = RepetitionCounter.Feedback.RELEASE;
            }
            return null;
        }

        boolean kneeBent = criteria.isKneeBent(kneeAngle);
        boolean inRange = smoothedAngle <= criteria.bentAngle && !kneeBent;

        if (inRange) {
            if (holdStartedAtMs < 0) {
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
        if (holdStartedAtMs >= 0) {
            attemptAccepted++;
            if (timestampMs - lastInRangeAtMs > criteria.holdGraceMs) {
                abortHold(RepetitionCounter.RejectReason.RELEASED_EARLY);
            }
        }
        double upright = Double.isNaN(uprightAngle) ? criteria.extendedAngle : uprightAngle;
        if (kneeBent) {
            feedback = RepetitionCounter.Feedback.STRAIGHTEN_KNEE;
        } else if (holdStartedAtMs >= 0 || smoothedAngle <= upright - LEAN_STARTED_DEGREES) {
            feedback = RepetitionCounter.Feedback.BEND_MORE;
        } else {
            feedback = RepetitionCounter.Feedback.START_BENDING;
        }
        return null;
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
        releaseAngle = Math.min(criteria.extendedAngle, average + criteria.releaseRiseDegrees);
        uprightAngle = releaseAngle;
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
        uprightAngle = Double.NaN;
        clearHold();
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

    @Override
    public boolean isRepInProgress() {
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

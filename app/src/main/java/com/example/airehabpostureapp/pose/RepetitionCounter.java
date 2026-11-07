package com.example.airehabpostureapp.pose;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * 관절 각도로 반복 횟수를 세는 상태 머신 (Android 의존성 없는 순수 Java)
 *
 * 1회 = 편 상태(extendedAngle 이상) → bentAngle 이하까지 굽힘 → 다시 편 상태
 * - 판정에는 최근 smoothingWindow 프레임의 평균 각도를 쓴다. (한 프레임 튐 완화)
 * - 편 기준과 굽힘 기준 사이에 간격이 있어서, 경계에서 흔들려도 두 번 세지지 않는다.
 * - 굽히기 시작해서 다시 펼 때까지 minRepDurationMs 미만이면 흔들림으로 보고 세지 않는다.
 * - 동작 중 엉덩이 각도가 minHipAngle보다 작아지면(스쿼트처럼 엉덩이를 굽힘) 세지 않는다.
 * - 관절이 maxInvisibleMs 넘게 안 보이면 진행 중이던 동작은 버리고, 다시 편 상태부터 기다린다.
 * - 각도 연속성 검사: 직전에 받아들인 각도와 비교해 실제 무릎이 움직일 수 없을 만큼 튄 값은
 *   추적 오류(가려진 다리 대신 서 있는 다리 값이 들어온 경우 등)로 보고 그 프레임을 건너뛴다.
 * 세지 않고 버린 동작은 이유와 함께 takeRejectedAttempt()로 꺼낼 수 있다. (기준값 조정용)
 * 동작이 너무 빠르거나 동작 중 건너뛴 프레임이 많으면 takeMoveSlowlyHint()가 true가 된다. (천천히 안내용)
 *
 * 한 스레드에서만 호출해야 한다. (ExerciseActivity에서는 UI 스레드)
 */
public class RepetitionCounter {

    // 편 상태에서 이만큼도 굽히지 않은 작은 흔들림은 "버려진 동작"으로 기록하지 않음
    private static final double MIN_ATTEMPT_DEPTH_DEGREES = 10;

    /** 화면 안내 문구 종류 (문구 자체는 화면에서 strings.xml로 바꾼다) */
    public enum Feedback {
        // 관절이 안 보임
        NOT_VISIBLE,
        // 처음 시작 시 또는 동작을 놓친 뒤: 다리를 편 상태를 기다림
        STAND_STRAIGHT,
        // 편 상태에서 다음 동작을 기다림
        START_BENDING,
        // 굽히는 중인데 아직 목표 범위에 못 미침
        BEND_MORE,
        // 목표 범위까지 굽힘 → 이제 펴면 됨
        GOOD_STRAIGHTEN,
        // 상체가 허용 각도보다 많이 숙여짐
        KEEP_TRUNK_UPRIGHT,
        // 엉덩이를 굽힘 (스쿼트처럼 허벅지가 기움)
        THIGH_UPRIGHT
    }

    /** 세지 않고 버린 이유 */
    public enum RejectReason {
        // bentAngle까지 굽히지 않음
        SHALLOW,
        // minRepDurationMs보다 빨리 끝남
        TOO_FAST,
        // 엉덩이 각도가 minHipAngle보다 작아짐
        HIP_BENT,
        // 동작 중 관절이 maxInvisibleMs 넘게 안 보임
        LOST_TRACKING
    }

    /** 한 번의 동작 동안 받은 프레임 수 (기준값 조정 / 천천히 안내용) */
    public static final class FrameStats {
        // 판정에 쓴 프레임
        public final int accepted;
        // 연속성 검사로 건너뛴 프레임 (각도가 실제로 움직일 수 없을 만큼 튐)
        public final int skippedByJump;
        // 관절이 안 보여 건너뛴 프레임 (visibility 부족 / 사람 인식 안 됨)
        public final int invisible;

        FrameStats(int accepted, int skippedByJump, int invisible) {
            this.accepted = accepted;
            this.skippedByJump = skippedByJump;
            this.invisible = invisible;
        }

        public int total() {
            return accepted + skippedByJump + invisible;
        }

        /** 건너뛴 프레임 비율 (0~1) */
        public double missedRatio() {
            int total = total();
            return total == 0 ? 0 : (double) (skippedByJump + invisible) / total;
        }
    }

    /** 1회 동작 결과 */
    public static final class RepResult {
        public final int repNumber;
        // 이번 회차에 가장 많이 굽힌 각도 (평균 각도 기준)
        public final double minJointAngle;
        // 이번 회차에 가장 많이 숙인 상체 각도 (상체가 안 보였으면 NaN)
        public final double maxTrunkLean;
        // 이번 회차에 가장 작았던 엉덩이 각도 (안 보였으면 NaN)
        public final double minHipAngle;
        // 가장 많이 굽힌 각도 - 목표 각도 (+: 덜 굽힘, -: 더 굽힘)
        public final double jointError;
        // 상체 허용 각도를 넘은 만큼 (넘지 않으면 0, 안 보였으면 NaN)
        public final double trunkError;
        public final double accuracy;
        public final long durationMs;
        public final FrameStats frames;

        RepResult(int repNumber, double minJointAngle, double maxTrunkLean, double minHipAngle,
                  double jointError, double trunkError, double accuracy, long durationMs) {
            this(repNumber, minJointAngle, maxTrunkLean, minHipAngle, jointError, trunkError,
                    accuracy, durationMs, new FrameStats(0, 0, 0));
        }

        RepResult(int repNumber, double minJointAngle, double maxTrunkLean, double minHipAngle,
                  double jointError, double trunkError, double accuracy, long durationMs, FrameStats frames) {
            this.repNumber = repNumber;
            this.minJointAngle = minJointAngle;
            this.maxTrunkLean = maxTrunkLean;
            this.minHipAngle = minHipAngle;
            this.jointError = jointError;
            this.trunkError = trunkError;
            this.accuracy = accuracy;
            this.durationMs = durationMs;
            this.frames = frames;
        }
    }

    /** 세지 않고 버린 동작 (기준값 조정용 기록) */
    public static final class RejectedAttempt {
        public final RejectReason reason;
        public final double minJointAngle;
        public final double maxTrunkLean;
        public final double minHipAngle;
        public final long durationMs;
        public final FrameStats frames;

        RejectedAttempt(RejectReason reason, double minJointAngle, double maxTrunkLean,
                        double minHipAngle, long durationMs, FrameStats frames) {
            this.reason = reason;
            this.minJointAngle = minJointAngle;
            this.maxTrunkLean = maxTrunkLean;
            this.minHipAngle = minHipAngle;
            this.durationMs = durationMs;
            this.frames = frames;
        }
    }

    private final ExerciseCriteria criteria;
    private final Deque<Double> jointWindow = new ArrayDeque<>();
    private final Deque<Double> trunkWindow = new ArrayDeque<>();
    private final Deque<Double> hipWindow = new ArrayDeque<>();
    private final List<RepResult> results = new ArrayList<>();

    // 편 상태를 한 번 확인한 뒤부터 동작을 센다
    private boolean ready;
    // 편 상태에서 벗어나 굽히기 시작한 시각 (-1이면 동작 중 아님)
    private long bendStartedAtMs = -1;
    // 동작 중 편 기준 이상이 시작된 시각 (-1이면 아님). extendedHoldMs 이상 이어지면 1회 끝
    private long extendedSinceMs = -1;
    private boolean reachedBent;
    private double minJointAngle;
    private double maxTrunkLean;
    private double minHipAngle;
    // 진행 중인 동작의 프레임 수
    private int attemptAcceptedFrames;
    private int attemptSkippedFrames;
    private int attemptInvisibleFrames;
    private long lastVisibleAtMs = -1;
    // 연속성 검사 기준: 마지막으로 받아들인 원래 각도 (평균 전)
    private double lastAcceptedAngle = Double.NaN;
    // 연속성 검사로 건너뛴 프레임 수 (누적, 디버그 기록용)
    private int skippedFrameCount;
    private double smoothedJointAngle = Double.NaN;
    private Feedback feedback = Feedback.NOT_VISIBLE;
    private RejectedAttempt pendingRejection;
    private boolean moveSlowlyHint;

    public RepetitionCounter(ExerciseCriteria criteria) {
        this.criteria = criteria;
    }

    /** 엉덩이 각도 없이 판정 (엉덩이 규칙은 적용하지 않음) */
    public RepResult onFrame(long timestampMs, double jointAngle, double trunkLean) {
        return onFrame(timestampMs, jointAngle, trunkLean, Double.NaN);
    }

    /**
     * 프레임 하나를 반영한다.
     *
     * @param timestampMs 프레임 시각 (계속 증가)
     * @param jointAngle  판정할 관절 각도. 관절이 안 보이면 NaN
     * @param trunkLean   상체가 수직에서 기운 각도. 안 보이면 NaN
     * @param hipAngle    엉덩이 각도(어깨-엉덩이-무릎). 안 보이면 NaN
     * @return 이 프레임에서 1회가 끝났으면 그 결과, 아니면 null
     */
    public RepResult onFrame(long timestampMs, double jointAngle, double trunkLean, double hipAngle) {
        if (Double.isNaN(jointAngle)) {
            if (bendStartedAtMs >= 0) {
                attemptInvisibleFrames++;
            }
            handleInvisible(timestampMs);
            return null;
        }
        // 동작 진행 중에는 오래 건너뛰어도 허용 폭이 inRepMaxGapMs분 이상 커지지 않음
        // (굽히는 다리가 가려진 사이 서 있는 다리 값이 받아들여져 동작이 끊기는 것 방지)
        long gapMs = timestampMs - lastVisibleAtMs;
        if (bendStartedAtMs >= 0) {
            gapMs = Math.min(gapMs, criteria.inRepMaxGapMs);
        }
        if (!Double.isNaN(lastAcceptedAngle)
                && Math.abs(jointAngle - lastAcceptedAngle) > criteria.maxAngleChange(gapMs)) {
            // 실제로 움직일 수 없을 만큼 튐 → 안 보인 프레임과 같이 건너뜀 (오래 이어지면 허용 폭이 커져 다시 받아들임)
            skippedFrameCount++;
            if (bendStartedAtMs >= 0) {
                attemptSkippedFrames++;
            }
            handleInvisible(timestampMs);
            return null;
        }
        lastAcceptedAngle = jointAngle;
        lastVisibleAtMs = timestampMs;
        smoothedJointAngle = pushAndAverage(jointWindow, jointAngle);
        double smoothedTrunk = Double.isNaN(trunkLean) ? Double.NaN : pushAndAverage(trunkWindow, trunkLean);
        double smoothedHip = Double.isNaN(hipAngle) ? Double.NaN : pushAndAverage(hipWindow, hipAngle);

        if (!ready) {
            if (smoothedJointAngle >= criteria.extendedAngle) {
                ready = true;
                feedback = Feedback.START_BENDING;
            } else {
                feedback = Feedback.STAND_STRAIGHT;
            }
            return null;
        }

        if (smoothedJointAngle >= criteria.extendedAngle) {
            if (bendStartedAtMs >= 0) {
                // 편 기준을 extendedHoldMs 동안 유지해야 동작이 끝난 것으로 봄 (그 전에 다시 굽혀지면 같은 동작으로 이어짐)
                if (extendedSinceMs < 0) {
                    extendedSinceMs = timestampMs;
                }
                if (timestampMs - extendedSinceMs < criteria.extendedHoldMs) {
                    return null;
                }
            }
            RepResult result = null;
            if (bendStartedAtMs >= 0) {
                // 동작 시간은 편 기준에 처음 닿은 시각까지
                long durationMs = extendedSinceMs - bendStartedAtMs;
                FrameStats frames = currentFrameStats();
                RejectReason reason = rejectReason(durationMs);
                if (reason == null) {
                    result = completeRep(durationMs, frames);
                    if (frames.missedRatio() >= criteria.maxMissedFrameRatio) {
                        moveSlowlyHint = true;
                    }
                } else if (criteria.extendedAngle - minJointAngle >= MIN_ATTEMPT_DEPTH_DEGREES) {
                    pendingRejection = new RejectedAttempt(reason, minJointAngle, maxTrunkLean,
                            minHipAngle, durationMs, frames);
                    if (reason == RejectReason.TOO_FAST
                            || (reason == RejectReason.SHALLOW
                            && frames.missedRatio() >= criteria.maxMissedFrameRatio)) {
                        moveSlowlyHint = true;
                    }
                }
            }
            bendStartedAtMs = -1;
            extendedSinceMs = -1;
            reachedBent = false;
            feedback = Feedback.START_BENDING;
            return result;
        }

        // 편 기준 아래 → 유지 시간 측정은 처음부터 다시
        extendedSinceMs = -1;

        // 편 상태에서 벗어남 → 동작 진행 중
        if (bendStartedAtMs < 0) {
            bendStartedAtMs = timestampMs;
            minJointAngle = smoothedJointAngle;
            maxTrunkLean = Double.NaN;
            minHipAngle = Double.NaN;
            attemptAcceptedFrames = 0;
            attemptSkippedFrames = 0;
            attemptInvisibleFrames = 0;
        }
        attemptAcceptedFrames++;
        minJointAngle = Math.min(minJointAngle, smoothedJointAngle);
        if (!Double.isNaN(smoothedTrunk)) {
            maxTrunkLean = Double.isNaN(maxTrunkLean) ? smoothedTrunk : Math.max(maxTrunkLean, smoothedTrunk);
        }
        if (!Double.isNaN(smoothedHip)) {
            minHipAngle = Double.isNaN(minHipAngle) ? smoothedHip : Math.min(minHipAngle, smoothedHip);
        }
        if (smoothedJointAngle <= criteria.bentAngle) {
            reachedBent = true;
        }

        if (criteria.isHipTooBent(smoothedHip)) {
            feedback = Feedback.THIGH_UPRIGHT;
        } else if (!Double.isNaN(smoothedTrunk) && smoothedTrunk > criteria.maxTrunkLean) {
            feedback = Feedback.KEEP_TRUNK_UPRIGHT;
        } else if (smoothedJointAngle <= criteria.targetAngle + criteria.underBendTolerance) {
            feedback = Feedback.GOOD_STRAIGHTEN;
        } else {
            feedback = Feedback.BEND_MORE;
        }
        return null;
    }

    // 다시 편 상태로 돌아왔을 때 이번 동작을 셀 수 없는 이유 (셀 수 있으면 null)
    private RejectReason rejectReason(long durationMs) {
        if (!reachedBent) {
            return RejectReason.SHALLOW;
        }
        if (durationMs < criteria.minRepDurationMs) {
            return RejectReason.TOO_FAST;
        }
        if (criteria.isHipTooBent(minHipAngle)) {
            return RejectReason.HIP_BENT;
        }
        return null;
    }

    private FrameStats currentFrameStats() {
        return new FrameStats(attemptAcceptedFrames, attemptSkippedFrames, attemptInvisibleFrames);
    }

    private void handleInvisible(long timestampMs) {
        boolean neverVisible = lastVisibleAtMs < 0;
        if (neverVisible || timestampMs - lastVisibleAtMs > criteria.maxInvisibleMs) {
            // 오래 안 보이면 진행 중이던 동작과 평균값을 버리고 편 상태부터 다시 기다림
            if (bendStartedAtMs >= 0 && criteria.extendedAngle - minJointAngle >= MIN_ATTEMPT_DEPTH_DEGREES) {
                pendingRejection = new RejectedAttempt(RejectReason.LOST_TRACKING, minJointAngle,
                        maxTrunkLean, minHipAngle, timestampMs - bendStartedAtMs, currentFrameStats());
            }
            ready = false;
            bendStartedAtMs = -1;
            extendedSinceMs = -1;
            reachedBent = false;
            jointWindow.clear();
            trunkWindow.clear();
            hipWindow.clear();
            smoothedJointAngle = Double.NaN;
            lastAcceptedAngle = Double.NaN;
            feedback = Feedback.NOT_VISIBLE;
        }
        // 짧게 안 보인 프레임은 건너뛰고 상태 유지
    }

    private RepResult completeRep(long durationMs, FrameStats frames) {
        RepResult result = new RepResult(
                results.size() + 1,
                minJointAngle,
                maxTrunkLean,
                minHipAngle,
                criteria.jointError(minJointAngle),
                criteria.trunkError(maxTrunkLean),
                criteria.repAccuracy(minJointAngle, maxTrunkLean),
                durationMs,
                frames);
        results.add(result);
        return result;
    }

    private double pushAndAverage(Deque<Double> window, double value) {
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

    /** "처음부터 다시": 센 횟수와 진행 상태를 모두 초기화 */
    public void reset() {
        results.clear();
        ready = false;
        bendStartedAtMs = -1;
        extendedSinceMs = -1;
        reachedBent = false;
        lastVisibleAtMs = -1;
        jointWindow.clear();
        trunkWindow.clear();
        hipWindow.clear();
        smoothedJointAngle = Double.NaN;
        lastAcceptedAngle = Double.NaN;
        skippedFrameCount = 0;
        feedback = Feedback.NOT_VISIBLE;
        pendingRejection = null;
        moveSlowlyHint = false;
    }

    /** 각도 연속성 검사로 건너뛴 프레임 수 (누적, 디버그 기록용) */
    public int getSkippedFrameCount() {
        return skippedFrameCount;
    }

    /** 마지막으로 받아들인 원래 각도 (평균 전, 없으면 NaN). 동작 중 다리 고르기의 기준으로 사용 */
    public double getLastAcceptedAngle() {
        return lastAcceptedAngle;
    }

    /** 마지막으로 버려진 동작을 꺼낸다 (한 번 꺼내면 null). 기준값 조정용 디버그 기록에 사용 */
    public RejectedAttempt takeRejectedAttempt() {
        RejectedAttempt rejected = pendingRejection;
        pendingRejection = null;
        return rejected;
    }

    /**
     * 방금 끝난 동작이 너무 빨랐거나(TOO_FAST), 동작 중 건너뛴 프레임이 많았으면 true (한 번 꺼내면 false).
     * 화면에서 "천천히 움직여 주세요" 안내를 잠시 띄우는 데 사용
     */
    public boolean takeMoveSlowlyHint() {
        boolean hint = moveSlowlyHint;
        moveSlowlyHint = false;
        return hint;
    }

    public int getCompletedReps() {
        return results.size();
    }

    public List<RepResult> getResults() {
        return Collections.unmodifiableList(results);
    }

    /** 동작 진행 중이면 true (이 동안에는 판정할 다리를 바꾸지 않는다) */
    public boolean isRepInProgress() {
        return bendStartedAtMs >= 0;
    }

    public Feedback getFeedback() {
        return feedback;
    }

    public double getSmoothedJointAngle() {
        return smoothedJointAngle;
    }
}

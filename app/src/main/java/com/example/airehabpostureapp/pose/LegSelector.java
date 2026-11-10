package com.example.airehabpostureapp.pose;

/**
 * 매 프레임 판정에 쓸 다리를 고른다. (Android 의존성 없는 순수 Java)
 *
 * 옆모습에서는 두 다리가 겹쳐 보여 MediaPipe가 왼쪽/오른쪽 이름을 순간적으로 바꿔 붙이는 경우가 있다.
 * 그래서 이름(L/R)으로 다리를 고정하지 않고, "지금 더 많이 굽혀진 다리"를 움직이는 다리로 본다.
 * (무릎 굽힘 운동 A안은 한쪽 다리만 굽히므로, 이름이 바뀌어도 굽히는 다리를 계속 따라간다)
 *
 * - 엉덩이 / 무릎 / 발목 visibility가 모두 MIN_VISIBILITY 이상인 다리만 후보로 쓴다.
 *   (가려진 다리는 MediaPipe가 짐작한 값이라, 가만히 서 있는데 굽힌 것처럼 튈 수 있음)
 * - 두 다리 모두 후보가 아니면 null → 그 프레임은 판정에서 건너뛴다.
 * - 평균(흔들림 완화)은 고른 뒤의 각도에 RepetitionCounter가 적용한다. (L/R 값이 섞이지 않게)
 *
 * 운동 화면은 다리 고정 방식을 쓴다: 정옆으로 서서 두 다리의 visibility 차이가 0.2 이상인 상태가
 * 1초 이어지면 더 잘 보이는 다리(카메라 쪽)를 정하고
 * 그 뒤로는 그 다리만 쓴다 (observeForLock / selectLocked). 카메라에서 먼 다리는 가려져 값이 튀기 쉬워서
 * "더 많이 굽혀진 다리"로 고르면 먼 다리의 튄 값이 동작으로 세어질 수 있기 때문.
 * select / selectClosest는 고정하지 않는 방식으로, 단위 테스트에서 기존 동작 확인용으로 남겨 둔다.
 */
public class LegSelector {

    public static final float MIN_VISIBILITY = 0.5f;

    /** 한쪽 다리의 한 프레임 측정값 */
    public static final class LegSample {
        public final String label;
        // 판정 관절 각도 (엉덩이-무릎-발목)
        public final double jointAngle;
        // 상체가 수직에서 기운 각도 (안 보이면 NaN)
        public final double trunkLean;
        // 엉덩이 각도 (어깨-엉덩이-무릎, 안 보이면 NaN)
        public final double hipAngle;
        // 엉덩이 / 무릎 / 발목 visibility 중 가장 낮은 값
        public final float minVisibility;
        // 엉덩이 / 무릎 / 발목 visibility 평균 (운동하는 다리를 정할 때 사용)
        public final float averageVisibility;
        // 무릎 각도 (엉덩이-무릎-발목, 안 보이면 NaN). 무릎을 판정하는 운동이면 jointAngle과 같음
        public final double kneeAngle;

        public LegSample(String label, double jointAngle, double trunkLean, double hipAngle, float minVisibility) {
            this(label, jointAngle, trunkLean, hipAngle, minVisibility, minVisibility);
        }

        public LegSample(String label, double jointAngle, double trunkLean, double hipAngle,
                         float minVisibility, float averageVisibility) {
            this(label, jointAngle, trunkLean, hipAngle, minVisibility, averageVisibility, jointAngle);
        }

        public LegSample(String label, double jointAngle, double trunkLean, double hipAngle,
                         float minVisibility, float averageVisibility, double kneeAngle) {
            this.label = label;
            this.jointAngle = jointAngle;
            this.trunkLean = trunkLean;
            this.hipAngle = hipAngle;
            this.minVisibility = minVisibility;
            this.averageVisibility = averageVisibility;
            this.kneeAngle = kneeAngle;
        }

        /** 엉덩이 / 무릎 / 발목이 모두 잘 보이고 각도를 계산할 수 있으면 true */
        public boolean isCandidate() {
            return minVisibility >= MIN_VISIBILITY && !Double.isNaN(jointAngle);
        }
    }

    /**
     * 운동하는 다리를 정하는 조건: 두 다리의 평균 visibility 차이가 이 값 이상
     * (카메라를 보고 서면 두 다리가 비슷하게 보여 차이가 0.02~0.03, 정옆으로 서면 0.5 이상)
     */
    public static final float LOCK_MIN_VISIBILITY_GAP = 0.2f;
    /** 위 조건이 이 시간 이상 이어져야 정함 (몸을 돌리는 중간에 잠깐 차이가 나는 경우 제외) */
    public static final long LOCK_HOLD_MS = 1000;

    private String lastLabel;
    private int switchCount;

    // 운동하는 다리 고정: 정해지기 전에는 null
    private String lockedLabel;
    // 다리를 정하는 중인 구간 (조건을 만족하는 프레임이 이어지는 동안)의 더 잘 보이는 다리 / 시작 시각 / visibility 합계
    private String lockCandidateLabel;
    private long lockWindowStartMs = -1;
    private double leftVisibilitySum;
    private double rightVisibilitySum;
    private int lockFrames;
    // 정해질 때의 다리별 평균 visibility (디버그 기록용)
    private double lockedLeftAverage = Double.NaN;
    private double lockedRightAverage = Double.NaN;

    /**
     * 운동하는 다리를 정하기 위한 프레임 하나를 반영한다.
     * 아래 조건을 모두 만족하는 프레임이 LOCK_HOLD_MS 이상 이어지면 더 잘 보이는 다리(카메라 쪽)로 정한다.
     * - 서 있음: 한쪽 다리 이상이 잘 보이고 편 기준 각도 이상
     * - 두 다리의 엉덩이·무릎·발목 평균 visibility 차이가 LOCK_MIN_VISIBILITY_GAP 이상 (정옆으로 섬)
     * - 더 잘 보이는 다리가 같은 쪽으로 유지됨
     * 조건이 끊기면 처음부터 다시 잰다. (카메라를 보고 서 있거나 몸을 돌리는 중이면 정하지 않음)
     *
     * @return 이 프레임에서 다리가 정해졌으면 true
     */
    public boolean observeForLock(LegSample left, LegSample right, long timestampMs, double extendedAngle) {
        if (lockedLabel != null) {
            return false;
        }
        String higherLabel = moreVisibleLabel(left, right);
        if (!isStanding(left, right, extendedAngle) || higherLabel == null) {
            clearLockWindow();
            return false;
        }
        if (!higherLabel.equals(lockCandidateLabel)) {
            // 더 잘 보이는 다리가 바뀌면 그 프레임부터 다시 잼
            clearLockWindow();
            lockCandidateLabel = higherLabel;
        }
        if (lockWindowStartMs < 0) {
            lockWindowStartMs = timestampMs;
        }
        leftVisibilitySum += left.averageVisibility;
        rightVisibilitySum += right.averageVisibility;
        lockFrames++;
        if (timestampMs - lockWindowStartMs < LOCK_HOLD_MS) {
            return false;
        }
        lockedLeftAverage = leftVisibilitySum / lockFrames;
        lockedRightAverage = rightVisibilitySum / lockFrames;
        lockedLabel = lockCandidateLabel;
        clearLockWindow();
        return true;
    }

    // 평균 visibility 차이가 기준 이상이면 더 잘 보이는 다리 이름, 아니면 null
    private static String moreVisibleLabel(LegSample left, LegSample right) {
        float gap = left.averageVisibility - right.averageVisibility;
        if (gap >= LOCK_MIN_VISIBILITY_GAP) {
            return left.label;
        }
        if (-gap >= LOCK_MIN_VISIBILITY_GAP) {
            return right.label;
        }
        return null;
    }

    private static boolean isStanding(LegSample left, LegSample right, double extendedAngle) {
        return (left.isCandidate() && left.jointAngle >= extendedAngle)
                || (right.isCandidate() && right.jointAngle >= extendedAngle);
    }

    private void clearLockWindow() {
        lockCandidateLabel = null;
        lockWindowStartMs = -1;
        leftVisibilitySum = 0;
        rightVisibilitySum = 0;
        lockFrames = 0;
    }

    /**
     * 다리가 정해진 뒤의 고르기: 정해진 다리가 잘 보이면 그 다리, 아니면 null (반대쪽 다리 값은 쓰지 않음)
     */
    public LegSample selectLocked(LegSample left, LegSample right) {
        if (lockedLabel == null) {
            return null;
        }
        LegSample sample = lockedLabel.equals(left.label) ? left : right;
        return sample.isCandidate() ? remember(sample) : null;
    }

    public boolean isLocked() {
        return lockedLabel != null;
    }

    /** 정해진 다리 이름 (L / R, 아직 없으면 null) */
    public String getLockedLabel() {
        return lockedLabel;
    }

    public double getLockedLeftAverage() {
        return lockedLeftAverage;
    }

    public double getLockedRightAverage() {
        return lockedRightAverage;
    }

    private LegSample remember(LegSample chosen) {
        if (lastLabel != null && !lastLabel.equals(chosen.label)) {
            switchCount++;
        }
        lastLabel = chosen.label;
        return chosen;
    }

    /** 마지막으로 고른 다리 이름 (아직 없으면 null) */
    public String getLastLabel() {
        return lastLabel;
    }

    /** resetSwitchCount() 이후 고른 다리가 L↔R로 바뀐 횟수 (디버그 기록용) */
    public int getSwitchCount() {
        return switchCount;
    }

    public void resetSwitchCount() {
        switchCount = 0;
    }

    /** 처음 상태로 (운동하는 다리도 다시 정함) */
    public void reset() {
        lastLabel = null;
        switchCount = 0;
        lockedLabel = null;
        lockedLeftAverage = Double.NaN;
        lockedRightAverage = Double.NaN;
        clearLockWindow();
    }
}

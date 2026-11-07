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

        public LegSample(String label, double jointAngle, double trunkLean, double hipAngle, float minVisibility) {
            this.label = label;
            this.jointAngle = jointAngle;
            this.trunkLean = trunkLean;
            this.hipAngle = hipAngle;
            this.minVisibility = minVisibility;
        }

        /** 엉덩이 / 무릎 / 발목이 모두 잘 보이고 각도를 계산할 수 있으면 true */
        public boolean isCandidate() {
            return minVisibility >= MIN_VISIBILITY && !Double.isNaN(jointAngle);
        }
    }

    private String lastLabel;
    private int switchCount;

    /**
     * @return 판정에 쓸 다리 (더 많이 굽혀진 후보). 후보가 없으면 null
     */
    public LegSample select(LegSample left, LegSample right) {
        boolean leftOk = left != null && left.isCandidate();
        boolean rightOk = right != null && right.isCandidate();
        LegSample chosen;
        if (leftOk && rightOk) {
            chosen = left.jointAngle <= right.jointAngle ? left : right;
        } else if (leftOk) {
            chosen = left;
        } else if (rightOk) {
            chosen = right;
        } else {
            return null;
        }
        return remember(chosen);
    }

    /**
     * 동작 진행 중에 쓰는 고르기: 후보 중 직전 각도(referenceAngle)와 가장 가까운 다리.
     * L/R 이름이 바뀌어도 굽힌 값이 붙은 쪽이 가장 가까우므로 굽히는 다리를 계속 따라간다.
     * 굽히는 다리가 가려져 서 있는 다리만 남으면 그 값을 돌려주지만, 직전 각도와 크게 다르므로
     * RepetitionCounter의 연속성 검사에서 건너뛰어진다.
     * referenceAngle이 NaN이면 select()와 같이 더 많이 굽혀진 다리를 고른다.
     */
    public LegSample selectClosest(LegSample left, LegSample right, double referenceAngle) {
        if (Double.isNaN(referenceAngle)) {
            return select(left, right);
        }
        boolean leftOk = left != null && left.isCandidate();
        boolean rightOk = right != null && right.isCandidate();
        LegSample chosen;
        if (leftOk && rightOk) {
            chosen = Math.abs(left.jointAngle - referenceAngle) <= Math.abs(right.jointAngle - referenceAngle)
                    ? left : right;
        } else if (leftOk) {
            chosen = left;
        } else if (rightOk) {
            chosen = right;
        } else {
            return null;
        }
        return remember(chosen);
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

    public void reset() {
        lastLabel = null;
        switchCount = 0;
    }
}

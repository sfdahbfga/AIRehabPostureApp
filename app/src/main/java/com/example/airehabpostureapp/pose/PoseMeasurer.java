package com.example.airehabpostureapp.pose;

import androidx.annotation.Nullable;

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;

import java.util.List;

/**
 * MediaPipe 관절 좌표(정규화 0~1)로 한쪽 다리의 관절 각도를 계산한다. (Android 의존성 없는 순수 Java)
 * M12: ExerciseActivity에 있던 measureSide / calculateTrunkLean / calculateJointAngle을 동작 그대로 옮김.
 *
 * MediaPipe는 관절 위치만 주고, 각도 계산과 판정은 이 클래스와 ExerciseCriteria / 카운터가 한다.
 */
public final class PoseMeasurer {

    // 이 값보다 visibility가 낮은 관절은 "안 보임"으로 본다 (뼈대 그리기 PoseOverlayView도 같은 기준)
    public static final float MIN_VISIBILITY = 0.5f;

    // 상체 기울기 계산용: 엉덩이 바로 위 가상의 점까지 거리 (픽셀, 방향만 쓰므로 값은 상관없음)
    private static final double VERTICAL_REFERENCE_PX = 100;

    private PoseMeasurer() {
    }

    public static boolean isVisible(NormalizedLandmark landmark) {
        return landmark.visibility().orElse(0f) >= MIN_VISIBILITY;
    }

    // 한쪽 다리의 측정값: 판정 각도 / 상체 기울기 / 엉덩이 각도 / 무릎 각도 + 판정 관절 세 점 중 가장 낮은 visibility
    // judgeHip: 판정 각도가 엉덩이 각도(어깨-엉덩이-무릎)인 운동 (하체 스트레칭). 아니면 무릎 각도(엉덩이-무릎-발목)
    public static LegSelector.LegSample measureSide(@Nullable List<NormalizedLandmark> pose,
                                                    ExerciseCriteria.Side side, boolean judgeHip,
                                                    int imageWidth, int imageHeight) {
        if (pose == null) {
            return new LegSelector.LegSample(side.label, Double.NaN, Double.NaN, Double.NaN, 0f, 0f, Double.NaN);
        }
        double kneeAngle = calculateJointAngle(pose, side.first, side.middle, side.last, imageWidth, imageHeight);
        double trunkLean = calculateTrunkLean(pose, side.shoulder, side.first, imageWidth, imageHeight);
        double hipAngle = calculateJointAngle(pose, side.shoulder, side.first, side.middle, imageWidth, imageHeight);
        // 판정 관절 세 점의 visibility (무릎 판정: 엉덩이·무릎·발목, 엉덩이 판정: 어깨·엉덩이·무릎)
        float firstVisibility = visibility(pose, judgeHip ? side.shoulder : side.first);
        float middleVisibility = visibility(pose, judgeHip ? side.first : side.middle);
        float lastVisibility = visibility(pose, judgeHip ? side.middle : side.last);
        float minVisibility = Math.min(firstVisibility, Math.min(middleVisibility, lastVisibility));
        float averageVisibility = (firstVisibility + middleVisibility + lastVisibility) / 3f;
        return new LegSelector.LegSample(side.label, judgeHip ? hipAngle : kneeAngle, trunkLean, hipAngle,
                minVisibility, averageVisibility, kneeAngle);
    }

    // 앞쪽(무릎 / 발목 방향)을 정할 때 엉덩이와 이만큼(이미지 너비 비율) 이상 떨어져 있어야 함
    private static final double MIN_FORWARD_OFFSET = 0.02;

    /**
     * 앞으로 숙인 상체 기울기 (하체 스트레칭 판정 값): 어깨-엉덩이 선이 수직에서 기운 각도, 앞으로 숙임 +, 뒤로 기댐 -
     * - 다리를 정하지 않고 프레임마다 어깨·엉덩이가 잘 보이는(visibility 0.5 이상) 쪽 중 평균 visibility가 높은 쪽을 쓴다.
     * - 앞쪽 = 그쪽 무릎(가까이 붙어 있으면 발목)이 엉덩이보다 있는 방향 (앉아서 다리를 앞으로 뻗음)
     * - tiltDegrees: 휴대폰이 기운 만큼 이미지의 수직이 돌아간 각도 (PhoneTilt.imageVerticalOffset). 모르면 0
     * 계산할 수 없으면 NaN
     */
    public static double measureForwardLean(@Nullable List<NormalizedLandmark> pose,
                                            ExerciseCriteria.Side left, ExerciseCriteria.Side right,
                                            int imageWidth, int imageHeight, double tiltDegrees) {
        if (pose == null) {
            return Double.NaN;
        }
        ExerciseCriteria.Side side = betterTrunkSide(pose, left, right);
        if (side == null) {
            return Double.NaN;
        }
        NormalizedLandmark shoulder = pose.get(side.shoulder);
        NormalizedLandmark hip = pose.get(side.first);
        int forward = forwardDirection(pose, side, hip);
        if (forward == 0) {
            return Double.NaN;
        }
        return signedForwardLean(shoulder.x() * imageWidth, shoulder.y() * imageHeight,
                hip.x() * imageWidth, hip.y() * imageHeight, forward, tiltDegrees);
    }

    /**
     * 순수 계산: 엉덩이 → 어깨 선이 이미지 위쪽에서 기운 각도(이미지 오른쪽 +)에 휴대폰 기울기를 보정하고
     * 앞쪽 방향(forward: 이미지 오른쪽 +1 / 왼쪽 -1)으로 부호를 맞춘다. 앞으로 숙임 +
     */
    public static double signedForwardLean(double shoulderX, double shoulderY, double hipX, double hipY,
                                           int forward, double tiltDegrees) {
        double imageAngle = Math.toDegrees(Math.atan2(shoulderX - hipX, hipY - shoulderY));
        double trueAngle = imageAngle + tiltDegrees;
        // -180~180 범위로
        trueAngle = ((trueAngle + 540) % 360) - 180;
        return trueAngle * forward;
    }

    @Nullable
    private static ExerciseCriteria.Side betterTrunkSide(List<NormalizedLandmark> pose,
                                                         ExerciseCriteria.Side left, ExerciseCriteria.Side right) {
        float leftScore = trunkVisibility(pose, left);
        float rightScore = trunkVisibility(pose, right);
        if (leftScore < 0 && rightScore < 0) {
            return null;
        }
        return leftScore >= rightScore ? left : right;
    }

    // 어깨·엉덩이가 모두 잘 보이면 평균 visibility, 아니면 -1
    private static float trunkVisibility(List<NormalizedLandmark> pose, ExerciseCriteria.Side side) {
        if (pose.size() <= Math.max(side.shoulder, side.first)) {
            return -1;
        }
        float shoulder = visibility(pose, side.shoulder);
        float hip = visibility(pose, side.first);
        if (shoulder < MIN_VISIBILITY || hip < MIN_VISIBILITY) {
            return -1;
        }
        return (shoulder + hip) / 2f;
    }

    // 앞쪽 방향: 무릎(엉덩이와 너무 가까우면 발목)이 엉덩이보다 이미지 오른쪽이면 +1, 왼쪽이면 -1, 알 수 없으면 0
    private static int forwardDirection(List<NormalizedLandmark> pose, ExerciseCriteria.Side side,
                                        NormalizedLandmark hip) {
        for (int index : new int[]{side.middle, side.last}) {
            if (index < pose.size()) {
                double offset = pose.get(index).x() - hip.x();
                if (Math.abs(offset) >= MIN_FORWARD_OFFSET) {
                    return offset > 0 ? 1 : -1;
                }
            }
        }
        return 0;
    }

    /**
     * 상체 기울기를 쓸 쪽: 상체 기울기를 계산할 수 있는(어깨·엉덩이가 잘 보이는) 쪽 중 평균 visibility가 높은 쪽.
     * 두 쪽 모두 계산할 수 없으면 null (다리를 정하지 않고 프레임마다 고름)
     */
    @Nullable
    public static LegSelector.LegSample betterTrunkSide(LegSelector.LegSample left, LegSelector.LegSample right) {
        boolean leftOk = !Double.isNaN(left.trunkLean);
        boolean rightOk = !Double.isNaN(right.trunkLean);
        if (leftOk && rightOk) {
            return left.averageVisibility >= right.averageVisibility ? left : right;
        }
        return leftOk ? left : rightOk ? right : null;
    }

    /**
     * 잘 보이는(엉덩이·무릎·발목 visibility 0.5 이상) 다리 중 더 곧게 펴진 다리의 무릎 각도. 없으면 NaN
     * (스트레칭하는 다리는 펴져 있고 반대쪽은 굽혀져 있음)
     */
    public static double straighterKneeAngle(LegSelector.LegSample left, LegSelector.LegSample right) {
        if (Double.isNaN(left.kneeAngle)) {
            return right.kneeAngle;
        }
        if (Double.isNaN(right.kneeAngle)) {
            return left.kneeAngle;
        }
        return Math.max(left.kneeAngle, right.kneeAngle);
    }

    private static float visibility(List<NormalizedLandmark> pose, int index) {
        return index < pose.size() ? pose.get(index).visibility().orElse(0f) : 0f;
    }

    // 상체 기울기: 엉덩이 → 어깨 선이 수직선과 이루는 각도 (똑바로 서면 0도)
    // 엉덩이 바로 위에 가상의 점을 두고 어깨 - 엉덩이 - 가상의 점 각도를 계산 (휴대폰을 세워 둔다고 가정)
    public static double calculateTrunkLean(List<NormalizedLandmark> pose, int shoulderIndex, int hipIndex,
                                            int imageWidth, int imageHeight) {
        if (pose.size() <= Math.max(shoulderIndex, hipIndex)) {
            return Double.NaN;
        }
        NormalizedLandmark shoulder = pose.get(shoulderIndex);
        NormalizedLandmark hip = pose.get(hipIndex);
        if (!isVisible(shoulder) || !isVisible(hip)) {
            return Double.NaN;
        }
        double hipX = hip.x() * imageWidth;
        double hipY = hip.y() * imageHeight;
        // 이미지 좌표는 y가 아래로 증가하므로 "위"는 y가 작은 쪽
        return JointAngleCalculator.calculateAngle(
                shoulder.x() * imageWidth, shoulder.y() * imageHeight,
                hipX, hipY,
                hipX, hipY - VERTICAL_REFERENCE_PX);
    }

    // 세 관절 중 하나라도 잘 안 보이면(visibility < 0.5) NaN
    // 정규화 좌표(0~1)를 픽셀 좌표로 바꿔 계산 (4:3 이미지에서 가로/세로 비율 왜곡 방지)
    public static double calculateJointAngle(@Nullable List<NormalizedLandmark> pose,
                                             int first, int middle, int last,
                                             int imageWidth, int imageHeight) {
        if (pose == null || pose.size() <= Math.max(first, Math.max(middle, last))) {
            return Double.NaN;
        }
        NormalizedLandmark a = pose.get(first);
        NormalizedLandmark b = pose.get(middle);
        NormalizedLandmark c = pose.get(last);
        if (!isVisible(a) || !isVisible(b) || !isVisible(c)) {
            return Double.NaN;
        }
        return JointAngleCalculator.calculateAngle(
                a.x() * imageWidth, a.y() * imageHeight,
                b.x() * imageWidth, b.y() * imageHeight,
                c.x() * imageWidth, c.y() * imageHeight);
    }
}

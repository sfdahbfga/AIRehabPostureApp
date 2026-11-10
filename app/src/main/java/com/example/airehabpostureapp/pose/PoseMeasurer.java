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

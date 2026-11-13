package com.example.airehabpostureapp.pose;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 하체 스트레칭은 다리를 정하지 않는다 (1차 실측에서 먼 다리로 고정되어 유지 시간이 쌓이지 않았던 문제).
 * 프레임마다 어깨·엉덩이가 더 잘 보이는 쪽의 "앞으로 숙인 상체 기울기"를 판정 값으로 쓴다.
 * - 앞쪽 = 무릎이 엉덩이보다 있는 방향 → 카메라 기준 왼쪽 / 오른쪽 어느 쪽을 보고 앉아도 앞으로 숙임 +
 * - 뒤로 기대면 - (1차 실측: 다리를 펴고 똑바로 앉으면 뒤로 약 23도)
 * - 앉는 중 몸 방향을 바꿔도 바로 그 프레임의 잘 보이는 쪽으로 측정
 */
public class StretchLegLockTest {

    private static final int WIDTH = 480;
    private static final int HEIGHT = 640;
    private static final ExerciseCriteria STRETCH = ExerciseCriteria.SEATED_HAMSTRING_STRETCH;

    private static NormalizedLandmark point(double xPx, double yPx, float visibility) {
        return NormalizedLandmark.create((float) (xPx / WIDTH), (float) (yPx / HEIGHT), 0f,
                Optional.of(visibility), Optional.of(visibility));
    }

    private static List<NormalizedLandmark> emptyPose() {
        List<NormalizedLandmark> pose = new ArrayList<>();
        for (int i = 0; i < 33; i++) {
            pose.add(point(0, 0, 0f));
        }
        return pose;
    }

    /**
     * 옆으로 앉은 자세: 엉덩이(240,400)에서 상체 길이 200px로 leanDegrees만큼 앞으로 숙임 (뒤로 기대면 -)
     * facing = +1이면 이미지 오른쪽을 보고 앉음(무릎이 오른쪽), -1이면 왼쪽
     */
    private static void seated(List<NormalizedLandmark> pose, ExerciseCriteria.Side side, int facing,
                               double leanDegrees, float visibility) {
        double rad = Math.toRadians(leanDegrees);
        double shoulderX = 240 + facing * 200 * Math.sin(rad);
        double shoulderY = 400 - 200 * Math.cos(rad);
        pose.set(side.shoulder, point(shoulderX, shoulderY, visibility));
        pose.set(side.first, point(240, 400, visibility));
        pose.set(side.middle, point(240 + facing * 150, 450, visibility));
        pose.set(side.last, point(240 + facing * 300, 520, visibility));
    }

    private static double lean(List<NormalizedLandmark> pose) {
        return PoseMeasurer.measureForwardLean(pose, STRETCH.leftSide, STRETCH.rightSide, WIDTH, HEIGHT, 0);
    }

    @Test
    public void forwardLeanPositive_whicheverWaySeated() {
        for (int facing : new int[]{1, -1}) {
            List<NormalizedLandmark> pose = emptyPose();
            seated(pose, ExerciseCriteria.RIGHT_LEG, facing, 30, 0.95f);
            assertEquals("facing " + facing, 30, lean(pose), 0.5);
        }
    }

    @Test
    public void leaningBack_isNegative() {
        List<NormalizedLandmark> pose = emptyPose();
        seated(pose, ExerciseCriteria.RIGHT_LEG, 1, -23, 0.95f);
        assertEquals(-23, lean(pose), 0.5);
    }

    @Test
    public void usesBetterVisibleSide_eachFrame_noLock() {
        // 22:48 실측처럼 처음에는 L이 잘 보이다가(왼쪽을 보고 앉음) 방향을 바꿔 R이 잘 보임
        List<NormalizedLandmark> first = emptyPose();
        seated(first, ExerciseCriteria.LEFT_LEG, -1, 10, 0.94f);
        seated(first, ExerciseCriteria.RIGHT_LEG, -1, 40, 0.43f);
        assertEquals(10, lean(first), 0.5);

        List<NormalizedLandmark> turned = emptyPose();
        seated(turned, ExerciseCriteria.LEFT_LEG, 1, 40, 0.33f);
        seated(turned, ExerciseCriteria.RIGHT_LEG, 1, 48, 0.92f);
        assertEquals(48, lean(turned), 0.5);
    }

    @Test
    public void trunkHidden_onBothSides_isNaN() {
        List<NormalizedLandmark> pose = emptyPose();
        seated(pose, ExerciseCriteria.RIGHT_LEG, 1, 30, 0.3f);
        assertTrue(Double.isNaN(lean(pose)));
        assertTrue(Double.isNaN(PoseMeasurer.measureForwardLean(null, STRETCH.leftSide, STRETCH.rightSide,
                WIDTH, HEIGHT, 0)));
    }

    @Test
    public void forwardFromAnkle_whenKneeRightBelowHip() {
        List<NormalizedLandmark> pose = emptyPose();
        seated(pose, ExerciseCriteria.RIGHT_LEG, 1, 25, 0.95f);
        // 무릎이 엉덩이 바로 아래(같은 x)여도 발목이 앞에 있으면 앞쪽을 알 수 있음
        pose.set(ExerciseCriteria.RIGHT_KNEE, point(240, 480, 0.95f));
        assertEquals(25, lean(pose), 0.5);
    }

    @Test
    public void realLogCase_turnedWhileSitting_holdStillCounted() {
        // 고치기 전: 먼 다리(L)로 고정되어 R로 25초 숙여도 0회 / 이제는 잘 보이는 쪽(R)으로 바로 측정
        HoldCounter counter = new HoldCounter(STRETCH);
        long t = 0;
        double[][] phases = {{10, 2000, -1}, {0, 5000, 1}, {48, 25000, 1}, {0, 3000, 1}};
        for (double[] phase : phases) {
            for (long elapsed = 0; elapsed < phase[1]; elapsed += 33) {
                List<NormalizedLandmark> pose = emptyPose();
                boolean leftVisible = phase[2] < 0;
                seated(pose, ExerciseCriteria.LEFT_LEG, (int) phase[2], phase[0], leftVisible ? 0.94f : 0.33f);
                seated(pose, ExerciseCriteria.RIGHT_LEG, (int) phase[2], phase[0], leftVisible ? 0.43f : 0.92f);
                counter.onFrame(t, lean(pose), Double.NaN, 175);
                t += 33;
            }
        }
        assertEquals(1, counter.getCompletedReps());
    }
}

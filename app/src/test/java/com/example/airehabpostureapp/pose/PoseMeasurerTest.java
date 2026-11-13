package com.example.airehabpostureapp.pose;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// M12: ExerciseActivity에서 옮긴 관절 각도 계산 (동작 그대로인지 확인)
public class PoseMeasurerTest {

    private static final double DELTA = 0.01;
    // 4:3 분석 이미지를 세로로 돌린 크기
    private static final int WIDTH = 480;
    private static final int HEIGHT = 640;

    private static NormalizedLandmark point(double xPx, double yPx, float visibility) {
        return NormalizedLandmark.create((float) (xPx / WIDTH), (float) (yPx / HEIGHT), 0f,
                Optional.of(visibility), Optional.of(visibility));
    }

    // 33개 관절, 기본은 모두 안 보임(visibility 0). 필요한 관절만 채운다
    private static List<NormalizedLandmark> emptyPose() {
        List<NormalizedLandmark> pose = new ArrayList<>();
        for (int i = 0; i < 33; i++) {
            pose.add(point(0, 0, 0f));
        }
        return pose;
    }

    // 왼쪽 다리: 어깨(240,100) 엉덩이(240,300) 무릎(240,450) 발목(390,450) → 무릎 90도, 엉덩이 180도, 상체 0도
    private static List<NormalizedLandmark> leftLegBentAtKnee(float visibility) {
        List<NormalizedLandmark> pose = emptyPose();
        pose.set(ExerciseCriteria.LEFT_SHOULDER, point(240, 100, visibility));
        pose.set(ExerciseCriteria.LEFT_HIP, point(240, 300, visibility));
        pose.set(ExerciseCriteria.LEFT_KNEE, point(240, 450, visibility));
        pose.set(ExerciseCriteria.LEFT_ANKLE, point(390, 450, visibility));
        return pose;
    }

    @Test
    public void jointAngle_usesPixelCoordinates() {
        double knee = PoseMeasurer.calculateJointAngle(leftLegBentAtKnee(0.9f),
                ExerciseCriteria.LEFT_HIP, ExerciseCriteria.LEFT_KNEE, ExerciseCriteria.LEFT_ANKLE, WIDTH, HEIGHT);
        assertEquals(90, knee, DELTA);
    }

    @Test
    public void jointAngle_lowVisibilityOrMissing_isNaN() {
        assertTrue(Double.isNaN(PoseMeasurer.calculateJointAngle(leftLegBentAtKnee(0.49f),
                ExerciseCriteria.LEFT_HIP, ExerciseCriteria.LEFT_KNEE, ExerciseCriteria.LEFT_ANKLE, WIDTH, HEIGHT)));
        assertTrue(Double.isNaN(PoseMeasurer.calculateJointAngle(null, 23, 25, 27, WIDTH, HEIGHT)));
        assertTrue(Double.isNaN(PoseMeasurer.calculateJointAngle(new ArrayList<>(), 23, 25, 27, WIDTH, HEIGHT)));
    }

    @Test
    public void trunkLean_uprightIsZero_leaningForwardIsAngle() {
        List<NormalizedLandmark> pose = leftLegBentAtKnee(0.9f);
        assertEquals(0, PoseMeasurer.calculateTrunkLean(pose, ExerciseCriteria.LEFT_SHOULDER,
                ExerciseCriteria.LEFT_HIP, WIDTH, HEIGHT), DELTA);

        // 어깨가 엉덩이에서 오른쪽 위 45도
        pose.set(ExerciseCriteria.LEFT_SHOULDER, point(340, 200, 0.9f));
        assertEquals(45, PoseMeasurer.calculateTrunkLean(pose, ExerciseCriteria.LEFT_SHOULDER,
                ExerciseCriteria.LEFT_HIP, WIDTH, HEIGHT), DELTA);
    }

    @Test
    public void measureSide_kneeJudged() {
        LegSelector.LegSample sample = PoseMeasurer.measureSide(leftLegBentAtKnee(0.8f),
                ExerciseCriteria.LEFT_LEG, false, WIDTH, HEIGHT);
        assertEquals("L", sample.label);
        assertEquals(90, sample.jointAngle, DELTA);
        assertEquals(90, sample.kneeAngle, DELTA);
        assertEquals(180, sample.hipAngle, DELTA);
        assertEquals(0, sample.trunkLean, DELTA);
        assertEquals(0.8f, sample.minVisibility, 1e-6);
    }

    @Test
    public void measureSide_hipJudged_usesHipAngle_andShoulderHipKneeVisibility() {
        List<NormalizedLandmark> pose = leftLegBentAtKnee(0.9f);
        // 발목만 잘 안 보임: 엉덩이 판정에는 발목이 들어가지 않으므로 visibility에 영향 없음
        pose.set(ExerciseCriteria.LEFT_ANKLE, point(390, 450, 0.2f));
        LegSelector.LegSample sample = PoseMeasurer.measureSide(pose, ExerciseCriteria.LEFT_LEG, true, WIDTH, HEIGHT);
        assertEquals(180, sample.jointAngle, DELTA);
        assertTrue(Double.isNaN(sample.kneeAngle));
        assertEquals(0.9f, sample.minVisibility, 1e-6);
    }

    @Test
    public void measureSide_noPose_isUnknownAndInvisible() {
        LegSelector.LegSample sample = PoseMeasurer.measureSide(null, ExerciseCriteria.RIGHT_LEG, false, WIDTH, HEIGHT);
        assertEquals("R", sample.label);
        assertTrue(Double.isNaN(sample.jointAngle));
        assertEquals(0f, sample.minVisibility, 0f);
    }

    // ---------- 하체 스트레칭 재측정용: 잘 보이는 쪽 상체 기울기 / 더 펴진 다리의 무릎 ----------

    private static LegSelector.LegSample side(String label, double trunk, double knee, float avgVisibility) {
        return new LegSelector.LegSample(label, 100, trunk, 100, avgVisibility, avgVisibility, knee);
    }

    @Test
    public void betterTrunkSide_higherVisibility_orTheOnlyMeasurable() {
        assertEquals("R", PoseMeasurer.betterTrunkSide(side("L", 30, 120, 0.6f), side("R", 35, 175, 0.95f)).label);
        assertEquals("L", PoseMeasurer.betterTrunkSide(side("L", 30, 120, 0.9f), side("R", 35, 175, 0.6f)).label);
        // 한쪽 어깨·엉덩이가 가려져 상체 기울기가 없으면 다른 쪽
        assertEquals("L", PoseMeasurer.betterTrunkSide(side("L", 30, 120, 0.4f), side("R", Double.NaN, 175, 0.95f)).label);
        assertNull(PoseMeasurer.betterTrunkSide(side("L", Double.NaN, 120, 0.9f), side("R", Double.NaN, 175, 0.9f)));
    }

    @Test
    public void straighterKneeAngle_ofVisibleLegs() {
        // 스트레칭하는 다리는 펴져 있고(175도) 반대쪽은 굽혀져 있음(95도)
        assertEquals(175, PoseMeasurer.straighterKneeAngle(side("L", 30, 95, 0.9f), side("R", 30, 175, 0.9f)), 1e-9);
        // 안 보이는 다리(NaN)는 빼고 판단
        assertEquals(95, PoseMeasurer.straighterKneeAngle(side("L", 30, 95, 0.9f), side("R", 30, Double.NaN, 0.3f)), 1e-9);
        assertTrue(Double.isNaN(PoseMeasurer.straighterKneeAngle(
                side("L", 30, Double.NaN, 0.3f), side("R", 30, Double.NaN, 0.3f))));
    }
}
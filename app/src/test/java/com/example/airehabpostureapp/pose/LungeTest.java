package com.example.airehabpostureapp.pose;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.Random;

/**
 * 런지(제자리 스플릿 스쿼트): 가짜 각도 데이터(30fps)로 반복 횟수 / 보조 규칙 검증
 * 기준: 올라옴 >= 160도, 1회 인정 <= 120도, 목표 90도(허용 덜 10 / 더 15), 상체 20도, 최소 동작 0.8초
 * aux = 카메라 쪽 다리의 엉덩이 각도. 앞다리면 무릎과 함께 작아지고(약 100도), 뒷다리면 펴진 채(약 170도)
 */
public class LungeTest {

    private static final double UP = 170;
    private static final double TRUNK_UPRIGHT = 5;
    private static final double DELTA = 1e-6;

    private final ExerciseCriteria criteria = ExerciseCriteria.LUNGE;
    private CounterHarness h;

    @Before
    public void setUp() {
        h = new CounterHarness(new RepetitionCounter(criteria));
    }

    private void up(long durationMs) {
        h.hold(UP, TRUNK_UPRIGHT, UP, durationMs);
    }

    /** 1회: 1초 동안 내려감 → 0.3초 유지 → 1초 동안 올라옴 → 0.4초 유지 */
    private void lunge(double minKnee, double minHip, double trunk) {
        h.move(UP, minKnee, UP, minHip, 1000, trunk);
        h.hold(minKnee, trunk, minHip, 300);
        h.move(minKnee, UP, minHip, UP, 1000, trunk);
        up(400);
    }

    @Test
    public void registeredByName() {
        assertSame(criteria, ExerciseCriteria.forExercise("런지"));
        assertEquals(8, criteria.targetReps);
    }

    // ---------- 정상 동작 ----------

    @Test
    public void normalFiveLunges_frontLegJudged_countsFive() {
        up(500);
        for (int i = 0; i < 5; i++) {
            lunge(90, 100, TRUNK_UPRIGHT);
        }
        assertEquals(5, h.counter.getCompletedReps());
        for (RepetitionCounter.RepResult r : h.completed) {
            assertEquals(0, r.jointError, 0.5);
            assertEquals(100, r.accuracy, 0.5);
        }
    }

    @Test
    public void backLegJudged_hipStaysOpen_stillCounts() {
        // 카메라 쪽 다리가 뒷다리: 엉덩이는 펴진 채(170도) 무릎만 90도 → 런지에서는 정상
        up(500);
        for (int i = 0; i < 3; i++) {
            lunge(90, 170, TRUNK_UPRIGHT);
        }
        assertEquals(3, h.counter.getCompletedReps());
        assertTrue(h.rejected.isEmpty());
    }

    @Test
    public void feedback_followsLunge() {
        up(500);
        assertEquals(RepetitionCounter.Feedback.START_BENDING, h.counter.getFeedback());
        // 내려가는 중에는 "조금 더 내려가" 대신 시작 안내 유지
        h.move(UP, 135, UP, 135, 600, TRUNK_UPRIGHT);
        assertEquals(RepetitionCounter.Feedback.START_BENDING, h.counter.getFeedback());
        h.move(135, 92, 135, 100, 500, TRUNK_UPRIGHT);
        h.hold(92, TRUNK_UPRIGHT, 100, 300);
        assertEquals(RepetitionCounter.Feedback.GOOD_STRAIGHTEN, h.counter.getFeedback());
    }

    // ---------- 덜 한 동작 ----------

    @Test
    public void shallowLunge_notCounted() {
        up(500);
        lunge(128, 130, TRUNK_UPRIGHT);
        assertEquals(0, h.counter.getCompletedReps());
        assertEquals(RepetitionCounter.RejectReason.SHALLOW, h.lastRejectReason());
    }

    @Test
    public void slightlyShallow_countedWithPenalty() {
        up(500);
        lunge(110, 115, TRUNK_UPRIGHT);
        // 20도 덜 내려감 → 허용 10도를 10도 넘음 → 무릎 80점 → 80 x 0.8 + 100 x 0.2 = 84
        assertEquals(1, h.completed.size());
        assertEquals(20, h.completed.get(0).jointError, 0.5);
        assertEquals(84, h.completed.get(0).accuracy, 0.5);
    }

    // ---------- 잘못된 동작 ----------

    @Test
    public void trunkLeaningForward_penalizedAndWarned() {
        up(500);
        lunge(90, 100, 30);
        // 상체 30도 → 허용 20도를 10도 넘음 → 상체 60점 → 100 x 0.8 + 60 x 0.2 = 92
        assertEquals(1, h.completed.size());
        assertEquals(10, h.completed.get(0).trunkError, 0.5);
        assertEquals(92, h.completed.get(0).accuracy, 0.5);
        assertTrue(h.sawFeedback(RepetitionCounter.Feedback.KEEP_TRUNK_UPRIGHT));
    }

    @Test
    public void bounceTooFast_notCounted() {
        up(500);
        h.move(UP, 90, UP, 100, 250, TRUNK_UPRIGHT);
        h.move(90, UP, 100, UP, 250, TRUNK_UPRIGHT);
        up(400);
        assertEquals(0, h.counter.getCompletedReps());
        assertEquals(RepetitionCounter.RejectReason.TOO_FAST, h.lastRejectReason());
    }

    // ---------- 관절이 안 보이는 구간 ----------

    @Test
    public void shortOcclusion_stillCounts() {
        up(500);
        h.move(UP, 90, UP, 100, 1000, TRUNK_UPRIGHT);
        h.invisible(300);
        h.hold(90, TRUNK_UPRIGHT, 100, 200);
        h.move(90, UP, 100, UP, 1000, TRUNK_UPRIGHT);
        up(400);
        assertEquals(1, h.counter.getCompletedReps());
    }

    @Test
    public void longOcclusion_whileSwitchingFeet_dropsRep_thenRecovers() {
        // 발을 바꾸는 중 1.5초 동안 다리가 가려짐 → 진행 중 동작은 버리고, 다시 올라온 상태부터 셈
        up(500);
        h.move(UP, 100, UP, 110, 800, TRUNK_UPRIGHT);
        h.invisible(1500);
        assertEquals(RepetitionCounter.Feedback.NOT_VISIBLE, h.counter.getFeedback());
        up(500);
        lunge(90, 100, TRUNK_UPRIGHT);
        assertEquals(1, h.counter.getCompletedReps());
    }

    // ---------- 흔들림 ----------

    @Test
    public void jitteryFiveLunges_countsFive() {
        Random random = new Random(11);
        up(500);
        for (int i = 0; i < 5; i++) {
            h.move(UP, 92, UP, 100, 1000, TRUNK_UPRIGHT, 8, random);
            h.hold(92, TRUNK_UPRIGHT, 100, 300);
            h.move(92, UP, 100, UP, 1000, TRUNK_UPRIGHT, 8, random);
            up(400);
        }
        assertEquals(5, h.counter.getCompletedReps());
    }

    @Test
    public void wobbleNearTop_notCounted() {
        // 올라온 상태에서 150~170도로 흔들림 (굽힘 기준 120도까지 안 감) → 세지 않음
        up(500);
        Random random = new Random(3);
        for (int i = 0; i < 60; i++) {
            double angle = 160 + (random.nextDouble() * 2 - 1) * 10;
            h.frame(angle, TRUNK_UPRIGHT, angle);
        }
        up(500);
        assertEquals(0, h.counter.getCompletedReps());
        assertTrue(h.rejected.isEmpty());
    }
}

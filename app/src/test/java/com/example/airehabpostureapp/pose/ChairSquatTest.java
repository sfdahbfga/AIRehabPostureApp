package com.example.airehabpostureapp.pose;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.Random;

/**
 * 의자 스쿼트: 가짜 각도 데이터(30fps)로 반복 횟수 / 보조 규칙 검증
 * 기준: 일어섬 >= 160도, 1회 인정 <= 120도, 목표 100도(허용 덜 10 / 더 15), 상체 50도,
 *       가장 깊을 때 엉덩이 각도 <= 150도, 최소 동작 시간 1초
 * 정상 / 얕게 / 무릎만 동작의 값은 1차 실측(2026-11-10) 로그에서 가져옴
 * aux = 엉덩이 각도(어깨-엉덩이-무릎). 서 있으면 약 175도, 스쿼트로 앉으면 무릎과 함께 약 90도까지 작아짐
 */
public class ChairSquatTest {

    private static final double STAND = 175;
    private static final double TRUNK_NORMAL = 35;
    // 1차 실측 정상 동작: 무릎 103~115도, 엉덩이 66~72도, 상체 최대 46~49도
    private static final double TRUNK_MEASURED = 48;
    private static final double DELTA = 1e-6;

    private final ExerciseCriteria criteria = ExerciseCriteria.CHAIR_SQUAT;
    private CounterHarness h;

    @Before
    public void setUp() {
        h = new CounterHarness(new RepetitionCounter(criteria));
    }

    private void stand(long durationMs) {
        h.hold(STAND, 5, STAND, durationMs);
    }

    /** 1회: 1.2초 동안 앉음 → 0.3초 유지 → 1.2초 동안 일어섬 → 0.4초 서 있기 */
    private void squat(double minKnee, double minHip, double trunk) {
        h.move(STAND, minKnee, STAND, minHip, 1200, trunk);
        h.hold(minKnee, trunk, minHip, 300);
        h.move(minKnee, STAND, minHip, STAND, 1200, trunk);
        stand(400);
    }

    @Test
    public void registeredByName() {
        assertSame(criteria, ExerciseCriteria.forExercise("의자 스쿼트"));
        assertEquals(10, criteria.targetReps);
    }

    // ---------- 정상 동작 ----------

    @Test
    public void normalFiveSquats_countsFive_withFullAccuracy() {
        stand(500);
        double[] knees = {108, 103, 105, 110, 109};
        for (double knee : knees) {
            squat(knee, 70, TRUNK_MEASURED);
        }
        assertEquals(5, h.counter.getCompletedReps());
        for (int i = 0; i < knees.length; i++) {
            RepetitionCounter.RepResult r = h.completed.get(i);
            assertEquals(knees[i], r.minJointAngle, 0.5);
            assertEquals(0, r.trunkError, DELTA);   // 상체 48도는 스쿼트 허용(50도) 안
            assertEquals(100, r.accuracy, 0.5);
        }
        assertTrue(h.rejected.isEmpty());
    }

    @Test
    public void feedback_followsSquat() {
        stand(500);
        assertEquals(RepetitionCounter.Feedback.START_BENDING, h.counter.getFeedback());
        h.move(STAND, 130, STAND, 130, 700, TRUNK_NORMAL);
        assertEquals(RepetitionCounter.Feedback.BEND_MORE, h.counter.getFeedback());
        h.move(130, 90, 130, 90, 500, TRUNK_NORMAL);
        h.hold(90, TRUNK_NORMAL, 90, 300);
        assertEquals(RepetitionCounter.Feedback.GOOD_STRAIGHTEN, h.counter.getFeedback());
    }

    @Test
    public void deepSquat_overTolerance_losesPoints() {
        stand(500);
        squat(80, 70, TRUNK_NORMAL);
        // 목표보다 20도 더 앉음 → 허용 15도를 5도 넘음 → 무릎 90점, 상체 100점 → 90 x 0.7 + 100 x 0.3 = 93
        assertEquals(1, h.completed.size());
        assertEquals(-20, h.completed.get(0).jointError, 0.5);
        assertEquals(93, h.completed.get(0).accuracy, 0.5);
    }

    @Test
    public void trunkTooFarForward_losesPoints() {
        stand(500);
        squat(100, 90, 60);
        // 상체 60도 → 허용 50도를 10도 넘음 → 상체 60점 → 100 x 0.7 + 60 x 0.3 = 88
        assertEquals(1, h.completed.size());
        assertEquals(10, h.completed.get(0).trunkError, 0.5);
        assertEquals(88, h.completed.get(0).accuracy, 0.5);
        assertTrue(h.sawFeedback(RepetitionCounter.Feedback.KEEP_TRUNK_UPRIGHT));
    }

    // ---------- 덜 한 동작 ----------

    @Test
    public void shallowSquat_notCounted() {
        // 1차 실측 B세트: 엉덩이는 굽혔지만 덜 앉음 (무릎 128~135도, 엉덩이 94~99도)
        stand(500);
        squat(130, 97, TRUNK_NORMAL);
        assertEquals(0, h.counter.getCompletedReps());
        assertEquals(RepetitionCounter.RejectReason.SHALLOW, h.lastRejectReason());
        assertEquals(RepetitionCounter.Feedback.START_BENDING, h.counter.getFeedback());
    }

    // ---------- 잘못된 동작 ----------

    @Test
    public void kneeOnlyBend_hipStaysOpen_notCounted() {
        // 무릎 굽힘 운동처럼 허벅지를 세운 채 무릎만 접음 → 엉덩이 각도가 170도 그대로
        stand(500);
        squat(90, 170, 5);
        assertEquals(0, h.counter.getCompletedReps());
        assertEquals(RepetitionCounter.RejectReason.HIP_NOT_BENT, h.lastRejectReason());
        assertTrue(h.sawFeedback(RepetitionCounter.Feedback.HIPS_BACK));
    }

    @Test
    public void kneeOnlyShallow_hipStaysOpen_judgedByHipRule() {
        // 1차 실측 C세트: 상체를 세운 채 무릎만 굽히면 무릎은 137~150도에서 멈추고 엉덩이는 155~171도
        // → 120도까지 못 내려갔어도 "덜 앉음"이 아니라 엉덩이 규칙으로 판정하고, 일어선 뒤 "엉덩이를 뒤로" 안내
        stand(500);
        squat(140, 160, 5);
        assertEquals(0, h.counter.getCompletedReps());
        assertEquals(RepetitionCounter.RejectReason.HIP_NOT_BENT, h.lastRejectReason());
        assertEquals(RepetitionCounter.Feedback.HIPS_BACK, h.counter.getFeedback());
        // 서 있는 동안 안내가 계속 유지됨
        stand(1000);
        assertEquals(RepetitionCounter.Feedback.HIPS_BACK, h.counter.getFeedback());
    }

    @Test
    public void hipsBackHint_clearsWhenNextSquatStarts_andNextSquatCounts() {
        stand(500);
        squat(140, 160, 5);
        assertEquals(RepetitionCounter.Feedback.HIPS_BACK, h.counter.getFeedback());
        // 다음 동작을 시작하면 평소 안내로 돌아가고, 제대로 앉으면 셈
        h.move(STAND, 130, STAND, 100, 700, TRUNK_NORMAL);
        assertEquals(RepetitionCounter.Feedback.BEND_MORE, h.counter.getFeedback());
        h.move(130, 105, 100, 70, 500, TRUNK_NORMAL);
        h.hold(105, TRUNK_NORMAL, 70, 300);
        h.move(105, STAND, 70, STAND, 1200, TRUNK_NORMAL);
        stand(400);
        assertEquals(1, h.counter.getCompletedReps());
        assertEquals(RepetitionCounter.Feedback.START_BENDING, h.counter.getFeedback());
    }

    @Test
    public void kneeOnlyShallow_hipNotVisible_staysShallow() {
        // 엉덩이 각도를 모르면(NaN) 엉덩이 규칙을 적용하지 않음 → 덜 앉음
        stand(500);
        h.move(STAND, 140, Double.NaN, Double.NaN, 1200, 5);
        h.hold(140, 5, Double.NaN, 300);
        h.move(140, STAND, Double.NaN, Double.NaN, 1200, 5);
        stand(400);
        assertEquals(RepetitionCounter.RejectReason.SHALLOW, h.lastRejectReason());
        assertEquals(RepetitionCounter.Feedback.START_BENDING, h.counter.getFeedback());
    }

    @Test
    public void tooFastSquat_notCounted_andMoveSlowlyHint() {
        stand(500);
        h.move(STAND, 90, STAND, 90, 300, TRUNK_NORMAL);
        h.move(90, STAND, 90, STAND, 300, TRUNK_NORMAL);
        stand(400);
        assertEquals(0, h.counter.getCompletedReps());
        assertEquals(RepetitionCounter.RejectReason.TOO_FAST, h.lastRejectReason());
    }

    // ---------- 관절이 안 보이는 구간 ----------

    @Test
    public void shortOcclusionAtBottom_stillCounts() {
        // 앉을 때 의자에 엉덩이가 잠깐(0.4초) 가려짐
        stand(500);
        h.move(STAND, 90, STAND, 90, 1200, TRUNK_NORMAL);
        h.invisible(400);
        h.hold(90, TRUNK_NORMAL, 90, 200);
        h.move(90, STAND, 90, STAND, 1200, TRUNK_NORMAL);
        stand(400);
        assertEquals(1, h.counter.getCompletedReps());
    }

    @Test
    public void longOcclusion_dropsRep_thenCountsNext() {
        stand(500);
        h.move(STAND, 90, STAND, 90, 1200, TRUNK_NORMAL);
        h.invisible(1500);
        assertEquals(RepetitionCounter.Feedback.NOT_VISIBLE, h.counter.getFeedback());
        assertEquals(RepetitionCounter.RejectReason.LOST_TRACKING, h.lastRejectReason());
        stand(500);
        squat(90, 90, TRUNK_NORMAL);
        assertEquals(1, h.counter.getCompletedReps());
    }

    // ---------- 흔들림 ----------

    @Test
    public void jitteryFiveSquats_countsFive() {
        Random random = new Random(7);
        stand(500);
        for (int i = 0; i < 5; i++) {
            h.move(STAND, 92, STAND, 92, 1200, TRUNK_NORMAL, 8, random);
            h.hold(92, TRUNK_NORMAL, 92, 300);
            h.move(92, STAND, 92, STAND, 1200, TRUNK_NORMAL, 8, random);
            stand(400);
        }
        assertEquals(5, h.counter.getCompletedReps());
        for (RepetitionCounter.RepResult r : h.completed) {
            assertTrue("흔들려도 가장 깊은 각도는 목표 근처: " + r.minJointAngle, Math.abs(r.minJointAngle - 92) < 6);
        }
    }

    @Test
    public void singleFrameSpikeWhileStanding_notCounted() {
        // 서 있는 중 먼 다리 값 등으로 한 프레임만 60도로 튐 → 연속성 검사로 건너뜀
        stand(500);
        h.frame(60, 5, 60);
        stand(1000);
        assertEquals(0, h.counter.getCompletedReps());
        assertTrue(h.counter.getSkippedFrameCount() >= 1);
    }
}

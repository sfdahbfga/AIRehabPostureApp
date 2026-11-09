package com.example.airehabpostureapp.pose;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.Random;

/**
 * 하체 스트레칭(의자에 앉아 하는 허벅지 뒤 스트레칭, 유지 시간형): 가짜 각도 데이터(30fps)로 검증
 * 기준: 판정 = 편 다리 쪽 엉덩이 각도(어깨-엉덩이-무릎), 유지 인정 <= 110도, 목표 95도(허용 덜 5 / 더 20),
 *       편 다리 무릎 >= 160도, 20초 유지 = 1회, 기준 밖 1초까지 유지 이어짐, 다시 세움 = min(120, 직전 평균 + 15)
 * aux = 편 다리 무릎 각도. 상체 기울기는 쓰지 않으므로 NaN
 */
public class HamstringStretchTest {

    private static final double UPRIGHT = 120;
    private static final double STRAIGHT_KNEE = 175;
    private static final double NO_TRUNK = Double.NaN;
    private static final long HOLD_MS = 20000;

    private final ExerciseCriteria criteria = ExerciseCriteria.SEATED_HAMSTRING_STRETCH;
    private CounterHarness h;

    @Before
    public void setUp() {
        h = new CounterHarness(new HoldCounter(criteria));
    }

    private void sitUpright(long durationMs) {
        h.hold(UPRIGHT, NO_TRUNK, STRAIGHT_KNEE, durationMs);
    }

    /** 1회: 2초 동안 숙임 → hipAngle로 holdMs 유지 → 2초 동안 세움 → 1초 앉아 있기 */
    private void stretch(double hipAngle, long holdMs) {
        h.move(UPRIGHT, hipAngle, STRAIGHT_KNEE, STRAIGHT_KNEE, 2000, NO_TRUNK);
        h.hold(hipAngle, NO_TRUNK, STRAIGHT_KNEE, holdMs);
        h.move(hipAngle, UPRIGHT, STRAIGHT_KNEE, STRAIGHT_KNEE, 2000, NO_TRUNK);
        sitUpright(1000);
    }

    @Test
    public void registeredByName_asHoldType() {
        assertSame(criteria, ExerciseCriteria.forExercise("하체 스트레칭"));
        assertTrue(criteria.isHoldType());
        assertEquals(ExerciseCriteria.JudgedJoint.HIP, criteria.judgedJoint);
        assertEquals(3, criteria.targetReps);
    }

    // ---------- 정상 동작 ----------

    @Test
    public void threeNormalHolds_countThree_withFullAccuracy() {
        sitUpright(1000);
        for (int i = 0; i < 3; i++) {
            stretch(95, HOLD_MS);
        }
        assertEquals(3, h.counter.getCompletedReps());
        for (RepetitionCounter.RepResult r : h.completed) {
            assertTrue("유지 시간 20초 이상: " + r.durationMs, r.durationMs >= HOLD_MS);
            assertEquals(95, r.minJointAngle, 1.5);   // 유지 중 평균 (숙이는 구간 일부 포함)
            assertEquals(100, r.accuracy, 0.5);
            assertTrue(Double.isNaN(r.trunkError));   // 유지형은 back을 저장하지 않음
        }
        assertTrue(h.rejected.isEmpty());
    }

    @Test
    public void feedback_followsHoldCycle() {
        sitUpright(1000);
        assertEquals(RepetitionCounter.Feedback.START_BENDING, h.counter.getFeedback());
        h.move(UPRIGHT, 113, STRAIGHT_KNEE, STRAIGHT_KNEE, 600, NO_TRUNK);
        h.hold(113, NO_TRUNK, STRAIGHT_KNEE, 300);
        assertEquals(RepetitionCounter.Feedback.BEND_MORE, h.counter.getFeedback());
        h.move(113, 95, STRAIGHT_KNEE, STRAIGHT_KNEE, 1000, NO_TRUNK);
        h.hold(95, NO_TRUNK, STRAIGHT_KNEE, 5000);
        assertEquals(RepetitionCounter.Feedback.HOLD, h.counter.getFeedback());
        assertTrue(((HoldCounter) h.counter).getHeldMs() >= 5000);
        h.hold(95, NO_TRUNK, STRAIGHT_KNEE, 16000);
        assertEquals(1, h.counter.getCompletedReps());
        assertEquals(RepetitionCounter.Feedback.RELEASE, h.counter.getFeedback());
        h.move(95, UPRIGHT, STRAIGHT_KNEE, STRAIGHT_KNEE, 2000, NO_TRUNK);
        assertEquals(RepetitionCounter.Feedback.START_BENDING, h.counter.getFeedback());
    }

    @Test
    public void mustRelease_beforeNextHold() {
        // 1회를 마친 뒤 세우지 않고 계속 숙이고 있으면 두 번째로 세지 않음
        sitUpright(1000);
        h.move(UPRIGHT, 95, STRAIGHT_KNEE, STRAIGHT_KNEE, 2000, NO_TRUNK);
        h.hold(95, NO_TRUNK, STRAIGHT_KNEE, 45000);
        assertEquals(1, h.counter.getCompletedReps());
    }

    @Test
    public void releaseFallback_whenUprightHipAngleIsLow() {
        // 다리를 수평에 가깝게 둬서 똑바로 앉아도 엉덩이 각도가 102도인 사람:
        // 85도로 유지 → 다시 세움 기준 = min(120, 85 + 15) = 100도 → 102도로 세우면 다음 유지를 셈
        h.hold(102, NO_TRUNK, STRAIGHT_KNEE, 1000);
        for (int i = 0; i < 2; i++) {
            h.move(102, 85, STRAIGHT_KNEE, STRAIGHT_KNEE, 1500, NO_TRUNK);
            h.hold(85, NO_TRUNK, STRAIGHT_KNEE, HOLD_MS);
            h.move(85, 102, STRAIGHT_KNEE, STRAIGHT_KNEE, 1500, NO_TRUNK);
            h.hold(102, NO_TRUNK, STRAIGHT_KNEE, 1000);
        }
        assertEquals(2, h.counter.getCompletedReps());
    }

    // ---------- 덜 한 동작 ----------

    @Test
    public void notLeaningEnough_neverCounts() {
        sitUpright(1000);
        h.move(UPRIGHT, 115, STRAIGHT_KNEE, STRAIGHT_KNEE, 1000, NO_TRUNK);
        h.hold(115, NO_TRUNK, STRAIGHT_KNEE, 25000);
        assertEquals(0, h.counter.getCompletedReps());
        assertEquals(0, ((HoldCounter) h.counter).getHeldMs());
        assertEquals(RepetitionCounter.Feedback.BEND_MORE, h.counter.getFeedback());
    }

    @Test
    public void slightlyShallowHold_countedWithPenalty() {
        sitUpright(1000);
        h.move(UPRIGHT, 105, STRAIGHT_KNEE, STRAIGHT_KNEE, 500, NO_TRUNK);
        h.hold(105, NO_TRUNK, STRAIGHT_KNEE, HOLD_MS + 500);
        // 목표보다 10도 덜 숙임 → 허용 5도를 5도 넘음 → 90점
        assertEquals(1, h.completed.size());
        assertEquals(10, h.completed.get(0).jointError, 1);
        assertEquals(90, h.completed.get(0).accuracy, 2);
    }

    // ---------- 잘못된 동작 ----------

    @Test
    public void kneeBent_holdDoesNotAccumulate() {
        // 상체는 충분히 숙였지만 편 다리 무릎을 140도로 굽힘 → 허벅지 뒤가 늘어나지 않으므로 세지 않음
        sitUpright(1000);
        h.move(UPRIGHT, 95, STRAIGHT_KNEE, 140, 2000, NO_TRUNK);
        h.hold(95, NO_TRUNK, 140, 25000);
        assertEquals(0, h.counter.getCompletedReps());
        assertEquals(RepetitionCounter.Feedback.STRAIGHTEN_KNEE, h.counter.getFeedback());
    }

    @Test
    public void kneeBentMidHold_overGrace_endsHold() {
        sitUpright(1000);
        h.move(UPRIGHT, 95, STRAIGHT_KNEE, STRAIGHT_KNEE, 2000, NO_TRUNK);
        h.hold(95, NO_TRUNK, STRAIGHT_KNEE, 8000);
        h.hold(95, NO_TRUNK, 140, 2000);   // 2초 동안 무릎 굽힘 → 1초 넘게 기준 밖
        h.hold(95, NO_TRUNK, STRAIGHT_KNEE, 5000);
        assertEquals(0, h.counter.getCompletedReps());
        assertEquals(RepetitionCounter.RejectReason.RELEASED_EARLY, h.lastRejectReason());
        assertTrue(h.rejected.get(0).durationMs >= 8000);
    }

    @Test
    public void shortKneeBend_pausesButKeepsHold() {
        sitUpright(1000);
        h.move(UPRIGHT, 95, STRAIGHT_KNEE, STRAIGHT_KNEE, 2000, NO_TRUNK);
        h.hold(95, NO_TRUNK, STRAIGHT_KNEE, 10000);
        h.hold(95, NO_TRUNK, 140, 500);    // 0.5초만 굽힘 → 시간은 멈추고 유지는 이어짐
        h.hold(95, NO_TRUNK, STRAIGHT_KNEE, 11000);
        assertEquals(1, h.counter.getCompletedReps());
        assertTrue(h.rejected.isEmpty());
    }

    // ---------- 관절이 안 보이는 구간 ----------

    @Test
    public void shortOcclusion_pausesButKeepsHold() {
        // 발끝으로 손을 뻗어 1.2초 동안 무릎이 가려짐 (기준 1.5초 이내)
        sitUpright(1000);
        h.move(UPRIGHT, 95, STRAIGHT_KNEE, STRAIGHT_KNEE, 2000, NO_TRUNK);
        h.hold(95, NO_TRUNK, STRAIGHT_KNEE, 10000);
        long heldBefore = ((HoldCounter) h.counter).getHeldMs();
        h.invisible(1200);
        assertEquals(heldBefore, ((HoldCounter) h.counter).getHeldMs());   // 안 보이는 동안 시간은 멈춤
        h.hold(95, NO_TRUNK, STRAIGHT_KNEE, 11000);
        assertEquals(1, h.counter.getCompletedReps());
    }

    @Test
    public void longOcclusion_dropsHold() {
        sitUpright(1000);
        h.move(UPRIGHT, 95, STRAIGHT_KNEE, STRAIGHT_KNEE, 2000, NO_TRUNK);
        h.hold(95, NO_TRUNK, STRAIGHT_KNEE, 6000);
        h.invisible(2000);
        assertEquals(RepetitionCounter.Feedback.NOT_VISIBLE, h.counter.getFeedback());
        assertEquals(RepetitionCounter.RejectReason.LOST_TRACKING, h.lastRejectReason());
        assertEquals(0, h.counter.getCompletedReps());
    }

    @Test
    public void kneeNotVisible_kneeRuleSkipped() {
        // 무릎 각도만 안 보이면(발목 가려짐) 무릎 조건은 검사하지 않고 엉덩이 각도로 유지 판정
        sitUpright(1000);
        h.move(UPRIGHT, 95, Double.NaN, Double.NaN, 2000, NO_TRUNK);
        h.hold(95, NO_TRUNK, Double.NaN, HOLD_MS + 500);
        assertEquals(1, h.counter.getCompletedReps());
    }

    // ---------- 흔들림 ----------

    @Test
    public void jitteryHold_stillCounts() {
        Random random = new Random(5);
        sitUpright(1000);
        h.move(UPRIGHT, 97, STRAIGHT_KNEE, STRAIGHT_KNEE, 2000, NO_TRUNK, 6, random);
        for (long t = 0; t < HOLD_MS + 1000; t += CounterHarness.FRAME_MS) {
            double knee = STRAIGHT_KNEE + (random.nextDouble() * 2 - 1) * 8;   // 무릎도 167~183으로 흔들림
            h.frame(97 + (random.nextDouble() * 2 - 1) * 6, NO_TRUNK, knee);
        }
        assertEquals(1, h.counter.getCompletedReps());
        assertTrue(h.rejected.isEmpty());
    }

    @Test
    public void singleFrameSpike_skipped() {
        sitUpright(1000);
        h.move(UPRIGHT, 95, STRAIGHT_KNEE, STRAIGHT_KNEE, 2000, NO_TRUNK);
        h.hold(95, NO_TRUNK, STRAIGHT_KNEE, 3000);
        h.frame(40, NO_TRUNK, STRAIGHT_KNEE);   // 한 프레임만 40도로 튐
        h.hold(95, NO_TRUNK, STRAIGHT_KNEE, 18000);
        assertEquals(1, h.counter.getCompletedReps());
        assertTrue(h.counter.getSkippedFrameCount() >= 1);
        assertEquals(95, h.completed.get(0).minJointAngle, 1.5);
    }

    @Test
    public void reset_clearsEverything() {
        sitUpright(1000);
        stretch(95, HOLD_MS);
        h.counter.reset();
        assertEquals(0, h.counter.getCompletedReps());
        assertEquals(RepetitionCounter.Feedback.NOT_VISIBLE, h.counter.getFeedback());
    }
}

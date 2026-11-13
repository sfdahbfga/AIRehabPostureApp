package com.example.airehabpostureapp.pose;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.Random;

/**
 * 하체 스트레칭(의자에 앉아 하는 허벅지 뒤 스트레칭, 유지 시간형): 가짜 각도 데이터(30fps)로 검증
 * 기준: 판정 = 앞으로 숙인 상체 기울기(앞으로 +, 뒤로 -), 유지 시작 >= 20도 / 유지 중 >= 17도,
 *       목표 30도(허용 덜 5 / 더 20), 더 펴진 다리 무릎 >= 160도(굽힌 뒤에는 165도 이상이어야 편 것),
 *       20초 유지 = 1회, 기준 밖 1초까지 유지 이어짐, 다시 세움 = max(10, 직전 평균 - 15) 이하
 * aux = 잘 보이는 다리 중 더 펴진 다리의 무릎 각도. 상체 기울기 인자는 쓰지 않으므로 NaN
 */
public class HamstringStretchTest {

    // 1차 실측: 다리를 쭉 펴고 똑바로 앉으면 뒤로 약 23도 기댐 / 살짝 숙임 약 9도 / 깊게 숙임 약 48도
    private static final double UPRIGHT = 0;
    private static final double STRAIGHT_KNEE = 175;
    private static final double NO_TRUNK = Double.NaN;
    private static final long HOLD_MS = 20000;

    private final ExerciseCriteria criteria = ExerciseCriteria.SEATED_HAMSTRING_STRETCH;
    private CounterHarness h;

    @Before
    public void setUp() {
        h = new CounterHarness(new HoldCounter(criteria));
    }

    private HoldCounter counter() {
        return (HoldCounter) h.counter;
    }

    private void sitUpright(long durationMs) {
        h.hold(UPRIGHT, NO_TRUNK, STRAIGHT_KNEE, durationMs);
    }

    /** 1회: 2초 동안 숙임 → lean으로 holdMs 유지 → 2초 동안 세움 → 1초 앉아 있기 */
    private void stretch(double lean, long holdMs) {
        h.move(UPRIGHT, lean, STRAIGHT_KNEE, STRAIGHT_KNEE, 2000, NO_TRUNK);
        h.hold(lean, NO_TRUNK, STRAIGHT_KNEE, holdMs);
        h.move(lean, UPRIGHT, STRAIGHT_KNEE, STRAIGHT_KNEE, 2000, NO_TRUNK);
        sitUpright(1000);
    }

    @Test
    public void registeredByName_asHoldType_judgedByForwardLean() {
        assertSame(criteria, ExerciseCriteria.forExercise("하체 스트레칭"));
        assertTrue(criteria.isHoldType());
        assertTrue(criteria.higherIsDeeper);
        assertEquals(ExerciseCriteria.JudgedJoint.HIP, criteria.judgedJoint);   // 저장 키 hip
        assertEquals(3, criteria.targetReps);
        assertEquals(20, criteria.bentAngle, 1e-9);
        assertEquals(17, criteria.holdKeepAngle, 1e-9);
        assertEquals(30, criteria.targetAngle, 1e-9);
    }

    // ---------- 정상 동작 ----------

    @Test
    public void threeNormalHolds_countThree_withFullAccuracy() {
        sitUpright(1000);
        for (int i = 0; i < 3; i++) {
            stretch(30, HOLD_MS);
        }
        assertEquals(3, h.counter.getCompletedReps());
        for (RepetitionCounter.RepResult r : h.completed) {
            assertTrue("유지 시간 20초 이상: " + r.durationMs, r.durationMs >= HOLD_MS);
            assertEquals(30, r.minJointAngle, 1.5);   // 유지 중 평균 (숙이는 구간 일부 포함)
            assertEquals(100, r.accuracy, 0.5);
            assertTrue(Double.isNaN(r.trunkError));   // 유지형은 back을 저장하지 않음
        }
        assertTrue(h.rejected.isEmpty());
    }

    @Test
    public void measuredDeepLean_countsWithFullAccuracy() {
        // 1차 실측 깊게 숙임 평균 48.5도 → 더 숙임 허용(50도까지) 안이라 만점
        sitUpright(1000);
        stretch(48.5, HOLD_MS);
        assertEquals(1, h.completed.size());
        assertEquals(-18.5, h.completed.get(0).jointError, 1);   // 목표 30 - 48.5
        assertEquals(100, h.completed.get(0).accuracy, 0.5);
    }

    @Test
    public void feedback_followsHoldCycle() {
        sitUpright(1000);
        assertEquals(RepetitionCounter.Feedback.START_BENDING, h.counter.getFeedback());
        h.move(UPRIGHT, 30, STRAIGHT_KNEE, STRAIGHT_KNEE, 1000, NO_TRUNK);
        h.hold(30, NO_TRUNK, STRAIGHT_KNEE, 5000);
        assertEquals(RepetitionCounter.Feedback.HOLD, h.counter.getFeedback());
        assertTrue(counter().getHeldMs() >= 5000);
        h.hold(30, NO_TRUNK, STRAIGHT_KNEE, 16000);
        assertEquals(1, h.counter.getCompletedReps());
        assertEquals(RepetitionCounter.Feedback.RELEASE, h.counter.getFeedback());
        h.move(30, UPRIGHT, STRAIGHT_KNEE, STRAIGHT_KNEE, 2000, NO_TRUNK);
        assertEquals(RepetitionCounter.Feedback.START_BENDING, h.counter.getFeedback());
    }

    @Test
    public void mustRelease_beforeNextHold() {
        // 1회를 마친 뒤 세우지 않고 계속 숙이고 있으면 두 번째로 세지 않음
        sitUpright(1000);
        h.move(UPRIGHT, 30, STRAIGHT_KNEE, STRAIGHT_KNEE, 2000, NO_TRUNK);
        h.hold(30, NO_TRUNK, STRAIGHT_KNEE, 45000);
        assertEquals(1, h.counter.getCompletedReps());
    }

    @Test
    public void releaseFallback_whenUprightIsLeaningForward() {
        // 똑바로 앉아도 앞으로 15도 기우는 사람: 35도로 유지 → 다시 세움 기준 = max(10, 35 - 15) = 20도
        // → 15도로 세우면 다음 유지를 셈
        h.hold(15, NO_TRUNK, STRAIGHT_KNEE, 1000);
        for (int i = 0; i < 2; i++) {
            h.move(15, 35, STRAIGHT_KNEE, STRAIGHT_KNEE, 1500, NO_TRUNK);
            h.hold(35, NO_TRUNK, STRAIGHT_KNEE, HOLD_MS);
            h.move(35, 15, STRAIGHT_KNEE, STRAIGHT_KNEE, 1500, NO_TRUNK);
            h.hold(15, NO_TRUNK, STRAIGHT_KNEE, 1000);
        }
        assertEquals(2, h.counter.getCompletedReps());
    }

    @Test
    public void leaningBack_isNotCountedAsLean() {
        // 1차 실측: 다리를 펴고 똑바로 앉으면 뒤로 23도 기댐 → 앞으로 숙인 것으로 보지 않음
        h.hold(-23, NO_TRUNK, STRAIGHT_KNEE, 25000);
        assertEquals(0, h.counter.getCompletedReps());
        assertEquals(0, counter().getHeldMs());
        assertEquals(RepetitionCounter.Feedback.START_BENDING, h.counter.getFeedback());
        assertFalse(h.counter.isRepInProgress());
    }

    // ---------- 덜 한 동작 ----------

    @Test
    public void slightLean_measured_neverCounts_bendMoreOnceAfterStall() {
        // 1차 실측 살짝 숙임 약 9도 (8~12도)로 25초
        sitUpright(1000);
        h.move(UPRIGHT, 9, STRAIGHT_KNEE, STRAIGHT_KNEE, 1000, NO_TRUNK);
        h.hold(9, NO_TRUNK, STRAIGHT_KNEE, 25000);
        assertEquals(0, h.counter.getCompletedReps());
        assertEquals(0, counter().getHeldMs());
        assertEquals(RepetitionCounter.Feedback.BEND_MORE, h.counter.getFeedback());
        assertEquals(1, appearances(RepetitionCounter.Feedback.BEND_MORE));
    }

    @Test
    public void slightlyShallowHold_countedWithPenalty() {
        sitUpright(1000);
        h.move(UPRIGHT, 20, STRAIGHT_KNEE, STRAIGHT_KNEE, 500, NO_TRUNK);
        h.hold(20, NO_TRUNK, STRAIGHT_KNEE, HOLD_MS + 500);
        // 목표보다 10도 덜 숙임 → 허용 5도를 5도 넘음 → 90점
        assertEquals(1, h.completed.size());
        assertEquals(10, h.completed.get(0).jointError, 1);
        assertEquals(90, h.completed.get(0).accuracy, 2);
    }

    // ---------- 여유 범위 (기준 근처에서 시작 / 끊김 반복 방지) ----------

    @Test
    public void justBelowStart_doesNotStartHold() {
        sitUpright(1000);
        h.move(UPRIGHT, 19, STRAIGHT_KNEE, STRAIGHT_KNEE, 1000, NO_TRUNK);
        h.hold(19, NO_TRUNK, STRAIGHT_KNEE, 5000);
        assertFalse(counter().isHolding());
    }

    @Test
    public void duringHold_between17and20_keepsAccumulating() {
        sitUpright(1000);
        h.move(UPRIGHT, 22, STRAIGHT_KNEE, STRAIGHT_KNEE, 1000, NO_TRUNK);
        h.hold(22, NO_TRUNK, STRAIGHT_KNEE, 3000);
        long before = counter().getHeldMs();
        // 18도로 내려가도 유지 중에는 이어지고 시간도 쌓임
        h.move(22, 18, STRAIGHT_KNEE, STRAIGHT_KNEE, 300, NO_TRUNK);
        h.hold(18, NO_TRUNK, STRAIGHT_KNEE, 3000);
        assertTrue(counter().isHolding());
        assertTrue(counter().getHeldMs() >= before + 2900);
    }

    @Test
    public void flickeringAround20_holdNotRestarted_feedbackStaysHold() {
        // 19~21도로 흔들려도 한 번 시작한 유지는 이어지고 안내는 "유지하세요" 그대로
        sitUpright(1000);
        h.move(UPRIGHT, 21, STRAIGHT_KNEE, STRAIGHT_KNEE, 1000, NO_TRUNK);
        h.hold(21, NO_TRUNK, STRAIGHT_KNEE, 500);
        int before = h.feedbackHistory.size();
        for (int i = 0; i < 40; i++) {
            h.hold(i % 2 == 0 ? 19 : 21, NO_TRUNK, STRAIGHT_KNEE, 300);
        }
        for (int i = before; i < h.feedbackHistory.size(); i++) {
            assertEquals(RepetitionCounter.Feedback.HOLD, h.feedbackHistory.get(i));
        }
        // 12초 동안 끊기지 않고 이어짐 (19도인 동안도 시간이 쌓임)
        assertTrue(counter().isHolding());
        assertTrue(counter().getHeldMs() >= 12000);
    }

    @Test
    public void risingAfterHold_isNotNewLean_noBendMore() {
        // 1회를 마치고 30도 → 0도로 천천히 세우다 10도에서 2초 멈춰도 "조금 더 숙여"가 나오지 않음 (새 동작 아님)
        sitUpright(1000);
        h.move(UPRIGHT, 30, STRAIGHT_KNEE, STRAIGHT_KNEE, 1000, NO_TRUNK);
        h.hold(30, NO_TRUNK, STRAIGHT_KNEE, HOLD_MS + 500);
        assertEquals(1, h.counter.getCompletedReps());
        h.move(30, 10, STRAIGHT_KNEE, STRAIGHT_KNEE, 2000, NO_TRUNK);
        h.hold(10, NO_TRUNK, STRAIGHT_KNEE, 2000);
        assertFalse(h.counter.isRepInProgress());
        assertEquals(0, appearances(RepetitionCounter.Feedback.BEND_MORE));
        // 똑바로 앉았다가 다시 숙이면 새 동작
        h.move(10, UPRIGHT, STRAIGHT_KNEE, STRAIGHT_KNEE, 800, NO_TRUNK);
        sitUpright(500);
        h.move(UPRIGHT, 12, STRAIGHT_KNEE, STRAIGHT_KNEE, 800, NO_TRUNK);
        assertTrue(h.counter.isRepInProgress());
    }

    @Test
    public void dropBelow17_underOneSecond_keepsHold_overOneSecond_ends() {
        sitUpright(1000);
        h.move(UPRIGHT, 30, STRAIGHT_KNEE, STRAIGHT_KNEE, 1000, NO_TRUNK);
        h.hold(30, NO_TRUNK, STRAIGHT_KNEE, 6000);
        h.hold(12, NO_TRUNK, STRAIGHT_KNEE, 700);   // 0.7초: 이어짐 (시간은 멈춤)
        assertTrue(counter().isHolding());
        h.hold(30, NO_TRUNK, STRAIGHT_KNEE, 3000);
        h.hold(12, NO_TRUNK, STRAIGHT_KNEE, 1500);  // 1초 넘게: 끊김
        assertFalse(counter().isHolding());
        assertEquals(RepetitionCounter.RejectReason.RELEASED_EARLY, h.lastRejectReason());
    }

    // ---------- 무릎 ----------

    @Test
    public void kneeBent_holdDoesNotAccumulate() {
        // 상체는 충분히 숙였지만 무릎을 140도로 굽힘 → 허벅지 뒤가 늘어나지 않으므로 세지 않음
        sitUpright(1000);
        h.move(UPRIGHT, 30, STRAIGHT_KNEE, 140, 2000, NO_TRUNK);
        h.hold(30, NO_TRUNK, 140, 25000);
        assertEquals(0, h.counter.getCompletedReps());
        assertEquals(RepetitionCounter.Feedback.STRAIGHTEN_KNEE, h.counter.getFeedback());
    }

    @Test
    public void kneeAround160_noFlicker_staysStraightAbove155() {
        // 실측처럼 무릎이 156~163도로 흔들려도 편 상태에서는 155도 아래로 내려가야 굽힘 → 유지가 이어짐
        sitUpright(1000);
        h.move(UPRIGHT, 30, STRAIGHT_KNEE, STRAIGHT_KNEE, 1000, NO_TRUNK);
        h.hold(30, NO_TRUNK, STRAIGHT_KNEE, 300);
        int before = h.feedbackHistory.size();
        for (int i = 0; i < 20; i++) {
            h.hold(30, NO_TRUNK, i % 2 == 0 ? 163 : 157, 200);
        }
        for (int i = before; i < h.feedbackHistory.size(); i++) {
            assertEquals(RepetitionCounter.Feedback.HOLD, h.feedbackHistory.get(i));
        }
    }

    @Test
    public void kneeHysteresis_bentBelow155_straightAgainAt160() {
        sitUpright(1000);
        h.move(UPRIGHT, 30, STRAIGHT_KNEE, STRAIGHT_KNEE, 1000, NO_TRUNK);
        // 1차 실측 A1처럼 155~157도로 숙여 있음 → 편 것으로 보고 시간이 쌓임
        h.hold(30, NO_TRUNK, 156, 3000);
        assertEquals(RepetitionCounter.Feedback.HOLD, h.counter.getFeedback());
        assertTrue(counter().getHeldMs() >= 2500);
        // 150도로 굽힘 → 굽힘
        h.hold(30, NO_TRUNK, 150, 500);
        assertEquals(RepetitionCounter.Feedback.STRAIGHTEN_KNEE, h.counter.getFeedback());
        // 158도: 아직 160도 전이라 굽힘 그대로
        h.hold(30, NO_TRUNK, 158, 500);
        assertEquals(RepetitionCounter.Feedback.STRAIGHTEN_KNEE, h.counter.getFeedback());
        // 162도: 편 것
        h.hold(30, NO_TRUNK, 162, 500);
        assertEquals(RepetitionCounter.Feedback.HOLD, h.counter.getFeedback());
    }

    @Test
    public void singleFrameKneeDip_averaged_noWarning() {
        // 한쪽 다리만 보여 한 프레임만 145도로 튐 → 5프레임 평균으로 무시
        sitUpright(1000);
        h.move(UPRIGHT, 30, STRAIGHT_KNEE, STRAIGHT_KNEE, 1000, NO_TRUNK);
        h.hold(30, NO_TRUNK, 172, 1000);
        h.frame(30, NO_TRUNK, 145);
        h.hold(30, NO_TRUNK, 172, 1000);
        assertEquals(0, appearances(RepetitionCounter.Feedback.STRAIGHTEN_KNEE));
    }

    @Test
    public void kneeBentMidHold_overGrace_endsHold() {
        sitUpright(1000);
        h.move(UPRIGHT, 30, STRAIGHT_KNEE, STRAIGHT_KNEE, 2000, NO_TRUNK);
        h.hold(30, NO_TRUNK, STRAIGHT_KNEE, 8000);
        h.hold(30, NO_TRUNK, 140, 2000);   // 2초 동안 무릎 굽힘 → 1초 넘게 기준 밖
        h.hold(30, NO_TRUNK, STRAIGHT_KNEE, 5000);
        assertEquals(0, h.counter.getCompletedReps());
        assertEquals(RepetitionCounter.RejectReason.RELEASED_EARLY, h.lastRejectReason());
        assertTrue(h.rejected.get(0).durationMs >= 8000);
    }

    @Test
    public void shortKneeBend_pausesButKeepsHold() {
        sitUpright(1000);
        h.move(UPRIGHT, 30, STRAIGHT_KNEE, STRAIGHT_KNEE, 2000, NO_TRUNK);
        h.hold(30, NO_TRUNK, STRAIGHT_KNEE, 10000);
        h.hold(30, NO_TRUNK, 140, 500);    // 0.5초만 굽힘 → 시간은 멈추고 유지는 이어짐
        h.hold(30, NO_TRUNK, STRAIGHT_KNEE, 11000);
        assertEquals(1, h.counter.getCompletedReps());
        assertTrue(h.rejected.isEmpty());
    }

    @Test
    public void kneeNotVisible_kneeRuleSkipped() {
        // 두 다리 모두 무릎이 안 보이면 무릎 조건은 검사하지 않고 상체 기울기로 유지 판정
        sitUpright(1000);
        h.move(UPRIGHT, 30, Double.NaN, Double.NaN, 2000, NO_TRUNK);
        h.hold(30, NO_TRUNK, Double.NaN, HOLD_MS + 500);
        assertEquals(1, h.counter.getCompletedReps());
    }

    // ---------- "조금 더 숙여 주세요" ----------

    @Test
    public void leaningDownSmoothly_neverSaysBendMore() {
        sitUpright(1000);
        h.move(UPRIGHT, 30, STRAIGHT_KNEE, STRAIGHT_KNEE, 3000, NO_TRUNK);
        h.hold(30, NO_TRUNK, STRAIGHT_KNEE, 2000);
        assertEquals(0, appearances(RepetitionCounter.Feedback.BEND_MORE));
    }

    @Test
    public void stallBelowStart_bendMoreOnce_perMotion() {
        sitUpright(1000);
        h.move(UPRIGHT, 12, STRAIGHT_KNEE, STRAIGHT_KNEE, 800, NO_TRUNK);
        h.hold(12, NO_TRUNK, STRAIGHT_KNEE, 1200);
        assertEquals(RepetitionCounter.Feedback.START_BENDING, h.counter.getFeedback());   // 1.5초 전
        h.hold(12, NO_TRUNK, STRAIGHT_KNEE, 600);
        assertEquals(RepetitionCounter.Feedback.BEND_MORE, h.counter.getFeedback());
        // 조금 더 숙여 15도에서 또 멈춰도 같은 동작 안에서는 다시 나오지 않음
        h.move(12, 15, STRAIGHT_KNEE, STRAIGHT_KNEE, 300, NO_TRUNK);
        h.hold(15, NO_TRUNK, STRAIGHT_KNEE, 3000);
        assertEquals(1, appearances(RepetitionCounter.Feedback.BEND_MORE));
        // 세웠다가 다시 숙여 멈추면 새 동작이라 한 번 더
        h.move(15, UPRIGHT, STRAIGHT_KNEE, STRAIGHT_KNEE, 800, NO_TRUNK);
        sitUpright(500);
        h.move(UPRIGHT, 12, STRAIGHT_KNEE, STRAIGHT_KNEE, 800, NO_TRUNK);
        h.hold(12, NO_TRUNK, STRAIGHT_KNEE, 2000);
        assertEquals(2, appearances(RepetitionCounter.Feedback.BEND_MORE));
    }

    // ---------- 관절이 안 보이는 구간 ----------

    @Test
    public void shortOcclusion_pausesButKeepsHold() {
        // 발끝으로 손을 뻗어 1.2초 동안 어깨·엉덩이가 가려짐 (기준 1.5초 이내)
        sitUpright(1000);
        h.move(UPRIGHT, 30, STRAIGHT_KNEE, STRAIGHT_KNEE, 2000, NO_TRUNK);
        h.hold(30, NO_TRUNK, STRAIGHT_KNEE, 10000);
        long heldBefore = counter().getHeldMs();
        h.invisible(1200);
        assertEquals(heldBefore, counter().getHeldMs());   // 안 보이는 동안 시간은 멈춤
        h.hold(30, NO_TRUNK, STRAIGHT_KNEE, 11000);
        assertEquals(1, h.counter.getCompletedReps());
    }

    @Test
    public void longOcclusion_dropsHold() {
        sitUpright(1000);
        h.move(UPRIGHT, 30, STRAIGHT_KNEE, STRAIGHT_KNEE, 2000, NO_TRUNK);
        h.hold(30, NO_TRUNK, STRAIGHT_KNEE, 6000);
        h.invisible(2000);
        assertEquals(RepetitionCounter.Feedback.NOT_VISIBLE, h.counter.getFeedback());
        assertEquals(RepetitionCounter.RejectReason.LOST_TRACKING, h.lastRejectReason());
        assertEquals(0, h.counter.getCompletedReps());
    }

    // ---------- 흔들림 ----------

    @Test
    public void jitteryHold_stillCounts() {
        Random random = new Random(5);
        sitUpright(1000);
        h.move(UPRIGHT, 30, STRAIGHT_KNEE, STRAIGHT_KNEE, 2000, NO_TRUNK, 6, random);
        for (long t = 0; t < HOLD_MS + 1000; t += CounterHarness.FRAME_MS) {
            double knee = STRAIGHT_KNEE + (random.nextDouble() * 2 - 1) * 8;   // 무릎도 167~183으로 흔들림
            h.frame(30 + (random.nextDouble() * 2 - 1) * 6, NO_TRUNK, knee);
        }
        assertEquals(1, h.counter.getCompletedReps());
        assertTrue(h.rejected.isEmpty());
    }

    @Test
    public void singleFrameSpike_skipped() {
        sitUpright(1000);
        h.move(UPRIGHT, 30, STRAIGHT_KNEE, STRAIGHT_KNEE, 2000, NO_TRUNK);
        h.hold(30, NO_TRUNK, STRAIGHT_KNEE, 3000);
        h.frame(85, NO_TRUNK, STRAIGHT_KNEE);   // 한 프레임만 85도로 튐
        h.hold(30, NO_TRUNK, STRAIGHT_KNEE, 18000);
        assertEquals(1, h.counter.getCompletedReps());
        assertTrue(h.counter.getSkippedFrameCount() >= 1);
        assertEquals(30, h.completed.get(0).minJointAngle, 1.5);
    }

    @Test
    public void reset_clearsEverything() {
        sitUpright(1000);
        stretch(30, HOLD_MS);
        h.counter.reset();
        assertEquals(0, h.counter.getCompletedReps());
        assertEquals(RepetitionCounter.Feedback.NOT_VISIBLE, h.counter.getFeedback());
    }

    // 피드백이 새로 나타난 횟수 (이어서 유지된 프레임은 한 번으로 셈)
    private int appearances(RepetitionCounter.Feedback feedback) {
        int count = 0;
        for (int i = 0; i < h.feedbackHistory.size(); i++) {
            if (h.feedbackHistory.get(i) == feedback && (i == 0 || h.feedbackHistory.get(i - 1) != feedback)) {
                count++;
            }
        }
        return count;
    }
}

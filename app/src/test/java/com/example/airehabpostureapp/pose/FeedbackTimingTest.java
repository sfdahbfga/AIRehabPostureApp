package com.example.airehabpostureapp.pose;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/**
 * 안내 문구 시점 (무릎 굽힘 / 의자 스쿼트 / 런지)
 * - 내려가는 중에는 "조금 더"(BEND_MORE)를 띄우지 않고 시작 안내 유지
 * - "좋아요" 지점(목표 + 덜 굽힘 허용: 스쿼트 110도, 런지 100도, 무릎 굽힘 100도)에 닿으면 GOOD_STRAIGHTEN
 * - 일어서는 중(가장 깊은 각도보다 5도 넘게 펴짐)에는 BEND_MORE가 새로 나오지 않음
 * - "좋아요" 지점 전에 0.8초 넘게 멈추면 BEND_MORE를 동작 하나에 한 번만
 * - 1회 인정 기준까지 못 굽히고 끝나면(SHALLOW) 일어선 뒤 SHALLOW_RETRY
 * 판정 / 횟수는 바뀌지 않았는지도 함께 확인한다.
 */
public class FeedbackTimingTest {

    private static final double STAND = 175;
    private static final double TRUNK = 30;
    // 스쿼트로 앉을 때의 엉덩이 각도 (엉덩이 규칙에 걸리지 않게 충분히 굽힘)
    private static final double SQUAT_HIP = 80;

    private static CounterHarness squatHarness() {
        return new CounterHarness(new RepetitionCounter(ExerciseCriteria.CHAIR_SQUAT));
    }

    private static void stand(CounterHarness h, long durationMs) {
        h.hold(STAND, 5, STAND, durationMs);
    }

    /** 스쿼트 1회: 1.2초 동안 minKnee까지 앉음 → 0.3초 유지 → 1.2초 동안 일어섬 → 0.4초 서 있기 */
    private static void squat(CounterHarness h, double minKnee) {
        h.move(STAND, minKnee, STAND, SQUAT_HIP, 1200, TRUNK);
        h.hold(minKnee, TRUNK, SQUAT_HIP, 300);
        h.move(minKnee, STAND, SQUAT_HIP, STAND, 1200, TRUNK);
        stand(h, 400);
    }

    // BEND_MORE가 새로 나타난 횟수 (이어서 유지된 프레임은 한 번으로 셈 = 음성이 읽히는 횟수)
    private static int appearances(List<RepetitionCounter.Feedback> history, RepetitionCounter.Feedback feedback) {
        int count = 0;
        for (int i = 0; i < history.size(); i++) {
            if (history.get(i) == feedback && (i == 0 || history.get(i - 1) != feedback)) {
                count++;
            }
        }
        return count;
    }

    // ---------- 정상 동작: "조금 더"가 한 번도 안 나옴 ----------

    @Test
    public void chairSquat_normalReps_neverShowBendMore() {
        CounterHarness h = squatHarness();
        stand(h, 500);
        for (double knee : new double[]{108, 103, 105, 106, 101}) {
            squat(h, knee);
        }
        assertEquals(5, h.counter.getCompletedReps());
        assertEquals(0, appearances(h.feedbackHistory, RepetitionCounter.Feedback.BEND_MORE));
        assertEquals(5, appearances(h.feedbackHistory, RepetitionCounter.Feedback.GOOD_STRAIGHTEN));
    }

    @Test
    public void lunge_normalReps_neverShowBendMore() {
        CounterHarness h = new CounterHarness(new RepetitionCounter(ExerciseCriteria.LUNGE));
        h.hold(170, 5, 170, 500);
        for (int i = 0; i < 3; i++) {
            h.move(170, 92, 170, 100, 1000, 5);
            h.hold(92, 5, 100, 300);
            h.move(92, 170, 100, 170, 1000, 5);
            h.hold(170, 5, 170, 400);
        }
        assertEquals(3, h.counter.getCompletedReps());
        assertEquals(0, appearances(h.feedbackHistory, RepetitionCounter.Feedback.BEND_MORE));
        assertEquals(3, appearances(h.feedbackHistory, RepetitionCounter.Feedback.GOOD_STRAIGHTEN));
    }

    @Test
    public void kneeFlexion_normalReps_neverShowBendMore() {
        CounterHarness h = new CounterHarness(new RepetitionCounter(ExerciseCriteria.KNEE_FLEXION));
        h.hold(175, 5, 170, 500);
        for (int i = 0; i < 3; i++) {
            h.move(175, 92, 170, 170, 1000, 5);
            h.hold(92, 5, 170, 300);
            h.move(92, 175, 170, 170, 1000, 5);
            h.hold(175, 5, 170, 400);
        }
        assertEquals(3, h.counter.getCompletedReps());
        assertEquals(0, appearances(h.feedbackHistory, RepetitionCounter.Feedback.BEND_MORE));
        assertEquals(3, appearances(h.feedbackHistory, RepetitionCounter.Feedback.GOOD_STRAIGHTEN));
    }

    // ---------- 일어서는 중 ----------

    @Test
    public void rising_afterGood_staysGood_untilStanding() {
        // 실측 A세트에서 일어서며 "좋아요" → "조금 더 앉아"로 바뀌던 구간: 이제 "좋아요"가 유지됨
        CounterHarness h = squatHarness();
        stand(h, 500);
        h.move(STAND, 105, STAND, SQUAT_HIP, 1200, TRUNK);
        h.hold(105, TRUNK, SQUAT_HIP, 300);
        assertEquals(RepetitionCounter.Feedback.GOOD_STRAIGHTEN, h.counter.getFeedback());
        h.move(105, 150, SQUAT_HIP, 150, 900, TRUNK);
        assertEquals(RepetitionCounter.Feedback.GOOD_STRAIGHTEN, h.counter.getFeedback());
        h.move(150, STAND, 150, STAND, 400, TRUNK);
        stand(h, 400);
        assertEquals(1, h.counter.getCompletedReps());
        assertEquals(0, appearances(h.feedbackHistory, RepetitionCounter.Feedback.BEND_MORE));
        assertEquals(RepetitionCounter.Feedback.START_BENDING, h.counter.getFeedback());
    }

    @Test
    public void rising_slowlyWithPause_neverShowsBendMore() {
        // "좋아요" 지점 전(115도)까지만 앉았다가 천천히 일어서며 중간(135도)에서 1.5초 멈춤
        // → 일어서는 중이므로 멈춰도 "조금 더"를 띄우지 않음. 120도 이하까지 앉았으므로 1회 인정
        CounterHarness h = squatHarness();
        stand(h, 500);
        h.move(STAND, 115, STAND, SQUAT_HIP, 1200, TRUNK);
        h.hold(115, TRUNK, SQUAT_HIP, 300);
        h.move(115, 135, SQUAT_HIP, 110, 800, TRUNK);
        h.hold(135, TRUNK, 110, 1500);
        h.move(135, STAND, 110, STAND, 800, TRUNK);
        stand(h, 400);
        assertEquals(1, h.counter.getCompletedReps());
        assertEquals(0, appearances(h.feedbackHistory, RepetitionCounter.Feedback.BEND_MORE));
    }

    // ---------- 덜 한 동작 ----------

    @Test
    public void shallowSquat_showsRetryHint_afterStanding_untilNextSquat() {
        CounterHarness h = squatHarness();
        stand(h, 500);
        h.move(STAND, 140, STAND, 110, 700, TRUNK);
        h.hold(140, TRUNK, 110, 300);
        // 동작 중에는 시작 안내 (거부 문구는 아직 없음)
        assertEquals(RepetitionCounter.Feedback.START_BENDING, h.counter.getFeedback());
        h.move(140, STAND, 110, STAND, 700, TRUNK);
        stand(h, 400);
        assertEquals(0, h.counter.getCompletedReps());
        assertEquals(RepetitionCounter.RejectReason.SHALLOW, h.lastRejectReason());
        assertEquals(RepetitionCounter.Feedback.SHALLOW_RETRY, h.counter.getFeedback());
        assertEquals(0, appearances(h.feedbackHistory, RepetitionCounter.Feedback.BEND_MORE));
        // 서 있는 동안 유지, 다음 동작을 시작하면 사라짐
        stand(h, 1000);
        assertEquals(RepetitionCounter.Feedback.SHALLOW_RETRY, h.counter.getFeedback());
        h.move(STAND, 140, STAND, 110, 700, TRUNK);
        assertEquals(RepetitionCounter.Feedback.START_BENDING, h.counter.getFeedback());
    }

    @Test
    public void threeShallowSquats_eachShowRetryHint_onceAfterEach() {
        CounterHarness h = squatHarness();
        stand(h, 500);
        for (double knee : new double[]{143.8, 147.8, 136.4}) {
            h.move(STAND, knee, STAND, 110, 500, TRUNK);
            h.hold(knee, TRUNK, 110, 200);
            h.move(knee, STAND, 110, STAND, 500, TRUNK);
            stand(h, 600);
        }
        assertEquals(0, h.counter.getCompletedReps());
        assertEquals(3, appearances(h.feedbackHistory, RepetitionCounter.Feedback.SHALLOW_RETRY));
        assertEquals(0, appearances(h.feedbackHistory, RepetitionCounter.Feedback.BEND_MORE));
    }

    @Test
    public void kneeOnlySquat_stillShowsHipsBack_notShallowRetry() {
        CounterHarness h = squatHarness();
        stand(h, 500);
        h.move(STAND, 146, STAND, 167, 600, 5);
        h.hold(146, 5, 167, 200);
        h.move(146, STAND, 167, STAND, 600, 5);
        stand(h, 400);
        assertEquals(RepetitionCounter.RejectReason.HIP_NOT_BENT, h.lastRejectReason());
        assertEquals(RepetitionCounter.Feedback.HIPS_BACK, h.counter.getFeedback());
    }

    @Test
    public void shallowLunge_andKneeFlexion_showRetryHint() {
        CounterHarness lunge = new CounterHarness(new RepetitionCounter(ExerciseCriteria.LUNGE));
        lunge.hold(170, 5, 170, 500);
        lunge.move(170, 135, 170, 140, 800, 5);
        lunge.hold(135, 5, 140, 300);
        lunge.move(135, 170, 140, 170, 800, 5);
        lunge.hold(170, 5, 170, 400);
        assertEquals(RepetitionCounter.Feedback.SHALLOW_RETRY, lunge.counter.getFeedback());

        CounterHarness knee = new CounterHarness(new RepetitionCounter(ExerciseCriteria.KNEE_FLEXION));
        knee.hold(175, 5, 170, 500);
        knee.move(175, 140, 170, 170, 800, 5);
        knee.hold(140, 5, 170, 300);
        knee.move(140, 175, 170, 170, 800, 5);
        knee.hold(175, 5, 170, 400);
        assertEquals(RepetitionCounter.Feedback.SHALLOW_RETRY, knee.counter.getFeedback());
    }

    // ---------- 중간에 멈춤: 한 번만 ----------

    @Test
    public void stallAboveCountThreshold_showsBendMoreOnce() {
        // 140도(1회 인정 120도 전)에서 2초 멈춤 → 0.8초 지나 한 번, 계속 멈춰 있어도 다시 나오지 않음
        CounterHarness h = squatHarness();
        stand(h, 500);
        h.move(STAND, 140, STAND, 110, 700, TRUNK);
        h.hold(140, TRUNK, 110, 2000);
        assertEquals(RepetitionCounter.Feedback.BEND_MORE, h.counter.getFeedback());
        // 조금 더 내려가 다시 멈춰도(130도) 같은 동작 안에서는 새로 나오지 않음
        h.move(140, 130, 110, 100, 300, TRUNK);
        h.hold(130, TRUNK, 100, 2000);
        h.move(130, STAND, 100, STAND, 700, TRUNK);
        stand(h, 400);
        assertEquals(1, appearances(h.feedbackHistory, RepetitionCounter.Feedback.BEND_MORE));
        assertEquals(0, h.counter.getCompletedReps());
    }

    @Test
    public void stallBetweenCountThresholdAndGood_showsBendMoreOnce_thenGood() {
        // 115도: 1회 인정 기준(120도)은 넘었지만 "좋아요" 지점(110도) 전 → 0.8초 넘게 멈추면 한 번
        CounterHarness h = squatHarness();
        stand(h, 500);
        h.move(STAND, 115, STAND, SQUAT_HIP, 1000, TRUNK);
        h.hold(115, TRUNK, SQUAT_HIP, 500);
        assertEquals(RepetitionCounter.Feedback.START_BENDING, h.counter.getFeedback());
        h.hold(115, TRUNK, SQUAT_HIP, 1000);
        assertEquals(RepetitionCounter.Feedback.BEND_MORE, h.counter.getFeedback());
        // 더 앉으면 "좋아요"
        h.move(115, 105, SQUAT_HIP, SQUAT_HIP, 400, TRUNK);
        h.hold(105, TRUNK, SQUAT_HIP, 300);
        assertEquals(RepetitionCounter.Feedback.GOOD_STRAIGHTEN, h.counter.getFeedback());
        h.move(105, STAND, SQUAT_HIP, STAND, 1200, TRUNK);
        stand(h, 400);
        assertEquals(1, appearances(h.feedbackHistory, RepetitionCounter.Feedback.BEND_MORE));
        assertEquals(1, h.counter.getCompletedReps());
    }

    @Test
    public void stallBetweenCountThresholdAndGood_thenStandUp_stillCounts_noRepeat() {
        // 115도에서 멈춰 안내를 받고 그대로 일어섬 → 120도 이하였으므로 1회 인정, 안내는 한 번뿐
        CounterHarness h = squatHarness();
        stand(h, 500);
        h.move(STAND, 115, STAND, SQUAT_HIP, 1000, TRUNK);
        h.hold(115, TRUNK, SQUAT_HIP, 1500);
        h.move(115, STAND, SQUAT_HIP, STAND, 1200, TRUNK);
        stand(h, 400);
        assertEquals(1, h.counter.getCompletedReps());
        assertEquals(1, appearances(h.feedbackHistory, RepetitionCounter.Feedback.BEND_MORE));
    }

    @Test
    public void shortPause_underStallTime_doesNotShowBendMore() {
        CounterHarness h = squatHarness();
        stand(h, 500);
        h.move(STAND, 130, STAND, 100, 700, TRUNK);
        h.hold(130, TRUNK, 100, 500);
        h.move(130, 105, 100, SQUAT_HIP, 500, TRUNK);
        h.hold(105, TRUNK, SQUAT_HIP, 300);
        h.move(105, STAND, SQUAT_HIP, STAND, 1200, TRUNK);
        stand(h, 400);
        assertEquals(1, h.counter.getCompletedReps());
        assertEquals(0, appearances(h.feedbackHistory, RepetitionCounter.Feedback.BEND_MORE));
    }

    @Test
    public void stallHint_isOncePerMotion_notOncePerSession() {
        CounterHarness h = squatHarness();
        stand(h, 500);
        for (int i = 0; i < 2; i++) {
            h.move(STAND, 140, STAND, 110, 700, TRUNK);
            h.hold(140, TRUNK, 110, 1500);
            h.move(140, STAND, 110, STAND, 700, TRUNK);
            stand(h, 600);
        }
        assertEquals(2, appearances(h.feedbackHistory, RepetitionCounter.Feedback.BEND_MORE));
    }

    @Test
    public void lungeAndKneeFlexion_stallShowsBendMoreOnce() {
        CounterHarness lunge = new CounterHarness(new RepetitionCounter(ExerciseCriteria.LUNGE));
        lunge.hold(170, 5, 170, 500);
        lunge.move(170, 110, 170, 120, 800, 5);
        lunge.hold(110, 5, 120, 2000);
        assertEquals(RepetitionCounter.Feedback.BEND_MORE, lunge.counter.getFeedback());
        assertEquals(1, appearances(lunge.feedbackHistory, RepetitionCounter.Feedback.BEND_MORE));

        CounterHarness knee = new CounterHarness(new RepetitionCounter(ExerciseCriteria.KNEE_FLEXION));
        knee.hold(175, 5, 170, 500);
        knee.move(175, 115, 170, 170, 800, 5);
        knee.hold(115, 5, 170, 2000);
        assertEquals(RepetitionCounter.Feedback.BEND_MORE, knee.counter.getFeedback());
        assertEquals(1, appearances(knee.feedbackHistory, RepetitionCounter.Feedback.BEND_MORE));
    }

    @Test
    public void nearlyStanding_pause_doesNotShowBendMore() {
        // 편 상태에서 10도도 안 굽힌 채 멈춤(서서 쉬는 자세) → 안내하지 않음
        CounterHarness h = squatHarness();
        stand(h, 500);
        h.move(STAND, 155, STAND, 165, 300, TRUNK);
        h.hold(155, TRUNK, 165, 2000);
        assertTrue(appearances(h.feedbackHistory, RepetitionCounter.Feedback.BEND_MORE) == 0);
    }
}

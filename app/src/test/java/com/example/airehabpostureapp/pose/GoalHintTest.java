package com.example.airehabpostureapp.pose;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 목표 도달 화면 안내: 최대 6초, 다음 동작 시작 / 자세 경고가 생기면 바로 내림,
 * 하체 스트레칭은 목표 안내 다음에 "천천히 상체를 세워 주세요"를 이어서 보여 줌
 */
public class GoalHintTest {

    private static final RepetitionCounter.Feedback START = RepetitionCounter.Feedback.START_BENDING;
    private static final RepetitionCounter.Feedback RELEASE = RepetitionCounter.Feedback.RELEASE;

    // ---------- 규칙 단위 ----------

    @Test
    public void nothingHappens_goalShownUpToSixSeconds() {
        GoalHint g = new GoalHint();
        g.onGoalReached(1000, START);
        assertEquals(GoalHint.Display.GOAL, g.update(1000, false, START));
        assertEquals(GoalHint.Display.GOAL, g.update(6999, false, START));
        assertEquals(GoalHint.Display.NORMAL, g.update(7000, false, START));
        assertEquals(GoalHint.Display.NORMAL, g.update(9000, false, START));
    }

    @Test
    public void nextMotionStarts_goalRemovedImmediately_andDoesNotComeBack() {
        GoalHint g = new GoalHint();
        g.onGoalReached(0, START);
        assertEquals(GoalHint.Display.GOAL, g.update(500, false, START));
        assertEquals(GoalHint.Display.NORMAL, g.update(1000, true, START));
        // 동작을 멈추고 다시 서도 목표 안내로 돌아가지 않음
        assertEquals(GoalHint.Display.NORMAL, g.update(1500, false, START));
    }

    @Test
    public void postureWarning_goalRemovedImmediately() {
        for (RepetitionCounter.Feedback warning : new RepetitionCounter.Feedback[]{
                RepetitionCounter.Feedback.KEEP_TRUNK_UPRIGHT, RepetitionCounter.Feedback.THIGH_UPRIGHT,
                RepetitionCounter.Feedback.HIPS_BACK, RepetitionCounter.Feedback.STRAIGHTEN_KNEE}) {
            GoalHint g = new GoalHint();
            g.onGoalReached(0, START);
            assertEquals(warning.name(), GoalHint.Display.NORMAL, g.update(300, false, warning));
        }
    }

    @Test
    public void holdType_releaseShownAfterGoal() {
        GoalHint g = new GoalHint();
        g.onGoalReached(0, RELEASE);
        assertEquals(GoalHint.Display.GOAL, g.update(5000, false, RELEASE));
        assertEquals(GoalHint.Display.RELEASE_FOLLOW_UP, g.update(6000, false, RELEASE));
        // 이미 상체를 세웠어도(START_BENDING) 놓치지 않게 이어서 보여 줌
        assertEquals(GoalHint.Display.RELEASE_FOLLOW_UP, g.update(8999, false, START));
        assertEquals(GoalHint.Display.NORMAL, g.update(9000, false, START));
    }

    @Test
    public void holdType_newHoldStarts_followUpRemoved() {
        GoalHint g = new GoalHint();
        g.onGoalReached(0, RELEASE);
        g.update(6500, false, RELEASE);
        assertEquals(GoalHint.Display.NORMAL, g.update(7000, true, RepetitionCounter.Feedback.HOLD));
        assertEquals(GoalHint.Display.NORMAL, g.update(7500, false, RELEASE));
    }

    @Test
    public void repTypeGoal_noFollowUp() {
        GoalHint g = new GoalHint();
        g.onGoalReached(0, START);
        assertEquals(GoalHint.Display.NORMAL, g.update(6000, false, START));
    }

    // ---------- 측정기와 함께 (ExerciseActivity의 프레임 처리와 같은 순서) ----------

    /** 30fps로 측정기에 각도를 넣고, 프레임마다 화면에 보이는 것(목표 / 이어서 안내 / 측정기 안내)을 기록 */
    private static final class ScreenSim {
        final ExerciseCounter counter;
        final int targetReps;
        final GoalHint hint = new GoalHint();
        final List<String> shown = new ArrayList<>();
        long t;

        ScreenSim(ExerciseCounter counter, int targetReps) {
            this.counter = counter;
            this.targetReps = targetReps;
        }

        void frame(double joint, double trunk, double aux) {
            t += 33;
            RepetitionCounter.RepResult rep = counter.onFrame(t, joint, trunk, aux);
            if (rep != null && RepGoal.isJustReached(counter.getCompletedReps(), targetReps)) {
                hint.onGoalReached(t, counter.getFeedback());
            }
            GoalHint.Display d = hint.update(t, counter.isRepInProgress(), counter.getFeedback());
            String label = d == GoalHint.Display.GOAL ? "GOAL"
                    : d == GoalHint.Display.RELEASE_FOLLOW_UP ? "RELEASE_FOLLOW_UP"
                    : counter.getFeedback().name();
            if (shown.isEmpty() || !shown.get(shown.size() - 1).equals(label)) {
                shown.add(label);
            }
        }

        void hold(double joint, double trunk, double aux, long ms) {
            for (long i = 0; i < ms; i += 33) {
                frame(joint, trunk, aux);
            }
        }

        void move(double fromJoint, double toJoint, double fromAux, double toAux, double trunk, long ms) {
            int n = (int) (ms / 33);
            for (int i = 1; i <= n; i++) {
                double r = (double) i / n;
                frame(fromJoint + (toJoint - fromJoint) * r, trunk, fromAux + (toAux - fromAux) * r);
            }
        }

        void lunge(double trunk) {
            move(170, 88, 170, 95, 5, 1000);
            hold(88, trunk, 95, 300);
            move(88, 170, 95, 170, 5, 1000);
            hold(170, 5, 170, 400);
        }
    }

    @Test
    public void lunge_tiredAfterGoal_trunkWarningShownRightAway() {
        // 8회(목표)를 채운 뒤 1초 만에 9회째를 시작해 상체를 숙임 → 목표 안내가 가리지 않고 경고가 보임
        ScreenSim s = new ScreenSim(new RepetitionCounter(ExerciseCriteria.LUNGE), ExerciseCriteria.LUNGE.targetReps);
        s.hold(170, 5, 170, 500);
        for (int i = 0; i < 8; i++) {
            s.lunge(5);
        }
        assertEquals("GOAL", s.shown.get(s.shown.size() - 1));
        s.lunge(35);
        assertEquals(9, s.counter.getCompletedReps());
        int goal = s.shown.lastIndexOf("GOAL");
        assertTrue(s.shown.subList(goal, s.shown.size()).contains("KEEP_TRUNK_UPRIGHT"));
        // 목표 안내는 한 번만
        assertEquals(1, s.shown.stream().filter("GOAL"::equals).count());
    }

    @Test
    public void lunge_restAfterGoal_goalShownSixSeconds_thenNormal() {
        ScreenSim s = new ScreenSim(new RepetitionCounter(ExerciseCriteria.LUNGE), ExerciseCriteria.LUNGE.targetReps);
        s.hold(170, 5, 170, 500);
        for (int i = 0; i < 8; i++) {
            s.lunge(5);
        }
        s.hold(170, 5, 170, 5000);
        assertEquals("GOAL", s.shown.get(s.shown.size() - 1));
        s.hold(170, 5, 170, 2000);
        assertEquals("START_BENDING", s.shown.get(s.shown.size() - 1));
    }

    @Test
    public void hamstringStretch_thirdHold_goalThenRelease() {
        ScreenSim s = new ScreenSim(new HoldCounter(ExerciseCriteria.SEATED_HAMSTRING_STRETCH),
                ExerciseCriteria.SEATED_HAMSTRING_STRETCH.targetReps);
        // 판정 값 = 앞으로 숙인 상체 기울기: 똑바로 0도, 30도로 숙여 유지
        s.hold(0, Double.NaN, 175, 1000);
        for (int i = 0; i < 3; i++) {
            s.move(0, 30, 175, 175, Double.NaN, 2000);
            s.hold(30, Double.NaN, 175, 20000);
            if (i < 2) {
                s.move(30, 0, 175, 175, Double.NaN, 2000);
                s.hold(0, Double.NaN, 175, 1000);
            }
        }
        // 3번째 유지가 끝난 뒤 숙인 채로 잠시 있다가 천천히 세움
        s.hold(30, Double.NaN, 175, 2000);
        s.move(30, 0, 175, 175, Double.NaN, 3000);
        s.hold(0, Double.NaN, 175, 6000);
        assertEquals(3, s.counter.getCompletedReps());
        int goal = s.shown.indexOf("GOAL");
        assertTrue(goal >= 0);
        assertEquals("RELEASE_FOLLOW_UP", s.shown.get(goal + 1));
        assertFalse(s.shown.subList(goal + 2, s.shown.size()).contains("GOAL"));
    }
}

package com.example.airehabpostureapp.pose;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 목표 횟수 도달 안내: 목표 회차에서 한 번만, 넘긴 뒤에도 횟수는 계속 셈
 * (ExerciseActivity는 1회가 끝날 때마다 isJustReached(완료 횟수, 목표 횟수)를 확인)
 */
public class RepGoalTest {

    @Test
    public void justReached_onlyAtTarget() {
        assertFalse(RepGoal.isJustReached(7, 8));
        assertTrue(RepGoal.isJustReached(8, 8));
        assertFalse(RepGoal.isJustReached(9, 8));
        assertFalse(RepGoal.isJustReached(12, 8));
        // 목표가 없으면 안내하지 않음
        assertFalse(RepGoal.isJustReached(0, 0));
    }

    // 끝난 회차마다 안내 여부를 확인해 안내한 회차 번호를 모음
    private static List<Integer> announcedAt(CounterHarness h, int targetReps) {
        List<Integer> announced = new ArrayList<>();
        for (int i = 0; i < h.completed.size(); i++) {
            if (RepGoal.isJustReached(i + 1, targetReps)) {
                announced.add(i + 1);
            }
        }
        return announced;
    }

    private static void standingRep(CounterHarness h, double up, double minJoint, double upAux, double minAux,
                                    double trunk) {
        h.move(up, minJoint, upAux, minAux, 1200, trunk);
        h.hold(minJoint, trunk, minAux, 300);
        h.move(minJoint, up, minAux, upAux, 1200, trunk);
        h.hold(up, trunk, upAux, 400);
    }

    @Test
    public void chairSquat_twelveReps_announcedOnceAtTen_keepsCounting() {
        CounterHarness h = new CounterHarness(new RepetitionCounter(ExerciseCriteria.CHAIR_SQUAT));
        h.hold(175, 30, 175, 500);
        for (int i = 0; i < 12; i++) {
            standingRep(h, 175, 105, 175, 80, 30);
        }
        assertEquals(12, h.counter.getCompletedReps());
        assertEquals(Collections.singletonList(10), announcedAt(h, ExerciseCriteria.CHAIR_SQUAT.targetReps));
    }

    @Test
    public void lunge_twelveReps_announcedOnceAtEight_keepsCounting() {
        // 실측 런지 테스트처럼 12 / 8회까지
        CounterHarness h = new CounterHarness(new RepetitionCounter(ExerciseCriteria.LUNGE));
        h.hold(170, 5, 170, 500);
        for (int i = 0; i < 12; i++) {
            standingRep(h, 170, 88, 170, 95, 5);
        }
        assertEquals(12, h.counter.getCompletedReps());
        assertEquals(Collections.singletonList(8), announcedAt(h, ExerciseCriteria.LUNGE.targetReps));
    }

    @Test
    public void kneeFlexion_announcedOnceAtTwenty() {
        CounterHarness h = new CounterHarness(new RepetitionCounter(ExerciseCriteria.KNEE_FLEXION));
        h.hold(175, 5, 170, 500);
        for (int i = 0; i < 21; i++) {
            standingRep(h, 175, 92, 170, 170, 5);
        }
        assertEquals(21, h.counter.getCompletedReps());
        assertEquals(Collections.singletonList(20), announcedAt(h, ExerciseCriteria.KNEE_FLEXION.targetReps));
    }

    @Test
    public void hamstringStretch_announcedOnceAtThirdHold() {
        CounterHarness h = new CounterHarness(new HoldCounter(ExerciseCriteria.SEATED_HAMSTRING_STRETCH));
        h.hold(120, Double.NaN, 175, 1000);
        for (int i = 0; i < 4; i++) {
            h.move(120, 95, 175, 175, 2000, Double.NaN);
            h.hold(95, Double.NaN, 175, 20000);
            h.move(95, 120, 175, 175, 2000, Double.NaN);
            h.hold(120, Double.NaN, 175, 1000);
        }
        assertEquals(4, h.counter.getCompletedReps());
        assertEquals(Collections.singletonList(3), announcedAt(h, ExerciseCriteria.SEATED_HAMSTRING_STRETCH.targetReps));
    }
}

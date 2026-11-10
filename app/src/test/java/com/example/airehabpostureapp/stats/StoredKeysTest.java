package com.example.airehabpostureapp.stats;

import static org.junit.Assert.assertEquals;

import com.example.airehabpostureapp.network.CheckType;

import org.junit.Test;

// L12: DB에 저장되는 키 값이 바뀌면 과거 기록을 읽지 못하므로 값을 고정해 둔다
public class StoredKeysTest {

    @Test
    public void jointErrorKeys() {
        assertEquals("knee", JointKeys.KNEE);
        assertEquals("back", JointKeys.BACK);
        assertEquals("hip", JointKeys.HIP);
    }

    @Test
    public void checkTypes_matchDbConstraint() {
        // session_checkins.check_type: CHECK (check_type IN ('pre', 'post'))
        assertEquals("pre", CheckType.PRE);
        assertEquals("post", CheckType.POST);
    }

    @Test
    public void goalTargets_unchanged() {
        assertEquals(3, GoalTargets.WEEKLY_EXERCISE_COUNT);
        assertEquals(60, GoalTargets.WEEKLY_EXERCISE_MINUTES);
        assertEquals(90, GoalTargets.AVERAGE_ACCURACY);
        assertEquals(7, GoalTargets.CHALLENGE_STREAK_DAYS);
    }
}

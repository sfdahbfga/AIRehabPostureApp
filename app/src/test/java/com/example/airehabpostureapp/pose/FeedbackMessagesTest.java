package com.example.airehabpostureapp.pose;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.example.airehabpostureapp.R;

import org.junit.Test;

// M12: ExerciseActivity에서 옮긴 안내 문구 매핑 (동작 그대로인지 확인)
public class FeedbackMessagesTest {

    private static int message(ExerciseCriteria criteria, RepetitionCounter.Feedback feedback, int reps) {
        return FeedbackMessages.forFeedback(criteria, feedback, reps);
    }

    @Test
    public void chairSquat_usesSquatMessages_andCommonForOthers() {
        ExerciseCriteria c = ExerciseCriteria.CHAIR_SQUAT;
        assertEquals(R.string.feedback_squat_sit_more, message(c, RepetitionCounter.Feedback.BEND_MORE, 1));
        assertEquals(R.string.feedback_squat_good_stand_up, message(c, RepetitionCounter.Feedback.GOOD_STRAIGHTEN, 1));
        assertEquals(R.string.feedback_squat_hips_back, message(c, RepetitionCounter.Feedback.HIPS_BACK, 1));
        assertEquals(R.string.feedback_squat_start, message(c, RepetitionCounter.Feedback.START_BENDING, 1));
        assertEquals(R.string.feedback_squat_shallow, message(c, RepetitionCounter.Feedback.SHALLOW_RETRY, 1));
        // 운동별 문구가 없으면 공통 문구
        assertEquals(R.string.feedback_full_body_visible, message(c, RepetitionCounter.Feedback.NOT_VISIBLE, 1));
        assertEquals(R.string.feedback_keep_trunk_upright,
                message(c, RepetitionCounter.Feedback.KEEP_TRUNK_UPRIGHT, 1));
    }

    @Test
    public void lunge_usesLungeMessages() {
        ExerciseCriteria c = ExerciseCriteria.LUNGE;
        assertEquals(R.string.feedback_lunge_lower_more, message(c, RepetitionCounter.Feedback.BEND_MORE, 0));
        assertEquals(R.string.feedback_lunge_good_come_up, message(c, RepetitionCounter.Feedback.GOOD_STRAIGHTEN, 0));
        assertEquals(R.string.feedback_lunge_start, message(c, RepetitionCounter.Feedback.START_BENDING, 0));
        assertEquals(R.string.feedback_lunge_shallow, message(c, RepetitionCounter.Feedback.SHALLOW_RETRY, 0));
        assertEquals(R.string.feedback_stand_straight, message(c, RepetitionCounter.Feedback.STAND_STRAIGHT, 0));
    }

    @Test
    public void hamstringStretch_usesHoldMessages() {
        ExerciseCriteria c = ExerciseCriteria.SEATED_HAMSTRING_STRETCH;
        assertEquals(R.string.feedback_stretch_start, message(c, RepetitionCounter.Feedback.START_BENDING, 0));
        assertEquals(R.string.feedback_stretch_lean_more, message(c, RepetitionCounter.Feedback.BEND_MORE, 0));
        assertEquals(R.string.feedback_stretch_hold, message(c, RepetitionCounter.Feedback.HOLD, 0));
        assertEquals(R.string.feedback_stretch_straighten_knee,
                message(c, RepetitionCounter.Feedback.STRAIGHTEN_KNEE, 0));
        assertEquals(R.string.feedback_stretch_release, message(c, RepetitionCounter.Feedback.RELEASE, 0));
    }

    @Test
    public void kneeFlexion_startBending_dependsOnCompletedReps() {
        ExerciseCriteria c = ExerciseCriteria.KNEE_FLEXION;
        // 첫 동작 전: 어느 다리로 할지 안내, 1회 이후: "굽혀 주세요"
        assertEquals(R.string.feedback_use_camera_side_leg, message(c, RepetitionCounter.Feedback.START_BENDING, 0));
        assertEquals(R.string.feedback_start_bending, message(c, RepetitionCounter.Feedback.START_BENDING, 1));
        assertEquals(R.string.feedback_bend_knee_more, message(c, RepetitionCounter.Feedback.BEND_MORE, 1));
        assertEquals(R.string.feedback_good_straighten, message(c, RepetitionCounter.Feedback.GOOD_STRAIGHTEN, 1));
        assertEquals(R.string.feedback_thigh_upright, message(c, RepetitionCounter.Feedback.THIGH_UPRIGHT, 1));
        assertEquals(R.string.feedback_knee_shallow, message(c, RepetitionCounter.Feedback.SHALLOW_RETRY, 1));
    }

    @Test
    public void noCriteria_usesCommonMessages() {
        assertEquals(R.string.feedback_bend_knee_more, message(null, RepetitionCounter.Feedback.BEND_MORE, 0));
        assertEquals(R.string.feedback_use_camera_side_leg,
                message(null, RepetitionCounter.Feedback.START_BENDING, 0));
        assertEquals(R.string.feedback_stand_sideways, FeedbackMessages.sideways(null));
        assertEquals(R.string.feedback_use_camera_side_leg, FeedbackMessages.legLocked(null));
    }

    @Test
    public void rising_perExercise_isScreenOnly() {
        assertEquals(R.string.feedback_squat_rising,
                message(ExerciseCriteria.CHAIR_SQUAT, RepetitionCounter.Feedback.RISING, 1));
        assertEquals(R.string.feedback_lunge_rising,
                message(ExerciseCriteria.LUNGE, RepetitionCounter.Feedback.RISING, 1));
        assertEquals(R.string.feedback_knee_rising,
                message(ExerciseCriteria.KNEE_FLEXION, RepetitionCounter.Feedback.RISING, 1));
        assertTrue(FeedbackMessages.isScreenOnly(R.string.feedback_squat_rising));
        assertTrue(FeedbackMessages.isScreenOnly(R.string.feedback_lunge_rising));
        assertTrue(FeedbackMessages.isScreenOnly(R.string.feedback_knee_rising));
        // 평가 / 깊이 안내는 그대로 음성으로 읽음
        assertFalse(FeedbackMessages.isScreenOnly(R.string.feedback_squat_sit_more));
        assertFalse(FeedbackMessages.isScreenOnly(R.string.feedback_squat_good_stand_up));
        assertFalse(FeedbackMessages.isScreenOnly(R.string.feedback_squat_shallow));
        assertFalse(FeedbackMessages.isScreenOnly(R.string.feedback_squat_start));
    }

    @Test
    public void hamstringStretch_neverAsksToStand() {
        ExerciseCriteria c = ExerciseCriteria.SEATED_HAMSTRING_STRETCH;
        // 측정 중 다리를 놓치면 "전신이 보이게 앉아 주세요."
        assertEquals(R.string.feedback_stretch_full_body_visible,
                message(c, RepetitionCounter.Feedback.NOT_VISIBLE, 1));
        // 다리를 정하기 전에는 다리가 보이든 안 보이든 "옆으로 앉아 주세요"
        assertEquals(R.string.feedback_stretch_sit_sideways, FeedbackMessages.beforeLegLocked(c, false));
        assertEquals(R.string.feedback_stretch_sit_sideways, FeedbackMessages.beforeLegLocked(c, true));
        // 하체 스트레칭 측정기가 내는 안내 전부에 "서 주세요" 문구가 없음
        for (RepetitionCounter.Feedback f : new RepetitionCounter.Feedback[]{
                RepetitionCounter.Feedback.NOT_VISIBLE, RepetitionCounter.Feedback.START_BENDING,
                RepetitionCounter.Feedback.BEND_MORE, RepetitionCounter.Feedback.HOLD,
                RepetitionCounter.Feedback.STRAIGHTEN_KNEE, RepetitionCounter.Feedback.RELEASE}) {
            int res = message(c, f, 1);
            assertNotEquals(f.name(), R.string.feedback_full_body_visible, res);
            assertNotEquals(f.name(), R.string.feedback_stand_straight, res);
        }
    }

    @Test
    public void standingExercises_beforeLegLocked_unchanged() {
        for (ExerciseCriteria c : new ExerciseCriteria[]{
                ExerciseCriteria.KNEE_FLEXION, ExerciseCriteria.CHAIR_SQUAT, ExerciseCriteria.LUNGE}) {
            assertEquals(R.string.feedback_full_body_visible, FeedbackMessages.beforeLegLocked(c, false));
            assertEquals(FeedbackMessages.sideways(c), FeedbackMessages.beforeLegLocked(c, true));
            assertEquals(R.string.feedback_full_body_visible, message(c, RepetitionCounter.Feedback.NOT_VISIBLE, 1));
        }
    }

    @Test
    public void sidewaysAndLegLocked_perExercise() {
        assertEquals(R.string.feedback_squat_stand_sideways, FeedbackMessages.sideways(ExerciseCriteria.CHAIR_SQUAT));
        assertEquals(R.string.feedback_lunge_stand_sideways, FeedbackMessages.sideways(ExerciseCriteria.LUNGE));
        assertEquals(R.string.feedback_stretch_sit_sideways,
                FeedbackMessages.sideways(ExerciseCriteria.SEATED_HAMSTRING_STRETCH));
        assertEquals(R.string.feedback_stand_sideways, FeedbackMessages.sideways(ExerciseCriteria.KNEE_FLEXION));

        assertEquals(R.string.feedback_squat_start, FeedbackMessages.legLocked(ExerciseCriteria.CHAIR_SQUAT));
        assertEquals(R.string.feedback_lunge_switch_feet, FeedbackMessages.legLocked(ExerciseCriteria.LUNGE));
        assertEquals(R.string.feedback_stretch_extend_leg,
                FeedbackMessages.legLocked(ExerciseCriteria.SEATED_HAMSTRING_STRETCH));
        assertEquals(R.string.feedback_use_camera_side_leg, FeedbackMessages.legLocked(ExerciseCriteria.KNEE_FLEXION));
    }
}

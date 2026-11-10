package com.example.airehabpostureapp.pose;

import static org.junit.Assert.assertEquals;

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

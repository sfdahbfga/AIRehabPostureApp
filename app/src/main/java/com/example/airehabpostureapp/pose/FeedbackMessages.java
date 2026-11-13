package com.example.airehabpostureapp.pose;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.example.airehabpostureapp.R;

/**
 * 측정기 피드백(RepetitionCounter.Feedback) → 운동별 안내 문구(문자열 리소스 id) (Android 의존성 없는 순수 Java)
 * M12: ExerciseActivity에 있던 안내 문구 매핑을 동작 그대로 옮김. 운동 기준이 없으면(null) 공통(무릎 굽힘) 문구.
 */
public final class FeedbackMessages {

    private FeedbackMessages() {
    }

    // 운동별 안내 문구. 운동마다 다른 문구가 있으면 그 문구, 없으면 공통(무릎 굽힘) 문구
    @StringRes
    public static int forFeedback(@Nullable ExerciseCriteria exerciseCriteria,
                                  RepetitionCounter.Feedback feedback, int completedReps) {
        int exerciseMessage = exerciseFeedbackMessage(exerciseCriteria, feedback);
        return exerciseMessage != 0 ? exerciseMessage
                : defaultFeedbackMessage(exerciseCriteria, feedback, completedReps);
    }

    // 운동별로 바꿔 쓰는 안내 문구 (없으면 0 → 공통 문구 사용)
    @StringRes
    private static int exerciseFeedbackMessage(@Nullable ExerciseCriteria exerciseCriteria,
                                               RepetitionCounter.Feedback feedback) {
        if (exerciseCriteria == null) {
            return 0;
        }
        switch (exerciseCriteria.id) {
            case ExerciseCriteria.ID_CHAIR_SQUAT:
                return chairSquatFeedback(feedback);
            case ExerciseCriteria.ID_LUNGE:
                return lungeFeedback(feedback);
            case ExerciseCriteria.ID_SEATED_HAMSTRING_STRETCH:
                return hamstringStretchFeedback(feedback);
            default:
                return 0;
        }
    }

    // 의자 스쿼트 안내
    @StringRes
    private static int chairSquatFeedback(RepetitionCounter.Feedback feedback) {
        switch (feedback) {
            case BEND_MORE:
                return R.string.feedback_squat_sit_more;
            case GOOD_STRAIGHTEN:
                return R.string.feedback_squat_good_stand_up;
            case HIPS_BACK:
                return R.string.feedback_squat_hips_back;
            case SHALLOW_RETRY:
                return R.string.feedback_squat_shallow;
            case RISING:
                return R.string.feedback_squat_rising;
            case START_BENDING:
                return R.string.feedback_squat_start;
            default:
                return 0;
        }
    }

    // 런지 안내
    @StringRes
    private static int lungeFeedback(RepetitionCounter.Feedback feedback) {
        switch (feedback) {
            case BEND_MORE:
                return R.string.feedback_lunge_lower_more;
            case GOOD_STRAIGHTEN:
                return R.string.feedback_lunge_good_come_up;
            case SHALLOW_RETRY:
                return R.string.feedback_lunge_shallow;
            case RISING:
                return R.string.feedback_lunge_rising;
            case START_BENDING:
                return R.string.feedback_lunge_start;
            default:
                return 0;
        }
    }

    // 하체 스트레칭 안내 (유지 중 문구에는 유지 시간 표시)
    @StringRes
    private static int hamstringStretchFeedback(RepetitionCounter.Feedback feedback) {
        switch (feedback) {
            case NOT_VISIBLE:
                // 앉아서 하는 운동이라 "서 주세요" 대신
                return R.string.feedback_stretch_full_body_visible;
            case START_BENDING:
                return R.string.feedback_stretch_start;
            case BEND_MORE:
                return R.string.feedback_stretch_lean_more;
            case HOLD:
                return R.string.feedback_stretch_hold;
            case STRAIGHTEN_KNEE:
                return R.string.feedback_stretch_straighten_knee;
            case RELEASE:
                return R.string.feedback_stretch_release;
            default:
                return 0;
        }
    }

    // 화면에만 보여 주고 음성으로 읽지 않는 문구 (일어서는 중 안내: 일어선 뒤의 평가 안내만 읽히도록)
    public static boolean isScreenOnly(@StringRes int messageRes) {
        return messageRes == R.string.feedback_squat_rising
                || messageRes == R.string.feedback_lunge_rising
                || messageRes == R.string.feedback_knee_rising;
    }

    /**
     * 운동하는 다리를 정하기 전 안내: 다리가 보이면 운동별 "옆으로 서 / 앉아 주세요", 안 보이면 "전신이 보이게 서 주세요"
     * 하체 스트레칭은 앉아서 하는 운동이라 다리가 안 보여도 "의자 끝에 카메라 쪽으로 옆으로 앉아 주세요."
     */
    @StringRes
    public static int beforeLegLocked(@Nullable ExerciseCriteria exerciseCriteria, boolean legVisible) {
        if (legVisible || isSeated(exerciseCriteria)) {
            return sideways(exerciseCriteria);
        }
        return R.string.feedback_full_body_visible;
    }

    private static boolean isSeated(@Nullable ExerciseCriteria exerciseCriteria) {
        return exerciseCriteria != null && ExerciseCriteria.ID_SEATED_HAMSTRING_STRETCH.equals(exerciseCriteria.id);
    }

    // 다리를 정하기 전 "옆으로 서 주세요" 안내 (운동별)
    @StringRes
    public static int sideways(@Nullable ExerciseCriteria exerciseCriteria) {
        if (exerciseCriteria != null) {
            switch (exerciseCriteria.id) {
                case ExerciseCriteria.ID_CHAIR_SQUAT:
                    return R.string.feedback_squat_stand_sideways;
                case ExerciseCriteria.ID_LUNGE:
                    return R.string.feedback_lunge_stand_sideways;
                case ExerciseCriteria.ID_SEATED_HAMSTRING_STRETCH:
                    return R.string.feedback_stretch_sit_sideways;
                default:
                    break;
            }
        }
        return R.string.feedback_stand_sideways;
    }

    // 다리가 정해진 직후 / 첫 동작 전 안내 (운동별)
    @StringRes
    public static int legLocked(@Nullable ExerciseCriteria exerciseCriteria) {
        if (exerciseCriteria != null) {
            switch (exerciseCriteria.id) {
                case ExerciseCriteria.ID_CHAIR_SQUAT:
                    // 양쪽 다리로 하는 운동이라 "카메라 쪽 다리" 안내 대신 시작 안내
                    return R.string.feedback_squat_start;
                case ExerciseCriteria.ID_LUNGE:
                    // 다리가 정해진 직후: 다리를 바꿀 때 몸을 돌리지 않도록 안내 (돌리면 고정한 다리가 가려짐)
                    return R.string.feedback_lunge_switch_feet;
                case ExerciseCriteria.ID_SEATED_HAMSTRING_STRETCH:
                    return R.string.feedback_stretch_extend_leg;
                default:
                    break;
            }
        }
        return R.string.feedback_use_camera_side_leg;
    }

    @StringRes
    private static int defaultFeedbackMessage(@Nullable ExerciseCriteria exerciseCriteria,
                                              RepetitionCounter.Feedback feedback, int completedReps) {
        switch (feedback) {
            case NOT_VISIBLE:
                return R.string.feedback_full_body_visible;
            case STAND_STRAIGHT:
                return R.string.feedback_stand_straight;
            case BEND_MORE:
                return R.string.feedback_bend_knee_more;
            case GOOD_STRAIGHTEN:
                return R.string.feedback_good_straighten;
            case KEEP_TRUNK_UPRIGHT:
                return R.string.feedback_keep_trunk_upright;
            case THIGH_UPRIGHT:
                return R.string.feedback_thigh_upright;
            case SHALLOW_RETRY:
                return R.string.feedback_knee_shallow;
            case RISING:
                return R.string.feedback_knee_rising;
            case START_BENDING:
            default:
                // 첫 동작 전에는 어느 다리로 할지 안내
                return completedReps == 0
                        ? legLocked(exerciseCriteria)
                        : R.string.feedback_start_bending;
        }
    }
}

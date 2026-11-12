package com.example.airehabpostureapp.pose;

/**
 * 목표 횟수 도달 안내를 화면에 보여 줄지 정한다. (Android 의존성 없는 순수 Java, 판정 / 횟수와 무관)
 * - 목표를 채우면 최대 SHOW_MS 동안 목표 안내
 * - 그 사이 다음 동작을 시작하거나 자세 경고가 생기면 바로 내림 (경고는 항상 보여야 함)
 * - 유지형(하체 스트레칭)처럼 목표를 채운 순간 "천천히 상체를 세워 주세요"(RELEASE)였으면,
 *   목표 안내가 끝난 뒤 FOLLOW_UP_MS 동안 이어서 보여 줌 (이때도 동작 시작 / 경고가 생기면 바로 내림)
 * 음성은 ExerciseActivity가 "N회" 다음에 바로 읽으므로 여기서는 화면만 다룬다.
 */
public final class GoalHint {

    public static final long SHOW_MS = 6000L;
    public static final long FOLLOW_UP_MS = 3000L;

    /** 이번 프레임에 보여 줄 것 */
    public enum Display {
        // 측정기 안내를 그대로
        NORMAL,
        // 목표 도달 안내
        GOAL,
        // 목표 안내 다음에 이어서 "천천히 상체를 세워 주세요"
        RELEASE_FOLLOW_UP
    }

    private long goalUntilMs = -1;
    private long followUpUntilMs = -1;
    private boolean followUpRelease;

    /** 이번 회차로 목표를 처음 채웠을 때. feedbackAtGoal = 그 순간 측정기 안내 */
    public void onGoalReached(long nowMs, RepetitionCounter.Feedback feedbackAtGoal) {
        goalUntilMs = nowMs + SHOW_MS;
        followUpUntilMs = -1;
        followUpRelease = feedbackAtGoal == RepetitionCounter.Feedback.RELEASE;
    }

    /** 매 프레임: 지금 보여 줄 것 */
    public Display update(long nowMs, boolean repInProgress, RepetitionCounter.Feedback current) {
        boolean interrupted = repInProgress || isPostureWarning(current);
        if (goalUntilMs >= 0) {
            if (interrupted) {
                reset();
                return Display.NORMAL;
            }
            if (nowMs < goalUntilMs) {
                return Display.GOAL;
            }
            // 목표 안내가 다 보여진 뒤: 필요하면 이어서 안내
            followUpUntilMs = followUpRelease ? goalUntilMs + FOLLOW_UP_MS : -1;
            goalUntilMs = -1;
        }
        if (followUpUntilMs >= 0) {
            if (interrupted || nowMs >= followUpUntilMs) {
                reset();
                return Display.NORMAL;
            }
            return Display.RELEASE_FOLLOW_UP;
        }
        return Display.NORMAL;
    }

    public void reset() {
        goalUntilMs = -1;
        followUpUntilMs = -1;
        followUpRelease = false;
    }

    // 목표 안내보다 먼저 보여야 하는 자세 경고
    private static boolean isPostureWarning(RepetitionCounter.Feedback feedback) {
        switch (feedback) {
            case KEEP_TRUNK_UPRIGHT:
            case THIGH_UPRIGHT:
            case HIPS_BACK:
            case STRAIGHTEN_KNEE:
                return true;
            default:
                return false;
        }
    }
}

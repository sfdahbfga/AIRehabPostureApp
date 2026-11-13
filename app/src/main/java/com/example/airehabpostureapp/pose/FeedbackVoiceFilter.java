package com.example.airehabpostureapp.pose;

import androidx.annotation.StringRes;

import com.example.airehabpostureapp.R;

import java.util.HashSet;
import java.util.Set;

/**
 * 화면 안내 문구가 바뀌었을 때 음성으로 읽을지 정한다. (Android 의존성 없는 순수 Java, 판정 / 감점과는 무관)
 * - 화면 전용 문구(일어서는 중 안내)는 읽지 않음
 * - 자세 경고(상체를 곧게 / 허벅지를 세워)는 같은 동작 안에서 경고마다 한 번만 읽음 (화면 문구는 그대로 바뀜)
 * - 경고가 풀려 경고 전 문구(예: "좋아요")로 돌아가면 이미 읽은 문구이므로 다시 읽지 않음
 * 동작이 끝나면(onMotionEnded) 처음부터 다시 센다.
 */
public final class FeedbackVoiceFilter {

    // 이번 동작에서 이미 읽은 자세 경고
    private final Set<Integer> spokenWarnings = new HashSet<>();
    // 이번 동작에서 이미 읽은 "한 번만" 문구
    private final Set<Integer> spokenOnce = new HashSet<>();
    // 마지막으로 보인 경고가 아닌 문구 (경고가 풀리면 이 문구로 돌아왔는지 비교)
    private int lastNonWarningRes;
    private int resBeforeWarning;
    private boolean inWarning;

    /** 같은 동작 안에서 한 번만 읽는 자세 경고 문구 */
    public static boolean isPostureWarning(@StringRes int messageRes) {
        return messageRes == R.string.feedback_keep_trunk_upright
                || messageRes == R.string.feedback_thigh_upright;
    }

    /**
     * 같은 동작 안에서 한 번만 읽는 문구: 하체 스트레칭 "좋아요! 그대로 유지하세요."
     * (유지가 시작될 때 한 번. 잠깐 끊겼다 다시 이어져도 다시 읽지 않음)
     */
    public static boolean isOncePerMotion(@StringRes int messageRes) {
        return messageRes == R.string.feedback_stretch_hold;
    }

    /** 화면 문구가 messageRes로 바뀌었을 때 호출. true면 음성으로 읽음 */
    public boolean shouldSpeak(@StringRes int messageRes) {
        if (isOncePerMotion(messageRes)) {
            inWarning = false;
            lastNonWarningRes = messageRes;
            return spokenOnce.add(messageRes);
        }
        if (isPostureWarning(messageRes)) {
            if (!inWarning) {
                inWarning = true;
                resBeforeWarning = lastNonWarningRes;
            }
            return spokenWarnings.add(messageRes);
        }
        boolean returningAfterWarning = inWarning && messageRes == resBeforeWarning;
        inWarning = false;
        lastNonWarningRes = messageRes;
        // 목표 도달 안내는 "N회" 직후에 바로 읽으므로(VoiceGuide.speakAnnouncement) 화면이 바뀔 때는 읽지 않음
        if (FeedbackMessages.isScreenOnly(messageRes) || messageRes == R.string.feedback_goal_reached) {
            return false;
        }
        return !returningAfterWarning;
    }

    /** 동작 하나가 끝남 (또는 동작 중이 아님): 경고 횟수를 처음부터 다시 셈 */
    public void onMotionEnded() {
        spokenWarnings.clear();
        spokenOnce.clear();
        inWarning = false;
        resBeforeWarning = 0;
    }
}

package com.example.airehabpostureapp.workout;

import com.example.airehabpostureapp.network.ExerciseSessionApi;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 서버에 아직 저장하지 못한 운동 결과 1건 (휴대폰 임시 저장용, Android 의존성 없는 순수 Java / Gson 직렬화)
 *
 * - 운동이 끝나는 순간 만들어 PendingWorkoutStore에 저장하고, 서버 저장에 성공하면 지운다.
 * - 세션 / 체크인 id(UUID)를 여기서 미리 만들어 두므로, 응답을 못 받아 다시 보내도 서버에 중복 저장되지 않는다.
 * - userId: 운동한 사용자. 로그인한 사용자와 같을 때만 저장을 묻고 올린다.
 */
public final class PendingWorkout {

    public String userId;
    public String sessionId;
    public String preCheckinId;
    public String postCheckinId;

    public String exerciseName;
    public long startedAtMillis;
    public long endedAtMillis;
    public int durationSeconds;
    public int completedReps;
    public int targetReps;
    // 측정값이 없으면(0회 / 자세 인식 미지원 운동) null
    public Integer averageAccuracy;
    public Integer firstHalfAccuracy;
    public Integer secondHalfAccuracy;
    // 관절별 평균 오차 (도). 값이 없으면 null
    public Map<String, Integer> jointErrors;

    // 11_PreCheck 입력값
    public int preDiscomfortLevel;
    public String preBodyCondition;
    public String preNote;

    // 15_PostCheck 입력값 (입력 전에는 postChecked = false)
    public boolean postChecked;
    public int postDiscomfortLevel;
    public String postBodyCondition;
    public String postNote;

    // 서버에 exercise_sessions 행이 남아 있을 수 있으면 true (체크인 저장 실패 후 롤백 삭제도 실패한 경우 등)
    // → "삭제"를 고르면 서버의 반쪽 기록도 지운다.
    public boolean sessionMayExistOnServer;

    /** Gson용 */
    public PendingWorkout() {
    }

    /** 새 임시 기록: 세션 / 체크인 id를 새로 만든다 */
    public static PendingWorkout create(String userId) {
        PendingWorkout workout = new PendingWorkout();
        workout.userId = userId;
        workout.sessionId = UUID.randomUUID().toString();
        workout.preCheckinId = UUID.randomUUID().toString();
        workout.postCheckinId = UUID.randomUUID().toString();
        return workout;
    }

    public void setPostCheck(int discomfortLevel, String bodyCondition, String note) {
        postChecked = true;
        postDiscomfortLevel = discomfortLevel;
        postBodyCondition = bodyCondition;
        postNote = emptyToNull(note);
    }

    public ExerciseSessionApi.SessionInsert toSessionInsert() {
        return new ExerciseSessionApi.SessionInsert(sessionId, userId, exerciseName,
                Instant.ofEpochMilli(startedAtMillis).toString(), Instant.ofEpochMilli(endedAtMillis).toString(),
                durationSeconds, completedReps, targetReps,
                averageAccuracy, firstHalfAccuracy, secondHalfAccuracy,
                jointErrors == null || jointErrors.isEmpty() ? null : jointErrors);
    }

    public List<ExerciseSessionApi.CheckinInsert> toCheckinInserts() {
        return Arrays.asList(
                new ExerciseSessionApi.CheckinInsert(preCheckinId, sessionId, CheckType.PRE,
                        preDiscomfortLevel, preBodyCondition, emptyToNull(preNote)),
                new ExerciseSessionApi.CheckinInsert(postCheckinId, sessionId, CheckType.POST,
                        postDiscomfortLevel, postBodyCondition, emptyToNull(postNote)));
    }

    private static String emptyToNull(String text) {
        return text == null || text.trim().isEmpty() ? null : text.trim();
    }

    /** session_checkins.check_type 값 */
    public static final class CheckType {
        public static final String PRE = "pre";
        public static final String POST = "post";

        private CheckType() {
        }
    }
}

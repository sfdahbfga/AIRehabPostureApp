package com.example.airehabpostureapp.network;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Query;

/**
 * exercise_sessions / session_checkins 테이블 REST API.
 * RLS 때문에 로그인한 사용자의 Bearer 토큰이 있어야 자기 기록을 읽고 쓸 수 있다.
 */
public interface ExerciseSessionApi {

    // 추가된 행의 id를 돌려받아 session_checkins 저장에 사용한다.
    @Headers("Prefer: return=representation")
    @POST("rest/v1/exercise_sessions?select=id")
    Call<List<BodyConditionApi.IdRow>> insertSession(@Header("Authorization") String bearerToken,
                                                     @Body SessionInsert body);

    // pre / post 두 행을 한 요청으로 보내므로 둘 다 저장되거나 둘 다 실패한다.
    @Headers("Prefer: return=minimal")
    @POST("rest/v1/session_checkins")
    Call<Void> insertCheckins(@Header("Authorization") String bearerToken, @Body List<CheckinInsert> body);

    // idFilter: "eq.{세션 id}". 행이 없으면 빈 목록
    @GET("rest/v1/exercise_sessions?select=duration_seconds,completed_repetitions,target_repetitions,average_accuracy")
    Call<List<SessionRow>> getSession(@Header("Authorization") String bearerToken,
                                      @Query("id") String idFilter);

    class SessionInsert {
        @SerializedName("user_id")
        final String userId;
        @SerializedName("exercise_name")
        final String exerciseName;
        // ISO 8601 형식 (예: 2026-11-01T03:00:00Z)
        @SerializedName("started_at")
        final String startedAt;
        @SerializedName("ended_at")
        final String endedAt;
        @SerializedName("duration_seconds")
        final int durationSeconds;
        @SerializedName("completed_repetitions")
        final int completedRepetitions;
        @SerializedName("target_repetitions")
        final int targetRepetitions;
        @SerializedName("average_accuracy")
        final int averageAccuracy;
        @SerializedName("first_half_accuracy")
        final int firstHalfAccuracy;
        @SerializedName("second_half_accuracy")
        final int secondHalfAccuracy;
        // 관절별 오류 횟수 (예: {"knee": 12, "back": 3})
        @SerializedName("joint_errors")
        final Map<String, Integer> jointErrors;

        public SessionInsert(String userId, String exerciseName, String startedAt, String endedAt,
                             int durationSeconds, int completedRepetitions, int targetRepetitions,
                             int averageAccuracy, int firstHalfAccuracy, int secondHalfAccuracy,
                             Map<String, Integer> jointErrors) {
            this.userId = userId;
            this.exerciseName = exerciseName;
            this.startedAt = startedAt;
            this.endedAt = endedAt;
            this.durationSeconds = durationSeconds;
            this.completedRepetitions = completedRepetitions;
            this.targetRepetitions = targetRepetitions;
            this.averageAccuracy = averageAccuracy;
            this.firstHalfAccuracy = firstHalfAccuracy;
            this.secondHalfAccuracy = secondHalfAccuracy;
            this.jointErrors = jointErrors;
        }
    }

    class CheckinInsert {
        @SerializedName("session_id")
        final String sessionId;
        // pre, post
        @SerializedName("check_type")
        final String checkType;
        // 0~10
        @SerializedName("discomfort_level")
        final int discomfortLevel;
        // good, normal, bad
        @SerializedName("body_condition")
        final String bodyCondition;
        // 입력하지 않으면 null (Gson은 null 필드를 보내지 않음)
        @SerializedName("note")
        final String note;

        public CheckinInsert(String sessionId, String checkType, int discomfortLevel,
                             String bodyCondition, String note) {
            this.sessionId = sessionId;
            this.checkType = checkType;
            this.discomfortLevel = discomfortLevel;
            this.bodyCondition = bodyCondition;
            this.note = note;
        }
    }

    class SessionRow {
        @SerializedName("duration_seconds")
        public int durationSeconds;
        @SerializedName("completed_repetitions")
        public Integer completedRepetitions;
        @SerializedName("target_repetitions")
        public Integer targetRepetitions;
        // DB numeric → 소수일 수 있음
        @SerializedName("average_accuracy")
        public Double averageAccuracy;
    }
}

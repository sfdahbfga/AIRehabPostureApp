package com.example.airehabpostureapp.network;

import com.example.airehabpostureapp.stats.SessionRecord;
import com.google.gson.annotations.SerializedName;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
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

    // 기록 목록 / 상세 / 주간 리포트에서 쓰는 열
    String RECORD_COLUMNS = "id,exercise_name,started_at,duration_seconds,completed_repetitions,target_repetitions,"
            + "average_accuracy,first_half_accuracy,second_half_accuracy,joint_errors";

    // 내 기록 전체를 최신순으로 (RLS로 본인 기록만 오지만 userIdFilter로도 명시). userIdFilter: "eq.{사용자 id}"
    @GET("rest/v1/exercise_sessions?select=" + RECORD_COLUMNS + "&order=started_at.desc")
    Call<List<RecordRow>> getRecords(@Header("Authorization") String bearerToken,
                                     @Query("user_id") String userIdFilter);

    // 기간 안의 내 기록. startedAtFilters: ["gte.{시작 시각}", "lt.{끝 시각}"] (같은 이름의 조건을 두 번 보냄)
    @GET("rest/v1/exercise_sessions?select=" + RECORD_COLUMNS + "&order=started_at.desc")
    Call<List<RecordRow>> getRecordsBetween(@Header("Authorization") String bearerToken,
                                            @Query("user_id") String userIdFilter,
                                            @Query("started_at") List<String> startedAtFilters);

    // 기록 1개. idFilter: "eq.{세션 id}". 행이 없으면 빈 목록
    @GET("rest/v1/exercise_sessions?select=" + RECORD_COLUMNS)
    Call<List<RecordRow>> getRecord(@Header("Authorization") String bearerToken,
                                    @Query("id") String idFilter);

    // AI 리포트 화면의 분석 결과(관절별 평균 오차 / 전반·후반 정확도). idFilter: "eq.{세션 id}"
    @GET("rest/v1/exercise_sessions?select=first_half_accuracy,second_half_accuracy,joint_errors")
    Call<List<ReportMetricsRow>> getReportMetrics(@Header("Authorization") String bearerToken,
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
        // 정확도 / 관절 오차는 측정값이 없으면(0회 / 자세 인식 미지원 운동) null → DB에도 null로 저장
        @SerializedName("average_accuracy")
        final Integer averageAccuracy;
        @SerializedName("first_half_accuracy")
        final Integer firstHalfAccuracy;
        @SerializedName("second_half_accuracy")
        final Integer secondHalfAccuracy;
        // 관절별 평균 각도 오차 (도). 예: {"knee": 12, "back": 3}
        // knee: 가장 많이 굽힌 각도 - 목표 각도 (+ 덜 굽힘 / - 더 굽힘), back: 상체 숙임 허용 각도 초과량
        @SerializedName("joint_errors")
        final Map<String, Integer> jointErrors;

        public SessionInsert(String userId, String exerciseName, String startedAt, String endedAt,
                             int durationSeconds, int completedRepetitions, int targetRepetitions,
                             Integer averageAccuracy, Integer firstHalfAccuracy, Integer secondHalfAccuracy,
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

    class RecordRow {
        @SerializedName("id")
        public String id;
        @SerializedName("exercise_name")
        public String exerciseName;
        // 예: 2026-11-01T14:11:13.756+00:00
        @SerializedName("started_at")
        public String startedAt;
        @SerializedName("duration_seconds")
        public Integer durationSeconds;
        @SerializedName("completed_repetitions")
        public Integer completedRepetitions;
        @SerializedName("target_repetitions")
        public Integer targetRepetitions;
        // DB numeric → 소수일 수 있음
        @SerializedName("average_accuracy")
        public Double averageAccuracy;
        @SerializedName("first_half_accuracy")
        public Double firstHalfAccuracy;
        @SerializedName("second_half_accuracy")
        public Double secondHalfAccuracy;
        @SerializedName("joint_errors")
        public Map<String, Double> jointErrors;

        // 시각을 읽지 못하면 null
        public Instant startedAtInstant() {
            if (startedAt == null) {
                return null;
            }
            try {
                return OffsetDateTime.parse(startedAt).toInstant();
            } catch (DateTimeParseException e) {
                return null;
            }
        }

        // ReportStatsCalculator 계산용
        public SessionRecord toSessionRecord() {
            return new SessionRecord(exerciseName, startedAtInstant(), durationSeconds,
                    completedRepetitions, targetRepetitions,
                    averageAccuracy, firstHalfAccuracy, secondHalfAccuracy, jointErrors);
        }
    }

    class ReportMetricsRow {
        // DB numeric → 소수일 수 있음
        @SerializedName("first_half_accuracy")
        public Double firstHalfAccuracy;
        @SerializedName("second_half_accuracy")
        public Double secondHalfAccuracy;
        // 관절별 평균 오차 (키: knee, back)
        @SerializedName("joint_errors")
        public Map<String, Double> jointErrors;
    }
}

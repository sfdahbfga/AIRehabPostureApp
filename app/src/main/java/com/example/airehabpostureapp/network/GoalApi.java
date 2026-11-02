package com.example.airehabpostureapp.network;

import com.google.gson.annotations.SerializedName;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Query;

/**
 * user_goals 테이블 REST API.
 * userIdFilter: "eq.{사용자 id}"
 */
public interface GoalApi {

    // 1개 이상 있는지만 확인하므로 한 줄만 받는다.
    @GET("rest/v1/user_goals?select=id&limit=1")
    Call<List<BodyConditionApi.IdRow>> getAny(@Header("Authorization") String bearerToken,
                                              @Query("user_id") String userIdFilter);

    @Headers("Prefer: return=minimal")
    @DELETE("rest/v1/user_goals")
    Call<Void> deleteAll(@Header("Authorization") String bearerToken,
                         @Query("user_id") String userIdFilter);

    // 목록을 보내면 여러 행이 한 번에 추가된다.
    @Headers("Prefer: return=minimal")
    @POST("rest/v1/user_goals")
    Call<Void> insert(@Header("Authorization") String bearerToken, @Body List<GoalInsert> body);

    class GoalInsert {
        @SerializedName("user_id")
        final String userId;
        // pain_relief, range_of_motion, strength, flexibility, balance, consistency
        @SerializedName("goal_code")
        final String goalCode;

        public GoalInsert(String userId, String goalCode) {
            this.userId = userId;
            this.goalCode = goalCode;
        }
    }
}

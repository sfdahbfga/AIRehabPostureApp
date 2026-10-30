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
 * user_body_conditions 테이블 REST API.
 * userIdFilter: "eq.{사용자 id}"
 */
public interface BodyConditionApi {

    // 1개 이상 있는지만 확인하므로 한 줄만 받는다.
    @GET("rest/v1/user_body_conditions?select=id&limit=1")
    Call<List<IdRow>> getAny(@Header("Authorization") String bearerToken,
                             @Query("user_id") String userIdFilter);

    @Headers("Prefer: return=minimal")
    @DELETE("rest/v1/user_body_conditions")
    Call<Void> deleteAll(@Header("Authorization") String bearerToken,
                         @Query("user_id") String userIdFilter);

    // 목록을 보내면 여러 행이 한 번에 추가된다.
    @Headers("Prefer: return=minimal")
    @POST("rest/v1/user_body_conditions")
    Call<Void> insert(@Header("Authorization") String bearerToken, @Body List<BodyConditionInsert> body);

    class BodyConditionInsert {
        @SerializedName("user_id")
        final String userId;
        // neck, shoulder, elbow, waist, knee, ankle
        @SerializedName("body_part")
        final String bodyPart;
        // 0~10
        @SerializedName("discomfort_level")
        final int discomfortLevel;

        public BodyConditionInsert(String userId, String bodyPart, int discomfortLevel) {
            this.userId = userId;
            this.bodyPart = bodyPart;
            this.discomfortLevel = discomfortLevel;
        }
    }

    class IdRow {
        @SerializedName("id")
        public String id;
    }
}

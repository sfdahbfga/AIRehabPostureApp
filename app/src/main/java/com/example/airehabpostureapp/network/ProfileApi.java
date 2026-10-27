package com.example.airehabpostureapp.network;

import com.google.gson.annotations.SerializedName;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.POST;

/**
 * profiles 테이블 REST API.
 * RLS 때문에 로그인한 사용자의 Bearer 토큰이 있어야 자기 행을 쓸 수 있다.
 */
public interface ProfileApi {

    @Headers("Prefer: return=minimal")
    @POST("rest/v1/profiles")
    Call<Void> insertProfile(@Header("Authorization") String bearerToken, @Body ProfileInsert body);

    class ProfileInsert {
        @SerializedName("id")
        final String id;
        @SerializedName("display_name")
        final String displayName;
        // DB date 형식 (YYYY-MM-DD)
        @SerializedName("birth_date")
        final String birthDate;

        public ProfileInsert(String id, String displayName, String birthDate) {
            this.id = id;
            this.displayName = displayName;
            this.birthDate = birthDate;
        }
    }
}

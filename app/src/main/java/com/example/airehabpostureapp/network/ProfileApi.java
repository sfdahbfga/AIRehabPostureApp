package com.example.airehabpostureapp.network;

import com.google.gson.annotations.SerializedName;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Query;

/**
 * profiles 테이블 REST API.
 * RLS 때문에 로그인한 사용자의 Bearer 토큰이 있어야 자기 행을 쓸 수 있다.
 */
public interface ProfileApi {

    @Headers("Prefer: return=minimal")
    @POST("rest/v1/profiles")
    Call<Void> insertProfile(@Header("Authorization") String bearerToken, @Body ProfileInsert body);

    // idFilter: "eq.{사용자 id}". 행이 없으면 빈 목록
    @GET("rest/v1/profiles?select=exercise_experience")
    Call<List<ProfileRow>> getProfile(@Header("Authorization") String bearerToken,
                                      @Query("id") String idFilter);

    // 마이페이지 프로필 이름. idFilter: "eq.{사용자 id}". 행이 없으면 빈 목록
    @GET("rest/v1/profiles?select=name")
    Call<List<ProfileNameRow>> getProfileName(@Header("Authorization") String bearerToken,
                                              @Query("id") String idFilter);

    // 대상 행이 없어도 204로 성공하므로, 바뀐 행을 돌려받아 비어 있으면 실패로 처리한다.
    @Headers("Prefer: return=representation")
    @PATCH("rest/v1/profiles")
    Call<List<ProfileRow>> updateProfile(@Header("Authorization") String bearerToken,
                                         @Query("id") String idFilter, @Body ProfileUpdate body);

    class ProfileInsert {
        @SerializedName("id")
        final String id;
        // profiles.name (NOT NULL)
        @SerializedName("name")
        final String name;
        // DB date 형식 (YYYY-MM-DD)
        @SerializedName("birth_date")
        final String birthDate;

        public ProfileInsert(String id, String name, String birthDate) {
            this.id = id;
            this.name = name;
            this.birthDate = birthDate;
        }
    }

    class ProfileUpdate {
        @SerializedName("height_cm")
        final double heightCm;
        @SerializedName("weight_kg")
        final double weightKg;
        // none, beginner, intermediate, advanced
        @SerializedName("exercise_experience")
        final String exerciseExperience;

        public ProfileUpdate(double heightCm, double weightKg, String exerciseExperience) {
            this.heightCm = heightCm;
            this.weightKg = weightKg;
            this.exerciseExperience = exerciseExperience;
        }
    }

    class ProfileRow {
        @SerializedName("exercise_experience")
        public String exerciseExperience;
    }

    class ProfileNameRow {
        @SerializedName("name")
        public String name;
    }
}

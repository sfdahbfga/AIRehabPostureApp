package com.example.airehabpostureapp.network;

import com.google.gson.annotations.SerializedName;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;

/**
 * Supabase Auth(GoTrue) REST API.
 * apikey 헤더는 SupabaseClient가 모든 요청에 자동으로 붙인다.
 */
public interface AuthApi {

    @POST("auth/v1/signup")
    Call<AuthResponse> signUp(@Body Credentials body);

    @POST("auth/v1/token?grant_type=password")
    Call<AuthResponse> signIn(@Body Credentials body);

    @POST("auth/v1/token?grant_type=refresh_token")
    Call<AuthResponse> refreshToken(@Body RefreshTokenRequest body);

    // bearerToken: "Bearer {access token}" (SessionManager.getBearerToken())
    @POST("auth/v1/logout")
    Call<Void> logout(@Header("Authorization") String bearerToken);

    class Credentials {
        @SerializedName("email")
        final String email;
        @SerializedName("password")
        final String password;

        public Credentials(String email, String password) {
            this.email = email;
            this.password = password;
        }
    }

    class RefreshTokenRequest {
        @SerializedName("refresh_token")
        final String refreshToken;

        public RefreshTokenRequest(String refreshToken) {
            this.refreshToken = refreshToken;
        }
    }

    // 회원가입 시 이메일 확인이 켜져 있으면 세션 없이 응답하므로 accessToken/user가 null일 수 있다.
    class AuthResponse {
        @SerializedName("access_token")
        public String accessToken;
        @SerializedName("refresh_token")
        public String refreshToken;
        @SerializedName("user")
        public User user;
    }

    class User {
        @SerializedName("id")
        public String id;
    }
}

package com.example.airehabpostureapp.network;

import com.google.gson.annotations.SerializedName;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;

/**
 * Supabase Edge Function ai-summary 호출.
 * 함수가 운동 기록을 읽어 Gemini로 요약/팁을 만들어 돌려준다. (Gemini 키는 함수 안에만 있고 앱에는 없다)
 * 같은 운동 기록으로 다시 부르면 저장된 리포트를 cached: true로 돌려준다.
 */
public interface AiSummaryApi {

    // Gemini가 바쁘면 함수 안에서 최대 3번 재시도해서 10초 넘게 걸릴 수 있으므로 이 요청만 읽기 대기 시간을 늘린다.
    long READ_TIMEOUT_SECONDS = 60;

    @POST("functions/v1/ai-summary")
    Call<AiSummaryResponse> getSummary(@Header("Authorization") String bearerToken,
                                       @Body AiSummaryRequest body);

    // 공용 OkHttpClient(apikey 헤더, 로그 포함)를 복사해 읽기 대기 시간만 바꾼다. 다른 요청의 대기 시간은 그대로
    static AiSummaryApi create() {
        Retrofit baseRetrofit = SupabaseClient.getRetrofit();
        OkHttpClient longReadClient = ((OkHttpClient) baseRetrofit.callFactory()).newBuilder()
                .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();
        return baseRetrofit.newBuilder()
                .client(longReadClient)
                .build()
                .create(AiSummaryApi.class);
    }

    class AiSummaryRequest {
        @SerializedName("session_id")
        final String sessionId;

        public AiSummaryRequest(String sessionId) {
            this.sessionId = sessionId;
        }
    }

    class AiSummaryResponse {
        // 오늘의 분석
        @SerializedName("summary")
        public String summary;
        // 다음 운동 팁
        @SerializedName("tip")
        public String tip;
        // 저장된 리포트를 돌려준 경우 true
        @SerializedName("cached")
        public boolean cached;
    }
}

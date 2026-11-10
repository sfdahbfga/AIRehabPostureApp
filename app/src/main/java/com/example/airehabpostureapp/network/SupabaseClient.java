package com.example.airehabpostureapp.network;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.example.airehabpostureapp.BuildConfig;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Supabase REST API 호출용 Retrofit 인스턴스를 만든다.
 * URL과 publishable key는 local.properties → BuildConfig에서 읽는다.
 */
public final class SupabaseClient {

    private static Retrofit retrofit;
    // 로그인 만료(401) 처리에 필요한 앱 Context. AiRehabApplication에서 설정
    private static Context appContext;
    // 세션이 만료 처리됐을 때 알릴 곳 (AiRehabApplication에서 SessionExpiryWatcher 연결). 메인 스레드에서 호출
    private static Runnable onSessionExpired;
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private SupabaseClient() {
    }

    public static void init(Context context) {
        appContext = context.getApplicationContext();
    }

    public static void setOnSessionExpired(Runnable listener) {
        onSessionExpired = listener;
    }

    public static synchronized Retrofit getRetrofit() {
        if (retrofit == null) {
            OkHttpClient baseClient = createOkHttpClient();
            OkHttpClient.Builder clientBuilder = baseClient.newBuilder();
            if (appContext != null) {
                // 토큰 갱신 요청은 Authenticator가 없는 baseClient로 보낸다.
                AuthApi refreshApi = createRetrofit(baseClient).create(AuthApi.class);
                clientBuilder.authenticator(new TokenAuthenticator(new SessionManager(appContext), refreshApi,
                        () -> MAIN_HANDLER.post(() -> {
                            if (onSessionExpired != null) {
                                onSessionExpired.run();
                            }
                        })));
            }
            retrofit = createRetrofit(clientBuilder.build());
        }
        return retrofit;
    }

    private static Retrofit createRetrofit(OkHttpClient client) {
        return new Retrofit.Builder()
                .baseUrl(getBaseUrl())
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }

    // 설정값이 없을 때 쓰는 주소: 앱이 꺼지지 않고 요청만 네트워크 오류로 실패한다. (스플래시에서 먼저 안내하고 멈춤)
    static final String UNCONFIGURED_BASE_URL = "https://supabase-url-not-configured.invalid/";

    /** local.properties의 SUPABASE_URL / SUPABASE_PUBLISHABLE_KEY가 모두 있으면 true (M2) */
    public static boolean isConfigured() {
        return isConfigured(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY);
    }

    static boolean isConfigured(String url, String publishableKey) {
        return url != null && !url.trim().isEmpty() && publishableKey != null && !publishableKey.trim().isEmpty();
    }

    private static String getBaseUrl() {
        return baseUrl(BuildConfig.SUPABASE_URL);
    }

    // Retrofit의 baseUrl은 '/'로 끝나야 한다. 비어 있으면 예외 대신 UNCONFIGURED_BASE_URL
    static String baseUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return UNCONFIGURED_BASE_URL;
        }
        String trimmed = url.trim();
        return trimmed.endsWith("/") ? trimmed : trimmed + "/";
    }

    private static OkHttpClient createOkHttpClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    Request request = chain.request().newBuilder()
                            .header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
                            .build();
                    return chain.proceed(request);
                });

        // 요청 로그는 디버그 빌드에서만 출력한다.
        if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);
            logging.redactHeader("apikey");
            builder.addInterceptor(logging);
        }

        return builder.build();
    }
}

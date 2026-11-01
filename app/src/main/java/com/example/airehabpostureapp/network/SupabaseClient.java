package com.example.airehabpostureapp.network;

import android.content.Context;

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

    private SupabaseClient() {
    }

    public static void init(Context context) {
        appContext = context.getApplicationContext();
    }

    public static synchronized Retrofit getRetrofit() {
        if (retrofit == null) {
            OkHttpClient baseClient = createOkHttpClient();
            OkHttpClient.Builder clientBuilder = baseClient.newBuilder();
            if (appContext != null) {
                // 토큰 갱신 요청은 Authenticator가 없는 baseClient로 보낸다.
                AuthApi refreshApi = createRetrofit(baseClient).create(AuthApi.class);
                clientBuilder.authenticator(new TokenAuthenticator(appContext, refreshApi));
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

    private static String getBaseUrl() {
        String url = BuildConfig.SUPABASE_URL;
        if (url.isEmpty()) {
            throw new IllegalStateException(
                    "SUPABASE_URL이 비어 있습니다. local.properties에 SUPABASE_URL을 설정하세요.");
        }
        // Retrofit의 baseUrl은 '/'로 끝나야 한다.
        return url.endsWith("/") ? url : url + "/";
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

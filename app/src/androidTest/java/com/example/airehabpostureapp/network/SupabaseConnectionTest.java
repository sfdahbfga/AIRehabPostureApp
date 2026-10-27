package com.example.airehabpostureapp.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.airehabpostureapp.BuildConfig;

import org.junit.Test;
import org.junit.runner.RunWith;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.http.GET;

/**
 * Supabase 연결 확인용 테스트.
 * 기기/에뮬레이터가 인터넷에 연결되어 있고 local.properties에 값이 있어야 통과한다.
 */
@RunWith(AndroidJUnit4.class)
public class SupabaseConnectionTest {

    interface AuthSettingsApi {
        @GET("auth/v1/settings")
        Call<ResponseBody> getSettings();
    }

    @Test
    public void authSettings_returns200() throws Exception {
        assertFalse("local.properties에 SUPABASE_URL이 없습니다.",
                BuildConfig.SUPABASE_URL.isEmpty());
        assertFalse("local.properties에 SUPABASE_PUBLISHABLE_KEY가 없습니다.",
                BuildConfig.SUPABASE_PUBLISHABLE_KEY.isEmpty());

        AuthSettingsApi api = SupabaseClient.getRetrofit().create(AuthSettingsApi.class);
        Response<ResponseBody> response = api.getSettings().execute();

        assertEquals(200, response.code());
    }
}

package com.example.airehabpostureapp.network;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 로그인 세션(access token, refresh token, 사용자 id, 자동 로그인 여부)을 SharedPreferences에 저장한다.
 */
public class SessionManager {

    private static final String PREFS_NAME = "auth_session";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_AUTO_LOGIN = "auto_login";

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void saveSession(String accessToken, String refreshToken, String userId) {
        prefs.edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .putString(KEY_USER_ID, userId)
                .apply();
    }

    public void setAutoLogin(boolean autoLogin) {
        prefs.edit().putBoolean(KEY_AUTO_LOGIN, autoLogin).apply();
    }

    public boolean isAutoLogin() {
        return prefs.getBoolean(KEY_AUTO_LOGIN, false);
    }

    public String getAccessToken() {
        return prefs.getString(KEY_ACCESS_TOKEN, null);
    }

    public String getRefreshToken() {
        return prefs.getString(KEY_REFRESH_TOKEN, null);
    }

    public String getUserId() {
        return prefs.getString(KEY_USER_ID, null);
    }

    // Authorization 헤더 값. 로그인 상태가 아니면 null
    public String getBearerToken() {
        String accessToken = getAccessToken();
        return accessToken == null ? null : "Bearer " + accessToken;
    }

    public void clear() {
        prefs.edit().clear().apply();
    }
}

package com.example.airehabpostureapp.network;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

/**
 * 로그인 세션(access token, refresh token, 사용자 id, 자동 로그인 여부)을 SharedPreferences에 저장한다.
 * 로그인 만료 표시는 세션을 지워도 남도록 별도 파일(auth_events)에 저장한다. (M3)
 */
public class SessionManager implements TokenStore {

    public static final String PREFS_NAME = "auth_session";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_AUTO_LOGIN = "auto_login";

    // 로그인 만료 표시 (TokenAuthenticator가 남기고, 화면이 보일 때 SessionExpiryWatcher가 확인)
    public static final String EVENTS_PREFS_NAME = "auth_events";
    private static final String KEY_SESSION_EXPIRED = "session_expired";

    private final SharedPreferences prefs;
    private final SharedPreferences eventPrefs;

    public SessionManager(Context context) {
        Context appContext = context.getApplicationContext();
        prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        eventPrefs = appContext.getSharedPreferences(EVENTS_PREFS_NAME, Context.MODE_PRIVATE);
    }

    // L6: commit()으로 바로 디스크에 저장. refresh token은 갱신할 때마다 바뀌므로(이전 토큰은 재사용 불가)
    // apply()로 미뤘다가 직후 앱이 종료되면 이전 토큰이 남아 다음 갱신이 거부(로그아웃)될 수 있다.
    // TokenAuthenticator는 OkHttp 스레드에서 부르므로 메인 스레드를 막지 않는다.
    @SuppressLint("ApplySharedPref")
    @Override
    public void saveSession(String accessToken, String refreshToken, String userId) {
        prefs.edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .putString(KEY_USER_ID, userId)
                .commit();
        // 새로 로그인 / 갱신했으므로 만료 표시 해제
        clearSessionExpiredNotice();
    }

    @SuppressLint("ApplySharedPref")
    @Override
    public void expireSession() {
        clear();
        eventPrefs.edit().putBoolean(KEY_SESSION_EXPIRED, true).commit();
    }

    /** 로그인 만료 안내를 아직 보여주지 않았으면 true */
    public boolean hasSessionExpiredNotice() {
        return eventPrefs.getBoolean(KEY_SESSION_EXPIRED, false);
    }

    public void clearSessionExpiredNotice() {
        eventPrefs.edit().remove(KEY_SESSION_EXPIRED).apply();
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

    @Override
    public String getRefreshToken() {
        return prefs.getString(KEY_REFRESH_TOKEN, null);
    }

    public String getUserId() {
        return prefs.getString(KEY_USER_ID, null);
    }

    // Authorization 헤더 값. 로그인 상태가 아니면 null
    @Override
    public String getBearerToken() {
        String accessToken = getAccessToken();
        return accessToken == null ? null : "Bearer " + accessToken;
    }

    public void clear() {
        prefs.edit().clear().apply();
    }
}

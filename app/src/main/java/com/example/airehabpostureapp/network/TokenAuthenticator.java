package com.example.airehabpostureapp.network;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.example.airehabpostureapp.LoginActivity;
import com.example.airehabpostureapp.R;

import java.io.IOException;

import okhttp3.Authenticator;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;

/**
 * Supabase 요청이 401(로그인 만료)을 받으면 refresh token으로 한 번 갱신하고 같은 요청을 다시 보낸다.
 * 서버가 refresh token을 거부하면 세션을 지우고 로그인 화면으로 보낸다.
 * 네트워크 문제로 갱신하지 못하면 세션은 그대로 두고 원래 401 응답을 돌려준다.
 */
class TokenAuthenticator implements Authenticator {

    private final Context appContext;
    private final SessionManager sessionManager;
    // 토큰 갱신 전용: 이 Authenticator가 없는 클라이언트로 만든 AuthApi (갱신 요청이 다시 갱신을 부르지 않도록)
    private final AuthApi refreshApi;

    TokenAuthenticator(Context appContext, AuthApi refreshApi) {
        this.appContext = appContext;
        this.sessionManager = new SessionManager(appContext);
        this.refreshApi = refreshApi;
    }

    @Override
    public Request authenticate(Route route, Response response) throws IOException {
        String failedBearerToken = response.request().header("Authorization");
        // 토큰 없이 보낸 요청(로그인, 회원가입 등)은 처리하지 않음
        if (failedBearerToken == null) {
            return null;
        }
        // 갱신한 토큰으로 다시 보낸 요청도 401이면 더 시도하지 않음
        if (response.priorResponse() != null) {
            return null;
        }

        // 여러 요청이 동시에 401을 받아도 갱신은 한 번만 하도록 순서대로 처리
        synchronized (TokenAuthenticator.class) {
            String currentBearerToken = sessionManager.getBearerToken();
            // 그 사이 로그아웃된 경우
            if (currentBearerToken == null) {
                return null;
            }
            // 다른 요청이 이미 갱신한 경우: 새 토큰으로 다시 보냄
            if (!currentBearerToken.equals(failedBearerToken)) {
                return withBearerToken(response.request(), currentBearerToken);
            }

            String refreshToken = sessionManager.getRefreshToken();
            if (refreshToken == null) {
                expireSession();
                return null;
            }

            retrofit2.Response<AuthApi.AuthResponse> refreshResponse;
            try {
                refreshResponse = refreshApi.refreshToken(new AuthApi.RefreshTokenRequest(refreshToken)).execute();
            } catch (IOException e) {
                // 네트워크 문제: 세션은 지우지 않고 원래 401 응답을 그대로 돌려줌
                return null;
            }

            AuthApi.AuthResponse body = refreshResponse.body();
            if (!refreshResponse.isSuccessful() || body == null
                    || body.accessToken == null || body.user == null) {
                // 4xx = 서버가 refresh token을 거부 (만료, 이미 사용됨 등) → 로그아웃. 5xx는 세션 유지
                if (refreshResponse.code() >= 400 && refreshResponse.code() < 500) {
                    expireSession();
                }
                return null;
            }

            sessionManager.saveSession(body.accessToken, body.refreshToken, body.user.id);
            return withBearerToken(response.request(), sessionManager.getBearerToken());
        }
    }

    private static Request withBearerToken(Request request, String bearerToken) {
        return request.newBuilder().header("Authorization", bearerToken).build();
    }

    // 세션 삭제 → 안내 → 기존 화면을 모두 닫고 로그인 화면으로 (OkHttp 스레드에서 불리므로 메인 스레드로 넘김)
    private void expireSession() {
        sessionManager.clear();
        new Handler(Looper.getMainLooper()).post(() -> {
            Toast.makeText(appContext, R.string.error_session_expired, Toast.LENGTH_LONG).show();
            Intent intent = new Intent(appContext, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            appContext.startActivity(intent);
        });
    }
}

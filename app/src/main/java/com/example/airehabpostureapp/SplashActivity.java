package com.example.airehabpostureapp;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.airehabpostureapp.network.AuthApi;
import com.example.airehabpostureapp.network.SessionManager;
import com.example.airehabpostureapp.network.SupabaseClient;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SplashActivity extends AppCompatActivity {

    private static final long SPLASH_DURATION_MS = 1500L;
    private static final long DOT_INTERVAL_MS = 300L;
    private static final float DOT_ALPHA_ACTIVE = 1.0f;
    private static final float DOT_ALPHA_INACTIVE = 0.3f;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private View[] dots;
    private int activeDotIndex = 0;

    private final Runnable dotAnimation = new Runnable() {
        @Override
        public void run() {
            activeDotIndex = (activeDotIndex + 1) % dots.length;
            for (int i = 0; i < dots.length; i++) {
                dots[i].setAlpha(i == activeDotIndex ? DOT_ALPHA_ACTIVE : DOT_ALPHA_INACTIVE);
            }
            handler.postDelayed(this, DOT_INTERVAL_MS);
        }
    };

    private SessionManager sessionManager;
    // onResume이 다시 불려도 토큰 갱신 요청이 중복되지 않도록 표시
    private boolean isCheckingSession = false;

    // 자동 로그인이 켜져 있고 refresh token이 있으면 토큰 갱신 후 Home, 아니면 Login
    private final Runnable goToNextScreen = () -> {
        if (isCheckingSession) {
            return;
        }
        isCheckingSession = true;
        if (sessionManager.isAutoLogin() && sessionManager.getRefreshToken() != null) {
            refreshSession();
        } else {
            moveToLogin();
        }
    };

    private void refreshSession() {
        AuthApi authApi = SupabaseClient.getRetrofit().create(AuthApi.class);
        authApi.refreshToken(new AuthApi.RefreshTokenRequest(sessionManager.getRefreshToken()))
                .enqueue(new Callback<AuthApi.AuthResponse>() {
                    @Override
                    public void onResponse(Call<AuthApi.AuthResponse> call,
                                           Response<AuthApi.AuthResponse> response) {
                        AuthApi.AuthResponse body = response.body();
                        if (!response.isSuccessful() || body == null
                                || body.accessToken == null || body.user == null) {
                            moveToLogin();
                            return;
                        }
                        sessionManager.saveSession(body.accessToken, body.refreshToken, body.user.id);
                        moveTo(HomeActivity.class);
                    }

                    @Override
                    public void onFailure(Call<AuthApi.AuthResponse> call, Throwable t) {
                        moveToLogin();
                    }
                });
    }

    // 자동 로그인이 꺼져 있거나 갱신에 실패하면 저장된 세션을 지우고 로그인 화면으로
    private void moveToLogin() {
        sessionManager.clear();
        moveTo(LoginActivity.class);
    }

    private void moveTo(Class<?> activityClass) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        startActivity(new Intent(this, activityClass));
        finish();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 흰 배경 위에서 상태바 아이콘이 어둡게 보이도록 light 스타일 사용
        EdgeToEdge.enable(this,
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT));
        setContentView(R.layout.activity_splash);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.splash_root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        sessionManager = new SessionManager(this);

        dots = new View[]{
                findViewById(R.id.splash_dot_1),
                findViewById(R.id.splash_dot_2),
                findViewById(R.id.splash_dot_3)
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.postDelayed(dotAnimation, DOT_INTERVAL_MS);
        handler.postDelayed(goToNextScreen, SPLASH_DURATION_MS);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 백그라운드 전환 시 애니메이션/화면 전환 예약 취소
        handler.removeCallbacks(dotAnimation);
        handler.removeCallbacks(goToNextScreen);
    }
}

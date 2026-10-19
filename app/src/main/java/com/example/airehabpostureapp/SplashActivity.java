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

    private final Runnable goToNextScreen = () -> {
        startActivity(new Intent(SplashActivity.this, LoginActivity.class));
        finish();
    };

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

package com.example.airehabpostureapp;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class HomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 흰 배경 위에서 상태바 아이콘이 어둡게 보이도록 light 스타일 사용
        EdgeToEdge.enable(this,
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT));
        setContentView(R.layout.activity_home);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.homeRoot), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // TODO: 알림 기능 구현
        findViewById(R.id.notificationButton).setOnClickListener(v -> showNotReady());

        // 오늘의 추천 운동 Card → 08_Recommend
        findViewById(R.id.recommendedExerciseCard).setOnClickListener(v ->
                startActivity(new Intent(this, RecommendActivity.class)));

        // TODO: 운동 화면 구현 후 해당 운동 시작 화면으로 이동
        findViewById(R.id.startExerciseButton).setOnClickListener(v -> showNotReady());

        // Bottom Navigation: 현재 화면은 홈
        findViewById(R.id.bottomNavHome).setSelected(true);
        findViewById(R.id.bottomNavExercise).setOnClickListener(v ->
                startActivity(new Intent(this, ExerciseListActivity.class)));
        findViewById(R.id.bottomNavRecord).setOnClickListener(v ->
                startActivity(new Intent(this, RecordListActivity.class)));
        findViewById(R.id.bottomNavMyPage).setOnClickListener(v ->
                startActivity(new Intent(this, MyPageActivity.class)));
    }

    private void showNotReady() {
        Toast.makeText(this, R.string.feature_not_ready, Toast.LENGTH_SHORT).show();
    }
}

package com.example.airehabpostureapp;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

// 현재는 strings.xml의 고정 예시 데이터로 UI만 표시 (추천 로직 미구현)
public class RecommendActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 흰 배경 위에서 상태바 아이콘이 어둡게 보이도록 light 스타일 사용
        EdgeToEdge.enable(this,
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT));
        setContentView(R.layout.activity_recommend);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.recommendRoot), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 이전 화면(Home)으로 돌아가기: 새 Activity를 만들지 않고 현재 화면만 종료
        findViewById(R.id.backButton).setOnClickListener(v -> finish());

        // TODO: 10_ExerciseDetail 화면 구현 후 선택한 운동의 상세 화면으로 이동
        View.OnClickListener cardClickListener = v ->
                Toast.makeText(this, R.string.feature_not_ready, Toast.LENGTH_SHORT).show();
        findViewById(R.id.recommendedExerciseCard1).setOnClickListener(cardClickListener);
        findViewById(R.id.recommendedExerciseCard2).setOnClickListener(cardClickListener);
        findViewById(R.id.recommendedExerciseCard3).setOnClickListener(cardClickListener);
    }
}

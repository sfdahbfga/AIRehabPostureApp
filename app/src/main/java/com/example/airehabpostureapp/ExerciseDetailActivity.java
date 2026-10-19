package com.example.airehabpostureapp;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

// 현재는 "무릎 굽힘 운동" 고정 예시 데이터만 표시 (운동별 상세 데이터 연동 전)
public class ExerciseDetailActivity extends AppCompatActivity {

    // 이전 화면에서 선택한 운동 이름
    public static final String EXTRA_EXERCISE_NAME = "com.example.airehabpostureapp.EXTRA_EXERCISE_NAME";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 흰 배경 위에서 상태바 아이콘이 어둡게 보이도록 light 스타일 사용
        EdgeToEdge.enable(this,
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT));
        setContentView(R.layout.activity_exercise_detail);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.exerciseDetailRoot), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 전달받은 운동 이름이 있으면 표시 (없으면 레이아웃 기본값 유지)
        TextView exerciseTitleText = findViewById(R.id.exerciseTitleText);
        String exerciseName = getIntent().getStringExtra(EXTRA_EXERCISE_NAME);
        if (exerciseName != null) {
            exerciseTitleText.setText(exerciseName);
        }

        // 이전 화면(ExerciseList)으로 돌아가기: 새 Activity를 만들지 않고 현재 화면만 종료
        findViewById(R.id.backButton).setOnClickListener(v -> finish());

        // 운동 전 컨디션 체크(11_PreCheck)로 이동
        findViewById(R.id.startExerciseButton).setOnClickListener(v ->
                startActivity(new Intent(this, PreCheckActivity.class)));
    }
}

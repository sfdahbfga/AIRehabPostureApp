package com.example.airehabpostureapp;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

// 운동 결과 화면 (현재는 고정 예시 데이터만 표시, 운동 기록 데이터 연동 전)
public class ResultActivity extends AppCompatActivity {

    // TODO: 13_Exercise 실제 결과(MediaPipe + 관절 각도 계산) / Supabase 기록과 연동 후 교체 (현재는 임시 예시 데이터)
    private int exerciseDurationSeconds = 13 * 60 + 24;
    private int completedReps = 18;
    private int targetReps = 20;
    private int averageAccuracy = 88;
    private int completedSets = 3;
    private int targetSets = 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 흰 배경 위에서 상태바 아이콘이 어둡게 보이도록 light 스타일 사용
        EdgeToEdge.enable(this,
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT));
        setContentView(R.layout.activity_result);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.resultRoot), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        showResult();

        // AI 분석 리포트(17_AIReport)로 이동 (Back 시 이 화면으로 복귀)
        findViewById(R.id.viewAnalysisButton).setOnClickListener(v ->
                startActivity(new Intent(this, AIReportActivity.class)));

        // 시스템 Back: 끝난 운동 흐름(CameraSetup 등)으로 돌아가지 않고 기존 Home으로 복귀
        // CLEAR_TOP + SINGLE_TOP: Back Stack에 있는 Home을 재사용하고 그 위의 화면은 모두 종료
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                Intent intent = new Intent(ResultActivity.this, HomeActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
            }
        });
    }

    private void showResult() {
        TextView exerciseDurationValueText = findViewById(R.id.exerciseDurationValueText);
        TextView repetitionValueText = findViewById(R.id.repetitionValueText);
        TextView averageAccuracyValueText = findViewById(R.id.averageAccuracyValueText);
        TextView setCompletionValueText = findViewById(R.id.setCompletionValueText);

        exerciseDurationValueText.setText(getString(R.string.exercise_duration_format,
                exerciseDurationSeconds / 60, exerciseDurationSeconds % 60));
        repetitionValueText.setText(getString(R.string.repetition_count_format, completedReps, targetReps));
        averageAccuracyValueText.setText(getString(R.string.percent_format, averageAccuracy));
        setCompletionValueText.setText(getString(R.string.set_count_format, completedSets, targetSets));
    }
}

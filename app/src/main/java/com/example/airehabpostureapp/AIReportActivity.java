package com.example.airehabpostureapp;

import android.graphics.Color;
import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

// AI 분석 리포트 화면 (현재는 UI와 화면 흐름만 구현, Gemini 미연동)
// 역할 구분: 수치는 Java 관절 각도 계산 결과에서 오고, Gemini는 그 수치를 자연어로 요약만 한다.
// (aiSummaryText / nextExerciseTipText = 향후 Gemini 요약 결과가 들어갈 영역)
public class AIReportActivity extends AppCompatActivity {

    // 선택 전달값: 19_RecordDetail에서 운동 이름 전달 (없으면 레이아웃 기본값 유지)
    public static final String EXTRA_EXERCISE_NAME = "com.example.airehabpostureapp.EXTRA_REPORT_EXERCISE_NAME";

    // TODO: 실제 측정값 아님. Java 관절 각도 계산 결과 / Supabase 운동 기록 연동 후 교체 (현재는 UI용 예시 데이터)
    private int kneeAverageErrorDegree = 12;
    private int backAverageErrorDegree = 3;
    private int overallAccuracy = 91;
    private int lateAccuracy = 78;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 흰 배경 위에서 상태바 아이콘이 어둡게 보이도록 light 스타일 사용
        EdgeToEdge.enable(this,
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT));
        setContentView(R.layout.activity_ai_report);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.aiReportRoot), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 이전 화면(Result)으로 돌아가기: 새 Activity를 만들지 않고 현재 화면만 종료
        findViewById(R.id.backButton).setOnClickListener(v -> finish());

        String exerciseName = getIntent().getStringExtra(EXTRA_EXERCISE_NAME);
        if (exerciseName != null) {
            TextView exerciseNameText = findViewById(R.id.exerciseNameText);
            exerciseNameText.setText(exerciseName);
        }

        showAnalysisResults();
        // TODO: Gemini 연동 후 aiSummaryText / nextExerciseTipText를 요약 결과로 교체 (현재는 strings.xml 예시 문구)
    }

    private void showAnalysisResults() {
        TextView kneeAverageErrorText = findViewById(R.id.kneeAverageErrorText);
        TextView backAverageErrorText = findViewById(R.id.backAverageErrorText);
        TextView overallAccuracyText = findViewById(R.id.overallAccuracyText);
        TextView lateAccuracyText = findViewById(R.id.lateAccuracyText);

        kneeAverageErrorText.setText(getString(R.string.angle_error_format, kneeAverageErrorDegree));
        backAverageErrorText.setText(getString(R.string.angle_error_format, backAverageErrorDegree));
        overallAccuracyText.setText(getString(R.string.percent_format, overallAccuracy));
        lateAccuracyText.setText(getString(R.string.percent_format, lateAccuracy));
    }
}

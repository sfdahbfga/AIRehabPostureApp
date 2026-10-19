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

import com.google.android.material.progressindicator.LinearProgressIndicator;

// 운동 기록 상세 (기본 정보는 18_RecordList에서 전달, 나머지는 임시 예시 데이터, Supabase 연동 전)
public class RecordDetailActivity extends AppCompatActivity {

    // 18_RecordList에서 전달하는 선택한 기록 정보
    public static final String EXTRA_EXERCISE_NAME = "com.example.airehabpostureapp.EXTRA_RECORD_EXERCISE_NAME";
    public static final String EXTRA_EXERCISE_DATE = "com.example.airehabpostureapp.EXTRA_RECORD_EXERCISE_DATE";
    public static final String EXTRA_DURATION = "com.example.airehabpostureapp.EXTRA_RECORD_DURATION";
    public static final String EXTRA_ACCURACY = "com.example.airehabpostureapp.EXTRA_RECORD_ACCURACY";

    // TODO: Supabase 운동 기록 연동 후 교체 (현재는 모든 기록에 같은 임시 예시 데이터 사용)
    private int completedReps = 18;
    private int targetReps = 20;
    private int completedSets = 3;
    private int targetSets = 3;
    private int kneeAccuracy = 82;
    private int backAccuracy = 94;
    private int hipAccuracy = 89;
    // 인덱스 = 세트 번호 - 1
    private int[] setAccuracies = {92, 88, 84};

    private String exerciseName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 흰 배경 위에서 상태바 아이콘이 어둡게 보이도록 light 스타일 사용
        EdgeToEdge.enable(this,
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT));
        setContentView(R.layout.activity_record_detail);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.recordDetailRoot), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 이전 화면(RecordList)으로 돌아가기: 새 Activity를 만들지 않고 현재 화면만 종료
        findViewById(R.id.backButton).setOnClickListener(v -> finish());

        showRecordSummary();
        showJointAccuracy();
        showSetAccuracy();

        // 기존 17_AIReport 재사용: 운동 이름만 전달 (분석 내용은 AIReport의 임시 데이터 유지)
        findViewById(R.id.viewAIAnalysisButton).setOnClickListener(v -> {
            Intent intent = new Intent(this, AIReportActivity.class);
            intent.putExtra(AIReportActivity.EXTRA_EXERCISE_NAME, exerciseName);
            startActivity(intent);
        });
    }

    // 운동 기본 정보 + 요약 (운동명 / 날짜 / 시간 / 정확도는 RecordList에서 전달받은 값)
    private void showRecordSummary() {
        exerciseName = getIntent().getStringExtra(EXTRA_EXERCISE_NAME);
        String exerciseDate = getIntent().getStringExtra(EXTRA_EXERCISE_DATE);
        int durationMinutes = getIntent().getIntExtra(EXTRA_DURATION, 0);
        int accuracy = getIntent().getIntExtra(EXTRA_ACCURACY, 0);

        TextView exerciseNameText = findViewById(R.id.exerciseNameText);
        TextView exerciseDateText = findViewById(R.id.exerciseDateText);
        TextView durationValueText = findViewById(R.id.durationValueText);
        TextView accuracyValueText = findViewById(R.id.accuracyValueText);
        TextView repetitionValueText = findViewById(R.id.repetitionValueText);
        TextView setCompletionValueText = findViewById(R.id.setCompletionValueText);

        exerciseNameText.setText(exerciseName);
        exerciseDateText.setText(exerciseDate);
        durationValueText.setText(getString(R.string.duration_minutes_format, durationMinutes));
        accuracyValueText.setText(getString(R.string.percent_format, accuracy));
        repetitionValueText.setText(getString(R.string.repetition_count_format, completedReps, targetReps));
        setCompletionValueText.setText(getString(R.string.set_count_format, completedSets, targetSets));
    }

    private void showJointAccuracy() {
        showAccuracyRow(R.id.kneeAccuracyText, R.id.kneeProgressBar, kneeAccuracy);
        showAccuracyRow(R.id.backAccuracyText, R.id.backProgressBar, backAccuracy);
        showAccuracyRow(R.id.hipAccuracyText, R.id.hipProgressBar, hipAccuracy);
    }

    private void showSetAccuracy() {
        int[] setLabelIds = {R.id.set1LabelText, R.id.set2LabelText, R.id.set3LabelText};
        int[] setAccuracyTextIds = {R.id.set1AccuracyText, R.id.set2AccuracyText, R.id.set3AccuracyText};
        int[] setProgressBarIds = {R.id.set1ProgressBar, R.id.set2ProgressBar, R.id.set3ProgressBar};
        for (int i = 0; i < setAccuracies.length; i++) {
            TextView setLabelText = findViewById(setLabelIds[i]);
            setLabelText.setText(getString(R.string.set_label_format, i + 1));
            showAccuracyRow(setAccuracyTextIds[i], setProgressBarIds[i], setAccuracies[i]);
        }
    }

    // 정확도 % 텍스트 + Progress Bar 표시 (관절별 / 세트별 공통)
    private void showAccuracyRow(int accuracyTextId, int progressBarId, int accuracy) {
        TextView accuracyText = findViewById(accuracyTextId);
        LinearProgressIndicator progressBar = findViewById(progressBarId);
        accuracyText.setText(getString(R.string.percent_format, accuracy));
        progressBar.setProgress(accuracy);
    }
}

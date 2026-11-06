package com.example.airehabpostureapp;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.airehabpostureapp.network.AuthErrorMapper;
import com.example.airehabpostureapp.network.ExerciseSessionApi;
import com.example.airehabpostureapp.network.SessionManager;
import com.example.airehabpostureapp.network.SupabaseClient;
import com.example.airehabpostureapp.stats.ReportStatsCalculator;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

// 운동 기록 상세: 18_RecordList에서 고른 기록(session id)을 exercise_sessions에서 조회해 운동 정보 표시
public class RecordDetailActivity extends AppCompatActivity {

    // 18_RecordList에서 전달하는 선택한 기록의 exercise_sessions id
    public static final String EXTRA_SESSION_ID = "com.example.airehabpostureapp.EXTRA_RECORD_SESSION_ID";

    // TODO: 세트 / 관절별 정확도 / 세트별 정확도는 저장하지 않으므로 측정 연동 후 실제 값으로 교체 (현재는 임시 예시 데이터)
    private int completedSets = 3;
    private int targetSets = 3;
    private int kneeAccuracy = 82;
    private int backAccuracy = 94;
    private int hipAccuracy = 89;
    // 인덱스 = 세트 번호 - 1
    private int[] setAccuracies = {92, 88, 84};

    private String sessionId;
    // 조회 후 AI 리포트 화면에 함께 전달 (조회 전에는 null → AI 리포트는 레이아웃 기본 이름 표시)
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

        sessionId = getIntent().getStringExtra(EXTRA_SESSION_ID);
        showSetCompletion();
        showJointAccuracy();
        showSetAccuracy();
        loadRecord();

        // 17_AIReport: 이 기록의 session id를 넘겨 Gemini 요약 / 분석 수치를 표시
        findViewById(R.id.viewAIAnalysisButton).setOnClickListener(v -> {
            Intent intent = new Intent(this, AIReportActivity.class);
            intent.putExtra(AIReportActivity.EXTRA_SESSION_ID, sessionId);
            intent.putExtra(AIReportActivity.EXTRA_EXERCISE_NAME, exerciseName);
            startActivity(intent);
        });
    }

    // 조회에 실패하면 값 칸은 비워 두고 안내
    private void loadRecord() {
        if (sessionId == null) {
            showError(R.string.error_record_load_failed);
            return;
        }
        SessionManager sessionManager = new SessionManager(this);
        ExerciseSessionApi api = SupabaseClient.getRetrofit().create(ExerciseSessionApi.class);
        api.getRecord(sessionManager.getBearerToken(), "eq." + sessionId)
                .enqueue(new Callback<List<ExerciseSessionApi.RecordRow>>() {
                    @Override
                    public void onResponse(Call<List<ExerciseSessionApi.RecordRow>> call,
                                           Response<List<ExerciseSessionApi.RecordRow>> response) {
                        List<ExerciseSessionApi.RecordRow> rows = response.body();
                        if (!response.isSuccessful() || rows == null || rows.isEmpty()) {
                            showError(R.string.error_record_load_failed);
                            return;
                        }
                        showRecordSummary(rows.get(0));
                    }

                    @Override
                    public void onFailure(Call<List<ExerciseSessionApi.RecordRow>> call, Throwable t) {
                        showError(AuthErrorMapper.fromFailure(t, R.string.error_record_load_failed));
                    }
                });
    }

    // 운동 기본 정보 + 요약 (운동명 / 날짜 / 시간 / 정확도 / 반복 횟수)
    private void showRecordSummary(ExerciseSessionApi.RecordRow record) {
        exerciseName = record.exerciseName;

        TextView exerciseNameText = findViewById(R.id.exerciseNameText);
        TextView exerciseDateText = findViewById(R.id.exerciseDateText);
        TextView durationValueText = findViewById(R.id.durationValueText);
        TextView accuracyValueText = findViewById(R.id.accuracyValueText);
        TextView repetitionValueText = findViewById(R.id.repetitionValueText);

        Instant startedAt = record.startedAtInstant();
        LocalDate date = startedAt == null ? null
                : startedAt.atZone(ReportStatsCalculator.KOREA_ZONE).toLocalDate();
        int durationSeconds = record.durationSeconds == null ? 0 : record.durationSeconds;
        int completedReps = record.completedRepetitions == null ? 0 : record.completedRepetitions;
        int targetReps = record.targetRepetitions == null ? 0 : record.targetRepetitions;

        exerciseNameText.setText(exerciseName);
        exerciseDateText.setText(date == null ? getString(R.string.empty_value)
                : getString(R.string.record_detail_date_format,
                date.getYear(), date.getMonthValue(), date.getDayOfMonth()));
        durationValueText.setText(DurationFormatter.formatSecondsAsMinutes(this, durationSeconds));
        // 정확도가 없는 기록(자세 인식 미지원 운동 / 0회)은 "-"
        accuracyValueText.setText(record.averageAccuracy == null ? getString(R.string.empty_value)
                : getString(R.string.percent_format, (int) Math.round(record.averageAccuracy)));
        repetitionValueText.setText(getString(R.string.repetition_count_format, completedReps, targetReps));
    }

    private void showSetCompletion() {
        TextView setCompletionValueText = findViewById(R.id.setCompletionValueText);
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

    private void showError(@StringRes int messageRes) {
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show();
    }
}

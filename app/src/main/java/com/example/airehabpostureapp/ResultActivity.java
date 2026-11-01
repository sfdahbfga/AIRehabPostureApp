package com.example.airehabpostureapp;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
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

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

// 운동 결과 화면: 15_PostCheck에서 저장한 exercise_sessions 행을 조회해 운동 시간 / 횟수 / 정확도 표시
public class ResultActivity extends AppCompatActivity {

    // 15_PostCheck에서 저장한 exercise_sessions id
    public static final String EXTRA_SESSION_ID = "com.example.airehabpostureapp.EXTRA_RESULT_SESSION_ID";

    // TODO: 세트 측정 연동 후 실제 값으로 교체 (현재 세트는 저장하지 않으므로 임시 예시 데이터)
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

        showSetCompletion();
        loadSession();

        // AI 분석 리포트(17_AIReport)로 이동 (Back 시 이 화면으로 복귀, 운동 기록 id 전달)
        findViewById(R.id.viewAnalysisButton).setOnClickListener(v -> {
            Intent intent = new Intent(this, AIReportActivity.class);
            intent.putExtra(AIReportActivity.EXTRA_SESSION_ID, getIntent().getStringExtra(EXTRA_SESSION_ID));
            startActivity(intent);
        });

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

    // 조회에 실패하면 오류 메시지를 보여주고 이 화면에 머무름 (값 칸은 비어 있음)
    private void loadSession() {
        String sessionId = getIntent().getStringExtra(EXTRA_SESSION_ID);
        if (sessionId == null) {
            showError(R.string.error_result_load_failed);
            return;
        }
        SessionManager sessionManager = new SessionManager(this);
        ExerciseSessionApi api = SupabaseClient.getRetrofit().create(ExerciseSessionApi.class);
        api.getSession(sessionManager.getBearerToken(), "eq." + sessionId)
                .enqueue(new Callback<List<ExerciseSessionApi.SessionRow>>() {
                    @Override
                    public void onResponse(Call<List<ExerciseSessionApi.SessionRow>> call,
                                           Response<List<ExerciseSessionApi.SessionRow>> response) {
                        List<ExerciseSessionApi.SessionRow> rows = response.body();
                        if (!response.isSuccessful() || rows == null || rows.isEmpty()) {
                            showError(R.string.error_result_load_failed);
                            return;
                        }
                        showResult(rows.get(0));
                    }

                    @Override
                    public void onFailure(Call<List<ExerciseSessionApi.SessionRow>> call, Throwable t) {
                        showError(AuthErrorMapper.fromFailure(t, R.string.error_result_load_failed));
                    }
                });
    }

    private void showResult(ExerciseSessionApi.SessionRow session) {
        TextView exerciseDurationValueText = findViewById(R.id.exerciseDurationValueText);
        TextView repetitionValueText = findViewById(R.id.repetitionValueText);
        TextView averageAccuracyValueText = findViewById(R.id.averageAccuracyValueText);

        int completedReps = session.completedRepetitions == null ? 0 : session.completedRepetitions;
        int targetReps = session.targetRepetitions == null ? 0 : session.targetRepetitions;
        int averageAccuracy = session.averageAccuracy == null ? 0 : (int) Math.round(session.averageAccuracy);

        exerciseDurationValueText.setText(DurationFormatter.formatSeconds(this, session.durationSeconds));
        repetitionValueText.setText(getString(R.string.repetition_count_format, completedReps, targetReps));
        averageAccuracyValueText.setText(getString(R.string.percent_format, averageAccuracy));
    }

    private void showSetCompletion() {
        TextView setCompletionValueText = findViewById(R.id.setCompletionValueText);
        setCompletionValueText.setText(getString(R.string.set_count_format, completedSets, targetSets));
    }

    private void showError(@StringRes int messageRes) {
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show();
    }
}

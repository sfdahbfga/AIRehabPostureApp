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

import com.example.airehabpostureapp.network.ApiCalls;
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

    // L7: 화면이 닫힐 때 진행 중인 요청 취소
    private final ApiCalls apiCalls = ApiCalls.bind(this);

    // 15_PostCheck에서 저장한 exercise_sessions id
    public static final String EXTRA_SESSION_ID = "com.example.airehabpostureapp.EXTRA_RESULT_SESSION_ID";

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

        loadSession();

        // AI 분석 리포트(17_AIReport)로 이동 (Back 시 이 화면으로 복귀, 운동 기록 id 전달)
        findViewById(R.id.viewAnalysisButton).setOnClickListener(v -> {
            Intent intent = new Intent(this, AIReportActivity.class);
            intent.putExtra(AIReportActivity.EXTRA_SESSION_ID, getIntent().getStringExtra(EXTRA_SESSION_ID));
            // M6: 실제 운동 이름 전달 (조회 전이면 null → AI 리포트가 직접 조회한 이름 표시)
            intent.putExtra(AIReportActivity.EXTRA_EXERCISE_NAME, exerciseName);
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
        apiCalls.track(api.getSession(sessionManager.getBearerToken(), "eq." + sessionId))
                .enqueue(new Callback<List<ExerciseSessionApi.SessionRow>>() {
                    @Override
                    public void onResponse(Call<List<ExerciseSessionApi.SessionRow>> call,
                                           Response<List<ExerciseSessionApi.SessionRow>> response) {
                        List<ExerciseSessionApi.SessionRow> rows = response.body();
                        if (!response.isSuccessful() || rows == null || rows.isEmpty()) {
                            showLoadError(R.string.error_result_load_failed);
                            return;
                        }
                        showResult(rows.get(0));
                    }

                    @Override
                    public void onFailure(Call<List<ExerciseSessionApi.SessionRow>> call, Throwable t) {
                        showLoadError(AuthErrorMapper.fromFailure(t, R.string.error_result_load_failed));
                    }
                });
    }

    // 조회한 운동 이름 (AI 리포트에 전달)
    private String exerciseName;

    private void showResult(ExerciseSessionApi.SessionRow session) {
        // M6: 레이아웃 기본값("무릎 굽힘 운동") 대신 실제 운동 이름
        exerciseName = session.exerciseName;
        if (exerciseName != null) {
            TextView exerciseNameText = findViewById(R.id.exerciseNameText);
            exerciseNameText.setText(exerciseName);
        }
        TextView exerciseDurationValueText = findViewById(R.id.exerciseDurationValueText);
        TextView repetitionValueText = findViewById(R.id.repetitionValueText);
        TextView averageAccuracyValueText = findViewById(R.id.averageAccuracyValueText);

        int completedReps = session.completedRepetitions == null ? 0 : session.completedRepetitions;
        int targetReps = session.targetRepetitions == null ? 0 : session.targetRepetitions;

        exerciseDurationValueText.setText(DurationFormatter.formatSeconds(this, session.durationSeconds));
        repetitionValueText.setText(getString(R.string.repetition_count_format, completedReps, targetReps));
        // 정확도가 없는 기록(자세 인식 미지원 운동 / 0회)은 "-"
        averageAccuracyValueText.setText(session.averageAccuracy == null ? getString(R.string.empty_value)
                : getString(R.string.percent_format, (int) Math.round(session.averageAccuracy)));
        showSetCompletion(session.completedSets, session.targetSets);
    }

    // 저장된 세트 값이 하나라도 없으면 "-"
    private void showSetCompletion(Integer completedSets, Integer targetSets) {
        TextView setCompletionValueText = findViewById(R.id.setCompletionValueText);
        setCompletionValueText.setText(completedSets == null || targetSets == null
                ? getString(R.string.empty_value)
                : getString(R.string.set_count_format, completedSets, targetSets));
    }

    // M4: 불러오기 실패 → "다시 시도"
    private void showLoadError(@StringRes int messageRes) {
        RetrySnackbar.show(this, messageRes, this::loadSession);
    }

    private void showError(@StringRes int messageRes) {
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show();
    }
}

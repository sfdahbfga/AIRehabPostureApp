package com.example.airehabpostureapp;

import android.graphics.Color;
import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.snackbar.Snackbar;

import com.example.airehabpostureapp.network.ApiCalls;
import com.example.airehabpostureapp.network.AiSummaryApi;
import com.example.airehabpostureapp.network.AuthErrorMapper;
import com.example.airehabpostureapp.network.ExerciseSessionApi;
import com.example.airehabpostureapp.network.SessionManager;
import com.example.airehabpostureapp.network.SupabaseClient;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

// AI 분석 리포트 화면
// 역할 구분: 수치는 Java 관절 각도 계산 결과에서 오고, Gemini는 그 수치를 자연어로 요약만 한다.
// session id가 있으면 Edge Function ai-summary의 요약 / 팁과 exercise_sessions의 수치를 표시하고,
// 없으면(다른 화면에서 들어온 경우) 기존 예시 화면을 그대로 표시한다.
public class AIReportActivity extends AppCompatActivity {

    // L7: 화면이 닫힐 때 진행 중인 요청 취소
    private final ApiCalls apiCalls = ApiCalls.bind(this);

    // 선택 전달값: 19_RecordDetail에서 운동 이름 전달 (없으면 레이아웃 기본값 유지)
    public static final String EXTRA_EXERCISE_NAME = "com.example.airehabpostureapp.EXTRA_REPORT_EXERCISE_NAME";
    // 선택 전달값: 16_Result에서 운동 기록(exercise_sessions) id 전달
    public static final String EXTRA_SESSION_ID = "com.example.airehabpostureapp.EXTRA_REPORT_SESSION_ID";

    // ai-summary가 Gemini 호출에 실패했을 때 돌려주는 상태 코드 (502: Gemini 오류 응답, 504: 재시도 모두 시간 초과)
    private static final int HTTP_BAD_GATEWAY = 502;
    private static final int HTTP_GATEWAY_TIMEOUT = 504;

    // TODO: 실제 측정값 아님. session id 없이 들어온 경우에만 표시하는 UI용 예시 데이터 (기록 상세 연동 후 교체)
    private int kneeAverageErrorDegree = 12;
    private int backAverageErrorDegree = 3;
    private int overallAccuracy = 91;
    private int lateAccuracy = 78;

    private TextView aiSummaryText;
    private TextView nextExerciseTipText;
    private SessionManager sessionManager;

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

        aiSummaryText = findViewById(R.id.aiSummaryText);
        nextExerciseTipText = findViewById(R.id.nextExerciseTipText);
        sessionManager = new SessionManager(this);

        String sessionId = getIntent().getStringExtra(EXTRA_SESSION_ID);
        if (sessionId == null) {
            // 예시 화면: 수치는 예시 데이터, 요약 / 팁은 레이아웃의 strings.xml 예시 문구 유지
            showAnalysisResults();
            return;
        }
        loadAiSummary(sessionId);
        loadReportMetrics(sessionId);
    }

    // 요약 / 팁 자리에 안내 문구를 먼저 보여주고 ai-summary 응답이 오면 교체
    private void loadAiSummary(String sessionId) {
        aiSummaryText.setText(R.string.ai_report_analyzing);
        nextExerciseTipText.setText(R.string.ai_report_analyzing);

        apiCalls.track(AiSummaryApi.create()
                .getSummary(sessionManager.getBearerToken(), new AiSummaryApi.AiSummaryRequest(sessionId)))
                .enqueue(new Callback<AiSummaryApi.AiSummaryResponse>() {
                    @Override
                    public void onResponse(Call<AiSummaryApi.AiSummaryResponse> call,
                                           Response<AiSummaryApi.AiSummaryResponse> response) {
                        AiSummaryApi.AiSummaryResponse body = response.body();
                        // 502: Gemini가 바빠서(503 등) 재시도가 모두 실패한 경우
                        // 504: 함수 안에서 Gemini 재시도가 모두 시간 초과된 경우
                        if (response.code() == HTTP_BAD_GATEWAY || response.code() == HTTP_GATEWAY_TIMEOUT) {
                            showAiSummaryError(R.string.error_ai_summary_delayed);
                            return;
                        }
                        // 404 / 500 / 502 등 서버가 응답했지만 실패한 경우
                        if (!response.isSuccessful() || body == null || body.summary == null) {
                            showAiSummaryError(R.string.error_ai_summary_server);
                            return;
                        }
                        aiSummaryText.setText(body.summary);
                        nextExerciseTipText.setText(body.tip == null ? "" : body.tip);
                    }

                    // 응답을 받지 못한 경우: 읽기 시간 초과는 지연 안내, 그 외 IOException(인터넷 끊김 등)은 네트워크 오류
                    // (SocketTimeoutException도 IOException이므로 먼저 검사)
                    @Override
                    public void onFailure(Call<AiSummaryApi.AiSummaryResponse> call, Throwable t) {
                        if (t instanceof SocketTimeoutException) {
                            showAiSummaryError(R.string.error_ai_summary_delayed);
                        } else if (t instanceof IOException) {
                            showAiSummaryError(R.string.error_ai_summary_network);
                        } else {
                            showAiSummaryError(R.string.error_ai_summary_server);
                        }
                    }
                });
    }

    // 실패 안내는 요약 / 팁 자리에 같은 문구로 표시
    private void showAiSummaryError(@StringRes int messageRes) {
        aiSummaryText.setText(messageRes);
        nextExerciseTipText.setText(messageRes);
        summaryFailed = true;
        showRetry(messageRes);
    }

    // M4: 요약 / 분석 수치 중 실패한 것만 다시 불러오는 "다시 시도" (Snackbar는 하나만)
    private boolean summaryFailed;
    private boolean metricsFailed;
    private Snackbar retrySnackbar;

    private void showRetry(@StringRes int messageRes) {
        if (retrySnackbar != null && retrySnackbar.isShownOrQueued()) {
            return;
        }
        retrySnackbar = RetrySnackbar.show(this, messageRes, () -> {
            String sessionId = getIntent().getStringExtra(EXTRA_SESSION_ID);
            if (summaryFailed) {
                summaryFailed = false;
                loadAiSummary(sessionId);
            }
            if (metricsFailed) {
                metricsFailed = false;
                loadReportMetrics(sessionId);
            }
        });
    }

    // 관절별 평균 오차 / 전반·후반 정확도를 exercise_sessions에서 조회 (실패하면 값 칸은 비워 두고 안내)
    private void loadReportMetrics(String sessionId) {
        ExerciseSessionApi api = SupabaseClient.getRetrofit().create(ExerciseSessionApi.class);
        apiCalls.track(api.getReportMetrics(sessionManager.getBearerToken(), "eq." + sessionId))
                .enqueue(new Callback<List<ExerciseSessionApi.ReportMetricsRow>>() {
                    @Override
                    public void onResponse(Call<List<ExerciseSessionApi.ReportMetricsRow>> call,
                                           Response<List<ExerciseSessionApi.ReportMetricsRow>> response) {
                        List<ExerciseSessionApi.ReportMetricsRow> rows = response.body();
                        if (!response.isSuccessful() || rows == null || rows.isEmpty()) {
                            metricsFailed = true;
                            showRetry(R.string.error_result_load_failed);
                            return;
                        }
                        showReportMetrics(rows.get(0));
                    }

                    @Override
                    public void onFailure(Call<List<ExerciseSessionApi.ReportMetricsRow>> call, Throwable t) {
                        metricsFailed = true;
                        showRetry(AuthErrorMapper.fromFailure(t, R.string.error_result_load_failed));
                    }
                });
    }

    // 값이 없는 항목(자세 인식 미지원 운동 / 0회)은 "-"로 표시
    private void showReportMetrics(ExerciseSessionApi.ReportMetricsRow metrics) {
        Map<String, Double> jointErrors = metrics.jointErrors;
        showAngleError(R.id.kneeAverageErrorText, jointErrors == null ? null : jointErrors.get("knee"));
        showAngleError(R.id.backAverageErrorText, jointErrors == null ? null : jointErrors.get("back"));
        showPercent(R.id.overallAccuracyText, metrics.firstHalfAccuracy);
        showPercent(R.id.lateAccuracyText, metrics.secondHalfAccuracy);
    }

    private void showAngleError(int textViewId, Double degrees) {
        TextView textView = findViewById(textViewId);
        textView.setText(degrees == null ? getString(R.string.empty_value)
                : getString(R.string.angle_error_format, (int) Math.round(degrees)));
    }

    private void showPercent(int textViewId, Double percent) {
        TextView textView = findViewById(textViewId);
        textView.setText(percent == null ? getString(R.string.empty_value)
                : getString(R.string.percent_format, (int) Math.round(percent)));
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

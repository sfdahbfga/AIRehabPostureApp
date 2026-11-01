package com.example.airehabpostureapp;

import android.graphics.Color;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.airehabpostureapp.network.AuthErrorMapper;
import com.example.airehabpostureapp.network.ExerciseSessionApi;
import com.example.airehabpostureapp.network.SessionManager;
import com.example.airehabpostureapp.network.SupabaseClient;
import com.example.airehabpostureapp.stats.ExerciseStreak;
import com.example.airehabpostureapp.stats.ReportStats;
import com.example.airehabpostureapp.stats.ReportStatsCalculator;
import com.example.airehabpostureapp.stats.SessionRecord;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

// 목표 / 챌린지: 목표 기준은 상수, 현재 값은 내 운동 기록으로 계산
// (횟수 / 시간 / 정확도는 이번 주 기록으로 ReportStatsCalculator 계산, 연속 운동일은 ExerciseStreak)
public class GoalChallengeActivity extends AppCompatActivity {

    // 목표 기준
    private static final int WEEKLY_EXERCISE_GOAL = 3;
    private static final int EXERCISE_TIME_GOAL_MINUTES = 60;
    private static final int ACCURACY_GOAL = 90;
    private static final int CHALLENGE_GOAL_DAYS = 7;

    private final ReportStatsCalculator calculator = new ReportStatsCalculator();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 흰 배경 위에서 상태바 아이콘이 어둡게 보이도록 light 스타일 사용
        EdgeToEdge.enable(this,
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT));
        setContentView(R.layout.activity_goal_challenge);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.goalChallengeRoot), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 이전 화면으로 돌아가기: 새 Activity를 만들지 않고 현재 화면만 종료
        findViewById(R.id.backButton).setOnClickListener(v -> finish());

        showGoalTitles();
        loadRecords();
    }

    // 목표 제목은 기록과 관계없이 바로 표시
    private void showGoalTitles() {
        setText(R.id.weeklyExerciseGoalText, getString(R.string.weekly_exercise_goal, WEEKLY_EXERCISE_GOAL));
        setText(R.id.exerciseTimeGoalText, getString(R.string.exercise_time_goal, EXERCISE_TIME_GOAL_MINUTES));
        setText(R.id.accuracyGoalText, getString(R.string.accuracy_goal, ACCURACY_GOAL));
        setText(R.id.sevenDayChallengeTitleText, getString(R.string.seven_day_challenge, CHALLENGE_GOAL_DAYS));
        setText(R.id.accuracyChallengeTitleText, getString(R.string.accuracy_challenge, ACCURACY_GOAL));
    }

    // 연속 운동일은 이번 주를 넘어갈 수 있어 내 기록 전체를 불러옴
    private void loadRecords() {
        SessionManager sessionManager = new SessionManager(this);
        ExerciseSessionApi api = SupabaseClient.getRetrofit().create(ExerciseSessionApi.class);
        api.getRecords(sessionManager.getBearerToken(), "eq." + sessionManager.getUserId())
                .enqueue(new Callback<List<ExerciseSessionApi.RecordRow>>() {
                    @Override
                    public void onResponse(Call<List<ExerciseSessionApi.RecordRow>> call,
                                           Response<List<ExerciseSessionApi.RecordRow>> response) {
                        if (!response.isSuccessful() || response.body() == null) {
                            Toast.makeText(GoalChallengeActivity.this, R.string.error_record_load_failed,
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        List<SessionRecord> records = new ArrayList<>();
                        for (ExerciseSessionApi.RecordRow row : response.body()) {
                            records.add(row.toSessionRecord());
                        }
                        showProgress(records);
                    }

                    @Override
                    public void onFailure(Call<List<ExerciseSessionApi.RecordRow>> call, Throwable t) {
                        Toast.makeText(GoalChallengeActivity.this,
                                AuthErrorMapper.fromFailure(t, R.string.error_record_load_failed),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void showProgress(List<SessionRecord> records) {
        LocalDate today = LocalDate.now(ReportStatsCalculator.KOREA_ZONE);
        ReportStats weekly = calculator.calculate(records, ReportStatsCalculator.Period.WEEKLY, today);
        int weeklyExerciseCurrent = weekly.exerciseCount;
        int exerciseTimeCurrent = weekly.totalExerciseMinutes;
        int accuracyCurrent = (int) Math.round(weekly.averageAccuracy);
        int challengeCurrentDays = ExerciseStreak.consecutiveDays(records, today);

        // 나의 목표: "현재 n / 목표" + 진행률 %
        setText(R.id.weeklyExerciseProgressText,
                getString(R.string.goal_progress_format, weeklyExerciseCurrent, WEEKLY_EXERCISE_GOAL));
        showProgressBar(R.id.weeklyExercisePercentText, R.id.weeklyExerciseProgressBar,
                weeklyExerciseCurrent, WEEKLY_EXERCISE_GOAL);

        setText(R.id.exerciseTimeProgressText, getString(R.string.goal_time_progress_format,
                DurationFormatter.formatMinutes(this, exerciseTimeCurrent),
                DurationFormatter.formatMinutes(this, EXERCISE_TIME_GOAL_MINUTES)));
        showProgressBar(R.id.exerciseTimePercentText, R.id.exerciseTimeProgressBar,
                exerciseTimeCurrent, EXERCISE_TIME_GOAL_MINUTES);

        setText(R.id.accuracyProgressText,
                getString(R.string.goal_progress_format, accuracyCurrent, ACCURACY_GOAL));
        showProgressBar(R.id.accuracyPercentText, R.id.accuracyProgressBar, accuracyCurrent, ACCURACY_GOAL);

        // 챌린지: 연속 운동 일수 / 평균 정확도 달성 (목표 Card와 같은 진행률 표시)
        setText(R.id.sevenDayChallengeProgressText,
                getString(R.string.challenge_days_format, challengeCurrentDays, CHALLENGE_GOAL_DAYS));
        showProgressBar(R.id.sevenDayChallengePercentText, R.id.sevenDayChallengeProgressBar,
                challengeCurrentDays, CHALLENGE_GOAL_DAYS);

        setText(R.id.accuracyChallengeProgressText,
                getString(R.string.challenge_accuracy_format, accuracyCurrent, ACCURACY_GOAL));
        showProgressBar(R.id.accuracyChallengePercentText, R.id.accuracyChallengeProgressBar,
                accuracyCurrent, ACCURACY_GOAL);
    }

    // 진행률 = 현재 / 목표 (반올림, 최대 100%) → % 텍스트 + Progress Bar
    private void showProgressBar(int percentTextId, int progressBarId, int current, int goal) {
        int percent = Math.min(100, Math.round(current * 100f / goal));
        setText(percentTextId, getString(R.string.percent_format, percent));
        LinearProgressIndicator progressBar = findViewById(progressBarId);
        progressBar.setProgress(percent);
    }

    private void setText(int textViewId, String text) {
        TextView textView = findViewById(textViewId);
        textView.setText(text);
    }
}

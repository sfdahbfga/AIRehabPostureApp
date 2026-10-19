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

import com.google.android.material.progressindicator.LinearProgressIndicator;

// 목표 / 챌린지 (현재는 고정 예시 데이터만 표시, Supabase 목표 / 운동 기록 연동 전)
// TODO: 22_MyPage 구현 후 관련 메뉴에서 이 화면으로 진입 연결 (현재 진입 요소 없음)
public class GoalChallengeActivity extends AppCompatActivity {

    // TODO: Supabase 목표 데이터 / 운동 기록 연동 후 교체 (현재는 임시 예시 데이터)
    private int weeklyExerciseCurrent = 2;
    private int weeklyExerciseGoal = 3;
    private int exerciseTimeCurrent = 42;
    private int exerciseTimeGoal = 60;
    private int accuracyCurrent = 87;
    private int accuracyGoal = 90;
    private int challengeCurrentDays = 4;
    private int challengeGoalDays = 7;

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

        showGoals();
        showChallenges();
    }

    // 나의 목표: 제목 + "현재 n / 목표" + 진행률 %
    private void showGoals() {
        setText(R.id.weeklyExerciseGoalText, getString(R.string.weekly_exercise_goal, weeklyExerciseGoal));
        setText(R.id.weeklyExerciseProgressText,
                getString(R.string.goal_progress_format, weeklyExerciseCurrent, weeklyExerciseGoal));
        showProgress(R.id.weeklyExercisePercentText, R.id.weeklyExerciseProgressBar,
                weeklyExerciseCurrent, weeklyExerciseGoal);

        setText(R.id.exerciseTimeGoalText, getString(R.string.exercise_time_goal, exerciseTimeGoal));
        setText(R.id.exerciseTimeProgressText,
                getString(R.string.goal_progress_format, exerciseTimeCurrent, exerciseTimeGoal));
        showProgress(R.id.exerciseTimePercentText, R.id.exerciseTimeProgressBar,
                exerciseTimeCurrent, exerciseTimeGoal);

        setText(R.id.accuracyGoalText, getString(R.string.accuracy_goal, accuracyGoal));
        setText(R.id.accuracyProgressText,
                getString(R.string.goal_progress_format, accuracyCurrent, accuracyGoal));
        showProgress(R.id.accuracyPercentText, R.id.accuracyProgressBar, accuracyCurrent, accuracyGoal);
    }

    // 챌린지: 연속 운동 일수 / 평균 정확도 달성 (목표 Card와 같은 진행률 표시)
    private void showChallenges() {
        setText(R.id.sevenDayChallengeTitleText, getString(R.string.seven_day_challenge, challengeGoalDays));
        setText(R.id.sevenDayChallengeProgressText,
                getString(R.string.challenge_days_format, challengeCurrentDays, challengeGoalDays));
        showProgress(R.id.sevenDayChallengePercentText, R.id.sevenDayChallengeProgressBar,
                challengeCurrentDays, challengeGoalDays);

        setText(R.id.accuracyChallengeTitleText, getString(R.string.accuracy_challenge, accuracyGoal));
        setText(R.id.accuracyChallengeProgressText,
                getString(R.string.challenge_accuracy_format, accuracyCurrent, accuracyGoal));
        showProgress(R.id.accuracyChallengePercentText, R.id.accuracyChallengeProgressBar,
                accuracyCurrent, accuracyGoal);
    }

    // 진행률 = 현재 / 목표 (반올림, 최대 100%) → % 텍스트 + Progress Bar
    private void showProgress(int percentTextId, int progressBarId, int current, int goal) {
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

package com.example.airehabpostureapp;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.airehabpostureapp.network.AuthErrorMapper;
import com.example.airehabpostureapp.network.ExerciseSessionApi;
import com.example.airehabpostureapp.network.SessionManager;
import com.example.airehabpostureapp.network.SupabaseClient;
import com.example.airehabpostureapp.stats.ReportStatsCalculator;
import com.example.airehabpostureapp.workout.PendingWorkout;
import com.example.airehabpostureapp.workout.PendingWorkoutStore;
import com.example.airehabpostureapp.workout.WorkoutUploader;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HomeActivity extends AppCompatActivity {

    private TextView todayExerciseTimeText;
    private TextView todayAverageAccuracyText;
    private TextView todayCompletedExerciseText;
    private TextView recentExerciseTitleText;
    private TextView recentExerciseDateText;
    private TextView recentExerciseInfoText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 흰 배경 위에서 상태바 아이콘이 어둡게 보이도록 light 스타일 사용
        EdgeToEdge.enable(this,
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT));
        setContentView(R.layout.activity_home);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.homeRoot), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        todayExerciseTimeText = findViewById(R.id.todayExerciseTimeText);
        todayAverageAccuracyText = findViewById(R.id.todayAverageAccuracyText);
        todayCompletedExerciseText = findViewById(R.id.todayCompletedExerciseText);
        recentExerciseTitleText = findViewById(R.id.recentExerciseTitleText);
        recentExerciseDateText = findViewById(R.id.recentExerciseDateText);
        recentExerciseInfoText = findViewById(R.id.recentExerciseInfoText);

        // TODO: 알림 기능 구현
        findViewById(R.id.notificationButton).setOnClickListener(v -> showNotReady());

        // 오늘의 추천 운동 Card → 08_Recommend
        findViewById(R.id.recommendedExerciseCard).setOnClickListener(v ->
                startActivity(new Intent(this, RecommendActivity.class)));

        // 시작하기 → 추천 운동의 상세 화면 (운동 목록에서 상세로 갈 때와 같이 운동 이름을 전달)
        TextView recommendedExerciseTitleText = findViewById(R.id.recommendedExerciseTitleText);
        findViewById(R.id.startExerciseButton).setOnClickListener(v -> {
            Intent intent = new Intent(this, ExerciseDetailActivity.class);
            intent.putExtra(ExerciseDetailActivity.EXTRA_EXERCISE_NAME,
                    recommendedExerciseTitleText.getText().toString());
            startActivity(intent);
        });

        // Bottom Navigation: 현재 화면은 홈
        findViewById(R.id.bottomNavHome).setSelected(true);
        findViewById(R.id.bottomNavExercise).setOnClickListener(v ->
                startActivity(new Intent(this, ExerciseListActivity.class)));
        findViewById(R.id.bottomNavRecord).setOnClickListener(v ->
                startActivity(new Intent(this, RecordListActivity.class)));
        findViewById(R.id.bottomNavMyPage).setOnClickListener(v ->
                startActivity(new Intent(this, MyPageActivity.class)));
    }

    // 처음 열 때와, 운동을 끝내고 돌아올 때(CLEAR_TOP으로 재사용) 모두 최신 기록으로 다시 표시
    @Override
    protected void onResume() {
        super.onResume();
        loadRecords();
        checkPendingWorkouts();
    }

    // H2: 저장하지 못한 운동 기록 (이 화면이 새로 열릴 때 = 앱 실행 / 로그인 직후 한 번만 물어봄)
    private boolean pendingWorkoutAsked = false;

    // 로그인한 사용자의 임시 기록만 확인 (다른 사용자의 기록은 보여주지도 올리지도 않고 그대로 둠)
    private void checkPendingWorkouts() {
        if (pendingWorkoutAsked) {
            return;
        }
        List<PendingWorkout> pendings = PendingWorkoutStore.create(this)
                .findForUser(new SessionManager(this).getUserId());
        if (pendings.isEmpty()) {
            return;
        }
        pendingWorkoutAsked = true;
        PendingWorkout workout = pendings.get(0);
        LocalDate date = koreaDate(Instant.ofEpochMilli(workout.endedAtMillis));
        String name = workout.exerciseName == null ? getString(R.string.empty_value) : workout.exerciseName;
        int messageRes = workout.postChecked
                ? R.string.pending_workout_message : R.string.pending_workout_message_need_post_check;
        new AlertDialog.Builder(this)
                .setTitle(R.string.pending_workout_title)
                .setMessage(getString(messageRes, name, date.getMonthValue(), date.getDayOfMonth()))
                .setPositiveButton(R.string.pending_workout_save, (dialog, which) -> savePendingWorkout(workout))
                .setNegativeButton(R.string.pending_workout_discard, (dialog, which) -> discardPendingWorkout(workout))
                .setNeutralButton(R.string.pending_workout_later, null)
                .show();
    }

    // 운동 후 체크까지 끝난 기록은 바로 저장, 아니면 운동 후 체크 화면을 열어 입력 후 저장
    private void savePendingWorkout(PendingWorkout workout) {
        if (!workout.postChecked) {
            Intent intent = new Intent(this, PostCheckActivity.class);
            ExerciseFlowExtras.putPendingWorkout(intent, workout);
            startActivity(intent);
            return;
        }
        pendingUploader().upload(workout, new WorkoutUploader.Listener() {
            @Override
            public void onSaved(String sessionId) {
                Toast.makeText(HomeActivity.this, R.string.pending_workout_saved, Toast.LENGTH_SHORT).show();
                afterPendingHandled();
            }

            @Override
            public void onFailed(int messageRes) {
                Toast.makeText(HomeActivity.this, messageRes, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void discardPendingWorkout(PendingWorkout workout) {
        pendingUploader().discard(workout, new WorkoutUploader.Listener() {
            @Override
            public void onSaved(String sessionId) {
                Toast.makeText(HomeActivity.this, R.string.pending_workout_discarded, Toast.LENGTH_SHORT).show();
                afterPendingHandled();
            }

            @Override
            public void onFailed(int messageRes) {
                Toast.makeText(HomeActivity.this, messageRes, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // 기록을 다시 불러오고, 남은 임시 기록이 더 있으면 이어서 물어봄
    private void afterPendingHandled() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        pendingWorkoutAsked = false;
        loadRecords();
        checkPendingWorkouts();
    }

    private WorkoutUploader pendingUploader;

    private WorkoutUploader pendingUploader() {
        SessionManager sessionManager = new SessionManager(this);
        pendingUploader = new WorkoutUploader(SupabaseClient.getRetrofit().create(ExerciseSessionApi.class),
                PendingWorkoutStore.create(this), sessionManager.getBearerToken());
        return pendingUploader;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (pendingUploader != null) {
            pendingUploader.cancel();
        }
    }

    // 내 기록 전체(최신순)를 불러와 오늘의 기록과 최근 운동을 계산
    private void loadRecords() {
        SessionManager sessionManager = new SessionManager(this);
        ExerciseSessionApi api = SupabaseClient.getRetrofit().create(ExerciseSessionApi.class);
        api.getRecords(sessionManager.getBearerToken(), "eq." + sessionManager.getUserId())
                .enqueue(new Callback<List<ExerciseSessionApi.RecordRow>>() {
                    @Override
                    public void onResponse(Call<List<ExerciseSessionApi.RecordRow>> call,
                                           Response<List<ExerciseSessionApi.RecordRow>> response) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        if (!response.isSuccessful() || response.body() == null) {
                            Toast.makeText(HomeActivity.this, R.string.error_record_load_failed,
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        showTodayRecord(response.body());
                        showRecentExercise(response.body());
                    }

                    @Override
                    public void onFailure(Call<List<ExerciseSessionApi.RecordRow>> call, Throwable t) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        Toast.makeText(HomeActivity.this,
                                AuthErrorMapper.fromFailure(t, R.string.error_record_load_failed),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // 오늘(한국 시간) 기록의 운동 시간 합계 / 평균 정확도 / 완료 운동 수 (없으면 0분, -, 0개)
    // 평균 정확도는 주간 리포트(ReportStatsCalculator)와 같이 기록별 정확도의 평균 (값 없는 기록은 제외)
    private void showTodayRecord(List<ExerciseSessionApi.RecordRow> records) {
        LocalDate today = LocalDate.now(ReportStatsCalculator.KOREA_ZONE);
        int totalSeconds = 0;
        int count = 0;
        double accuracySum = 0;
        int accuracyCount = 0;
        for (ExerciseSessionApi.RecordRow record : records) {
            if (!today.equals(koreaDate(record.startedAtInstant()))) {
                continue;
            }
            count++;
            if (record.durationSeconds != null) {
                totalSeconds += record.durationSeconds;
            }
            if (record.averageAccuracy != null) {
                accuracySum += record.averageAccuracy;
                accuracyCount++;
            }
        }

        todayExerciseTimeText.setText(DurationFormatter.formatSecondsAsMinutes(this, totalSeconds));
        todayAverageAccuracyText.setText(accuracyCount == 0 ? getString(R.string.empty_value)
                : getString(R.string.percent_format, (int) Math.round(accuracySum / accuracyCount)));
        todayCompletedExerciseText.setText(getString(R.string.home_completed_count_format, count));
    }

    // 가장 최근 기록 1개 (목록은 started_at 최신순). 없으면 안내 문구만 표시
    private void showRecentExercise(List<ExerciseSessionApi.RecordRow> records) {
        if (records.isEmpty()) {
            recentExerciseTitleText.setText(R.string.home_recent_exercise_empty);
            recentExerciseDateText.setVisibility(View.GONE);
            recentExerciseInfoText.setVisibility(View.GONE);
            return;
        }
        ExerciseSessionApi.RecordRow recent = records.get(0);
        LocalDate date = koreaDate(recent.startedAtInstant());
        int durationSeconds = recent.durationSeconds == null ? 0 : recent.durationSeconds;
        String duration = DurationFormatter.formatSecondsAsMinutes(this, durationSeconds);

        recentExerciseTitleText.setText(recent.exerciseName);
        recentExerciseDateText.setText(date == null ? getString(R.string.empty_value)
                : getString(R.string.record_date_format, date.getMonthValue(), date.getDayOfMonth()));
        // 정확도가 없는 기록(자세 인식 미지원 운동 / 0회)은 "정확도 -"
        recentExerciseInfoText.setText(recent.averageAccuracy == null
                ? getString(R.string.record_summary_time_no_accuracy_format, duration)
                : getString(R.string.record_summary_time_format, duration,
                (int) Math.round(recent.averageAccuracy)));
        recentExerciseDateText.setVisibility(View.VISIBLE);
        recentExerciseInfoText.setVisibility(View.VISIBLE);
    }

    private static LocalDate koreaDate(Instant instant) {
        return instant == null ? null : instant.atZone(ReportStatsCalculator.KOREA_ZONE).toLocalDate();
    }

    private void showNotReady() {
        Toast.makeText(this, R.string.feature_not_ready, Toast.LENGTH_SHORT).show();
    }
}

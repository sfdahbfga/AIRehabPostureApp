package com.example.airehabpostureapp;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.progressindicator.LinearProgressIndicator;

// 현재는 UI와 화면 흐름만 구현 (CameraX / MediaPipe / 관절 각도 계산 연동 전)
// 향후 흐름: CameraX → MediaPipe Landmark → Java 관절 각도 계산 → 기준 범위 비교 → 피드백 / 반복 횟수
public class ExerciseActivity extends AppCompatActivity {

    private static final long TIMER_INTERVAL_MS = 1000L;

    // TODO: MediaPipe + 관절 각도 계산 연동 후 실제 값으로 교체 (현재는 임시 예시 데이터)
    private int currentReps = 8;
    private int targetReps = 20;
    private int currentAccuracy = 86;

    // 운동 진행 시간 (초), 화면이 보이는 동안만 1초씩 증가
    private int elapsedSeconds = 0;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private TextView exerciseTimerText;
    private TextView exerciseNameText;

    // 14_Pause 결과: "처음부터 다시"는 운동 상태 초기화, "운동 종료"는 PostCheck로 이동
    // (계속하기 / Back은 그대로 이어서 진행)
    private final ActivityResultLauncher<Intent> pauseLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == PauseActivity.RESULT_RESTART) {
                    restartExercise();
                } else if (result.getResultCode() == PauseActivity.RESULT_END_EXERCISE) {
                    endExercise();
                }
            });

    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            elapsedSeconds++;
            updateTimerText();
            timerHandler.postDelayed(this, TIMER_INTERVAL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 흰 배경 위에서 상태바 아이콘이 어둡게 보이도록 light 스타일 사용
        EdgeToEdge.enable(this,
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT));
        setContentView(R.layout.activity_exercise);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.exerciseRoot), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 운동 중 화면 꺼짐 방지
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        exerciseTimerText = findViewById(R.id.exerciseTimerText);
        exerciseNameText = findViewById(R.id.exerciseNameText);
        updateTimerText();
        updateExerciseStatus();

        // 이전 화면(CameraSetup)으로 돌아가기: 새 Activity를 만들지 않고 현재 화면만 종료
        findViewById(R.id.backButton).setOnClickListener(v -> finish());

        // 일시정지 화면(14_Pause)으로 이동: 이 화면은 그대로 두고 위에 열리므로 복귀 시 중복 생성되지 않음
        // (Pause 화면이 보이는 동안 onPause()에서 Timer가 멈추고, 복귀하면 onResume()에서 다시 시작)
        findViewById(R.id.pauseButton).setOnClickListener(v -> {
            Intent intent = new Intent(this, PauseActivity.class);
            intent.putExtra(PauseActivity.EXTRA_EXERCISE_NAME, exerciseNameText.getText().toString());
            intent.putExtra(PauseActivity.EXTRA_CURRENT_REPS, currentReps);
            intent.putExtra(PauseActivity.EXTRA_TARGET_REPS, targetReps);
            intent.putExtra(PauseActivity.EXTRA_ELAPSED_SECONDS, elapsedSeconds);
            pauseLauncher.launch(intent);
        });

        findViewById(R.id.endExerciseButton).setOnClickListener(v -> endExercise());
    }

    @Override
    protected void onResume() {
        super.onResume();
        timerHandler.postDelayed(timerRunnable, TIMER_INTERVAL_MS);
    }

    @Override
    protected void onPause() {
        super.onPause();
        timerHandler.removeCallbacks(timerRunnable);
    }

    // 운동 후 컨디션 체크(15_PostCheck)로 이동하고 운동 화면은 종료 (Back Stack에 남기지 않음)
    // TODO: 운동 기록(시간 / 횟수 / 정확도) 저장 및 16_Result 전달
    private void endExercise() {
        startActivity(new Intent(this, PostCheckActivity.class));
        finish();
    }

    // 반복 횟수 / 진행률 / 운동 시간을 0으로 초기화 (Timer는 화면 복귀 시 onResume()에서 다시 시작)
    private void restartExercise() {
        currentReps = 0;
        elapsedSeconds = 0;
        updateTimerText();
        updateExerciseStatus();
    }

    private void updateTimerText() {
        exerciseTimerText.setText(getString(R.string.exercise_timer_format,
                elapsedSeconds / 60, elapsedSeconds % 60));
    }

    // 반복 횟수 / 정확도 / 진행률 표시 (향후 자세 판정 결과가 바뀔 때마다 호출)
    private void updateExerciseStatus() {
        TextView repetitionCountText = findViewById(R.id.repetitionCountText);
        TextView accuracyText = findViewById(R.id.accuracyText);
        TextView exerciseProgressPercentText = findViewById(R.id.exerciseProgressPercentText);
        LinearProgressIndicator exerciseProgressBar = findViewById(R.id.exerciseProgressBar);

        int progressPercent = targetReps > 0 ? currentReps * 100 / targetReps : 0;

        repetitionCountText.setText(getString(R.string.repetition_count_format, currentReps, targetReps));
        accuracyText.setText(getString(R.string.percent_format, currentAccuracy));
        exerciseProgressPercentText.setText(getString(R.string.percent_format, progressPercent));
        exerciseProgressBar.setProgress(progressPercent);
    }
}

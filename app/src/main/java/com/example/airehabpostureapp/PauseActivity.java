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

// 13_Exercise 위에 열리는 일시정지 화면 (finish() 하면 기존 Exercise 화면으로 복귀)
public class PauseActivity extends AppCompatActivity {

    // 13_Exercise에서 전달하는 현재 운동 정보
    public static final String EXTRA_EXERCISE_NAME = "com.example.airehabpostureapp.EXTRA_PAUSE_EXERCISE_NAME";
    public static final String EXTRA_CURRENT_REPS = "com.example.airehabpostureapp.EXTRA_CURRENT_REPS";
    public static final String EXTRA_TARGET_REPS = "com.example.airehabpostureapp.EXTRA_TARGET_REPS";
    public static final String EXTRA_ELAPSED_SECONDS = "com.example.airehabpostureapp.EXTRA_ELAPSED_SECONDS";

    // Exercise 화면에 돌려주는 결과 코드: "처음부터 다시" / "운동 종료"
    public static final int RESULT_RESTART = RESULT_FIRST_USER;
    public static final int RESULT_END_EXERCISE = RESULT_FIRST_USER + 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 흰 배경 위에서 상태바 아이콘이 어둡게 보이도록 light 스타일 사용
        EdgeToEdge.enable(this,
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT));
        setContentView(R.layout.activity_pause);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.pauseRoot), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        showCurrentExerciseInfo();

        // 뒤로가기 / 계속하기: 현재 화면만 종료하여 기존 Exercise 화면으로 복귀 (시스템 Back도 동일)
        findViewById(R.id.backButton).setOnClickListener(v -> finish());
        findViewById(R.id.resumeButton).setOnClickListener(v -> finish());

        // 처음부터 다시: Exercise 화면에 결과 코드를 전달하여 반복 횟수 / 진행률 / 시간 초기화
        findViewById(R.id.restartButton).setOnClickListener(v -> {
            Toast.makeText(this, R.string.restart_exercise_message, Toast.LENGTH_SHORT).show();
            setResult(RESULT_RESTART);
            finish();
        });

        // 운동 종료: Exercise 화면에 결과 코드를 전달 → Exercise가 15_PostCheck로 이동 후 함께 종료
        // (Pause / Exercise가 Back Stack에 남지 않도록 처리)
        findViewById(R.id.endExerciseButton).setOnClickListener(v -> {
            setResult(RESULT_END_EXERCISE);
            finish();
        });
    }

    // 전달받은 값이 있으면 표시 (운동 이름이 없으면 레이아웃 기본값 유지)
    private void showCurrentExerciseInfo() {
        String exerciseName = getIntent().getStringExtra(EXTRA_EXERCISE_NAME);
        int currentReps = getIntent().getIntExtra(EXTRA_CURRENT_REPS, 0);
        int targetReps = getIntent().getIntExtra(EXTRA_TARGET_REPS, 0);
        int elapsedSeconds = getIntent().getIntExtra(EXTRA_ELAPSED_SECONDS, 0);

        if (exerciseName != null) {
            TextView currentExerciseNameText = findViewById(R.id.currentExerciseNameText);
            currentExerciseNameText.setText(exerciseName);
        }
        TextView currentRepetitionText = findViewById(R.id.currentRepetitionText);
        currentRepetitionText.setText(getString(R.string.repetition_count_format, currentReps, targetReps));
        TextView currentExerciseTimeText = findViewById(R.id.currentExerciseTimeText);
        currentExerciseTimeText.setText(getString(R.string.exercise_timer_format,
                elapsedSeconds / 60, elapsedSeconds % 60));
    }
}

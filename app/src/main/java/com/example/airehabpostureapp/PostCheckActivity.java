package com.example.airehabpostureapp;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.SystemBarStyle;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.airehabpostureapp.network.ExerciseSessionApi;
import com.example.airehabpostureapp.network.SessionManager;
import com.example.airehabpostureapp.network.SupabaseClient;
import com.example.airehabpostureapp.workout.PendingWorkout;
import com.example.airehabpostureapp.workout.PendingWorkoutStore;
import com.example.airehabpostureapp.workout.WorkoutUploader;


// 운동 후 컨디션 체크 (11_PreCheck와 같은 구조, 문구만 "운동 후"로 변경)
public class PostCheckActivity extends AppCompatActivity {

    private static final int NO_SELECTION = -1;

    // 선택된 불편감 정도 (0~10 / 선택 전에는 NO_SELECTION, 기본 선택 없음)
    private int selectedDiscomfortLevel = NO_SELECTION;
    // 선택된 몸 상태 키 (레이아웃의 android:tag 값: good, normal, bad / 선택 전에는 null)
    private String selectedBodyCondition = null;
    // 인덱스 = 불편감 숫자 (0~10)
    private View[] discomfortButtons;
    private View[] conditionButtons;
    private EditText noteEditText;
    private View nextButton;
    private SessionManager sessionManager;
    // 운동이 끝날 때 휴대폰에 임시 저장한 결과 (H2)
    private PendingWorkoutStore pendingStore;
    private PendingWorkout pendingWorkout;
    private WorkoutUploader uploader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 흰 배경 위에서 상태바 아이콘이 어둡게 보이도록 light 스타일 사용
        EdgeToEdge.enable(this,
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT));
        setContentView(R.layout.activity_post_check);
        // 키보드가 올라오면 그 높이만큼 하단 여백을 줘서 특이사항 입력창이 가려지지 않도록 처리
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.postCheckRoot), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right,
                    Math.max(systemBars.bottom, ime.bottom));
            return insets;
        });

        noteEditText = findViewById(R.id.noteEditText);

        // 뒤로가기 (버튼 / 시스템 Back): 끝난 운동 흐름(CameraSetup 등)으로 돌아가지 않고 기존 Home으로 복귀
        // (16_Result의 시스템 Back과 같은 방식)
        // H3: 저장하지 않고 나가면 측정 결과가 버려지므로 먼저 확인
        findViewById(R.id.backButton).setOnClickListener(v -> confirmExitWithoutSaving());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                confirmExitWithoutSaving();
            }
        });

        // 불편감: 0~10 단일 선택 (05_BodyCondition, 11_PreCheck와 같은 방식)
        discomfortButtons = new View[]{
                findViewById(R.id.discomfortButton0), findViewById(R.id.discomfortButton1),
                findViewById(R.id.discomfortButton2), findViewById(R.id.discomfortButton3),
                findViewById(R.id.discomfortButton4), findViewById(R.id.discomfortButton5),
                findViewById(R.id.discomfortButton6), findViewById(R.id.discomfortButton7),
                findViewById(R.id.discomfortButton8), findViewById(R.id.discomfortButton9),
                findViewById(R.id.discomfortButton10)
        };
        for (int level = 0; level < discomfortButtons.length; level++) {
            final int selectedLevel = level;
            discomfortButtons[level].setOnClickListener(v -> selectDiscomfortLevel(selectedLevel));
        }
        // 기본 선택 없음 (선택하지 않으면 "다음"에서 안내)

        // 몸 상태: 단일 선택 (기본 선택 없음)
        conditionButtons = new View[]{
                findViewById(R.id.conditionGoodButton),
                findViewById(R.id.conditionNormalButton),
                findViewById(R.id.conditionBadButton)
        };
        for (View button : conditionButtons) {
            button.setOnClickListener(this::selectBodyCondition);
        }
        if (savedInstanceState != null) {
            restoreSelections(savedInstanceState);
        }

        sessionManager = new SessionManager(this);
        pendingStore = PendingWorkoutStore.create(this);
        pendingWorkout = loadPendingWorkout();
        nextButton = findViewById(R.id.nextButton);
        nextButton.setOnClickListener(v -> {
            if (validateInputs()) {
                saveExerciseRecord();
            }
        });
    }

    // 저장 순서: exercise_sessions → session_checkins(pre, post) → 16_Result 이동 (WorkoutUploader)
    // 측정 결과는 운동이 끝날 때 휴대폰에 임시 저장돼 있고, 서버 저장에 성공하면 지워진다. (H2)
    // 실패하면 버튼을 다시 눌러 재시도할 수 있고, 화면을 나가도 다음 앱 실행 때 홈에서 저장을 물어본다.
    private void saveExerciseRecord() {
        // 저장 중 중복 클릭 방지
        nextButton.setEnabled(false);
        pendingWorkout.setPostCheck(selectedDiscomfortLevel, selectedBodyCondition,
                noteEditText.getText().toString());
        pendingStore.save(pendingWorkout);
        uploader().upload(pendingWorkout, new WorkoutUploader.Listener() {
            @Override
            public void onSaved(String sessionId) {
                // 운동 결과(16_Result)로 이동하고 이 화면은 Back Stack에서 제거
                Intent intent = new Intent(PostCheckActivity.this, ResultActivity.class);
                intent.putExtra(ResultActivity.EXTRA_SESSION_ID, sessionId);
                startActivity(intent);
                finish();
            }

            @Override
            public void onFailed(int messageRes) {
                showError(messageRes);
            }
        });
    }

    // 이 화면에서 쓸 임시 저장 기록: 운동 화면이 저장한 기록을 찾고, 없으면(이전 버전 흐름 등) Intent 값으로 새로 만든다.
    private PendingWorkout loadPendingWorkout() {
        String userId = sessionManager.getUserId();
        PendingWorkout workout = pendingStore.get(userId,
                getIntent().getStringExtra(ExerciseFlowExtras.PENDING_SESSION_ID));
        if (workout == null) {
            workout = ExerciseFlowExtras.toPendingWorkout(userId, getIntent());
            pendingStore.save(workout);
        }
        return workout;
    }

    private WorkoutUploader uploader() {
        if (uploader == null) {
            uploader = new WorkoutUploader(SupabaseClient.getRetrofit().create(ExerciseSessionApi.class),
                    pendingStore, sessionManager.getBearerToken());
        }
        return uploader;
    }

    // 사용자가 저장하지 않고 나가기로 한 경우: 임시 기록 삭제 (서버에 반쪽 기록이 있을 수 있으면 서버에서도 삭제)
    // 서버 삭제에 실패하면 임시 기록이 남아 다음에 홈에서 다시 물어본다.
    private void discardAndMoveToHome() {
        uploader().discard(pendingWorkout, new WorkoutUploader.Listener() {
            @Override
            public void onSaved(String sessionId) {
                moveToHome();
            }

            @Override
            public void onFailed(int messageRes) {
                moveToHome();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (uploader != null) {
            uploader.cancel();
        }
    }

    private void showError(@StringRes int messageRes) {
        nextButton.setEnabled(true);
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show();
    }

    // L3: 화면 재생성(회전 / 시스템 종료 후 복구) 때 선택값 유지
    private static final String STATE_DISCOMFORT_LEVEL = "state_discomfort_level";
    private static final String STATE_BODY_CONDITION = "state_body_condition";

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_DISCOMFORT_LEVEL, selectedDiscomfortLevel);
        outState.putString(STATE_BODY_CONDITION, selectedBodyCondition);
    }

    private void restoreSelections(Bundle savedInstanceState) {
        int level = savedInstanceState.getInt(STATE_DISCOMFORT_LEVEL, NO_SELECTION);
        if (level >= 0 && level < discomfortButtons.length) {
            selectDiscomfortLevel(level);
        }
        String condition = savedInstanceState.getString(STATE_BODY_CONDITION);
        for (View button : conditionButtons) {
            if (button.getTag() != null && button.getTag().equals(condition)) {
                selectBodyCondition(button);
            }
        }
    }

    // 선택한 숫자 버튼만 selected, 나머지는 해제
    private void selectDiscomfortLevel(int level) {
        for (int i = 0; i < discomfortButtons.length; i++) {
            discomfortButtons[i].setSelected(i == level);
        }
        selectedDiscomfortLevel = level;
    }

    // 누른 몸 상태 버튼만 selected, 나머지는 해제
    private void selectBodyCondition(View clickedButton) {
        for (View button : conditionButtons) {
            button.setSelected(button == clickedButton);
        }
        selectedBodyCondition = (String) clickedButton.getTag();
    }

    private void confirmExitWithoutSaving() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.exit_without_saving_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.exit_without_saving_confirm, (dialog, which) -> discardAndMoveToHome())
                .show();
    }

    // CLEAR_TOP + SINGLE_TOP: Back Stack에 있는 Home을 재사용하고 그 위의 화면은 모두 종료
    private void moveToHome() {
        Intent intent = new Intent(this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    // 필수: 불편감, 몸 상태 / 선택: 특이사항(noteEditText)은 검사하지 않음
    private boolean validateInputs() {
        if (selectedDiscomfortLevel == NO_SELECTION) {
            Toast.makeText(this, R.string.error_exercise_discomfort_not_selected, Toast.LENGTH_SHORT).show();
            return false;
        }
        if (selectedBodyCondition == null) {
            Toast.makeText(this, R.string.error_post_condition_not_selected, Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }
}

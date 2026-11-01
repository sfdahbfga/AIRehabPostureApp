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
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.airehabpostureapp.network.AuthErrorMapper;
import com.example.airehabpostureapp.network.BodyConditionApi;
import com.example.airehabpostureapp.network.ExerciseSessionApi;
import com.example.airehabpostureapp.network.SessionManager;
import com.example.airehabpostureapp.network.SupabaseClient;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

// 운동 후 컨디션 체크 (11_PreCheck와 같은 구조, 문구만 "운동 후"로 변경)
public class PostCheckActivity extends AppCompatActivity {

    private static final int DEFAULT_DISCOMFORT_LEVEL = 3;
    private static final int NO_SELECTION = -1;

    // TODO: MediaPipe 연동 후 실제 값으로 교체 (전반부 / 후반부 정확도, 관절별 오류 횟수 임시 값)
    private static final int TEMP_FIRST_HALF_ACCURACY = 91;
    private static final int TEMP_SECOND_HALF_ACCURACY = 78;
    private static final int TEMP_KNEE_ERRORS = 12;
    private static final int TEMP_BACK_ERRORS = 3;

    // 선택된 불편감 정도 (0~10)
    private int selectedDiscomfortLevel = NO_SELECTION;
    // 선택된 몸 상태 키 (레이아웃의 android:tag 값: good, normal, bad / 선택 전에는 null)
    private String selectedBodyCondition = null;
    // 인덱스 = 불편감 숫자 (0~10)
    private View[] discomfortButtons;
    private View[] conditionButtons;
    private EditText noteEditText;
    private View nextButton;
    private SessionManager sessionManager;
    // exercise_sessions 저장에 성공한 id. checkins 저장만 실패했을 때 재시도하면 세션을 중복 저장하지 않고 이 id를 사용
    private String savedSessionId = null;

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
        findViewById(R.id.backButton).setOnClickListener(v -> moveToHome());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                moveToHome();
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
        selectDiscomfortLevel(DEFAULT_DISCOMFORT_LEVEL);

        // 몸 상태: 단일 선택 (기본 선택 없음)
        conditionButtons = new View[]{
                findViewById(R.id.conditionGoodButton),
                findViewById(R.id.conditionNormalButton),
                findViewById(R.id.conditionBadButton)
        };
        for (View button : conditionButtons) {
            button.setOnClickListener(this::selectBodyCondition);
        }

        sessionManager = new SessionManager(this);
        nextButton = findViewById(R.id.nextButton);
        nextButton.setOnClickListener(v -> {
            if (validateInputs()) {
                saveExerciseRecord();
            }
        });
    }

    // 저장 순서: exercise_sessions insert → 받은 id로 session_checkins(pre, post) insert → 16_Result 이동
    private void saveExerciseRecord() {
        // 저장 중 중복 클릭 방지
        nextButton.setEnabled(false);
        if (savedSessionId != null) {
            saveCheckins(savedSessionId);
            return;
        }

        Intent extras = getIntent();
        Map<String, Integer> jointErrors = new LinkedHashMap<>();
        jointErrors.put("knee", TEMP_KNEE_ERRORS);
        jointErrors.put("back", TEMP_BACK_ERRORS);
        ExerciseSessionApi.SessionInsert session = new ExerciseSessionApi.SessionInsert(
                sessionManager.getUserId(),
                extras.getStringExtra(ExerciseFlowExtras.EXERCISE_NAME),
                toIsoString(extras.getLongExtra(ExerciseFlowExtras.STARTED_AT_MILLIS, System.currentTimeMillis())),
                toIsoString(extras.getLongExtra(ExerciseFlowExtras.ENDED_AT_MILLIS, System.currentTimeMillis())),
                extras.getIntExtra(ExerciseFlowExtras.DURATION_SECONDS, 0),
                extras.getIntExtra(ExerciseFlowExtras.COMPLETED_REPS, 0),
                extras.getIntExtra(ExerciseFlowExtras.TARGET_REPS, 0),
                extras.getIntExtra(ExerciseFlowExtras.AVERAGE_ACCURACY, 0),
                TEMP_FIRST_HALF_ACCURACY,
                TEMP_SECOND_HALF_ACCURACY,
                jointErrors);

        ExerciseSessionApi api = SupabaseClient.getRetrofit().create(ExerciseSessionApi.class);
        api.insertSession(sessionManager.getBearerToken(), session).enqueue(new Callback<List<BodyConditionApi.IdRow>>() {
            @Override
            public void onResponse(Call<List<BodyConditionApi.IdRow>> call,
                                   Response<List<BodyConditionApi.IdRow>> response) {
                List<BodyConditionApi.IdRow> rows = response.body();
                if (!response.isSuccessful() || rows == null || rows.isEmpty()) {
                    showError(R.string.error_exercise_save_failed);
                    return;
                }
                savedSessionId = rows.get(0).id;
                saveCheckins(savedSessionId);
            }

            @Override
            public void onFailure(Call<List<BodyConditionApi.IdRow>> call, Throwable t) {
                showError(AuthErrorMapper.fromFailure(t, R.string.error_exercise_save_failed));
            }
        });
    }

    private void saveCheckins(String sessionId) {
        Intent extras = getIntent();
        String preNote = extras.getStringExtra(ExerciseFlowExtras.PRE_NOTE);
        String postNote = noteEditText.getText().toString().trim();
        List<ExerciseSessionApi.CheckinInsert> checkins = Arrays.asList(
                new ExerciseSessionApi.CheckinInsert(sessionId, "pre",
                        extras.getIntExtra(ExerciseFlowExtras.PRE_DISCOMFORT_LEVEL, 0),
                        extras.getStringExtra(ExerciseFlowExtras.PRE_BODY_CONDITION),
                        emptyToNull(preNote)),
                new ExerciseSessionApi.CheckinInsert(sessionId, "post",
                        selectedDiscomfortLevel, selectedBodyCondition, emptyToNull(postNote)));

        ExerciseSessionApi api = SupabaseClient.getRetrofit().create(ExerciseSessionApi.class);
        api.insertCheckins(sessionManager.getBearerToken(), checkins).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (!response.isSuccessful()) {
                    showError(R.string.error_exercise_save_failed);
                    return;
                }
                // 운동 결과(16_Result)로 이동하고 이 화면은 Back Stack에서 제거
                Intent intent = new Intent(PostCheckActivity.this, ResultActivity.class);
                intent.putExtra(ResultActivity.EXTRA_SESSION_ID, sessionId);
                startActivity(intent);
                finish();
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                showError(AuthErrorMapper.fromFailure(t, R.string.error_exercise_save_failed));
            }
        });
    }

    private void showError(@StringRes int messageRes) {
        nextButton.setEnabled(true);
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show();
    }

    private static String toIsoString(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).toString();
    }

    private static String emptyToNull(String text) {
        return text == null || text.isEmpty() ? null : text;
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
            Toast.makeText(this, R.string.error_discomfort_not_selected, Toast.LENGTH_SHORT).show();
            return false;
        }
        if (selectedBodyCondition == null) {
            Toast.makeText(this, R.string.error_post_condition_not_selected, Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }
}

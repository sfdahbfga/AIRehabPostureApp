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
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

// 운동 후 컨디션 체크 (11_PreCheck와 같은 구조, 문구만 "운동 후"로 변경)
public class PostCheckActivity extends AppCompatActivity {

    private static final int DEFAULT_DISCOMFORT_LEVEL = 3;
    private static final int NO_SELECTION = -1;

    // 선택된 불편감 정도 (0~10)
    private int selectedDiscomfortLevel = NO_SELECTION;
    // 선택된 몸 상태 키 (레이아웃의 android:tag 값: good, normal, bad / 선택 전에는 null)
    private String selectedBodyCondition = null;
    // 인덱스 = 불편감 숫자 (0~10)
    private View[] discomfortButtons;
    private View[] conditionButtons;
    private EditText noteEditText;

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

        findViewById(R.id.nextButton).setOnClickListener(v -> {
            if (validateInputs()) {
                // TODO: 불편감 / 몸 상태 / 특이사항 저장 (Supabase 연동 후)
                // 운동 결과(16_Result)로 이동하고 이 화면은 Back Stack에서 제거
                startActivity(new Intent(this, ResultActivity.class));
                finish();
            }
        });
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

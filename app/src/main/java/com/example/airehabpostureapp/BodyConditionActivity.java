package com.example.airehabpostureapp;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.LinkedHashSet;
import java.util.Set;

public class BodyConditionActivity extends AppCompatActivity {

    private static final int DEFAULT_DISCOMFORT_LEVEL = 5;
    private static final int NO_SELECTION = -1;

    // 선택된 신체 부위 키 (레이아웃의 android:tag 값: neck, shoulder, ...)
    private final Set<String> selectedBodyParts = new LinkedHashSet<>();
    // 선택된 불편감 정도 (0~10)
    private int selectedDiscomfortLevel = NO_SELECTION;
    // 인덱스 = 불편감 숫자 (0~10)
    private View[] discomfortButtons;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 흰 배경 위에서 상태바 아이콘이 어둡게 보이도록 light 스타일 사용
        EdgeToEdge.enable(this,
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT));
        setContentView(R.layout.activity_body_condition);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.bodyConditionRoot), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 이전 화면(UserSetup)으로 돌아가기: 새 Activity를 만들지 않고 현재 화면만 종료
        findViewById(R.id.backButton).setOnClickListener(v -> finish());

        // 신체 부위: 복수 선택 (누를 때마다 선택/해제)
        int[] bodyPartButtonIds = {
                R.id.neckButton, R.id.shoulderButton, R.id.elbowButton,
                R.id.waistButton, R.id.kneeButton, R.id.ankleButton
        };
        for (int id : bodyPartButtonIds) {
            findViewById(id).setOnClickListener(this::toggleBodyPart);
        }

        // 불편감: 0~10 단일 선택
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

        findViewById(R.id.nextButton).setOnClickListener(v -> {
            if (validateInputs()) {
                // TODO: 입력값 저장(Supabase)
                startActivity(new Intent(this, GoalSetupActivity.class));
            }
        });
    }

    private void toggleBodyPart(View button) {
        String bodyPart = (String) button.getTag();
        boolean isSelected = !button.isSelected();
        button.setSelected(isSelected);
        if (isSelected) {
            selectedBodyParts.add(bodyPart);
        } else {
            selectedBodyParts.remove(bodyPart);
        }
    }

    // 선택한 숫자 버튼만 selected, 나머지는 해제
    private void selectDiscomfortLevel(int level) {
        for (int i = 0; i < discomfortButtons.length; i++) {
            discomfortButtons[i].setSelected(i == level);
        }
        selectedDiscomfortLevel = level;
    }

    private boolean validateInputs() {
        if (selectedBodyParts.isEmpty()) {
            Toast.makeText(this, R.string.error_body_part_not_selected, Toast.LENGTH_SHORT).show();
            return false;
        }
        if (selectedDiscomfortLevel == NO_SELECTION) {
            Toast.makeText(this, R.string.error_discomfort_not_selected, Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }
}

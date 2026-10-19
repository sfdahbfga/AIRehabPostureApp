package com.example.airehabpostureapp;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.LinkedHashSet;
import java.util.Set;

public class GoalSetupActivity extends AppCompatActivity {

    // 선택된 운동 목표 키 (레이아웃의 android:tag 값: pain_relief, strength, ...)
    private final Set<String> selectedGoals = new LinkedHashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 흰 배경 위에서 상태바 아이콘이 어둡게 보이도록 light 스타일 사용
        EdgeToEdge.enable(this,
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT));
        setContentView(R.layout.activity_goal_setup);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.goalSetupRoot), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 이전 화면(BodyCondition)으로 돌아가기: 새 Activity를 만들지 않고 현재 화면만 종료
        findViewById(R.id.backButton).setOnClickListener(v -> finish());

        // 운동 목표: 복수 선택 (누를 때마다 선택/해제)
        int[] goalCardIds = {
                R.id.painReliefCard, R.id.rangeOfMotionCard, R.id.strengthCard,
                R.id.flexibilityCard, R.id.balanceCard, R.id.consistencyCard
        };
        for (int id : goalCardIds) {
            findViewById(id).setOnClickListener(this::toggleGoal);
        }

        findViewById(R.id.completeButton).setOnClickListener(v -> {
            if (selectedGoals.isEmpty()) {
                Toast.makeText(this, R.string.error_goal_not_selected, Toast.LENGTH_SHORT).show();
                return;
            }
            // TODO: 입력값 저장(Supabase)
            // 초기 설정 화면들(Login~GoalSetup)을 모두 정리하고 Home을 새 시작 화면으로 사용
            Intent intent = new Intent(this, HomeActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });
    }

    // 선택 시 배경/글자색/우측 체크는 selector가 처리하고, 글자 굵기만 Java에서 변경
    private void toggleGoal(View card) {
        String goal = (String) card.getTag();
        boolean isSelected = !card.isSelected();
        card.setSelected(isSelected);
        ((TextView) card).setTypeface(null, isSelected ? Typeface.BOLD : Typeface.NORMAL);
        if (isSelected) {
            selectedGoals.add(goal);
        } else {
            selectedGoals.remove(goal);
        }
    }
}

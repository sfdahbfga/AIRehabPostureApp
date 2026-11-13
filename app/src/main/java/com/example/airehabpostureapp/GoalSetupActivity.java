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
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.airehabpostureapp.network.ApiCalls;
import com.example.airehabpostureapp.network.AuthErrorMapper;
import com.example.airehabpostureapp.network.GoalApi;
import com.example.airehabpostureapp.network.SessionManager;
import com.example.airehabpostureapp.network.SupabaseClient;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class GoalSetupActivity extends AppCompatActivity {

    // L7: 화면이 닫힐 때 진행 중인 요청 취소
    private final ApiCalls apiCalls = ApiCalls.bind(this);

    // 선택된 운동 목표 키 (레이아웃의 android:tag 값: pain_relief, strength, ...)
    private final Set<String> selectedGoals = new LinkedHashSet<>();
    private View completeButton;
    private SessionManager sessionManager;

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

        sessionManager = new SessionManager(this);
        completeButton = findViewById(R.id.completeButton);
        completeButton.setOnClickListener(v -> {
            if (selectedGoals.isEmpty()) {
                Toast.makeText(this, R.string.error_goal_not_selected, Toast.LENGTH_SHORT).show();
                return;
            }
            saveGoals();
        });
    }

    // 기존 목표를 모두 지우고 이번 선택만 저장 → Home 이동
    private void saveGoals() {
        String bearerToken = sessionManager.getBearerToken();
        String userId = sessionManager.getUserId();
        List<GoalApi.GoalInsert> rows = new ArrayList<>();
        for (String goal : selectedGoals) {
            rows.add(new GoalApi.GoalInsert(userId, goal));
        }

        // 저장 중 중복 클릭 방지
        completeButton.setEnabled(false);
        GoalApi goalApi = SupabaseClient.getRetrofit().create(GoalApi.class);
        apiCalls.track(goalApi.deleteAll(bearerToken, "eq." + userId)).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (!response.isSuccessful()) {
                    showError(R.string.error_goal_save_failed);
                    return;
                }
                apiCalls.track(goalApi.insert(bearerToken, rows)).enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        if (!response.isSuccessful()) {
                            showError(R.string.error_goal_save_failed);
                            return;
                        }
                        // 초기 설정 화면들(Login~GoalSetup)을 모두 정리하고 Home을 새 시작 화면으로 사용
                        Intent intent = new Intent(GoalSetupActivity.this, HomeActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                    }

                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        showError(AuthErrorMapper.fromFailure(t, R.string.error_goal_save_failed));
                    }
                });
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                showError(AuthErrorMapper.fromFailure(t, R.string.error_goal_save_failed));
            }
        });
    }

    private void showError(@StringRes int messageRes) {
        completeButton.setEnabled(true);
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show();
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

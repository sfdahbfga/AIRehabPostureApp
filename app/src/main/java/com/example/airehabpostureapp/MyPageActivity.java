package com.example.airehabpostureapp;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

// 마이페이지 (프로필은 임시 예시 데이터, Supabase 사용자 정보 / Auth 연동 전)
public class MyPageActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 흰 배경 위에서 상태바 아이콘이 어둡게 보이도록 light 스타일 사용
        EdgeToEdge.enable(this,
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT));
        setContentView(R.layout.activity_my_page);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.myPageRoot), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // TODO: Supabase 사용자 정보 연동 후 교체 (현재는 임시 예시 데이터, 프로필 사진 업로드 미구현)
        TextView profileNameText = findViewById(R.id.profileNameText);
        TextView profileEmailText = findViewById(R.id.profileEmailText);
        profileNameText.setText(R.string.user_name_placeholder);
        profileEmailText.setText(R.string.user_email_placeholder);

        // 설정 메뉴: 별도 화면이 없으므로 준비 중 안내만 표시
        // TODO: 내 정보 / 알림 설정 / 즐겨찾기 운동 / 이용약관 / 앱 설정 화면 구현 후 이동
        // TODO: 관심 부위 및 목표 → 초기 설정(불편 부위 / 운동 목표) 수정 기능으로 확장
        setNotReadyMenu(R.id.myInfoRow, R.string.my_information);
        setNotReadyMenu(R.id.interestGoalRow, R.string.interest_and_goal);
        setNotReadyMenu(R.id.notificationSettingRow, R.string.notification_settings);
        setNotReadyMenu(R.id.favoriteExerciseRow, R.string.favorite_exercises);
        setNotReadyMenu(R.id.termsRow, R.string.terms_of_service);
        setNotReadyMenu(R.id.appSettingRow, R.string.app_settings);

        findViewById(R.id.logoutRow).setOnClickListener(v -> showLogoutDialog());

        // Bottom Navigation: 현재 화면은 마이
        findViewById(R.id.bottomNavMyPage).setSelected(true);
        // 홈 / 운동 / 기록: Back Stack에 이미 있으면 그 화면을 재사용하고 위의 화면은 종료 (화면 중복 방지)
        findViewById(R.id.bottomNavHome).setOnClickListener(v -> moveToTab(HomeActivity.class));
        findViewById(R.id.bottomNavExercise).setOnClickListener(v -> moveToTab(ExerciseListActivity.class));
        findViewById(R.id.bottomNavRecord).setOnClickListener(v -> moveToTab(RecordListActivity.class));
    }

    private void setNotReadyMenu(int rowId, @StringRes int menuNameResId) {
        findViewById(rowId).setOnClickListener(v -> Toast.makeText(this,
                getString(R.string.menu_not_ready_format, getString(menuNameResId)),
                Toast.LENGTH_SHORT).show());
    }

    private void showLogoutDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.logout_confirm_title)
                .setMessage(R.string.logout_confirm_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.logout, (dialog, which) -> logout())
                .show();
    }

    // TODO: Supabase Auth 연동 후 실제 로그아웃 처리 (현재는 화면 이동만)
    // 기존 Task를 비우고 로그인 화면만 남겨 Back 버튼으로 홈에 돌아가지 못하게 함
    private void logout() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private void moveToTab(Class<?> activityClass) {
        Intent intent = new Intent(this, activityClass);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }
}

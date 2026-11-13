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

import com.google.android.material.snackbar.Snackbar;

import com.example.airehabpostureapp.network.ApiCalls;
import com.example.airehabpostureapp.network.AuthApi;
import com.example.airehabpostureapp.network.AuthErrorMapper;
import com.example.airehabpostureapp.network.ProfileApi;
import com.example.airehabpostureapp.network.SessionManager;
import com.example.airehabpostureapp.network.SupabaseClient;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

// 마이페이지 (프로필 이름 / 이메일은 Supabase에서 조회, 설정 메뉴는 준비 중)
public class MyPageActivity extends AppCompatActivity {

    // L7: 화면이 닫힐 때 진행 중인 요청 취소
    private final ApiCalls apiCalls = ApiCalls.bind(this);

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

        // 프로필: 이름은 profiles.name, 이메일은 로그인한 계정 이메일 (TODO: 프로필 사진 업로드 미구현)
        loadProfile();

        // 설정 메뉴: 별도 화면이 없으므로 준비 중 안내만 표시
        // TODO: 내 정보 / 알림 설정 / 즐겨찾기 운동 / 이용약관 / 앱 설정 화면 구현 후 이동
        setNotReadyMenu(R.id.myInfoRow, R.string.my_information);
        // 관심 부위 및 목표 → 21_GoalChallenge (뒤로가기 시 이 화면으로 복귀)
        // TODO: 초기 설정(불편 부위 / 운동 목표) 수정 기능으로 확장
        findViewById(R.id.interestGoalRow).setOnClickListener(v ->
                startActivity(new Intent(this, GoalChallengeActivity.class)));
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

    // 이름 / 이메일을 각각 불러와 표시 (값이 없으면 "-", 실패하면 안내 후 빈 칸 유지)
    private void loadProfile() {
        SessionManager sessionManager = new SessionManager(this);
        String bearerToken = sessionManager.getBearerToken();
        TextView profileNameText = findViewById(R.id.profileNameText);
        TextView profileEmailText = findViewById(R.id.profileEmailText);

        ProfileApi profileApi = SupabaseClient.getRetrofit().create(ProfileApi.class);
        apiCalls.track(profileApi.getProfileName(bearerToken, "eq." + sessionManager.getUserId()))
                .enqueue(new Callback<List<ProfileApi.ProfileNameRow>>() {
                    @Override
                    public void onResponse(Call<List<ProfileApi.ProfileNameRow>> call,
                                           Response<List<ProfileApi.ProfileNameRow>> response) {
                        List<ProfileApi.ProfileNameRow> rows = response.body();
                        if (!response.isSuccessful() || rows == null) {
                            showProfileError(R.string.error_profile_load_failed);
                            return;
                        }
                        String name = rows.isEmpty() ? null : rows.get(0).name;
                        profileNameText.setText(name == null || name.isEmpty()
                                ? getString(R.string.empty_value) : name);
                    }

                    @Override
                    public void onFailure(Call<List<ProfileApi.ProfileNameRow>> call, Throwable t) {
                        showProfileError(AuthErrorMapper.fromFailure(t, R.string.error_profile_load_failed));
                    }
                });

        AuthApi authApi = SupabaseClient.getRetrofit().create(AuthApi.class);
        apiCalls.track(authApi.getUser(bearerToken)).enqueue(new Callback<AuthApi.User>() {
            @Override
            public void onResponse(Call<AuthApi.User> call, Response<AuthApi.User> response) {
                AuthApi.User user = response.body();
                if (!response.isSuccessful() || user == null) {
                    showProfileError(R.string.error_profile_load_failed);
                    return;
                }
                profileEmailText.setText(user.email == null ? getString(R.string.empty_value) : user.email);
            }

            @Override
            public void onFailure(Call<AuthApi.User> call, Throwable t) {
                showProfileError(AuthErrorMapper.fromFailure(t, R.string.error_profile_load_failed));
            }
        });
    }

    // M4: 불러오기 실패 → "다시 시도" (이름 / 이메일 요청이 둘 다 실패해도 Snackbar는 하나만)
    private Snackbar profileRetrySnackbar;

    private void showProfileError(@StringRes int messageRes) {
        if (profileRetrySnackbar != null && profileRetrySnackbar.isShownOrQueued()) {
            return;
        }
        profileRetrySnackbar = RetrySnackbar.show(this, messageRes, this::loadProfile);
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

    // 서버 로그아웃 요청 → 성공/실패와 관계없이 앱의 세션 삭제 → 로그인 화면 이동
    private void logout() {
        // 요청 중 중복 클릭 방지
        findViewById(R.id.logoutRow).setEnabled(false);
        SessionManager sessionManager = new SessionManager(this);
        String bearerToken = sessionManager.getBearerToken();
        if (bearerToken == null) {
            finishLogout(sessionManager);
            return;
        }

        AuthApi authApi = SupabaseClient.getRetrofit().create(AuthApi.class);
        apiCalls.track(authApi.logout(bearerToken)).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                finishLogout(sessionManager);
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                finishLogout(sessionManager);
            }
        });
    }

    // 기존 Task를 비우고 로그인 화면만 남겨 Back 버튼으로 홈에 돌아가지 못하게 함
    private void finishLogout(SessionManager sessionManager) {
        sessionManager.clear();
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

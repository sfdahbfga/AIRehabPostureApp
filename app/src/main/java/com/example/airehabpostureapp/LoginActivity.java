package com.example.airehabpostureapp;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.airehabpostureapp.network.AuthApi;
import com.example.airehabpostureapp.network.AuthErrorMapper;
import com.example.airehabpostureapp.network.OnboardingChecker;
import com.example.airehabpostureapp.network.SessionManager;
import com.example.airehabpostureapp.network.SupabaseClient;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    private EditText emailInput;
    private EditText passwordInput;
    private ImageButton passwordToggle;
    private CheckBox autoLoginCheckBox;
    private boolean isPasswordVisible = false;
    private View loginButton;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 흰 배경 위에서 상태바 아이콘이 어둡게 보이도록 light 스타일 사용
        EdgeToEdge.enable(this,
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT));
        setContentView(R.layout.activity_login);
        // 키보드가 올라오면 그 높이만큼 하단 여백을 줘서 입력창이 가려지지 않도록 처리
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.login_root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right,
                    Math.max(systemBars.bottom, ime.bottom));
            return insets;
        });

        emailInput = findViewById(R.id.login_email_input);
        passwordInput = findViewById(R.id.login_password_input);
        passwordToggle = findViewById(R.id.login_password_toggle);
        autoLoginCheckBox = findViewById(R.id.login_auto_login_checkbox);

        passwordToggle.setOnClickListener(v -> togglePasswordVisibility());

        sessionManager = new SessionManager(this);
        loginButton = findViewById(R.id.login_button);
        loginButton.setOnClickListener(v -> signIn());

        findViewById(R.id.login_sign_up_text).setOnClickListener(v ->
                startActivity(new Intent(this, SignUpActivity.class)));

        findViewById(R.id.login_forgot_password_text).setOnClickListener(v -> {
            // TODO: 비밀번호 찾기 기능 구현
            showNotReady();
        });
    }

    private void togglePasswordVisibility() {
        // 전환 후에도 커서/선택 위치를 유지
        int selectionStart = passwordInput.getSelectionStart();
        int selectionEnd = passwordInput.getSelectionEnd();

        isPasswordVisible = !isPasswordVisible;
        if (isPasswordVisible) {
            passwordInput.setTransformationMethod(null);
            passwordToggle.setImageResource(R.drawable.ic_visibility);
            passwordToggle.setContentDescription(getString(R.string.hide_password));
        } else {
            passwordInput.setTransformationMethod(PasswordTransformationMethod.getInstance());
            passwordToggle.setImageResource(R.drawable.ic_visibility_off);
            passwordToggle.setContentDescription(getString(R.string.show_password));
        }

        passwordInput.setSelection(selectionStart, selectionEnd);
    }

    // 로그인 → 세션 / 자동 로그인 여부 저장 → Home 이동
    private void signIn() {
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        if (email.isEmpty()) {
            emailInput.requestFocus();
            Toast.makeText(this, R.string.error_email_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        if (password.isEmpty()) {
            passwordInput.requestFocus();
            Toast.makeText(this, R.string.error_password_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        // 요청 중 중복 클릭 방지
        loginButton.setEnabled(false);
        AuthApi authApi = SupabaseClient.getRetrofit().create(AuthApi.class);
        authApi.signIn(new AuthApi.Credentials(email, password)).enqueue(new Callback<AuthApi.AuthResponse>() {
            @Override
            public void onResponse(Call<AuthApi.AuthResponse> call, Response<AuthApi.AuthResponse> response) {
                AuthApi.AuthResponse body = response.body();
                if (!response.isSuccessful() || body == null || body.accessToken == null || body.user == null) {
                    showError(AuthErrorMapper.fromResponse(response));
                    return;
                }
                sessionManager.saveSession(body.accessToken, body.refreshToken, body.user.id);
                sessionManager.setAutoLogin(autoLoginCheckBox.isChecked());
                checkOnboarding(email);
            }

            @Override
            public void onFailure(Call<AuthApi.AuthResponse> call, Throwable t) {
                showError(AuthErrorMapper.fromFailure(t));
            }
        });
    }

    // 초기 설정을 마쳤으면 Home, 아니면 덜 끝난 설정 화면으로 이동. 실패하면 로그인 화면에 머문다.
    private void checkOnboarding(String email) {
        new OnboardingChecker(sessionManager, new OnboardingChecker.Listener() {
            @Override
            public void onDestination(Class<?> activityClass) {
                // 로그인 화면은 Back Stack에서 제거
                startActivity(new Intent(LoginActivity.this, activityClass));
                finish();
            }

            @Override
            public void onError(int messageRes) {
                showError(messageRes);
            }
        }).check(email);
    }

    private void showError(@StringRes int messageRes) {
        loginButton.setEnabled(true);
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show();
    }

    private void showNotReady() {
        Toast.makeText(this, R.string.feature_not_ready, Toast.LENGTH_SHORT).show();
    }
}

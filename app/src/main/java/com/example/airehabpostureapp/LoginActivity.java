package com.example.airehabpostureapp;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.method.PasswordTransformationMethod;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class LoginActivity extends AppCompatActivity {

    private EditText emailInput;
    private EditText passwordInput;
    private ImageButton passwordToggle;
    private CheckBox autoLoginCheckBox;
    private boolean isPasswordVisible = false;

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

        findViewById(R.id.login_button).setOnClickListener(v -> {
            // TODO: Supabase 로그인 연동 (emailInput, passwordInput, autoLoginCheckBox 사용)
            showNotReady();
        });

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

    private void showNotReady() {
        Toast.makeText(this, R.string.feature_not_ready, Toast.LENGTH_SHORT).show();
    }
}

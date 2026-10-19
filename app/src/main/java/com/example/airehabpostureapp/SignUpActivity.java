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
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class SignUpActivity extends AppCompatActivity {

    private EditText emailInput;
    private EditText passwordInput;
    private EditText passwordConfirmInput;
    private EditText nameInput;
    private EditText birthDateInput;
    private CheckBox termsCheckBox;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 흰 배경 위에서 상태바 아이콘이 어둡게 보이도록 light 스타일 사용
        EdgeToEdge.enable(this,
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT));
        setContentView(R.layout.activity_sign_up);
        // 키보드가 올라오면 그 높이만큼 하단 여백을 줘서 입력창이 가려지지 않도록 처리
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.sign_up_root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right,
                    Math.max(systemBars.bottom, ime.bottom));
            return insets;
        });

        emailInput = findViewById(R.id.sign_up_email_input);
        passwordInput = findViewById(R.id.sign_up_password_input);
        passwordConfirmInput = findViewById(R.id.sign_up_password_confirm_input);
        nameInput = findViewById(R.id.sign_up_name_input);
        birthDateInput = findViewById(R.id.sign_up_birth_date_input);
        termsCheckBox = findViewById(R.id.sign_up_terms_checkbox);

        // 이전 화면(Login)으로 돌아가기: 새 Activity를 만들지 않고 현재 화면만 종료
        findViewById(R.id.sign_up_back_button).setOnClickListener(v -> finish());

        ImageButton passwordToggle = findViewById(R.id.sign_up_password_toggle);
        passwordToggle.setOnClickListener(v -> togglePasswordVisibility(passwordInput, passwordToggle));

        ImageButton passwordConfirmToggle = findViewById(R.id.sign_up_password_confirm_toggle);
        passwordConfirmToggle.setOnClickListener(v ->
                togglePasswordVisibility(passwordConfirmInput, passwordConfirmToggle));

        findViewById(R.id.sign_up_button).setOnClickListener(v -> {
            if (validateInputs()) {
                // TODO: Supabase 회원가입 연동 (현재는 검증 성공 시 바로 이동)
                startActivity(new Intent(this, UserSetupActivity.class));
            }
        });
    }

    // 02_Login과 같은 방식: TransformationMethod 전환 + 커서/선택 위치 유지
    private void togglePasswordVisibility(EditText input, ImageButton toggle) {
        int selectionStart = input.getSelectionStart();
        int selectionEnd = input.getSelectionEnd();

        boolean isCurrentlyHidden = input.getTransformationMethod() instanceof PasswordTransformationMethod;
        if (isCurrentlyHidden) {
            input.setTransformationMethod(null);
            toggle.setImageResource(R.drawable.ic_visibility);
            toggle.setContentDescription(getString(R.string.hide_password));
        } else {
            input.setTransformationMethod(PasswordTransformationMethod.getInstance());
            toggle.setImageResource(R.drawable.ic_visibility_off);
            toggle.setContentDescription(getString(R.string.show_password));
        }

        input.setSelection(selectionStart, selectionEnd);
    }

    // 위에서부터 순서대로 확인하고, 첫 번째 문제만 안내
    private boolean validateInputs() {
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        String passwordConfirm = passwordConfirmInput.getText().toString();
        String name = nameInput.getText().toString().trim();
        String birthDate = birthDateInput.getText().toString().trim();

        if (email.isEmpty()) {
            return fail(emailInput, R.string.error_email_empty);
        }
        if (password.isEmpty()) {
            return fail(passwordInput, R.string.error_password_empty);
        }
        if (passwordConfirm.isEmpty()) {
            return fail(passwordConfirmInput, R.string.error_password_confirm_empty);
        }
        if (!password.equals(passwordConfirm)) {
            return fail(passwordConfirmInput, R.string.error_password_mismatch);
        }
        if (name.isEmpty()) {
            return fail(nameInput, R.string.error_name_empty);
        }
        if (birthDate.isEmpty()) {
            return fail(birthDateInput, R.string.error_birth_date_empty);
        }
        if (!termsCheckBox.isChecked()) {
            Toast.makeText(this, R.string.error_terms_not_agreed, Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private boolean fail(EditText field, @StringRes int messageRes) {
        field.requestFocus();
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show();
        return false;
    }
}

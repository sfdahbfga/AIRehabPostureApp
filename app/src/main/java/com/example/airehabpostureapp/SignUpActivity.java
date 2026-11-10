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

import com.example.airehabpostureapp.network.ApiCalls;
import com.example.airehabpostureapp.network.AuthApi;
import com.example.airehabpostureapp.network.AuthErrorMapper;
import com.example.airehabpostureapp.network.ProfileApi;
import com.example.airehabpostureapp.network.SessionManager;
import com.example.airehabpostureapp.network.SupabaseClient;

import java.time.LocalDate;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SignUpActivity extends AppCompatActivity {

    // L7: 화면이 닫힐 때 진행 중인 요청 취소
    private final ApiCalls apiCalls = ApiCalls.bind(this);

    private static final int HTTP_CONFLICT = 409;

    private EditText emailInput;
    private EditText passwordInput;
    private EditText passwordConfirmInput;
    private EditText nameInput;
    private EditText birthDateInput;
    private CheckBox termsCheckBox;
    private View signUpButton;
    private SessionManager sessionManager;

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

        sessionManager = new SessionManager(this);
        if (savedInstanceState != null) {
            signedUpEmail = savedInstanceState.getString(STATE_SIGNED_UP_EMAIL);
            signedUpUserId = savedInstanceState.getString(STATE_SIGNED_UP_USER_ID);
        }
        signUpButton = findViewById(R.id.sign_up_button);
        signUpButton.setOnClickListener(v -> {
            if (validateInputs()) {
                String birthDate = BirthDates.toDbBirthDate(birthDateInput.getText().toString().trim());
                if (birthDate == null) {
                    fail(birthDateInput, R.string.error_birth_date_format);
                    return;
                }
                if (!BirthDates.isInRange(birthDate, LocalDate.now())) {
                    fail(birthDateInput, R.string.error_birth_date_range);
                    return;
                }
                signUp(birthDate);
            }
        });
    }

    // L5: 이 화면에서 가입에 성공한 이메일 / 사용자 id (프로필 저장만 실패한 뒤 다시 누르면 가입은 건너뜀)
    private static final String STATE_SIGNED_UP_EMAIL = "state_signed_up_email";
    private static final String STATE_SIGNED_UP_USER_ID = "state_signed_up_user_id";
    private String signedUpEmail;
    private String signedUpUserId;

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_SIGNED_UP_EMAIL, signedUpEmail);
        outState.putString(STATE_SIGNED_UP_USER_ID, signedUpUserId);
    }

    // 회원가입 → 세션 저장 → profiles insert → UserSetup 이동
    private void signUp(String birthDate) {
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        String name = nameInput.getText().toString().trim();

        // L5: 가입은 이미 됐고 프로필 저장만 실패한 경우 → 프로필 저장만 다시 시도 ("이미 가입된 이메일" 오류 방지)
        if (signedUpUserId != null && email.equals(signedUpEmail)
                && signedUpUserId.equals(sessionManager.getUserId())) {
            signUpButton.setEnabled(false);
            insertProfile(signedUpUserId, name, birthDate);
            return;
        }

        // 요청 중 중복 클릭 방지
        signUpButton.setEnabled(false);
        AuthApi authApi = SupabaseClient.getRetrofit().create(AuthApi.class);
        apiCalls.track(authApi.signUp(new AuthApi.Credentials(email, password))).enqueue(new Callback<AuthApi.AuthResponse>() {
            @Override
            public void onResponse(Call<AuthApi.AuthResponse> call, Response<AuthApi.AuthResponse> response) {
                AuthApi.AuthResponse body = response.body();
                if (!response.isSuccessful() || body == null) {
                    showError(AuthErrorMapper.fromResponse(response));
                    return;
                }
                // 이메일 확인이 켜져 있으면 세션 없이 가입만 된다.
                if (body.accessToken == null || body.user == null) {
                    signUpButton.setEnabled(true);
                    Toast.makeText(SignUpActivity.this, R.string.sign_up_email_confirmation_required,
                            Toast.LENGTH_LONG).show();
                    return;
                }
                sessionManager.saveSession(body.accessToken, body.refreshToken, body.user.id);
                // 회원가입 화면에는 자동 로그인 체크박스가 없으므로 꺼둔다.
                sessionManager.setAutoLogin(false);
                signedUpEmail = email;
                signedUpUserId = body.user.id;
                insertProfile(body.user.id, name, birthDate);
            }

            @Override
            public void onFailure(Call<AuthApi.AuthResponse> call, Throwable t) {
                showError(AuthErrorMapper.fromFailure(t));
            }
        });
    }

    private void insertProfile(String userId, String name, String birthDate) {
        ProfileApi profileApi = SupabaseClient.getRetrofit().create(ProfileApi.class);
        apiCalls.track(profileApi.insertProfile(sessionManager.getBearerToken(),
                new ProfileApi.ProfileInsert(userId, name, birthDate))).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                // 409: 프로필이 이미 있음 (저장은 됐는데 응답을 못 받은 뒤 다시 시도한 경우) → 성공으로 처리
                if (!response.isSuccessful() && response.code() != HTTP_CONFLICT) {
                    showError(R.string.error_profile_save_failed);
                    return;
                }
                signUpButton.setEnabled(true);
                startActivity(new Intent(SignUpActivity.this, UserSetupActivity.class));
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                showError(R.string.error_profile_save_failed);
            }
        });
    }

    private void showError(@StringRes int messageRes) {
        signUpButton.setEnabled(true);
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show();
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

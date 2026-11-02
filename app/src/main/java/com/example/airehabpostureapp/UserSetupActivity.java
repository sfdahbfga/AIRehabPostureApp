package com.example.airehabpostureapp;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.airehabpostureapp.network.AuthErrorMapper;
import com.example.airehabpostureapp.network.ProfileApi;
import com.example.airehabpostureapp.network.SessionManager;
import com.example.airehabpostureapp.network.SupabaseClient;

import java.util.Arrays;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class UserSetupActivity extends AppCompatActivity {

    // profiles.exercise_experience 값. experienceButtons와 같은 순서 (없음, 초급, 중급, 고급)
    private static final String[] EXPERIENCE_CODES = {"none", "beginner", "intermediate", "advanced"};

    private EditText ageEditText;
    private EditText heightEditText;
    private EditText weightEditText;
    private View[] experienceButtons;
    // 선택된 운동 경험 버튼 (선택 전에는 null)
    private View selectedExperienceButton;
    private View nextButton;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 흰 배경 위에서 상태바 아이콘이 어둡게 보이도록 light 스타일 사용
        EdgeToEdge.enable(this,
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT));
        setContentView(R.layout.activity_user_setup);
        // 키보드가 올라오면 그 높이만큼 하단 여백을 줘서 입력창이 가려지지 않도록 처리
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.userSetupRoot), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right,
                    Math.max(systemBars.bottom, ime.bottom));
            return insets;
        });

        ageEditText = findViewById(R.id.ageEditText);
        heightEditText = findViewById(R.id.heightEditText);
        weightEditText = findViewById(R.id.weightEditText);

        // 이전 화면(SignUp)으로 돌아가기: 새 Activity를 만들지 않고 현재 화면만 종료
        findViewById(R.id.backButton).setOnClickListener(v -> finish());

        experienceButtons = new View[]{
                findViewById(R.id.experienceNoneButton),
                findViewById(R.id.experienceBeginnerButton),
                findViewById(R.id.experienceIntermediateButton),
                findViewById(R.id.experienceAdvancedButton)
        };
        for (View button : experienceButtons) {
            button.setOnClickListener(this::selectExperience);
        }

        sessionManager = new SessionManager(this);
        nextButton = findViewById(R.id.nextButton);
        nextButton.setOnClickListener(v -> {
            if (validateInputs()) {
                saveProfile();
            }
        });
    }

    // 키 / 체중 / 운동 경험을 profiles에 저장 (나이는 저장하지 않음) → BodyCondition 이동
    private void saveProfile() {
        double height = parsePositive(heightEditText);
        if (Double.isNaN(height)) {
            fail(heightEditText, R.string.error_height_invalid);
            return;
        }
        double weight = parsePositive(weightEditText);
        if (Double.isNaN(weight)) {
            fail(weightEditText, R.string.error_weight_invalid);
            return;
        }
        String experience = EXPERIENCE_CODES[Arrays.asList(experienceButtons).indexOf(selectedExperienceButton)];

        // 저장 중 중복 클릭 방지
        nextButton.setEnabled(false);
        ProfileApi profileApi = SupabaseClient.getRetrofit().create(ProfileApi.class);
        profileApi.updateProfile(sessionManager.getBearerToken(), "eq." + sessionManager.getUserId(),
                new ProfileApi.ProfileUpdate(height, weight, experience))
                .enqueue(new Callback<List<ProfileApi.ProfileRow>>() {
                    @Override
                    public void onResponse(Call<List<ProfileApi.ProfileRow>> call,
                                           Response<List<ProfileApi.ProfileRow>> response) {
                        List<ProfileApi.ProfileRow> rows = response.body();
                        if (!response.isSuccessful() || rows == null || rows.isEmpty()) {
                            showError(R.string.error_profile_save_failed);
                            return;
                        }
                        nextButton.setEnabled(true);
                        startActivity(new Intent(UserSetupActivity.this, BodyConditionActivity.class));
                    }

                    @Override
                    public void onFailure(Call<List<ProfileApi.ProfileRow>> call, Throwable t) {
                        showError(AuthErrorMapper.fromFailure(t, R.string.error_profile_save_failed));
                    }
                });
    }

    // 0보다 큰 유한한 숫자면 그 값, 아니면 NaN
    private double parsePositive(EditText field) {
        try {
            double value = Double.parseDouble(field.getText().toString().trim());
            return value > 0 && !Double.isInfinite(value) ? value : Double.NaN;
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private void showError(@StringRes int messageRes) {
        nextButton.setEnabled(true);
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show();
    }

    // 단일 선택: 누른 버튼만 selected, 나머지는 해제
    private void selectExperience(View clickedButton) {
        for (View button : experienceButtons) {
            button.setSelected(button == clickedButton);
        }
        selectedExperienceButton = clickedButton;
    }

    // 위에서부터 순서대로 확인하고, 첫 번째 문제만 안내
    // (의학적 기준이 아닌 빈 값 / 숫자 형식 오류만 검사)
    private boolean validateInputs() {
        if (!isValidNumber(ageEditText, R.string.error_age_empty, R.string.error_age_invalid)) {
            return false;
        }
        if (!isValidNumber(heightEditText, R.string.error_height_empty, R.string.error_height_invalid)) {
            return false;
        }
        if (!isValidNumber(weightEditText, R.string.error_weight_empty, R.string.error_weight_invalid)) {
            return false;
        }
        if (selectedExperienceButton == null) {
            Toast.makeText(this, R.string.error_experience_not_selected, Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    // 비어 있으면 emptyMessage, 숫자가 아니거나 0 이하이면 invalidMessage 표시
    private boolean isValidNumber(EditText field, @StringRes int emptyMessage, @StringRes int invalidMessage) {
        String text = field.getText().toString().trim();
        if (text.isEmpty()) {
            return fail(field, emptyMessage);
        }
        try {
            if (Double.parseDouble(text) <= 0) {
                return fail(field, invalidMessage);
            }
        } catch (NumberFormatException e) {
            return fail(field, invalidMessage);
        }
        return true;
    }

    private boolean fail(EditText field, @StringRes int messageRes) {
        field.requestFocus();
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show();
        return false;
    }
}

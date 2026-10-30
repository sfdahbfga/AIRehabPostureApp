package com.example.airehabpostureapp.network;

import androidx.annotation.StringRes;

import com.example.airehabpostureapp.BodyConditionActivity;
import com.example.airehabpostureapp.GoalSetupActivity;
import com.example.airehabpostureapp.HomeActivity;
import com.example.airehabpostureapp.R;
import com.example.airehabpostureapp.UserSetupActivity;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 로그인 직후 초기 설정을 어디까지 마쳤는지 확인하고 이동할 화면을 알려준다.
 * profiles → user_body_conditions → user_goals 순서로 확인하고, 처음 비어 있는 단계의 화면을 고른다.
 */
public class OnboardingChecker {

    public interface Listener {
        void onDestination(Class<?> activityClass);

        void onError(@StringRes int messageRes);
    }

    private final SessionManager sessionManager;
    private final Listener listener;

    public OnboardingChecker(SessionManager sessionManager, Listener listener) {
        this.sessionManager = sessionManager;
        this.listener = listener;
    }

    // email: profiles 행이 없을 때 display_name(이메일 앞부분)을 만드는 데 사용
    public void check(String email) {
        ProfileApi profileApi = SupabaseClient.getRetrofit().create(ProfileApi.class);
        profileApi.getProfile(sessionManager.getBearerToken(), userIdFilter())
                .enqueue(new Callback<List<ProfileApi.ProfileRow>>() {
                    @Override
                    public void onResponse(Call<List<ProfileApi.ProfileRow>> call,
                                           Response<List<ProfileApi.ProfileRow>> response) {
                        List<ProfileApi.ProfileRow> rows = response.body();
                        if (!response.isSuccessful() || rows == null) {
                            listener.onError(R.string.error_onboarding_check_failed);
                            return;
                        }
                        if (rows.isEmpty()) {
                            createProfile(email);
                            return;
                        }
                        if (rows.get(0).exerciseExperience == null) {
                            listener.onDestination(UserSetupActivity.class);
                            return;
                        }
                        checkBodyConditions();
                    }

                    @Override
                    public void onFailure(Call<List<ProfileApi.ProfileRow>> call, Throwable t) {
                        listener.onError(failureMessage(t));
                    }
                });
    }

    // 회원가입 중 프로필 저장이 실패한 경우: 이메일 앞부분을 이름으로 프로필을 만든 뒤 UserSetup으로
    private void createProfile(String email) {
        if (email == null || email.isEmpty()) {
            listener.onError(R.string.error_profile_save_failed);
            return;
        }
        int atIndex = email.indexOf('@');
        String displayName = atIndex > 0 ? email.substring(0, atIndex) : email;

        ProfileApi profileApi = SupabaseClient.getRetrofit().create(ProfileApi.class);
        profileApi.insertProfile(sessionManager.getBearerToken(),
                new ProfileApi.ProfileInsert(sessionManager.getUserId(), displayName, null))
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        if (!response.isSuccessful()) {
                            listener.onError(R.string.error_profile_save_failed);
                            return;
                        }
                        listener.onDestination(UserSetupActivity.class);
                    }

                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        listener.onError(failureMessage(t));
                    }
                });
    }

    private void checkBodyConditions() {
        BodyConditionApi bodyConditionApi = SupabaseClient.getRetrofit().create(BodyConditionApi.class);
        bodyConditionApi.getAny(sessionManager.getBearerToken(), userIdFilter())
                .enqueue(new Callback<List<BodyConditionApi.IdRow>>() {
                    @Override
                    public void onResponse(Call<List<BodyConditionApi.IdRow>> call,
                                           Response<List<BodyConditionApi.IdRow>> response) {
                        List<BodyConditionApi.IdRow> rows = response.body();
                        if (!response.isSuccessful() || rows == null) {
                            listener.onError(R.string.error_onboarding_check_failed);
                            return;
                        }
                        if (rows.isEmpty()) {
                            listener.onDestination(BodyConditionActivity.class);
                            return;
                        }
                        checkGoals();
                    }

                    @Override
                    public void onFailure(Call<List<BodyConditionApi.IdRow>> call, Throwable t) {
                        listener.onError(failureMessage(t));
                    }
                });
    }

    private void checkGoals() {
        GoalApi goalApi = SupabaseClient.getRetrofit().create(GoalApi.class);
        goalApi.getAny(sessionManager.getBearerToken(), userIdFilter())
                .enqueue(new Callback<List<BodyConditionApi.IdRow>>() {
                    @Override
                    public void onResponse(Call<List<BodyConditionApi.IdRow>> call,
                                           Response<List<BodyConditionApi.IdRow>> response) {
                        List<BodyConditionApi.IdRow> rows = response.body();
                        if (!response.isSuccessful() || rows == null) {
                            listener.onError(R.string.error_onboarding_check_failed);
                            return;
                        }
                        listener.onDestination(rows.isEmpty() ? GoalSetupActivity.class : HomeActivity.class);
                    }

                    @Override
                    public void onFailure(Call<List<BodyConditionApi.IdRow>> call, Throwable t) {
                        listener.onError(failureMessage(t));
                    }
                });
    }

    private String userIdFilter() {
        return "eq." + sessionManager.getUserId();
    }

    @StringRes
    private static int failureMessage(Throwable t) {
        return AuthErrorMapper.fromFailure(t, R.string.error_onboarding_check_failed);
    }
}

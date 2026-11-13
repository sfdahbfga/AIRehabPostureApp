package com.example.airehabpostureapp;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.example.airehabpostureapp.network.SessionManager;

/**
 * M3: 로그인 만료 안내.
 *
 * TokenAuthenticator가 세션을 만료 처리하면 "로그인 만료" 표시만 남긴다. 이 클래스는
 * - 지금 보이는 화면이 있으면 바로,
 * - 앱이 백그라운드였으면 다음에 화면이 보일 때(onActivityResumed)
 * 로그인이 필요한 화면 위에 "다시 로그인해 주세요" 안내를 띄우고 로그인 화면으로 보낸다.
 * 로그인 표시가 없더라도 토큰이 없는 상태로 로그인이 필요한 화면이 보이면 같은 안내를 한다. (토큰 없이 요청하면 빈 목록이 오므로)
 *
 * 저장하지 못한 운동 결과는 PendingWorkoutStore에 남아 있어 다시 로그인하면 홈에서 저장할 수 있다.
 */
public class SessionExpiryWatcher implements Application.ActivityLifecycleCallbacks {

    @Nullable
    private Activity resumedActivity;
    @Nullable
    private AlertDialog dialog;
    // 안내 창을 띄운 화면 (그 화면이 닫히면 창도 닫음)
    @Nullable
    private Activity dialogOwner;

    /** 메인 스레드에서 호출 (SupabaseClient → 세션 만료 직후) */
    public void checkNow() {
        if (resumedActivity != null) {
            check(resumedActivity);
        }
    }

    // 로그인 전에 쓰는 화면은 검사하지 않음
    static boolean requiresLogin(Activity activity) {
        return !(activity instanceof SplashActivity
                || activity instanceof LoginActivity
                || activity instanceof SignUpActivity);
    }

    private void check(Activity activity) {
        if (!requiresLogin(activity) || activity.isFinishing()) {
            return;
        }
        SessionManager sessionManager = new SessionManager(activity);
        if (!sessionManager.hasSessionExpiredNotice() && sessionManager.getBearerToken() != null) {
            return;
        }
        if (dialog != null && dialog.isShowing()) {
            return;
        }
        dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.session_expired_title)
                .setMessage(R.string.error_session_expired)
                .setCancelable(false)
                .setPositiveButton(R.string.session_expired_login, (d, which) -> {
                    sessionManager.clearSessionExpiredNotice();
                    Intent intent = new Intent(activity, LoginActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    activity.startActivity(intent);
                })
                .show();
        dialogOwner = activity;
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        resumedActivity = activity;
        check(activity);
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
        if (resumedActivity == activity) {
            resumedActivity = null;
        }
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
        if (dialogOwner == activity) {
            if (dialog != null) {
                dialog.dismiss();
            }
            dialog = null;
            dialogOwner = null;
        }
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
    }
}

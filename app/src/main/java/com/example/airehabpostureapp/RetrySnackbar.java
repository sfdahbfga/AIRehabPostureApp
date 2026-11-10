package com.example.airehabpostureapp;

import android.app.Activity;
import android.view.View;

import androidx.annotation.StringRes;

import com.google.android.material.snackbar.Snackbar;

/**
 * M4: 조회 화면에서 불러오기에 실패했을 때 "다시 시도" Snackbar (레이아웃을 바꾸지 않고 코드에서 표시)
 * 사용자가 "다시 시도"를 누르거나 화면을 나갈 때까지 계속 보인다.
 */
public final class RetrySnackbar {

    private RetrySnackbar() {
    }

    public static Snackbar show(Activity activity, @StringRes int messageRes, Runnable retry) {
        View root = activity.findViewById(android.R.id.content);
        Snackbar snackbar = Snackbar.make(root, messageRes, Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.retry, v -> retry.run());
        snackbar.show();
        return snackbar;
    }
}

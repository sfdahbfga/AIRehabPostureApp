package com.example.airehabpostureapp;

import android.app.Application;

import com.example.airehabpostureapp.network.SupabaseClient;

// 앱 시작 시 한 번: SupabaseClient에 앱 Context 전달 (로그인 만료 시 토큰 갱신 / 만료 처리에 사용)
// + 로그인 만료 안내(SessionExpiryWatcher): 로그인이 필요한 화면이 보일 때 만료됐으면 안내 후 로그인 화면으로
public class AiRehabApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        SupabaseClient.init(this);
        SessionExpiryWatcher watcher = new SessionExpiryWatcher();
        registerActivityLifecycleCallbacks(watcher);
        SupabaseClient.setOnSessionExpired(watcher::checkNow);
    }
}

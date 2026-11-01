package com.example.airehabpostureapp;

import android.app.Application;

import com.example.airehabpostureapp.network.SupabaseClient;

// 앱 시작 시 한 번: SupabaseClient에 앱 Context 전달 (로그인 만료 시 로그인 화면 이동에 사용)
public class AiRehabApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        SupabaseClient.init(this);
    }
}

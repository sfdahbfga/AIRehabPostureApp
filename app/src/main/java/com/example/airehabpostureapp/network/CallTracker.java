package com.example.airehabpostureapp.network;

import retrofit2.Call;

/**
 * 요청을 화면 생명주기에 묶는 도구 (실제: ApiCalls, 화면 없이 쓸 때: NONE)
 */
public interface CallTracker {

    <T> Call<T> track(Call<T> call);

    /** 아무것도 하지 않음 (요청을 그대로 사용) */
    CallTracker NONE = new CallTracker() {
        @Override
        public <T> Call<T> track(Call<T> call) {
            return call;
        }
    };
}

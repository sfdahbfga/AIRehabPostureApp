package com.example.airehabpostureapp.network;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import okhttp3.Request;
import okio.Timeout;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * L7: 화면(Activity)이 닫힐 때 진행 중인 Retrofit 요청을 모두 취소하는 공통 도구.
 *
 * 사용법: {@code apiCalls.track(api.getRecords(...)).enqueue(callback)}
 * - 화면의 onDestroy에서 아직 끝나지 않은 요청을 cancel() 한다. (응답을 기다리며 화면을 붙잡지 않음)
 * - 취소된 요청이나 닫힌 화면의 콜백은 부르지 않는다. (닫힌 화면에 Toast / setText 하지 않음)
 */
public final class ApiCalls implements CallTracker, DefaultLifecycleObserver {

    private final Set<Call<?>> activeCalls = new HashSet<>();
    private boolean destroyed;

    private ApiCalls() {
    }

    /** 화면의 생명주기에 연결 (onCreate에서 한 번) */
    public static ApiCalls bind(LifecycleOwner owner) {
        ApiCalls apiCalls = new ApiCalls();
        owner.getLifecycle().addObserver(apiCalls);
        return apiCalls;
    }

    @Override
    public <T> Call<T> track(Call<T> call) {
        return new TrackedCall<>(call);
    }

    @Override
    public void onDestroy(@NonNull LifecycleOwner owner) {
        List<Call<?>> calls;
        synchronized (activeCalls) {
            destroyed = true;
            calls = new ArrayList<>(activeCalls);
            activeCalls.clear();
        }
        for (Call<?> call : calls) {
            call.cancel();
        }
        owner.getLifecycle().removeObserver(this);
    }

    /** 진행 중인 요청 수 (테스트용) */
    int activeCount() {
        synchronized (activeCalls) {
            return activeCalls.size();
        }
    }

    private boolean register(Call<?> call) {
        synchronized (activeCalls) {
            if (destroyed) {
                return false;
            }
            activeCalls.add(call);
            return true;
        }
    }

    // 끝난 요청을 목록에서 빼고, 콜백을 불러도 되면 true
    private boolean finish(Call<?> call) {
        synchronized (activeCalls) {
            activeCalls.remove(call);
            return !destroyed && !call.isCanceled();
        }
    }

    /** enqueue할 때 이 화면의 목록에 등록하고 콜백을 감싸는 Call */
    private final class TrackedCall<T> implements Call<T> {
        private final Call<T> delegate;

        TrackedCall(Call<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void enqueue(Callback<T> callback) {
            if (!register(delegate)) {
                // 이미 닫힌 화면: 보내지 않음
                delegate.cancel();
                return;
            }
            delegate.enqueue(new Callback<T>() {
                @Override
                public void onResponse(Call<T> call, Response<T> response) {
                    if (finish(delegate)) {
                        callback.onResponse(TrackedCall.this, response);
                    }
                }

                @Override
                public void onFailure(Call<T> call, Throwable t) {
                    if (finish(delegate)) {
                        callback.onFailure(TrackedCall.this, t);
                    }
                }
            });
        }

        @Override
        public Response<T> execute() throws IOException {
            return delegate.execute();
        }

        @Override
        public boolean isExecuted() {
            return delegate.isExecuted();
        }

        @Override
        public void cancel() {
            delegate.cancel();
        }

        @Override
        public boolean isCanceled() {
            return delegate.isCanceled();
        }

        @SuppressWarnings("MethodDoesntCallSuperMethod")
        @Override
        public Call<T> clone() {
            return new TrackedCall<>(delegate.clone());
        }

        @Override
        public Request request() {
            return delegate.request();
        }

        @Override
        public Timeout timeout() {
            return delegate.timeout();
        }
    }
}

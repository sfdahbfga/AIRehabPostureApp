package com.example.airehabpostureapp.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.Request;
import okio.Timeout;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

// L7: 화면이 닫힐 때 진행 중인 요청 취소, 닫힌 화면에는 콜백을 부르지 않음
public class ApiCallsTest {

    /** 직접 응답을 넣을 수 있는 가짜 Call */
    static final class FakeCall implements Call<String> {
        Callback<String> callback;
        boolean canceled;

        void respond(String body) {
            callback.onResponse(this, Response.success(body));
        }

        @Override
        public void enqueue(Callback<String> callback) {
            this.callback = callback;
        }

        @Override
        public Response<String> execute() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isExecuted() {
            return callback != null;
        }

        @Override
        public void cancel() {
            canceled = true;
        }

        @Override
        public boolean isCanceled() {
            return canceled;
        }

        @SuppressWarnings("MethodDoesntCallSuperMethod")
        @Override
        public Call<String> clone() {
            return new FakeCall();
        }

        @Override
        public Request request() {
            return new Request.Builder().url("https://example.invalid/").build();
        }

        @Override
        public Timeout timeout() {
            return Timeout.NONE;
        }
    }

    static final class FakeOwner implements LifecycleOwner {
        final LifecycleRegistry registry = LifecycleRegistry.createUnsafe(this);

        @NonNull
        @Override
        public Lifecycle getLifecycle() {
            return registry;
        }
    }

    private final AtomicInteger delivered = new AtomicInteger();
    private final Callback<String> countingCallback = new Callback<String>() {
        @Override
        public void onResponse(Call<String> call, Response<String> response) {
            delivered.incrementAndGet();
        }

        @Override
        public void onFailure(Call<String> call, Throwable t) {
            delivered.incrementAndGet();
        }
    };

    private FakeOwner owner;
    private ApiCalls apiCalls;

    @Before
    public void setUp() {
        owner = new FakeOwner();
        owner.registry.setCurrentState(Lifecycle.State.RESUMED);
        apiCalls = ApiCalls.bind(owner);
    }

    @Test
    public void response_whileScreenOpen_isDelivered() {
        FakeCall call = new FakeCall();
        apiCalls.track(call).enqueue(countingCallback);
        assertEquals(1, apiCalls.activeCount());

        call.respond("ok");
        assertEquals(1, delivered.get());
        assertEquals(0, apiCalls.activeCount());
    }

    @Test
    public void destroy_cancelsPendingCalls_andSkipsLateCallbacks() {
        FakeCall call = new FakeCall();
        apiCalls.track(call).enqueue(countingCallback);

        owner.registry.setCurrentState(Lifecycle.State.DESTROYED);
        assertTrue(call.canceled);
        assertEquals(0, apiCalls.activeCount());

        // 취소 전에 이미 도착한 응답이 늦게 전달돼도 콜백을 부르지 않음
        call.respond("late");
        assertEquals(0, delivered.get());
    }

    @Test
    public void enqueue_afterDestroy_isNotSent() {
        owner.registry.setCurrentState(Lifecycle.State.DESTROYED);
        FakeCall call = new FakeCall();
        apiCalls.track(call).enqueue(countingCallback);

        assertTrue(call.canceled);
        assertFalse(call.isExecuted());
    }
}

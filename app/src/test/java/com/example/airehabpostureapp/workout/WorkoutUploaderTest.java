package com.example.airehabpostureapp.workout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.airehabpostureapp.R;
import com.example.airehabpostureapp.network.ExerciseSessionApi;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * M13: 운동 후 체크 / 홈의 저장 · 롤백 · 재시도 · 삭제 흐름 (WorkoutUploader)
 * 가짜 서버(MockWebServer)에 실제 Retrofit 요청을 보내 확인한다.
 */
public class WorkoutUploaderTest {

    private static final String USER = "user-a";
    private static final String TOKEN = "Bearer test";

    private MockWebServer server;
    private PendingWorkoutStoreTest.MemoryStorage storage;
    private PendingWorkoutStore store;
    private WorkoutUploader uploader;

    @Before
    public void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        ExerciseSessionApi api = new Retrofit.Builder()
                .baseUrl(server.url("/"))
                .addConverterFactory(GsonConverterFactory.create())
                // 콜백을 OkHttp 스레드에서 바로 실행 (Android 메인 스레드 없음)
                .callbackExecutor(Runnable::run)
                .build()
                .create(ExerciseSessionApi.class);
        storage = new PendingWorkoutStoreTest.MemoryStorage();
        store = new PendingWorkoutStore(storage);
        uploader = new WorkoutUploader(api, store, TOKEN);
    }

    @After
    public void tearDown() throws Exception {
        server.shutdown();
    }

    private PendingWorkout checkedWorkout() {
        PendingWorkout workout = PendingWorkoutStoreTest.workout(USER, "런지", 1_000_000);
        workout.setPostCheck(2, "good", "괜찮음");
        store.save(workout);
        return workout;
    }

    /** 리스너 결과를 기다림 */
    private static final class Result implements WorkoutUploader.Listener {
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicReference<String> savedId = new AtomicReference<>();
        final AtomicInteger failedMessage = new AtomicInteger();

        @Override
        public void onSaved(String sessionId) {
            savedId.set(sessionId);
            done.countDown();
        }

        @Override
        public void onFailed(int messageRes) {
            failedMessage.set(messageRes);
            done.countDown();
        }

        Result await() throws InterruptedException {
            assertTrue("응답을 기다리다 시간 초과", done.await(5, TimeUnit.SECONDS));
            return this;
        }
    }

    private Result upload(PendingWorkout workout) throws InterruptedException {
        Result result = new Result();
        uploader.upload(workout, result);
        return result.await();
    }

    private Result discard(PendingWorkout workout) throws InterruptedException {
        Result result = new Result();
        uploader.discard(workout, result);
        return result.await();
    }

    private PendingWorkout stored(PendingWorkout workout) {
        return store.get(USER, workout.sessionId);
    }

    @Test
    public void success_savesSessionThenCheckins_withClientIds_andRemovesPending() throws Exception {
        PendingWorkout workout = checkedWorkout();
        server.enqueue(new MockResponse().setResponseCode(201));
        server.enqueue(new MockResponse().setResponseCode(201));

        Result result = upload(workout);

        assertEquals(workout.sessionId, result.savedId.get());
        assertNull(stored(workout));

        RecordedRequest session = server.takeRequest();
        assertEquals("POST", session.getMethod());
        assertEquals("/rest/v1/exercise_sessions?on_conflict=id", session.getPath());
        assertTrue(session.getHeader("Prefer").contains("resolution=ignore-duplicates"));
        assertEquals(TOKEN, session.getHeader("Authorization"));
        assertTrue(session.getBody().readUtf8().contains("\"id\":\"" + workout.sessionId + "\""));

        RecordedRequest checkins = server.takeRequest();
        assertTrue(checkins.getPath().startsWith("/rest/v1/session_checkins?on_conflict=id&columns=id,"));
        String body = checkins.getBody().readUtf8();
        assertTrue(body.contains("\"id\":\"" + workout.preCheckinId + "\""));
        assertTrue(body.contains("\"id\":\"" + workout.postCheckinId + "\""));
    }

    @Test
    public void checkinFailure_rollsBackSession_andKeepsPending() throws Exception {
        PendingWorkout workout = checkedWorkout();
        server.enqueue(new MockResponse().setResponseCode(201));
        server.enqueue(new MockResponse().setResponseCode(500));
        server.enqueue(new MockResponse().setResponseCode(204));

        Result result = upload(workout);

        assertEquals(R.string.error_exercise_save_failed, result.failedMessage.get());
        server.takeRequest();
        server.takeRequest();
        RecordedRequest rollback = server.takeRequest();
        assertEquals("DELETE", rollback.getMethod());
        assertEquals("/rest/v1/exercise_sessions?id=eq." + workout.sessionId, rollback.getPath());
        // 서버에서 지웠으므로 반쪽 기록 없음, 임시 기록은 남아 다시 저장 가능
        PendingWorkout kept = stored(workout);
        assertNotNull(kept);
        assertFalse(kept.sessionMayExistOnServer);
    }

    @Test
    public void rollbackAlsoFails_marksHalfRecord_thenRetrySendsSameIds() throws Exception {
        PendingWorkout workout = checkedWorkout();
        server.enqueue(new MockResponse().setResponseCode(201));
        server.enqueue(new MockResponse().setResponseCode(500));
        server.enqueue(new MockResponse().setResponseCode(500));

        upload(workout);
        PendingWorkout kept = stored(workout);
        assertTrue("서버에 반쪽 기록이 남았을 수 있음", kept.sessionMayExistOnServer);
        for (int i = 0; i < 3; i++) {
            server.takeRequest();
        }

        // 다시 시도: 세션(같은 id → 서버가 중복 무시) → 체크인 성공
        server.enqueue(new MockResponse().setResponseCode(201));
        server.enqueue(new MockResponse().setResponseCode(201));
        Result retry = upload(kept);

        assertEquals(workout.sessionId, retry.savedId.get());
        assertTrue(server.takeRequest().getBody().readUtf8().contains("\"id\":\"" + workout.sessionId + "\""));
        assertTrue(server.takeRequest().getBody().readUtf8().contains("\"id\":\"" + workout.postCheckinId + "\""));
        assertNull(stored(workout));
    }

    @Test
    public void networkFailure_onSession_keepsPending_andMayExistOnServer() throws Exception {
        PendingWorkout workout = checkedWorkout();
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));

        Result result = upload(workout);

        assertEquals(R.string.error_network, result.failedMessage.get());
        // 응답을 못 받았으므로 서버에 저장됐을 수도 있음 → 삭제를 고르면 서버에서도 지움
        assertTrue(stored(workout).sessionMayExistOnServer);
    }

    @Test
    public void serverRejectsSession_noHalfRecord() throws Exception {
        PendingWorkout workout = checkedWorkout();
        server.enqueue(new MockResponse().setResponseCode(400));

        Result result = upload(workout);

        assertEquals(R.string.error_exercise_save_failed, result.failedMessage.get());
        assertFalse(stored(workout).sessionMayExistOnServer);
        assertEquals(1, server.getRequestCount());
    }

    @Test
    public void notPostChecked_isNotSent() throws Exception {
        PendingWorkout workout = PendingWorkoutStoreTest.workout(USER, "런지", 1_000_000);
        store.save(workout);

        Result result = upload(workout);

        assertEquals(R.string.error_exercise_save_failed, result.failedMessage.get());
        assertEquals(0, server.getRequestCount());
    }

    @Test
    public void discard_withoutServerRecord_removesLocallyOnly() throws Exception {
        PendingWorkout workout = checkedWorkout();

        Result result = discard(workout);

        assertEquals(workout.sessionId, result.savedId.get());
        assertNull(stored(workout));
        assertEquals(0, server.getRequestCount());
    }

    @Test
    public void discard_withHalfRecord_deletesOnServerFirst() throws Exception {
        PendingWorkout workout = checkedWorkout();
        workout.sessionMayExistOnServer = true;
        store.save(workout);
        server.enqueue(new MockResponse().setResponseCode(204));

        discard(workout);

        assertEquals("DELETE", server.takeRequest().getMethod());
        assertNull(stored(workout));
    }

    @Test
    public void discard_serverDeleteFails_keepsPendingToAskAgain() throws Exception {
        PendingWorkout workout = checkedWorkout();
        workout.sessionMayExistOnServer = true;
        store.save(workout);
        server.enqueue(new MockResponse().setResponseCode(500));

        Result result = discard(workout);

        assertEquals(R.string.error_pending_workout_discard_failed, result.failedMessage.get());
        assertNotNull(stored(workout));
    }
}

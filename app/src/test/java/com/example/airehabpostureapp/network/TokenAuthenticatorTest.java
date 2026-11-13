package com.example.airehabpostureapp.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * M11: 로그인 만료(401) 자동 갱신 (TokenAuthenticator)
 * 동시 401, 이미 갱신된 경우, 갱신 응답 4xx / 5xx / 네트워크 오류를 가짜 서버로 확인한다.
 */
public class TokenAuthenticatorTest {

    private static final String OLD_ACCESS = "old-access";
    private static final String NEW_ACCESS = "new-access";
    private static final String REFRESH = "refresh-1";

    /** 메모리 세션 저장소 */
    static final class FakeTokenStore implements TokenStore {
        volatile String accessToken = OLD_ACCESS;
        volatile String refreshToken = REFRESH;
        volatile boolean expired;

        @Override
        public String getBearerToken() {
            return accessToken == null ? null : "Bearer " + accessToken;
        }

        @Override
        public String getRefreshToken() {
            return refreshToken;
        }

        @Override
        public void saveSession(String accessToken, String refreshToken, String userId) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
        }

        @Override
        public void expireSession() {
            accessToken = null;
            refreshToken = null;
            expired = true;
        }
    }

    private MockWebServer server;
    private FakeTokenStore store;
    private final AtomicInteger refreshCalls = new AtomicInteger();
    private final AtomicInteger expiredCallbacks = new AtomicInteger();
    // 갱신 응답 (테스트마다 바꿈)
    private volatile MockResponse refreshResponse;
    private OkHttpClient client;

    private static MockResponse refreshSuccess() {
        return new MockResponse().setResponseCode(200).setBody("{\"access_token\":\"" + NEW_ACCESS
                + "\",\"refresh_token\":\"refresh-2\",\"user\":{\"id\":\"user-a\"}}");
    }

    @Before
    public void setUp() throws Exception {
        server = new MockWebServer();
        refreshResponse = refreshSuccess();
        // 데이터 요청: 새 토큰이면 200, 아니면 401 / 갱신 요청: 횟수를 세고 refreshResponse
        server.setDispatcher(new Dispatcher() {
            @NonNull
            @Override
            public MockResponse dispatch(@NonNull RecordedRequest request) throws InterruptedException {
                if (request.getPath().startsWith("/auth/v1/token")) {
                    refreshCalls.incrementAndGet();
                    // 동시 요청이 갱신 중에 겹치도록 잠깐 지연
                    Thread.sleep(200);
                    return refreshResponse;
                }
                String auth = request.getHeader("Authorization");
                if (("Bearer " + NEW_ACCESS).equals(auth)) {
                    return new MockResponse().setResponseCode(200).setBody("[]");
                }
                return new MockResponse().setResponseCode(401).setBody("{\"message\":\"JWT expired\"}");
            }
        });
        server.start();
        store = new FakeTokenStore();

        // 갱신 요청은 Authenticator가 없는 클라이언트로 (SupabaseClient와 같은 구조)
        OkHttpClient baseClient = new OkHttpClient();
        AuthApi refreshApi = new Retrofit.Builder()
                .baseUrl(server.url("/"))
                .client(baseClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(AuthApi.class);
        client = baseClient.newBuilder()
                .authenticator(new TokenAuthenticator(store, refreshApi, expiredCallbacks::incrementAndGet))
                .build();
    }

    @After
    public void tearDown() throws Exception {
        server.shutdown();
    }

    private Response get(String bearerToken) throws Exception {
        Request.Builder builder = new Request.Builder().url(server.url("/rest/v1/exercise_sessions"));
        if (bearerToken != null) {
            builder.header("Authorization", bearerToken);
        }
        return client.newCall(builder.build()).execute();
    }

    @Test
    public void expiredToken_refreshesOnce_andRetriesWithNewToken() throws Exception {
        try (Response response = get("Bearer " + OLD_ACCESS)) {
            assertEquals(200, response.code());
            assertEquals("Bearer " + NEW_ACCESS, response.request().header("Authorization"));
        }
        assertEquals(1, refreshCalls.get());
        assertEquals(NEW_ACCESS, store.accessToken);
        assertEquals("refresh-2", store.refreshToken);
        assertEquals(0, expiredCallbacks.get());
    }

    @Test
    public void concurrent401s_refreshOnlyOnce_andAllRetrySucceed() throws Exception {
        int count = 4;
        CountDownLatch done = new CountDownLatch(count);
        List<Integer> codes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            new Thread(() -> {
                try (Response response = get("Bearer " + OLD_ACCESS)) {
                    synchronized (codes) {
                        codes.add(response.code());
                    }
                } catch (Exception e) {
                    synchronized (codes) {
                        codes.add(-1);
                    }
                } finally {
                    done.countDown();
                }
            }).start();
        }
        assertTrue(done.await(10, TimeUnit.SECONDS));

        assertEquals(1, refreshCalls.get());
        assertEquals(count, codes.size());
        for (int code : codes) {
            assertEquals(200, code);
        }
    }

    @Test
    public void alreadyRefreshedByAnotherRequest_retriesWithoutRefreshing() throws Exception {
        // 다른 요청이 이미 갱신해 저장소에는 새 토큰이 있음 (이 요청은 예전 토큰으로 보냄)
        store.accessToken = NEW_ACCESS;
        try (Response response = get("Bearer " + OLD_ACCESS)) {
            assertEquals(200, response.code());
        }
        assertEquals(0, refreshCalls.get());
    }

    @Test
    public void refreshRejected4xx_expiresSession_andNotifies() throws Exception {
        refreshResponse = new MockResponse().setResponseCode(400)
                .setBody("{\"error_code\":\"refresh_token_not_found\"}");
        try (Response response = get("Bearer " + OLD_ACCESS)) {
            assertEquals(401, response.code());
        }
        assertTrue(store.expired);
        assertNull(store.getBearerToken());
        assertEquals(1, expiredCallbacks.get());
    }

    @Test
    public void refreshServerError5xx_keepsSession() throws Exception {
        refreshResponse = new MockResponse().setResponseCode(503);
        try (Response response = get("Bearer " + OLD_ACCESS)) {
            assertEquals(401, response.code());
        }
        assertFalse(store.expired);
        assertEquals(OLD_ACCESS, store.accessToken);
        assertEquals(0, expiredCallbacks.get());
    }

    @Test
    public void refreshNetworkError_keepsSession() throws Exception {
        refreshResponse = new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START);
        try (Response response = get("Bearer " + OLD_ACCESS)) {
            assertEquals(401, response.code());
        }
        assertFalse(store.expired);
        assertEquals(OLD_ACCESS, store.accessToken);
    }

    @Test
    public void noRefreshToken_expiresWithoutCallingServer() throws Exception {
        store.refreshToken = null;
        try (Response response = get("Bearer " + OLD_ACCESS)) {
            assertEquals(401, response.code());
        }
        assertEquals(0, refreshCalls.get());
        assertTrue(store.expired);
        assertEquals(1, expiredCallbacks.get());
    }

    @Test
    public void requestWithoutToken_isNotRefreshed() throws Exception {
        try (Response response = get(null)) {
            assertEquals(401, response.code());
        }
        assertEquals(0, refreshCalls.get());
        assertFalse(store.expired);
    }

    @Test
    public void stillUnauthorizedAfterRefresh_doesNotLoop() throws Exception {
        // 갱신은 성공했지만 새 토큰으로도 401 (서버가 다른 이유로 거부)
        refreshResponse = new MockResponse().setResponseCode(200).setBody(
                "{\"access_token\":\"other-token\",\"refresh_token\":\"refresh-2\",\"user\":{\"id\":\"user-a\"}}");
        try (Response response = get("Bearer " + OLD_ACCESS)) {
            assertEquals(401, response.code());
        }
        assertEquals(1, refreshCalls.get());
    }
}

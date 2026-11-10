package com.example.airehabpostureapp.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import com.example.airehabpostureapp.BodyConditionActivity;
import com.example.airehabpostureapp.GoalSetupActivity;
import com.example.airehabpostureapp.HomeActivity;
import com.example.airehabpostureapp.R;
import com.example.airehabpostureapp.UserSetupActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

// L15: 로그인 직후 초기 설정 확인 → 이동할 화면 분기 (profiles → user_body_conditions → user_goals)
public class OnboardingCheckerTest {

    private MockWebServer server;
    private Retrofit retrofit;
    // 경로별 응답 (테스트마다 설정)
    private volatile MockResponse profile;
    private volatile MockResponse profileInsert;
    private volatile MockResponse bodyConditions;
    private volatile MockResponse goals;
    private final AtomicReference<String> insertedProfileBody = new AtomicReference<>();

    private final SessionInfo session = new SessionInfo() {
        @Override
        public String getBearerToken() {
            return "Bearer test";
        }

        @Override
        public String getUserId() {
            return "user-a";
        }
    };

    private static MockResponse json(String body) {
        return new MockResponse().setResponseCode(200).setBody(body);
    }

    @Before
    public void setUp() throws Exception {
        server = new MockWebServer();
        server.setDispatcher(new Dispatcher() {
            @NonNull
            @Override
            public MockResponse dispatch(@NonNull RecordedRequest request) {
                String path = request.getPath();
                if (path.startsWith("/rest/v1/profiles") && "POST".equals(request.getMethod())) {
                    insertedProfileBody.set(request.getBody().readUtf8());
                    return profileInsert;
                }
                if (path.startsWith("/rest/v1/profiles")) {
                    return profile;
                }
                if (path.startsWith("/rest/v1/user_body_conditions")) {
                    return bodyConditions;
                }
                if (path.startsWith("/rest/v1/user_goals")) {
                    return goals;
                }
                return new MockResponse().setResponseCode(404);
            }
        });
        server.start();
        retrofit = new Retrofit.Builder()
                .baseUrl(server.url("/"))
                .addConverterFactory(GsonConverterFactory.create())
                .callbackExecutor(Runnable::run)
                .build();
        profile = json("[{\"exercise_experience\":\"beginner\"}]");
        profileInsert = new MockResponse().setResponseCode(201);
        bodyConditions = json("[{\"id\":\"b1\"}]");
        goals = json("[{\"id\":\"g1\"}]");
    }

    @After
    public void tearDown() throws Exception {
        server.shutdown();
    }

    private static final class Result implements OnboardingChecker.Listener {
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicReference<Class<?>> destination = new AtomicReference<>();
        final AtomicInteger error = new AtomicInteger();

        @Override
        public void onDestination(Class<?> activityClass) {
            destination.set(activityClass);
            done.countDown();
        }

        @Override
        public void onError(int messageRes) {
            error.set(messageRes);
            done.countDown();
        }
    }

    private Result check(String email) throws InterruptedException {
        Result result = new Result();
        new OnboardingChecker(session, retrofit, CallTracker.NONE, result).check(email);
        assertTrue("응답을 기다리다 시간 초과", result.done.await(5, TimeUnit.SECONDS));
        return result;
    }

    @Test
    public void allDone_goesHome() throws Exception {
        assertEquals(HomeActivity.class, check("a@b.com").destination.get());
    }

    @Test
    public void noProfile_createsProfileFromEmail_thenUserSetup() throws Exception {
        profile = json("[]");
        Result result = check("rehab.user@example.com");
        assertEquals(UserSetupActivity.class, result.destination.get());
        assertTrue(insertedProfileBody.get().contains("\"name\":\"rehab.user\""));
        assertTrue(insertedProfileBody.get().contains("\"id\":\"user-a\""));
    }

    @Test
    public void noProfile_andNoEmail_isProfileError() throws Exception {
        profile = json("[]");
        Result result = check("");
        assertEquals(R.string.error_profile_save_failed, result.error.get());
        assertNull(insertedProfileBody.get());
    }

    @Test
    public void profileWithoutExperience_goesUserSetup() throws Exception {
        profile = json("[{\"exercise_experience\":null}]");
        assertEquals(UserSetupActivity.class, check("a@b.com").destination.get());
    }

    @Test
    public void noBodyConditions_goesBodyCondition() throws Exception {
        bodyConditions = json("[]");
        assertEquals(BodyConditionActivity.class, check("a@b.com").destination.get());
    }

    @Test
    public void noGoals_goesGoalSetup() throws Exception {
        goals = json("[]");
        assertEquals(GoalSetupActivity.class, check("a@b.com").destination.get());
    }

    @Test
    public void serverError_isOnboardingError() throws Exception {
        bodyConditions = new MockResponse().setResponseCode(500);
        assertEquals(R.string.error_onboarding_check_failed, check("a@b.com").error.get());
    }

    @Test
    public void networkError_isNetworkMessage() throws Exception {
        profile = new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START);
        assertEquals(R.string.error_network, check("a@b.com").error.get());
    }
}

package com.example.airehabpostureapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.app.Activity;
import android.app.UiAutomation;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.example.airehabpostureapp.network.SessionManager;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 에뮬레이터 확인용 (로그인 없이 확인할 수 있는 안정성 항목)
 * Espresso 3.5/3.6은 API 37에서 InputManager.getInstance가 없어 동작하지 않으므로
 * 화면 조작은 Activity 메서드 / UiAutomation(접근성 노드)으로 한다.
 */
@RunWith(AndroidJUnit4.class)
public class StabilityFlowTest {

    private static final long DIALOG_TIMEOUT_MS = 5000;

    private final Context context = ApplicationProvider.getApplicationContext();
    private final UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();

    @Before
    public void setUp() {
        uiAutomation.grantRuntimePermission(context.getPackageName(), Manifest.permission.CAMERA);
        // 로그인이 필요한 화면이므로 테스트용 가짜 세션 (서버 요청은 하지 않는 항목만 확인)
        new SessionManager(context).saveSession("test-access-token", "test-refresh-token", "test-user");
    }

    @After
    public void tearDown() {
        // 에뮬레이터의 원래 상태(로그아웃)로 되돌림
        SessionManager sessionManager = new SessionManager(context);
        sessionManager.clear();
        sessionManager.clearSessionExpiredNotice();
    }

    // M3: 로그인이 만료된 뒤(앱이 백그라운드였다가 화면이 보이는 경우 포함) 로그인이 필요한 화면이 보이면 안내 → 로그인 화면
    @Test
    public void expiredSession_showsReLoginDialog_thenLogin() {
        new SessionManager(context).expireSession();
        try (ActivityScenario<GoalChallengeActivity> scenario = ActivityScenario.launch(GoalChallengeActivity.class)) {
            assertNotNull("로그인 만료 안내가 뜨지 않음",
                    waitForText(context.getString(R.string.session_expired_title)));
            AccessibilityNodeInfo loginButton = waitForText(context.getString(R.string.session_expired_login));
            assertNotNull(loginButton);
            loginButton.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            // 로그인 화면으로 이동하고 기존 화면은 닫힘
            long deadline = SystemClock.uptimeMillis() + DIALOG_TIMEOUT_MS;
            while (scenario.getState() != Lifecycle.State.DESTROYED && SystemClock.uptimeMillis() < deadline) {
                SystemClock.sleep(200);
            }
            assertEquals(Lifecycle.State.DESTROYED, scenario.getState());
            assertFalse(new SessionManager(context).hasSessionExpiredNotice());
        }
    }

    private Intent exerciseIntent() {
        Intent intent = new Intent(context, ExerciseActivity.class);
        intent.putExtra(ExerciseFlowExtras.EXERCISE_NAME, "의자 스쿼트");
        return intent;
    }

    private static int timerSeconds(ActivityScenario<ExerciseActivity> scenario) {
        AtomicReference<String> text = new AtomicReference<>();
        scenario.onActivity(activity ->
                text.set(((TextView) activity.findViewById(R.id.exerciseTimerText)).getText().toString()));
        String[] parts = text.get().split(":");
        return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
    }

    // 화면에 이 글자가 보이는 노드 (없으면 timeout까지 기다린 뒤 null)
    private AccessibilityNodeInfo waitForText(String text) {
        long deadline = SystemClock.uptimeMillis() + DIALOG_TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            AccessibilityNodeInfo root = uiAutomation.getRootInActiveWindow();
            if (root != null) {
                // 글자를 "포함"하는 노드를 찾으므로, 글자가 정확히 같은 노드(버튼 등)를 먼저 고름
                List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(text);
                for (AccessibilityNodeInfo node : nodes) {
                    if (node.getText() != null && text.contentEquals(node.getText())) {
                        return node;
                    }
                }
                if (!nodes.isEmpty()) {
                    return nodes.get(0);
                }
            }
            SystemClock.sleep(200);
        }
        return null;
    }

    private boolean isTextShown(String text) {
        AccessibilityNodeInfo root = uiAutomation.getRootInActiveWindow();
        return root != null && !root.findAccessibilityNodeInfosByText(text).isEmpty();
    }

    // H1: 화면 재생성(회전 / 시스템 종료 후 복구) 후 경과 시간 유지
    @Test
    public void exercise_recreate_keepsElapsedTime() throws InterruptedException {
        try (ActivityScenario<ExerciseActivity> scenario = ActivityScenario.launch(exerciseIntent())) {
            Thread.sleep(3500);
            int before = timerSeconds(scenario);
            assertTrue("타이머가 진행되지 않음: " + before, before >= 3);

            scenario.recreate();

            int after = timerSeconds(scenario);
            assertTrue("재생성 후 경과 시간이 초기화됨: " + before + " → " + after, after >= before);
        }
    }

    private void shell(String command) {
        try {
            uiAutomation.executeShellCommand(command).close();
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }

    // M4: 불러오기 실패(네트워크 끊김) → "다시 시도" Snackbar, 누르면 다시 요청 (여전히 실패하면 다시 표시)
    @Test
    public void recordList_networkError_showsRetrySnackbar() {
        String retry = context.getString(R.string.retry);
        shell("svc wifi disable");
        shell("svc data disable");
        try {
            SystemClock.sleep(1500);
            try (ActivityScenario<RecordListActivity> ignored = ActivityScenario.launch(RecordListActivity.class)) {
                AccessibilityNodeInfo retryButton = waitForText(retry);
                assertNotNull("다시 시도 Snackbar가 뜨지 않음", retryButton);
                retryButton.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                SystemClock.sleep(1000);
                assertNotNull("다시 시도 후 실패했는데 Snackbar가 다시 뜨지 않음", waitForText(retry));
            }
        } finally {
            shell("svc wifi enable");
            shell("svc data enable");
        }
    }

    // L9: 모델을 분석 스레드에서 불러온 뒤 카메라에 연결 (모델 / 카메라 오류 문구가 없어야 함)
    @Test
    public void exercise_loadsPoseModelInBackground_withoutErrorMessage() throws InterruptedException {
        try (ActivityScenario<ExerciseActivity> scenario = ActivityScenario.launch(exerciseIntent())) {
            Thread.sleep(5000);
            AtomicBoolean messageShown = new AtomicBoolean(true);
            AtomicReference<String> message = new AtomicReference<>();
            scenario.onActivity(activity -> {
                TextView cameraMessage = activity.findViewById(R.id.cameraMessageText);
                messageShown.set(cameraMessage.getVisibility() == android.view.View.VISIBLE);
                message.set(cameraMessage.getText().toString());
            });
            assertFalse("카메라 / 모델 오류 문구가 보임: " + message.get(), messageShown.get());
        }
    }

    // H3: 시스템 Back / 뒤로가기 버튼 → 확인 창, 취소하면 운동 화면 유지
    @Test
    public void exercise_back_showsConfirmDialog_andCancelKeepsScreen() {
        String message = context.getString(R.string.exit_without_saving_message);
        String cancel = context.getString(R.string.cancel);
        try (ActivityScenario<ExerciseActivity> scenario = ActivityScenario.launch(exerciseIntent())) {
            scenario.onActivity(activity -> activity.getOnBackPressedDispatcher().onBackPressed());
            assertNotNull("시스템 Back에 확인 창이 뜨지 않음", waitForText(message));
            AccessibilityNodeInfo cancelButton = waitForText(cancel);
            assertNotNull(cancelButton);
            cancelButton.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            SystemClock.sleep(800);
            assertFalse("취소 후에도 확인 창이 남아 있음", isTextShown(message));
            assertTrue(scenario.getState().isAtLeast(Lifecycle.State.RESUMED));

            scenario.onActivity(activity -> activity.findViewById(R.id.backButton).performClick());
            assertNotNull("뒤로가기 버튼에 확인 창이 뜨지 않음", waitForText(message));
            AtomicBoolean finishing = new AtomicBoolean(true);
            scenario.onActivity(activity -> finishing.set(activity.isFinishing()));
            assertFalse("확인 전에 화면이 닫힘", finishing.get());
        }
    }

    // L3: 운동 전 체크 화면 재생성 후 불편감 / 몸 상태 선택 유지
    @Test
    public void preCheck_recreate_keepsSelections() {
        Intent intent = new Intent(context, PreCheckActivity.class);
        intent.putExtra(ExerciseFlowExtras.EXERCISE_NAME, "런지");
        try (ActivityScenario<PreCheckActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                activity.findViewById(R.id.discomfortButton8).performClick();
                activity.findViewById(R.id.conditionGoodButton).performClick();
            });

            scenario.recreate();

            scenario.onActivity(activity -> {
                assertTrue(isSelected(activity, R.id.discomfortButton8));
                assertTrue(isSelected(activity, R.id.conditionGoodButton));
                assertFalse(isSelected(activity, R.id.discomfortButton5));
                assertEquals("good", activity.findViewById(R.id.conditionGoodButton).getTag());
            });
        }
    }

    private static boolean isSelected(Activity activity, int viewId) {
        return activity.findViewById(viewId).isSelected();
    }
}

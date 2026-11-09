package com.example.airehabpostureapp;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.util.Locale;

/**
 * 운동 화면 음성 안내 (Android 기본 TextToSpeech)
 * 전신이 보이도록 멀리 떨어지면 화면 글자가 안 보여서, 안내 문구와 횟수를 소리로 읽어 준다.
 *
 * - 횟수("3회")는 바로 읽는다. 읽던 안내 문구가 있으면 끊고 횟수를 먼저 읽는다.
 * - 안내 문구는 MESSAGE_SETTLE_MS 동안 바뀌지 않아야 읽는다. (경계에서 문구가 깜빡일 때 계속 말하지 않도록)
 *   횟수를 읽는 중이면 횟수 다음에 이어서 읽는다.
 * - 같은 문구는 SAME_MESSAGE_GAP_MS 안에 다시 읽지 않는다.
 * - 한국어 음성이 없거나 TTS 엔진이 없으면 아무것도 읽지 않는다. (화면 문구만 보임, 앱은 그대로 동작)
 *
 * UI 스레드에서만 호출해야 한다.
 */
public class VoiceGuide {

    private static final String TAG = "PoseDebug";
    private static final long MESSAGE_SETTLE_MS = 400;
    private static final long SAME_MESSAGE_GAP_MS = 3000;
    // 횟수 하나를 읽는 데 걸리는 대략적인 시간. 이 안에 안내 문구가 오면 끊지 않고 뒤에 이어서 읽음
    private static final long COUNT_SPEAKING_MS = 1200;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextToSpeech tts;
    private boolean ready;

    private String pendingMessage;
    private String lastSpokenMessage;
    private long lastSpokenMessageAtMs;
    private long lastCountAtMs = -COUNT_SPEAKING_MS;

    private final Runnable speakPendingMessage = this::speakPendingMessage;

    public VoiceGuide(Context context) {
        try {
            tts = new TextToSpeech(context.getApplicationContext(), this::onInit);
        } catch (RuntimeException e) {
            Log.w(TAG, "voice guide off: TTS 시작 실패", e);
            tts = null;
        }
    }

    private void onInit(int status) {
        if (tts == null) {
            return;
        }
        if (status != TextToSpeech.SUCCESS) {
            Log.w(TAG, "voice guide off: TTS 엔진 없음 status=" + status);
            return;
        }
        int result;
        try {
            result = tts.setLanguage(Locale.KOREAN);
        } catch (RuntimeException e) {
            result = TextToSpeech.LANG_NOT_SUPPORTED;
        }
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "voice guide off: 한국어 음성 없음 result=" + result);
            return;
        }
        ready = true;
        Log.d(TAG, "voice guide on");
        // 준비되기 전에 들어온 안내가 있으면 읽음
        if (pendingMessage != null) {
            handler.removeCallbacks(speakPendingMessage);
            handler.post(speakPendingMessage);
        }
    }

    /** 1회가 세어졌을 때: 읽던 안내를 끊고 바로 읽음 */
    public void speakCount(String countText) {
        if (!ready) {
            return;
        }
        lastCountAtMs = SystemClock.uptimeMillis();
        speak(countText, TextToSpeech.QUEUE_FLUSH);
    }

    /** 화면 안내 문구가 바뀌었을 때 */
    public void speakMessage(String message) {
        pendingMessage = message;
        handler.removeCallbacks(speakPendingMessage);
        handler.postDelayed(speakPendingMessage, MESSAGE_SETTLE_MS);
    }

    private void speakPendingMessage() {
        String message = pendingMessage;
        if (!ready || message == null) {
            return;
        }
        pendingMessage = null;
        long now = SystemClock.uptimeMillis();
        if (message.equals(lastSpokenMessage) && now - lastSpokenMessageAtMs < SAME_MESSAGE_GAP_MS) {
            return;
        }
        lastSpokenMessage = message;
        lastSpokenMessageAtMs = now;
        // 횟수를 읽는 중이면 끊지 않고 뒤에 이어서, 아니면 읽던 안내를 끊고 새 안내
        int queueMode = now - lastCountAtMs < COUNT_SPEAKING_MS
                ? TextToSpeech.QUEUE_ADD : TextToSpeech.QUEUE_FLUSH;
        speak(message, queueMode);
    }

    private void speak(String text, int queueMode) {
        try {
            tts.speak(text, queueMode, null, "guide");
        } catch (RuntimeException e) {
            Log.w(TAG, "voice guide speak 실패", e);
        }
    }

    /** 화면이 가려질 때(일시정지 화면 등): 읽던 것과 기다리던 안내를 멈춤 */
    public void stop() {
        handler.removeCallbacks(speakPendingMessage);
        pendingMessage = null;
        lastSpokenMessage = null;
        if (ready) {
            try {
                tts.stop();
            } catch (RuntimeException ignored) {
                // 엔진 연결이 끊긴 경우 무시
            }
        }
    }

    /** 화면을 나갈 때 */
    public void shutdown() {
        stop();
        ready = false;
        if (tts != null) {
            try {
                tts.shutdown();
            } catch (RuntimeException ignored) {
                // 엔진 연결이 끊긴 경우 무시
            }
            tts = null;
        }
    }
}

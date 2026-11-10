package com.example.airehabpostureapp;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
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
 * - L2: 읽기 전에 오디오 포커스(잠시, 다른 소리는 작게)를 요청하고 다 읽으면 반납한다.
 *   전화가 오거나 다른 앱이 소리를 내서 포커스를 잃으면 바로 멈추고, 통화 중처럼 포커스를 받지 못하면 읽지 않는다.
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
    private final AudioManager audioManager;
    private final AudioFocusRequest focusRequest;
    private boolean hasAudioFocus;
    private TextToSpeech tts;
    private boolean ready;

    private String pendingMessage;
    private String lastSpokenMessage;
    private long lastSpokenMessageAtMs;
    private long lastCountAtMs = -COUNT_SPEAKING_MS;

    private final Runnable speakPendingMessage = this::speakPendingMessage;

    public VoiceGuide(Context context) {
        audioManager = (AudioManager) context.getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
        focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(this::onAudioFocusChange, handler)
                .build();
        try {
            tts = new TextToSpeech(context.getApplicationContext(), this::onInit);
            tts.setAudioAttributes(attributes);
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
        // 다 읽으면 오디오 포커스 반납 (TTS 스레드에서 불리므로 메인 스레드로)
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
            }

            @Override
            public void onDone(String utteranceId) {
                handler.post(VoiceGuide.this::abandonFocusIfIdle);
            }

            @Override
            public void onError(String utteranceId) {
                handler.post(VoiceGuide.this::abandonFocusIfIdle);
            }
        });
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "voice guide on");
        }
        // 준비되기 전에 들어온 안내가 있으면 읽음
        if (pendingMessage != null) {
            handler.removeCallbacks(speakPendingMessage);
            handler.post(speakPendingMessage);
        }
    }

    /**
     * 화면 안내 문구 → 읽을 문구: 유지형의 "(3/20초)"처럼 끝의 괄호 안 숫자는 읽지 않음
     * (ExerciseActivity에서 옮김, 동작 같음)
     */
    public static String toSpeechText(String text) {
        return text.replaceAll("\\s*\\(.*\\)\\s*$", "");
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
        // 통화 중 등 포커스를 받지 못하면 읽지 않음
        if (!requestFocus()) {
            return;
        }
        try {
            tts.speak(text, queueMode, null, "guide");
        } catch (RuntimeException e) {
            Log.w(TAG, "voice guide speak 실패", e);
        }
    }

    private boolean requestFocus() {
        if (hasAudioFocus) {
            return true;
        }
        hasAudioFocus = audioManager != null
                && audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        return hasAudioFocus;
    }

    private void abandonFocus() {
        if (hasAudioFocus && audioManager != null) {
            audioManager.abandonAudioFocusRequest(focusRequest);
        }
        hasAudioFocus = false;
    }

    private void abandonFocusIfIdle() {
        try {
            if (tts != null && tts.isSpeaking()) {
                return;
            }
        } catch (RuntimeException ignored) {
            // 엔진 연결이 끊긴 경우: 반납
        }
        abandonFocus();
    }

    // 전화 / 다른 앱 소리로 포커스를 잃으면 읽던 것을 멈춤 (다음 안내 때 다시 요청)
    private void onAudioFocusChange(int focusChange) {
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS
                || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "voice guide paused: audio focus lost " + focusChange);
            }
            stop();
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
        abandonFocus();
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

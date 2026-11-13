package com.example.airehabpostureapp.pose;

/**
 * 안내 문구 음성의 간격 규칙 (Android 의존성 없는 순수 Java, VoiceGuide가 사용)
 * - 음성과 음성 사이(시작 기준)는 최소 MIN_GAP_MS. 읽는 중이면 다 읽을 때까지 기다림
 * - 기다리는 동안 문구가 여러 번 바뀌면 쌓아 두지 않고 가장 최근 것 하나만 읽음
 * - 횟수("N회")와 목표 도달 안내는 이 규칙을 거치지 않고 바로 읽음 (건너뛰지 않음). 대신 그 시각을 알려 줘서
 *   다음 안내 문구가 그 뒤로 MIN_GAP_MS를 지키게 함 (onUtteranceStarted)
 */
public final class VoicePacer {

    public static final long MIN_GAP_MS = 2000;
    // 읽는 중일 때 다시 확인하는 간격
    static final long BUSY_RECHECK_MS = 200;

    private String pending;
    private long lastStartMs = Long.MIN_VALUE / 2;

    /** 새 안내 문구 (기다리던 문구가 있으면 버리고 이것만 남김) */
    public void offer(String message) {
        pending = message;
    }

    /** 횟수 / 목표 안내 / 안내 문구를 읽기 시작함 */
    public void onUtteranceStarted(long nowMs) {
        lastStartMs = nowMs;
    }

    /** 지금 읽을 안내 문구. 아직 읽을 수 없거나 기다리는 문구가 없으면 null */
    public String poll(long nowMs, boolean speaking) {
        if (pending == null || waitMs(nowMs, speaking) > 0) {
            return null;
        }
        String message = pending;
        pending = null;
        lastStartMs = nowMs;
        return message;
    }

    /** 기다리는 문구를 읽을 수 있을 때까지 남은 시간 (0이면 지금) */
    public long waitMs(long nowMs, boolean speaking) {
        if (speaking) {
            return BUSY_RECHECK_MS;
        }
        return Math.max(0, lastStartMs + MIN_GAP_MS - nowMs);
    }

    public boolean hasPending() {
        return pending != null;
    }

    public void clear() {
        pending = null;
    }
}

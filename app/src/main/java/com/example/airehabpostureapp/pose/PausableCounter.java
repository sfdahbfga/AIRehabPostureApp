package com.example.airehabpostureapp.pose;

import java.util.ArrayList;

/**
 * 측정기 일시정지 (Android 의존성 없는 순수 Java)
 * 운동 화면에서 "나갈까요?" 확인 창이 떠 있는 동안 프레임을 측정기에 넘기지 않는다.
 * 다시 이어갈 때는 진행 중이던 동작을 버리고 끝난 회차만 남긴다. (화면 복구 때와 같은 restoreResults 방식)
 * → 창이 떠 있는 동안 화면 밖에서 한 움직임이 이어서 1회로 세지지 않는다.
 */
public final class PausableCounter {

    private final ExerciseCounter counter;
    private boolean paused;

    public PausableCounter(ExerciseCounter counter) {
        this.counter = counter;
    }

    /** 일시정지 중이면 프레임을 무시하고 null */
    public RepetitionCounter.RepResult onFrame(long timestampMs, double jointAngle, double trunkLean,
                                               double auxAngle) {
        if (paused) {
            return null;
        }
        return counter.onFrame(timestampMs, jointAngle, trunkLean, auxAngle);
    }

    public void pause() {
        paused = true;
    }

    /** 다시 이어감: 진행 중이던 동작은 버리고 끝난 회차 결과만 유지 */
    public void resume() {
        if (!paused) {
            return;
        }
        paused = false;
        counter.restoreResults(new ArrayList<>(counter.getResults()));
    }

    public boolean isPaused() {
        return paused;
    }
}

package com.example.airehabpostureapp.pose;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

/**
 * "나갈까요?" 확인 창이 떠 있는 동안(일시정지) 횟수가 세지지 않는지,
 * "취소"로 이어간 뒤에는 정상으로 세는지 확인 (의자 스쿼트 기준, 30fps 가짜 각도)
 */
public class PausableCounterTest {

    private static final long FRAME_MS = 33;
    private static final double STAND = 175;
    private static final double TRUNK = 30;
    private static final double SQUAT_HIP = 80;

    private RepetitionCounter counter;
    private PausableCounter pausable;
    private long now;
    private int completedReturned;

    @Before
    public void setUp() {
        counter = new RepetitionCounter(ExerciseCriteria.CHAIR_SQUAT);
        pausable = new PausableCounter(counter);
        now = 0;
        completedReturned = 0;
    }

    private void frame(double knee, double hip) {
        if (pausable.onFrame(now, knee, TRUNK, hip) != null) {
            completedReturned++;
        }
        now += FRAME_MS;
    }

    private void hold(double knee, double hip, long durationMs) {
        for (long t = 0; t < durationMs; t += FRAME_MS) {
            frame(knee, hip);
        }
    }

    private void move(double fromKnee, double toKnee, double fromHip, double toHip, long durationMs) {
        int frames = (int) (durationMs / FRAME_MS);
        for (int i = 0; i <= frames; i++) {
            double ratio = (double) i / frames;
            frame(fromKnee + (toKnee - fromKnee) * ratio, fromHip + (toHip - fromHip) * ratio);
        }
    }

    private void squat() {
        move(STAND, 105, STAND, SQUAT_HIP, 1200);
        hold(105, SQUAT_HIP, 300);
        move(105, STAND, SQUAT_HIP, STAND, 1200);
        hold(STAND, STAND, 400);
    }

    @Test
    public void whilePaused_fullSquats_areNotCounted() {
        hold(STAND, STAND, 500);
        squat();
        assertEquals(1, counter.getCompletedReps());

        pausable.pause();
        assertTrue(pausable.isPaused());
        squat();
        squat();
        assertEquals(1, counter.getCompletedReps());
        assertEquals(1, completedReturned);
        assertNull(pausable.onFrame(now, STAND, TRUNK, STAND));
    }

    @Test
    public void afterResume_countingContinues_andKeepsEarlierReps() {
        hold(STAND, STAND, 500);
        squat();
        pausable.pause();
        squat();
        pausable.resume();
        assertFalse(pausable.isPaused());
        // 끝난 회차는 그대로, 다시 편 상태부터 측정
        assertEquals(1, counter.getCompletedReps());
        hold(STAND, STAND, 500);
        squat();
        assertEquals(2, counter.getCompletedReps());
        assertEquals(2, counter.getResults().get(1).repNumber);
    }

    @Test
    public void squatStartedBeforeDialog_isNotFinishedAfterResume() {
        // 앉은 상태에서 창이 뜸 → 창이 떠 있는 동안 일어섬 → "취소" 후 서 있기
        // 진행 중이던 동작은 버려지므로 1회로 세지 않음
        hold(STAND, STAND, 500);
        move(STAND, 105, STAND, SQUAT_HIP, 1200);
        hold(105, SQUAT_HIP, 300);
        assertTrue(counter.isRepInProgress());
        pausable.pause();
        move(105, STAND, SQUAT_HIP, STAND, 1200);
        pausable.resume();
        assertFalse(counter.isRepInProgress());
        hold(STAND, STAND, 1000);
        assertEquals(0, counter.getCompletedReps());
        assertEquals(0, completedReturned);
    }

    @Test
    public void resumeWithoutPause_doesNothing() {
        hold(STAND, STAND, 500);
        move(STAND, 105, STAND, SQUAT_HIP, 1200);
        pausable.resume();
        // 일시정지하지 않았으면 진행 중인 동작을 버리지 않음
        assertTrue(counter.isRepInProgress());
        hold(105, SQUAT_HIP, 300);
        move(105, STAND, SQUAT_HIP, STAND, 1200);
        hold(STAND, STAND, 400);
        assertEquals(1, counter.getCompletedReps());
    }
}

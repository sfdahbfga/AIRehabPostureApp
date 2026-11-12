package com.example.airehabpostureapp.pose;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * 하체 스트레칭 다리 정하기 / 다시 정하기
 * 1차 실측 로그(2026-11-12 22:48~22:51)의 값을 사용:
 * - 22:48:18~19 서서 움직임: L 엉덩이 162~176도, visibility L 0.82~0.92 / R 0.32~0.71 → 예전에는 여기서 L로 정해짐
 * - 22:48:22 L이 카메라 쪽으로 잠깐 앉음: L 111도, visibility L 0.94 / R 0.43
 * - 22:48:26~ 몸 방향을 바꿔 앉음: R이 카메라 쪽 (visibility R 0.88~0.99, L 0.25~0.47), 숙이면 R 엉덩이 53~90도
 */
public class StretchLegLockTest {

    private static final ExerciseCriteria STRETCH = ExerciseCriteria.SEATED_HAMSTRING_STRETCH;
    private static final long FRAME_MS = 33;

    private final LegSelector selector = new LegSelector();
    private long now;

    private static LegSelector.LegSample leg(String label, double hip, float visibility) {
        // 하체 스트레칭: 판정 각도 = 엉덩이 각도, 무릎은 편 상태(178도)
        return new LegSelector.LegSample(label, hip, Double.NaN, hip, visibility, visibility, 178);
    }

    /** 다리를 정하기 전 프레임들 (ExerciseActivity와 같은 조건) */
    private void observeLock(double leftHip, float leftVis, double rightHip, float rightVis, long ms) {
        for (long t = 0; t < ms; t += FRAME_MS) {
            selector.observeForLock(leg("L", leftHip, leftVis), leg("R", rightHip, rightVis), now,
                    STRETCH.lockMinAngle, STRETCH.lockMaxAngle);
            now += FRAME_MS;
        }
    }

    /** 다리가 정해진 뒤 프레임들: 다시 정해진 시각(ms, 시작부터)을 돌려줌. 안 바뀌면 -1 */
    private long observeRelock(double leftHip, float leftVis, double rightHip, float rightVis, long ms,
                               boolean holdInProgress) {
        long start = now;
        long relockedAt = -1;
        for (long t = 0; t < ms; t += FRAME_MS) {
            if (selector.observeForRelock(leg("L", leftHip, leftVis), leg("R", rightHip, rightVis), now,
                    holdInProgress, STRETCH.relockHiddenMs) && relockedAt < 0) {
                relockedAt = now - start;
            }
            now += FRAME_MS;
        }
        return relockedAt;
    }

    private void lockLeftWhileSeated() {
        // 22:48:22: L이 카메라 쪽으로 앉음 (예전 / 지금 모두 L로 정해지는 상황)
        observeLock(111, 0.94f, 115, 0.43f, 1200);
        assertEquals("L", selector.getLockedLabel());
    }

    // ---------- 1. 앉은 뒤에만 정하기 ----------

    @Test
    public void criteria_stretchOnly() {
        assertEquals(150, STRETCH.lockMaxAngle, 1e-9);
        assertEquals(2000, STRETCH.relockHiddenMs);
        for (ExerciseCriteria c : new ExerciseCriteria[]{
                ExerciseCriteria.KNEE_FLEXION, ExerciseCriteria.CHAIR_SQUAT, ExerciseCriteria.LUNGE}) {
            assertEquals(c.id, 0, c.lockMaxAngle, 1e-9);
            assertEquals(c.id, 0, c.relockHiddenMs);
        }
    }

    @Test
    public void movingBeforeSitting_doesNotLock() {
        // 22:48:18~19: 서서 움직이는 중 L이 훨씬 잘 보임 (엉덩이 170도대) → 2초가 지나도 정하지 않음
        observeLock(173, 0.92f, 170, 0.32f, 2000);
        assertFalse(selector.isLocked());
        // 앉는 중 잠깐 150도 아래로 내려가도 1초가 안 되면 정하지 않음
        observeLock(143, 0.90f, 150, 0.23f, 500);
        observeLock(175, 0.96f, 175, 0.72f, 500);
        assertFalse(selector.isLocked());
    }

    @Test
    public void sameStandingFrames_lockedWithOldRule() {
        // 비교: 예전 규칙(각도 보지 않음)이면 서서 움직이는 1초 만에 L로 정해짐
        LegSelector old = new LegSelector();
        long t = 0;
        boolean locked = false;
        for (; t < 1100; t += FRAME_MS) {
            locked |= old.observeForLock(leg("L", 173, 0.92f), leg("R", 170, 0.32f), t, STRETCH.lockMinAngle);
        }
        assertTrue(locked);
        assertEquals("L", old.getLockedLabel());
    }

    @Test
    public void seatedWithCameraSideLegVisible_locksThatLeg() {
        observeLock(173, 0.92f, 170, 0.32f, 2000);
        // 22:48:40~44: 앉아서 R이 카메라 쪽 (R 엉덩이 125~135도)
        observeLock(131, 0.45f, 128, 0.93f, 1200);
        assertEquals("R", selector.getLockedLabel());
    }

    // ---------- 2. 다시 정하기 ----------

    @Test
    public void wronglyLockedLeg_hiddenOverTwoSeconds_relocksToVisibleLeg() {
        lockLeftWhileSeated();
        // 22:48:26~: 몸 방향을 바꿔 앉음 → L은 가려지고(0.33) R이 잘 보임(0.92)
        long relockedAt = observeRelock(100, 0.33f, 100, 0.92f, 3000, false);
        assertEquals("R", selector.getLockedLabel());
        assertTrue("2초 넘게 지난 뒤 바뀜: " + relockedAt, relockedAt > 2000 && relockedAt < 2100);
        assertEquals(1, selector.getRelockCount());
        // 이제 R이 판정 다리
        LegSelector.LegSample chosen = selector.selectLocked(leg("L", 100, 0.33f), leg("R", 57, 0.80f));
        assertEquals("R", chosen.label);
    }

    @Test
    public void hiddenUnderTwoSeconds_notRelocked() {
        lockLeftWhileSeated();
        assertEquals(-1, observeRelock(100, 0.33f, 100, 0.92f, 1900, false));
        // L이 잠깐 다시 보이면 처음부터 다시 잼
        observeRelock(100, 0.55f, 100, 0.92f, 100, false);
        assertEquals(-1, observeRelock(100, 0.33f, 100, 0.92f, 1900, false));
        assertEquals("L", selector.getLockedLabel());
    }

    @Test
    public void otherLegNotVisible_notRelocked() {
        lockLeftWhileSeated();
        // 사람이 화면 밖 (두 다리 모두 안 보임)
        assertEquals(-1, observeRelock(Double.NaN, 0f, Double.NaN, 0f, 5000, false));
        assertEquals("L", selector.getLockedLabel());
    }

    @Test
    public void duringHold_notRelocked_thenRelockedAfterHoldEnds() {
        lockLeftWhileSeated();
        // 유지 중(진행 중인 유지 포함): 정해진 다리가 오래 가려져도 바꾸지 않음
        assertEquals(-1, observeRelock(100, 0.33f, 70, 0.92f, 5000, true));
        assertEquals("L", selector.getLockedLabel());
        // 유지가 끝난 뒤부터 2초를 새로 잼
        long relockedAt = observeRelock(100, 0.33f, 70, 0.92f, 3000, false);
        assertTrue(relockedAt > 2000 && relockedAt < 2100);
        assertEquals("R", selector.getLockedLabel());
    }

    @Test
    public void otherExercises_neverRelock() {
        lockLeftWhileSeated();
        for (long t = 0; t < 10000; t += FRAME_MS) {
            assertFalse(selector.observeForRelock(leg("L", 100, 0.2f), leg("R", 100, 0.95f), now, false,
                    ExerciseCriteria.LUNGE.relockHiddenMs));
            now += FRAME_MS;
        }
        assertEquals("L", selector.getLockedLabel());
    }

    // ---------- 측정기와 함께 (ExerciseActivity의 프레임 처리와 같은 순서) ----------

    /** 정해진 다리로 HoldCounter에 넣음. 다시 정하기 사용 여부를 고를 수 있음 */
    private int holdsCountedAfterWrongLock(boolean relock) {
        lockLeftWhileSeated();
        HoldCounter counter = new HoldCounter(STRETCH);
        long relockMs = relock ? STRETCH.relockHiddenMs : 0;
        // 22:48:40~44 똑바로 앉음(R 128도) → 22:48:46~ R을 57~90도로 숙여 25초 → 다시 세움
        double[][] phases = {{128, 5000}, {80, 25000}, {128, 3000}};
        for (double[] phase : phases) {
            for (long t = 0; t < phase[1]; t += FRAME_MS) {
                LegSelector.LegSample l = leg("L", 100, 0.33f);
                LegSelector.LegSample r = leg("R", phase[0], 0.92f);
                selector.observeForRelock(l, r, now, counter.isRepInProgress(), relockMs);
                LegSelector.LegSample chosen = selector.selectLocked(l, r);
                if (chosen == null) {
                    counter.onFrame(now, Double.NaN, Double.NaN, Double.NaN);
                } else {
                    counter.onFrame(now, chosen.jointAngle, chosen.trunkLean, chosen.kneeAngle);
                }
                now += FRAME_MS;
            }
        }
        return counter.getCompletedReps();
    }

    @Test
    public void realLogCase_withRelock_holdCounted() {
        assertEquals(1, holdsCountedAfterWrongLock(true));
    }

    @Test
    public void realLogCase_withoutRelock_nothingCounted() {
        // 고치기 전 동작: 잘못 정한 다리(L)만 보므로 유지 시간이 쌓이지 않음
        assertEquals(0, new StretchLegLockTest().holdsCountedAfterWrongLock(false));
    }

    @Test
    public void briefOcclusionDuringHold_noRelock_holdCompletes() {
        // R로 정해져 유지 중 손을 뻗어 R이 1.2초 가려지고 L이 보여도 다리를 바꾸지 않고 유지가 이어짐
        observeLock(131, 0.45f, 128, 0.93f, 1200);
        assertEquals("R", selector.getLockedLabel());
        HoldCounter counter = new HoldCounter(STRETCH);
        long[][] phases = {{128, 93, 3000}, {80, 93, 8000}, {80, 30, 1200}, {80, 93, 14000}, {128, 93, 3000}};
        for (long[] phase : phases) {
            for (long t = 0; t < phase[2]; t += FRAME_MS) {
                LegSelector.LegSample l = leg("L", 100, 0.60f);
                LegSelector.LegSample r = leg("R", phase[0], phase[1] / 100f);
                assertFalse(selector.observeForRelock(l, r, now, counter.isRepInProgress(), STRETCH.relockHiddenMs));
                LegSelector.LegSample chosen = selector.selectLocked(l, r);
                if (chosen == null) {
                    counter.onFrame(now, Double.NaN, Double.NaN, Double.NaN);
                } else {
                    counter.onFrame(now, chosen.jointAngle, chosen.trunkLean, chosen.kneeAngle);
                }
                now += FRAME_MS;
            }
        }
        assertEquals("R", selector.getLockedLabel());
        assertEquals(1, counter.getCompletedReps());
        assertNull(counter.takeRejectedAttempt());
    }
}

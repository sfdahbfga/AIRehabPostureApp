package com.example.airehabpostureapp.pose;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 다리 고르기 + 반복 횟수 세기를 함께 검증한다. (옆모습 촬영에서 생기는 L/R 혼동, 가려진 다리 값 튐)
 */
public class LegSelectorTest {

    private static final long FRAME_MS = 33;
    private static final float VISIBLE = 0.9f;
    private static final float OCCLUDED = 0.3f;
    private static final double TRUNK = 8;
    private static final double HIP = 168;

    private LegSelector selector;
    private RepetitionCounter counter;
    private long now;
    private List<RepetitionCounter.RejectedAttempt> rejected;

    @Before
    public void setUp() {
        selector = new LegSelector();
        counter = new RepetitionCounter(ExerciseCriteria.KNEE_FLEXION);
        now = 0;
        rejected = new ArrayList<>();
    }

    private static LegSelector.LegSample left(double angle, float visibility) {
        return new LegSelector.LegSample("L", angle, TRUNK, HIP, visibility);
    }

    private static LegSelector.LegSample right(double angle, float visibility) {
        return new LegSelector.LegSample("R", angle, TRUNK, HIP, visibility);
    }

    // ---------- 다리 고르기 ----------

    // ---------- 고르기 + 횟수 세기 ----------

    // ---------- 운동하는 다리 고정 (ExerciseActivity의 현재 방식) ----------

    // ExerciseActivity와 같은 규칙: 다리가 정해지기 전에는 visibility만 재고 세지 않음,
    // 정해진 뒤에는 정해진 다리 값만 카운터에 넘김 (안 보이면 NaN)
    private void lockedFrame(LegSelector.LegSample l, LegSelector.LegSample r) {
        if (!selector.isLocked() && !selector.observeForLock(l, r, now, ExerciseCriteria.KNEE_FLEXION.extendedAngle)) {
            now += FRAME_MS;
            return;
        }
        LegSelector.LegSample chosen = selector.selectLocked(l, r);
        if (chosen == null) {
            counter.onFrame(now, Double.NaN, Double.NaN, Double.NaN);
        } else {
            counter.onFrame(now, chosen.jointAngle, chosen.trunkLean, chosen.hipAngle);
        }
        RepetitionCounter.RejectedAttempt attempt = counter.takeRejectedAttempt();
        if (attempt != null) {
            rejected.add(attempt);
        }
        now += FRAME_MS;
    }

    private void lockedFrames(int count, double leftAngle, float leftVis, double rightAngle, float rightVis) {
        for (int i = 0; i < count; i++) {
            lockedFrame(left(leftAngle, leftVis), right(rightAngle, rightVis));
        }
    }

    // 정옆으로 서서 먼 다리(L)는 0.6, 카메라 쪽(R)은 0.9 → 차이 0.3. 1초(약 31프레임) 넘게 이어지면 R로 정함
    // (먼 다리도 0.5 이상이라 후보 조건은 통과하므로, 예전 방식이면 튄 값이 고를 대상이 됨)
    private static final float FAR_LEG = 0.6f;
    private static final float FACING = 0.92f;

    private void standAndLockRight() {
        lockedFrames(40, 172, FAR_LEG, 174, VISIBLE);
        assertTrue(selector.isLocked());
        assertEquals("R", selector.getLockedLabel());
    }

    @Test
    public void lock_afterOneSecondSideways_picksHigherVisibilityLeg() {
        lockedFrames(30, 172, FAR_LEG, 174, VISIBLE);   // 약 0.96초: 아직 정하지 않음
        assertFalse(selector.isLocked());
        lockedFrames(5, 172, FAR_LEG, 174, VISIBLE);
        assertEquals("R", selector.getLockedLabel());
        assertEquals(FAR_LEG, selector.getLockedLeftAverage(), 0.001);
        assertEquals(VISIBLE, selector.getLockedRightAverage(), 0.001);
        // 정하는 동안은 세지 않음
        assertEquals(0, counter.getCompletedReps());
    }

    @Test
    public void lock_facingCamera_doesNotLock() {
        // 카메라를 보고 서 있음: 두 다리 visibility가 비슷 (지난 측정 0.94 / 0.92) → 오래 서 있어도 정하지 않음
        lockedFrames(150, 178, FACING, 178, 0.90f);
        assertFalse(selector.isLocked());
        assertEquals(0, counter.getCompletedReps());
        // 정옆으로 돌아서면 그때부터 1초 뒤에 정함
        lockedFrames(35, 172, FAR_LEG, 174, VISIBLE);
        assertEquals("R", selector.getLockedLabel());
    }

    @Test
    public void lock_briefGapWhileTurning_doesNotLock() {
        // 카메라를 봄 → 몸을 돌리는 중 0.5초만 차이가 남 (L이 더 잘 보임) → 다시 카메라를 봄
        lockedFrames(30, 178, FACING, 178, 0.90f);
        lockedFrames(15, 175, VISIBLE, 175, 0.5f);
        lockedFrames(30, 178, FACING, 178, 0.90f);
        assertFalse("0.5초만 차이가 난 쪽(L)으로 정하면 안 됨", selector.isLocked());
        // 정옆(R이 카메라 쪽)으로 선 뒤 1초가 지나면 R로 정함
        lockedFrames(35, 172, FAR_LEG, 174, VISIBLE);
        assertEquals("R", selector.getLockedLabel());
    }

    @Test
    public void lock_restartsWhenConditionIsInterrupted() {
        lockedFrames(20, 172, FAR_LEG, 174, VISIBLE);
        // 사람이 잠깐 안 보임 (두 다리 모두 후보 아님) → 처음부터 다시 잼
        lockedFrames(5, Double.NaN, 0f, Double.NaN, 0f);
        lockedFrames(20, 172, FAR_LEG, 174, VISIBLE);
        assertFalse("끊긴 뒤 1초가 안 지났으므로 아직 정하지 않음", selector.isLocked());
        lockedFrames(15, 172, FAR_LEG, 174, VISIBLE);
        assertEquals("R", selector.getLockedLabel());
    }

    @Test
    public void afterLock_oppositeLegSpikes_doNotChangeCount() {
        standAndLockRight();
        // 서 있는 동안 먼 다리(L)가 0.7초 동안 110도로 튐 (후보 조건은 통과)
        // 예전 방식(더 많이 굽혀진 다리)이면 이 값이 1회로 세어질 수 있음
        lockedFrames(20, 110, FAR_LEG, 174, VISIBLE);
        lockedFrames(20, 172, FAR_LEG, 174, VISIBLE);
        assertEquals(0, counter.getCompletedReps());

        // 카메라 쪽 다리(R)로 1회. 굽히는 도중과 가장 깊은 곳에서도 L 값이 튐
        for (int i = 0; i <= 20; i++) {
            lockedFrame(left(i % 5 == 0 ? 100 : 172, FAR_LEG), right(174 - (174 - 90) * i / 20.0, VISIBLE));
        }
        lockedFrames(6, 140, FAR_LEG, 90, VISIBLE);
        for (int i = 0; i <= 20; i++) {
            lockedFrame(left(172, FAR_LEG), right(90 + (174 - 90) * i / 20.0, VISIBLE));
        }
        lockedFrames(15, 172, FAR_LEG, 174, VISIBLE);
        // 다시 서 있는 동안 L이 튐
        lockedFrames(20, 105, FAR_LEG, 174, VISIBLE);
        lockedFrames(20, 172, FAR_LEG, 174, VISIBLE);

        assertEquals(1, counter.getCompletedReps());
        assertTrue("먼 다리 값이 버려진 동작으로도 남으면 안 됨", rejected.isEmpty());
        assertEquals(90, counter.getResults().get(0).minJointAngle, 0.5);
        assertEquals("R", selector.getLastLabel());
    }

    @Test
    public void afterLock_lockedLegHiddenOverOneSecond_showsNotVisible_evenIfOtherLegVisible() {
        standAndLockRight();
        // 카메라 쪽 다리(R)가 1초 넘게 안 보이고 먼 다리(L)만 보임 → L로 넘어가지 않고 "전신이 보이게"
        lockedFrames(35, 172, VISIBLE, 174, OCCLUDED);
        assertEquals(RepetitionCounter.Feedback.NOT_VISIBLE, counter.getFeedback());
        assertEquals(0, counter.getCompletedReps());
    }

    @Test
    public void reset_clearsLock() {
        standAndLockRight();
        selector.reset();
        assertFalse(selector.isLocked());
        assertNull(selector.getLockedLabel());
    }
}

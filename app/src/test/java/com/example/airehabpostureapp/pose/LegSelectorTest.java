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

    // ExerciseActivity와 같은 규칙: 서 있을 때는 더 많이 굽혀진 다리, 동작 중에는 직전 각도와 가장 가까운 다리
    // 고른 다리의 각도만 카운터에 넘김 (후보가 없으면 NaN → 건너뜀)
    private void frame(LegSelector.LegSample l, LegSelector.LegSample r) {
        LegSelector.LegSample chosen = counter.isRepInProgress()
                ? selector.selectClosest(l, r, counter.getLastAcceptedAngle())
                : selector.select(l, r);
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

    private void frames(int count, double leftAngle, float leftVis, double rightAngle, float rightVis) {
        for (int i = 0; i < count; i++) {
            frame(left(leftAngle, leftVis), right(rightAngle, rightVis));
        }
    }

    // ---------- 다리 고르기 ----------

    @Test
    public void picksMoreBentLeg_amongVisibleLegs() {
        LegSelector.LegSample l = left(170, VISIBLE);
        LegSelector.LegSample r = right(95, VISIBLE);
        assertSame(r, selector.select(l, r));
    }

    @Test
    public void ignoresOccludedLeg_evenIfMoreBent() {
        LegSelector.LegSample l = left(100, OCCLUDED);
        LegSelector.LegSample r = right(172, VISIBLE);
        assertSame(r, selector.select(l, r));
    }

    @Test
    public void ignoresLegWithUnknownAngle() {
        LegSelector.LegSample l = left(Double.NaN, VISIBLE);
        LegSelector.LegSample r = right(150, VISIBLE);
        assertSame(r, selector.select(l, r));
    }

    @Test
    public void noCandidate_returnsNull() {
        assertNull(selector.select(left(100, OCCLUDED), right(90, 0.49f)));
        assertNull(selector.select(null, null));
    }

    @Test
    public void selectClosest_picksLegNearestToReference() {
        LegSelector.LegSample l = left(118, VISIBLE);
        LegSelector.LegSample r = right(170, VISIBLE);
        assertSame(l, selector.selectClosest(l, r, 122));
        assertSame(r, selector.selectClosest(l, r, 165));
        // 기준 각도가 없으면 더 많이 굽혀진 다리
        assertSame(l, selector.selectClosest(l, r, Double.NaN));
        // 후보가 하나뿐이면 멀어도 그 다리 (연속성 검사가 거름)
        assertSame(r, selector.selectClosest(left(118, OCCLUDED), r, 122));
    }

    @Test
    public void countsLabelSwitches() {
        selector.select(left(170, VISIBLE), right(100, VISIBLE));   // R
        selector.select(left(100, VISIBLE), right(170, VISIBLE));   // L (1회)
        selector.select(left(100, VISIBLE), right(170, VISIBLE));   // L
        selector.select(left(170, VISIBLE), right(100, VISIBLE));   // R (2회)
        assertEquals(2, selector.getSwitchCount());
        assertEquals("R", selector.getLastLabel());
        selector.resetSwitchCount();
        assertEquals(0, selector.getSwitchCount());
    }

    // ---------- 고르기 + 횟수 세기 ----------

    @Test
    public void standing_occludedLegSpikesBelow130_doesNotCount() {
        // 서 있는 동안 가려진 다리(L)의 짐작한 값이 잠깐 100도로 튐 → 후보가 아니므로 무시
        frames(30, 172, OCCLUDED, 174, VISIBLE);
        frames(8, 100, OCCLUDED, 174, VISIBLE);
        frames(30, 172, OCCLUDED, 174, VISIBLE);
        frames(6, 95, OCCLUDED, 173, VISIBLE);
        frames(30, 172, OCCLUDED, 174, VISIBLE);
        assertEquals(0, counter.getCompletedReps());
        assertTrue(rejected.isEmpty());
        assertEquals("R", selector.getLastLabel());
    }

    @Test
    public void labelSwapMidRep_doesNotSplitRep() {
        // 서 있음 (서 있는 다리 L은 잘 보이는 172도, 굽히는 다리 R도 172도)
        frames(20, 172, VISIBLE, 174, VISIBLE);
        // R 다리를 굽힘
        for (int i = 0; i <= 20; i++) {
            frame(left(172, VISIBLE), right(174 - (174 - 90) * i / 20.0, VISIBLE));
        }
        // MediaPipe가 L/R 이름을 6프레임 동안 바꿔 붙임 (굽힌 각도가 L에, 서 있는 다리 각도가 R에)
        frames(6, 90, VISIBLE, 172, VISIBLE);
        frames(6, 172, VISIBLE, 90, VISIBLE);
        // 다시 폄
        for (int i = 0; i <= 20; i++) {
            frame(left(172, VISIBLE), right(90 + (174 - 90) * i / 20.0, VISIBLE));
        }
        frames(15, 172, VISIBLE, 174, VISIBLE);

        assertEquals(1, counter.getCompletedReps());
        assertTrue("쪼개진 조각이 버려진 동작으로 남으면 안 됨", rejected.isEmpty());
        assertEquals(90, counter.getResults().get(0).minJointAngle, 0.5);
    }

    @Test
    public void bendingLegOccluded_atShallowDepth_standingLegValueDoesNotSplitRep() {
        // 7차 측정에서 본 현상: 굽히는 다리(R)가 120도 근처에서 가려지고, 서 있는 다리(L, 168도)만 보임
        // (예전 방식은 약 0.14초 뒤 서 있는 다리 값을 받아들여 동작이 둘로 쪼개졌음)
        frames(20, 172, VISIBLE, 174, VISIBLE);
        for (int i = 0; i <= 20; i++) {
            frame(left(172, VISIBLE), right(174 - (174 - 120) * i / 20.0, VISIBLE));
        }
        frames(10, 168, VISIBLE, 120, OCCLUDED);   // 0.33초 가려짐
        for (int i = 0; i <= 10; i++) {
            frame(left(172, VISIBLE), right(120 - (120 - 95) * i / 10.0, VISIBLE));
        }
        frames(8, 172, VISIBLE, 95, VISIBLE);       // 가장 깊은 곳에서 잠깐 멈춤
        for (int i = 0; i <= 20; i++) {
            frame(left(172, VISIBLE), right(95 + (174 - 95) * i / 20.0, VISIBLE));
        }
        frames(15, 172, VISIBLE, 174, VISIBLE);

        assertEquals(1, counter.getCompletedReps());
        assertTrue("쪼개진 조각이 버려진 동작으로 남으면 안 됨", rejected.isEmpty());
        // 가려진 사이 서 있는 다리 값이 섞이지 않아 가장 깊은 각도를 그대로 기록
        assertEquals(95, counter.getResults().get(0).minJointAngle, 1.0);
    }

    @Test
    public void bendingLegOccluded_atDeepAngle_forHalfSecond_doesNotSplitRep() {
        frames(20, 172, VISIBLE, 174, VISIBLE);
        for (int i = 0; i <= 20; i++) {
            frame(left(172, VISIBLE), right(174 - (174 - 90) * i / 20.0, VISIBLE));
        }
        frames(15, 168, VISIBLE, 90, OCCLUDED);    // 0.5초 가려짐
        for (int i = 0; i <= 20; i++) {
            frame(left(172, VISIBLE), right(90 + (174 - 90) * i / 20.0, VISIBLE));
        }
        frames(15, 172, VISIBLE, 174, VISIBLE);
        assertEquals(1, counter.getCompletedReps());
        assertTrue(rejected.isEmpty());
    }

    @Test
    public void bothLegsHidden_briefly_isSkipped() {
        frames(20, 172, VISIBLE, 174, VISIBLE);
        for (int i = 0; i <= 20; i++) {
            frame(left(172, VISIBLE), right(174 - (174 - 90) * i / 20.0, VISIBLE));
        }
        // 두 다리 모두 0.3초 가려짐 → 건너뛰고 동작은 이어짐
        frames(9, 90, OCCLUDED, 90, OCCLUDED);
        for (int i = 0; i <= 20; i++) {
            frame(left(172, VISIBLE), right(90 + (174 - 90) * i / 20.0, VISIBLE));
        }
        frames(15, 172, VISIBLE, 174, VISIBLE);
        assertEquals(1, counter.getCompletedReps());
    }

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

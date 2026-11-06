package com.example.airehabpostureapp.pose;

import static org.junit.Assert.assertEquals;
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

    // 고른 다리의 각도만 카운터에 넘김 (후보가 없으면 NaN → 건너뜀)
    private void frame(LegSelector.LegSample l, LegSelector.LegSample r) {
        LegSelector.LegSample chosen = selector.select(l, r);
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
}

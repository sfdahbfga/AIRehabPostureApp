package com.example.airehabpostureapp.pose;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SessionMetricsTest {

    private static final double DELTA = 1e-6;

    private static RepetitionCounter.RepResult rep(int number, double accuracy, double jointError, double trunkError) {
        return new RepetitionCounter.RepResult(number, 90 + jointError, Double.NaN, Double.NaN,
                jointError, trunkError, accuracy, 2000);
    }

    @Test
    public void zeroReps_allValuesNull() {
        SessionMetrics m = SessionMetrics.from(new ArrayList<>());
        assertEquals(0, m.repetitions);
        assertNull(m.averageAccuracy);
        assertNull(m.firstHalfAccuracy);
        assertNull(m.secondHalfAccuracy);
        assertNull(m.jointError);
        assertNull(m.trunkError);
    }

    @Test
    public void oneRep_bothHalvesSameValue() {
        SessionMetrics m = SessionMetrics.from(Arrays.asList(rep(1, 80, 5, 0)));
        assertEquals(80, m.averageAccuracy, DELTA);
        assertEquals(80, m.firstHalfAccuracy, DELTA);
        assertEquals(80, m.secondHalfAccuracy, DELTA);
    }

    @Test
    public void fiveReps_firstTwoAndLastThree() {
        List<RepetitionCounter.RepResult> reps = Arrays.asList(
                rep(1, 100, 0, 0),
                rep(2, 90, 5, 0),
                rep(3, 80, 10, 2),
                rep(4, 70, 20, 4),
                rep(5, 60, 30, 6));
        SessionMetrics m = SessionMetrics.from(reps);
        assertEquals(5, m.repetitions);
        assertEquals(80, m.averageAccuracy, DELTA);
        assertEquals(95, m.firstHalfAccuracy, DELTA);   // (100 + 90) / 2
        assertEquals(70, m.secondHalfAccuracy, DELTA);  // (80 + 70 + 60) / 3
        assertEquals(13, m.jointError, DELTA);          // (0 + 5 + 10 + 20 + 30) / 5
        assertEquals(2.4, m.trunkError, DELTA);         // (0 + 0 + 2 + 4 + 6) / 5
    }

    @Test
    public void negativeJointError_meansMoreBent() {
        SessionMetrics m = SessionMetrics.from(Arrays.asList(rep(1, 100, -10, 0), rep(2, 100, -20, 0)));
        assertEquals(-15, m.jointError, DELTA);
    }

    @Test
    public void trunkNotVisible_excludedFromTrunkAverage() {
        SessionMetrics m = SessionMetrics.from(Arrays.asList(rep(1, 100, 0, Double.NaN), rep(2, 100, 0, 4)));
        assertEquals(4, m.trunkError, DELTA);
        SessionMetrics none = SessionMetrics.from(Arrays.asList(rep(1, 100, 0, Double.NaN)));
        assertNull(none.trunkError);
    }

    @Test
    public void round_handlesNull() {
        assertNull(SessionMetrics.round(null));
        assertEquals(Integer.valueOf(13), SessionMetrics.round(12.6));
        assertEquals(Integer.valueOf(-2), SessionMetrics.round(-2.4));
    }
}

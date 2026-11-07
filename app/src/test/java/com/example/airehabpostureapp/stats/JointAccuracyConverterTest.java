package com.example.airehabpostureapp.stats;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class JointAccuracyConverterTest {

    @Test
    public void knee_withinTolerance_isFullScore() {
        assertEquals(Integer.valueOf(100), JointAccuracyConverter.kneeAccuracy(0.0));
        assertEquals(Integer.valueOf(100), JointAccuracyConverter.kneeAccuracy(10.0));
        assertEquals(Integer.valueOf(100), JointAccuracyConverter.kneeAccuracy(-20.0));
    }

    @Test
    public void knee_underBend_losesTwoPointsPerDegreeOverTen() {
        // 12도 덜 굽힘 → 2도 초과 → 96점
        assertEquals(Integer.valueOf(96), JointAccuracyConverter.kneeAccuracy(12.0));
        assertEquals(Integer.valueOf(86), JointAccuracyConverter.kneeAccuracy(17.0));
    }

    @Test
    public void knee_overBend_losesTwoPointsPerDegreeOverTwenty() {
        assertEquals(Integer.valueOf(90), JointAccuracyConverter.kneeAccuracy(-25.0));
    }

    @Test
    public void back_losesFourPointsPerDegree() {
        assertEquals(Integer.valueOf(100), JointAccuracyConverter.backAccuracy(0.0));
        assertEquals(Integer.valueOf(88), JointAccuracyConverter.backAccuracy(3.0));
    }

    @Test
    public void score_isClampedAndRounded() {
        assertEquals(Integer.valueOf(0), JointAccuracyConverter.kneeAccuracy(100.0));
        assertEquals(Integer.valueOf(0), JointAccuracyConverter.backAccuracy(30.0));
        // 100 - 2.6 x 4 = 89.6 → 90
        assertEquals(Integer.valueOf(90), JointAccuracyConverter.backAccuracy(2.6));
    }

    @Test
    public void missingValue_isNull() {
        assertNull(JointAccuracyConverter.kneeAccuracy(null));
        assertNull(JointAccuracyConverter.backAccuracy(null));
    }
}

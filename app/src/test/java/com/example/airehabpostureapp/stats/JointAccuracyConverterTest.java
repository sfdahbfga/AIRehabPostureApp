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
        assertNull(JointAccuracyConverter.hipAccuracy("하체 스트레칭", null));
    }

    // ---------- 운동별 기준 ----------

    @Test
    public void chairSquat_usesSquatTolerances() {
        // 더 굽힘 허용 15도: -20 → 5도 초과 → 90점 (무릎 굽힘 기준이면 100점)
        assertEquals(Integer.valueOf(90), JointAccuracyConverter.kneeAccuracy("의자 스쿼트", -20.0));
        assertEquals(Integer.valueOf(100), JointAccuracyConverter.kneeAccuracy("무릎 굽힘 운동", -20.0));
        // 상체 오차는 허용(50도) 초과량으로 저장되므로 감점 규칙(1도당 4점)은 같음
        assertEquals(Integer.valueOf(88), JointAccuracyConverter.backAccuracy("의자 스쿼트", 3.0));
    }

    @Test
    public void lunge_usesLungeTolerances() {
        assertEquals(Integer.valueOf(90), JointAccuracyConverter.kneeAccuracy("런지", -20.0));
        assertEquals(Integer.valueOf(96), JointAccuracyConverter.kneeAccuracy("런지", 12.0));
    }

    @Test
    public void hamstringStretch_hipScore() {
        // 덜 숙임 허용 5도: +10 → 5도 초과 → 90점, 더 숙임 허용 20도: -20 → 100점
        assertEquals(Integer.valueOf(90), JointAccuracyConverter.hipAccuracy("하체 스트레칭", 10.0));
        assertEquals(Integer.valueOf(100), JointAccuracyConverter.hipAccuracy("하체 스트레칭", -20.0));
    }

    @Test
    public void unknownOrMissingExerciseName_fallsBackToKneeFlexion() {
        assertEquals(Integer.valueOf(96), JointAccuracyConverter.kneeAccuracy("허리 강화 운동", 12.0));
        assertEquals(Integer.valueOf(96), JointAccuracyConverter.kneeAccuracy(null, 12.0));
    }
}

package com.example.airehabpostureapp.pose;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ExerciseCriteriaTest {

    private static final double DELTA = 1e-6;
    private final ExerciseCriteria knee = ExerciseCriteria.KNEE_FLEXION;

    @Test
    public void forExercise_findsKneeFlexion_andNullForUnsupported() {
        assertSame(knee, ExerciseCriteria.forExercise("무릎 굽힘 운동"));
        assertNull(ExerciseCriteria.forExercise("없는 운동"));
        assertNull(ExerciseCriteria.forExercise(null));
    }

    @Test
    public void jointError_signConvention() {
        assertEquals(30, knee.jointError(120), DELTA);   // 덜 굽힘 → +
        assertEquals(-15, knee.jointError(75), DELTA);   // 더 굽힘 → -
    }

    @Test
    public void jointScore_withinTolerance_isFull() {
        assertEquals(100, knee.jointScore(90), DELTA);
        assertEquals(100, knee.jointScore(100), DELTA);  // 덜 굽힘 10도까지
        assertEquals(100, knee.jointScore(70), DELTA);   // 더 굽힘 20도까지
    }

    @Test
    public void jointScore_outsideTolerance_losesTwoPointsPerDegree() {
        assertEquals(60, knee.jointScore(120), DELTA);   // 100 - (30 - 10) x 2
        assertEquals(40, knee.jointScore(130), DELTA);
        assertEquals(90, knee.jointScore(65), DELTA);    // 100 - (25 - 20) x 2
        assertEquals(0, knee.jointScore(170), DELTA);    // 0 아래로 내려가지 않음
    }

    @Test
    public void trunk_withinAllowance_noError() {
        assertEquals(0, knee.trunkError(10), DELTA);
        assertEquals(5, knee.trunkError(20), DELTA);
        assertTrue(Double.isNaN(knee.trunkError(Double.NaN)));
    }

    @Test
    public void hipTooBent_belowThreshold() {
        assertTrue(knee.isHipTooBent(139.9));
        assertFalse(knee.isHipTooBent(140));
        assertFalse(knee.isHipTooBent(170));
        assertFalse(knee.isHipTooBent(Double.NaN));
    }

    @Test
    public void repAccuracy_combinesJointAndTrunk() {
        assertEquals(100, knee.repAccuracy(90, 10), DELTA);
        assertEquals(92, knee.repAccuracy(90, 25), DELTA);    // 100 x 0.8 + 60 x 0.2
        assertEquals(68, knee.repAccuracy(120, 5), DELTA);    // 60 x 0.8 + 100 x 0.2
        assertEquals(60, knee.repAccuracy(120, Double.NaN), DELTA);
    }

    // Builder로 바꾼 뒤에도 무릎 굽힘 운동 기준값이 그대로인지 확인
    @Test
    public void kneeFlexion_keepsTunedValues() {
        assertEquals("무릎 굽힘 운동", knee.exerciseName);
        assertEquals(20, knee.targetReps);
        assertEquals(160, knee.lockMinAngle, DELTA);
        assertEquals(160, knee.extendedAngle, DELTA);
        assertEquals(130, knee.bentAngle, DELTA);
        assertEquals(90, knee.targetAngle, DELTA);
        assertEquals(10, knee.underBendTolerance, DELTA);
        assertEquals(20, knee.overBendTolerance, DELTA);
        assertEquals(2, knee.pointsPerDegree, DELTA);
        assertEquals(15, knee.maxTrunkLean, DELTA);
        assertEquals(4, knee.trunkPointsPerDegree, DELTA);
        assertEquals(0.8, knee.jointWeight, DELTA);
        assertEquals(140, knee.minHipAngle, DELTA);
        assertEquals(5, knee.smoothingWindow);
        assertEquals(500, knee.minRepDurationMs);
        assertEquals(1000, knee.maxInvisibleMs);
        assertEquals(20, knee.maxJumpDegrees, DELTA);
        assertEquals(200, knee.maxDegreesPerSecond, DELTA);
        assertEquals(0.3, knee.maxMissedFrameRatio, DELTA);
        assertEquals(100, knee.inRepMaxGapMs);
        assertEquals(200, knee.extendedHoldMs);
    }

    @Test
    public void forExerciseOrDefault_fallsBackToKneeFlexion() {
        assertSame(knee, ExerciseCriteria.forExerciseOrDefault("없는 운동"));
        assertSame(knee, ExerciseCriteria.forExerciseOrDefault(null));
        assertNull(ExerciseCriteria.forExercise("없는 운동"));
    }
}

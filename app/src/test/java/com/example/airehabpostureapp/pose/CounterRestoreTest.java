package com.example.airehabpostureapp.pose;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

// H1: 화면 재생성 / 프로세스 종료 후 회차 결과 복구
public class CounterRestoreTest {

    private static final double DELTA = 1e-9;

    private static RepetitionCounter.RepResult rep(int number, double accuracy) {
        return new RepetitionCounter.RepResult(number, 92.5, 12.25, Double.NaN, 2.5, 0, accuracy, 2100,
                new RepetitionCounter.FrameStats(40, 2, 3));
    }

    @Test
    public void repResults_roundTripThroughArray() {
        List<RepetitionCounter.RepResult> original = Arrays.asList(rep(1, 95), rep(2, 80.5));
        List<RepetitionCounter.RepResult> restored =
                RepetitionCounter.RepResult.listFromArray(RepetitionCounter.RepResult.toArray(original));

        assertEquals(2, restored.size());
        RepetitionCounter.RepResult second = restored.get(1);
        assertEquals(2, second.repNumber);
        assertEquals(92.5, second.minJointAngle, DELTA);
        assertEquals(12.25, second.maxTrunkLean, DELTA);
        assertTrue(Double.isNaN(second.minHipAngle));
        assertEquals(2.5, second.jointError, DELTA);
        assertEquals(80.5, second.accuracy, DELTA);
        assertEquals(2100, second.durationMs);
        assertEquals(40, second.frames.accepted);
        assertEquals(2, second.frames.skippedByJump);
        assertEquals(3, second.frames.invisible);
    }

    @Test
    public void listFromArray_nullOrBrokenLength_isEmpty() {
        assertTrue(RepetitionCounter.RepResult.listFromArray(null).isEmpty());
        assertTrue(RepetitionCounter.RepResult.listFromArray(new double[5]).isEmpty());
    }

    @Test
    public void repetitionCounter_restore_keepsCountAndMetrics() {
        ExerciseCounter counter = new RepetitionCounter(ExerciseCriteria.KNEE_FLEXION);
        counter.restoreResults(Arrays.asList(rep(1, 90), rep(2, 70)));

        assertEquals(2, counter.getCompletedReps());
        assertEquals(80, SessionMetrics.from(counter.getResults()).averageAccuracy, DELTA);
    }

    @Test
    public void holdCounter_restore_keepsCount() {
        ExerciseCounter counter = new HoldCounter(ExerciseCriteria.SEATED_HAMSTRING_STRETCH);
        counter.restoreResults(Arrays.asList(rep(1, 100)));

        assertEquals(1, counter.getCompletedReps());
    }
}

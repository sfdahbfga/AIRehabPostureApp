package com.example.airehabpostureapp.pose;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.example.airehabpostureapp.ExerciseDetailActivity;

import org.junit.Test;

// L11: 운동은 고정 키로 찾고, DB / 과거 기록의 한글 이름(exercise_name)도 계속 읽을 수 있어야 함
public class ExerciseLookupTest {

    private static final ExerciseCriteria[] ALL = {
            ExerciseCriteria.KNEE_FLEXION, ExerciseCriteria.CHAIR_SQUAT,
            ExerciseCriteria.LUNGE, ExerciseCriteria.SEATED_HAMSTRING_STRETCH
    };

    @Test
    public void forId_findsEveryExercise() {
        assertSame(ExerciseCriteria.KNEE_FLEXION, ExerciseCriteria.forId("knee_flexion"));
        assertSame(ExerciseCriteria.CHAIR_SQUAT, ExerciseCriteria.forId("chair_squat"));
        assertSame(ExerciseCriteria.LUNGE, ExerciseCriteria.forId("lunge"));
        assertSame(ExerciseCriteria.SEATED_HAMSTRING_STRETCH, ExerciseCriteria.forId("seated_hamstring_stretch"));
        assertNull(ExerciseCriteria.forId("plank"));
        assertNull(ExerciseCriteria.forId(null));
    }

    @Test
    public void storedKoreanNames_stillResolve() {
        // DB에 저장된 exercise_name (과거 기록 포함)
        assertEquals("knee_flexion", ExerciseCriteria.idForName("무릎 굽힘 운동"));
        assertEquals("chair_squat", ExerciseCriteria.idForName("의자 스쿼트"));
        assertEquals("lunge", ExerciseCriteria.idForName("런지"));
        assertEquals("seated_hamstring_stretch", ExerciseCriteria.idForName("하체 스트레칭"));
        assertNull(ExerciseCriteria.idForName("플랭크"));
        assertSame(ExerciseCriteria.CHAIR_SQUAT, ExerciseCriteria.forExercise("의자 스쿼트"));
        // 기준이 없는 예전 기록은 무릎 굽힘 운동 기준으로 점수 계산
        assertSame(ExerciseCriteria.KNEE_FLEXION, ExerciseCriteria.forExerciseOrDefault("플랭크"));
    }

    @Test
    public void idsAndNames_areUnique_andRoundTrip() {
        for (ExerciseCriteria criteria : ALL) {
            assertSame(criteria, ExerciseCriteria.forId(criteria.id));
            assertEquals(criteria.id, ExerciseCriteria.idForName(criteria.exerciseName));
        }
    }

    @Test
    public void exerciseDetail_isLookedUpByKey() {
        for (ExerciseCriteria criteria : ALL) {
            assertTrue(criteria.id, ExerciseDetailActivity.hasDetail(criteria.id));
        }
        // 이름은 더 이상 상세 데이터의 키가 아님 (이름만 받으면 화면에서 idForName으로 키를 찾음)
        assertFalse(ExerciseDetailActivity.hasDetail("의자 스쿼트"));
        assertFalse(ExerciseDetailActivity.hasDetail(null));
    }
}

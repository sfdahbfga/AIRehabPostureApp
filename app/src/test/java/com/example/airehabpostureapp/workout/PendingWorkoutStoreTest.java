package com.example.airehabpostureapp.workout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.airehabpostureapp.network.ExerciseSessionApi;
import com.google.gson.Gson;

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// H2: 운동 결과 임시 저장 (사용자별 보관, 다른 사용자 기록은 보이지도 지워지지도 않음)
public class PendingWorkoutStoreTest {

    private static final String USER_A = "user-a";
    private static final String USER_B = "user-b";

    /** 메모리 저장소 (SharedPreferences 대신) */
    static final class MemoryStorage implements PendingWorkoutStore.Storage {
        final Map<String, String> values = new HashMap<>();

        @Override
        public Map<String, String> readAll() {
            return new HashMap<>(values);
        }

        @Override
        public void write(String key, String value) {
            values.put(key, value);
        }

        @Override
        public void remove(String key) {
            values.remove(key);
        }
    }

    private MemoryStorage storage;
    private PendingWorkoutStore store;

    @Before
    public void setUp() {
        storage = new MemoryStorage();
        store = new PendingWorkoutStore(storage);
    }

    static PendingWorkout workout(String userId, String exerciseName, long endedAtMillis) {
        PendingWorkout workout = PendingWorkout.create(userId);
        workout.exerciseName = exerciseName;
        workout.startedAtMillis = endedAtMillis - 60_000;
        workout.endedAtMillis = endedAtMillis;
        workout.durationSeconds = 60;
        workout.completedReps = 5;
        workout.targetReps = 10;
        workout.averageAccuracy = 90;
        Map<String, Integer> jointErrors = new LinkedHashMap<>();
        jointErrors.put("knee", 4);
        workout.jointErrors = jointErrors;
        workout.preDiscomfortLevel = 3;
        workout.preBodyCondition = "good";
        return workout;
    }

    @Test
    public void create_makesDistinctIds() {
        PendingWorkout workout = PendingWorkout.create(USER_A);
        assertNotNull(workout.sessionId);
        assertNotEquals(workout.sessionId, workout.preCheckinId);
        assertNotEquals(workout.preCheckinId, workout.postCheckinId);
    }

    @Test
    public void saveAndGet_roundTripsAllValues() {
        PendingWorkout saved = workout(USER_A, "의자 스쿼트", 1_000_000);
        saved.setPostCheck(2, "normal", "  조금 뻐근함 ");
        store.save(saved);

        PendingWorkout loaded = store.get(USER_A, saved.sessionId);
        assertNotNull(loaded);
        assertEquals("의자 스쿼트", loaded.exerciseName);
        assertEquals(Integer.valueOf(90), loaded.averageAccuracy);
        assertNull(loaded.firstHalfAccuracy);
        assertEquals(Integer.valueOf(4), loaded.jointErrors.get("knee"));
        assertTrue(loaded.postChecked);
        assertEquals("조금 뻐근함", loaded.postNote);
        assertEquals(saved.postCheckinId, loaded.postCheckinId);
    }

    @Test
    public void findForUser_onlyReturnsLoggedInUsersWorkouts_inEndOrder() {
        PendingWorkout later = workout(USER_A, "런지", 2_000_000);
        PendingWorkout earlier = workout(USER_A, "무릎 굽힘 운동", 1_000_000);
        store.save(later);
        store.save(earlier);
        store.save(workout(USER_B, "하체 스트레칭", 500_000));

        List<PendingWorkout> found = store.findForUser(USER_A);
        assertEquals(2, found.size());
        assertEquals(earlier.sessionId, found.get(0).sessionId);
        assertEquals(later.sessionId, found.get(1).sessionId);
    }

    @Test
    public void otherUsersWorkout_isHidden_notDeleted_andAvailableAfterOriginalUserLogsIn() {
        PendingWorkout userBWorkout = workout(USER_B, "하체 스트레칭", 500_000);
        store.save(userBWorkout);

        // A가 로그인: B의 기록은 보이지 않고, get으로도 꺼낼 수 없음
        assertTrue(store.findForUser(USER_A).isEmpty());
        assertNull(store.get(USER_A, userBWorkout.sessionId));

        // A의 기록을 저장 / 삭제해도 B의 기록은 그대로
        PendingWorkout userAWorkout = workout(USER_A, "런지", 600_000);
        store.save(userAWorkout);
        store.remove(userAWorkout);
        assertEquals(1, storage.values.size());

        // B가 다시 로그인하면 보임
        List<PendingWorkout> forB = store.findForUser(USER_B);
        assertEquals(1, forB.size());
        assertEquals(userBWorkout.sessionId, forB.get(0).sessionId);
    }

    @Test
    public void entryWhoseContentHasDifferentUserId_isIgnored() {
        // 키는 A인데 내용의 사용자 id가 B인 기록 (손상 / 조작): 보여주지도 올리지도 않음
        PendingWorkout userBWorkout = workout(USER_B, "런지", 700_000);
        storage.write(USER_A + "/" + userBWorkout.sessionId, new Gson().toJson(userBWorkout));

        assertTrue(store.findForUser(USER_A).isEmpty());
        assertNull(store.get(USER_A, userBWorkout.sessionId));
    }

    @Test
    public void brokenJsonOrNullUser_isIgnored() {
        storage.write(USER_A + "/broken", "{not json");
        store.save(workout(null, "런지", 1));

        assertTrue(store.findForUser(USER_A).isEmpty());
        assertTrue(store.findForUser(null).isEmpty());
        assertEquals(1, storage.values.size());
    }

    @Test
    public void inserts_useClientIds_soRetryDoesNotDuplicate() {
        PendingWorkout workout = workout(USER_A, "런지", 1_000_000);
        workout.setPostCheck(1, "good", "");

        String sessionJson = new Gson().toJson(workout.toSessionInsert());
        assertTrue(sessionJson.contains("\"id\":\"" + workout.sessionId + "\""));
        assertTrue(sessionJson.contains("\"user_id\":\"" + USER_A + "\""));

        List<ExerciseSessionApi.CheckinInsert> checkins = workout.toCheckinInserts();
        String checkinJson = new Gson().toJson(checkins);
        assertTrue(checkinJson.contains("\"id\":\"" + workout.preCheckinId + "\""));
        assertTrue(checkinJson.contains("\"id\":\"" + workout.postCheckinId + "\""));
        assertTrue(checkinJson.contains("\"check_type\":\"pre\""));
        assertTrue(checkinJson.contains("\"check_type\":\"post\""));
        // 빈 메모는 보내지 않음
        assertFalse(checkinJson.contains("\"note\""));
    }
}

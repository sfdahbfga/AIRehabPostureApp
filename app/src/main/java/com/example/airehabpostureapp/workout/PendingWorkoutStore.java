package com.example.airehabpostureapp.workout;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 서버에 아직 저장하지 못한 운동 결과(PendingWorkout)를 휴대폰에 보관한다.
 *
 * - 키: "{사용자 id}/{세션 id}" → 사용자마다, 운동마다 따로 보관 (새 운동이 이전 기록을 덮어쓰지 않음)
 * - 다른 사용자의 기록은 조회 결과에 나오지 않고 지워지지도 않는다. (원래 사용자가 다시 로그인하면 저장 가능)
 * - 저장은 commit()으로 바로 디스크에 쓴다. (직후에 앱이 종료돼도 남도록)
 * - 건강 정보가 들어 있으므로 백업 / 기기 이전에서 제외한다. (res/xml 백업 규칙의 PREFS_FILE)
 *
 * 저장소(Storage)를 바꿔 끼울 수 있어 단위 테스트에서는 메모리 저장소를 쓴다.
 */
public class PendingWorkoutStore {

    /** SharedPreferences 파일 이름 (백업 규칙에서 "pending_workouts.xml"로 제외) */
    public static final String PREFS_NAME = "pending_workouts";

    /** 키-값 저장소 (실제: SharedPreferences, 테스트: 메모리) */
    public interface Storage {
        Map<String, String> readAll();

        void write(String key, String value);

        void remove(String key);
    }

    private final Storage storage;
    private final Gson gson = new Gson();

    public PendingWorkoutStore(Storage storage) {
        this.storage = storage;
    }

    public static PendingWorkoutStore create(Context context) {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        // commit(): 운동 결과는 저장 직후 앱이 종료돼도 남아야 하므로 바로 디스크에 쓴다 (H2)
        return new PendingWorkoutStore(new Storage() {
            @Override
            public Map<String, String> readAll() {
                Map<String, String> values = new HashMap<>();
                for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
                    if (entry.getValue() instanceof String) {
                        values.put(entry.getKey(), (String) entry.getValue());
                    }
                }
                return values;
            }

            @SuppressLint("ApplySharedPref")
            @Override
            public void write(String key, String value) {
                prefs.edit().putString(key, value).commit();
            }

            @SuppressLint("ApplySharedPref")
            @Override
            public void remove(String key) {
                prefs.edit().remove(key).commit();
            }
        });
    }

    /** 저장 / 갱신 (사용자 id나 세션 id가 없으면 저장하지 않음) */
    public void save(PendingWorkout workout) {
        if (workout == null || workout.userId == null || workout.sessionId == null) {
            return;
        }
        storage.write(key(workout.userId, workout.sessionId), gson.toJson(workout));
    }

    /** 이 사용자의 기록 1건. 없으면 null */
    public PendingWorkout get(String userId, String sessionId) {
        if (userId == null || sessionId == null) {
            return null;
        }
        PendingWorkout workout = parse(storage.readAll().get(key(userId, sessionId)));
        return workout != null && userId.equals(workout.userId) ? workout : null;
    }

    /** 이 사용자의 기록 전체 (운동이 끝난 순서). 다른 사용자의 기록은 포함하지 않음 */
    public List<PendingWorkout> findForUser(String userId) {
        List<PendingWorkout> workouts = new ArrayList<>();
        if (userId == null) {
            return workouts;
        }
        String prefix = userId + "/";
        for (Map.Entry<String, String> entry : storage.readAll().entrySet()) {
            if (!entry.getKey().startsWith(prefix)) {
                continue;
            }
            PendingWorkout workout = parse(entry.getValue());
            // 키와 내용의 사용자 id가 모두 같을 때만 (내용이 다르면 보여주지도 올리지도 않음)
            if (workout != null && userId.equals(workout.userId)) {
                workouts.add(workout);
            }
        }
        workouts.sort(Comparator.comparingLong(w -> w.endedAtMillis));
        return workouts;
    }

    /** 서버 저장 성공 / 사용자가 삭제를 고른 경우 */
    public void remove(PendingWorkout workout) {
        if (workout == null || workout.userId == null || workout.sessionId == null) {
            return;
        }
        storage.remove(key(workout.userId, workout.sessionId));
    }

    private PendingWorkout parse(String json) {
        if (json == null) {
            return null;
        }
        try {
            return gson.fromJson(json, PendingWorkout.class);
        } catch (JsonParseException e) {
            return null;
        }
    }

    private static String key(String userId, String sessionId) {
        return userId + "/" + sessionId;
    }
}

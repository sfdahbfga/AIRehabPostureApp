package com.example.airehabpostureapp.stats;

import java.time.Instant;
import java.util.Map;

// Supabase exercise_sessions 테이블 한 행 (값이 없는 필드는 null 허용)
public class SessionRecord {

    public final String exerciseName;
    public final Instant startedAt;
    public final Integer durationSeconds;
    public final Integer completedRepetitions;
    public final Integer targetRepetitions;
    public final Double averageAccuracy;
    public final Double firstHalfAccuracy;
    public final Double secondHalfAccuracy;
    // 관절 이름(knee, back 등) → 기준 각도와의 오차(도)
    public final Map<String, Double> jointErrors;

    public SessionRecord(String exerciseName, Instant startedAt, Integer durationSeconds,
                         Integer completedRepetitions, Integer targetRepetitions,
                         Double averageAccuracy, Double firstHalfAccuracy, Double secondHalfAccuracy,
                         Map<String, Double> jointErrors) {
        this.exerciseName = exerciseName;
        this.startedAt = startedAt;
        this.durationSeconds = durationSeconds;
        this.completedRepetitions = completedRepetitions;
        this.targetRepetitions = targetRepetitions;
        this.averageAccuracy = averageAccuracy;
        this.firstHalfAccuracy = firstHalfAccuracy;
        this.secondHalfAccuracy = secondHalfAccuracy;
        this.jointErrors = jointErrors;
    }
}

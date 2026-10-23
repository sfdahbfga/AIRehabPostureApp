package com.example.airehabpostureapp.stats;

import android.content.Context;

import com.example.airehabpostureapp.R;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 20_WeeklyReport 임시 운동 기록 (Supabase 연동 전)
// 기준 날짜로부터 며칠 전 기록으로 만들어 언제 실행해도 이번 주 / 이번 달 범위에 기록이 들어가도록 함
public class SampleSessionData {

    public static List<SessionRecord> create(Context context, LocalDate today) {
        String kneeFlexion = context.getString(R.string.exercise_name_knee_flexion);
        String backStrength = context.getString(R.string.record2_exercise_name);
        String shoulder = context.getString(R.string.record3_exercise_name);
        String lowerBodyStretch = context.getString(R.string.record4_exercise_name);

        List<SessionRecord> records = new ArrayList<>();
        records.add(record(kneeFlexion, today, 0, 300, 88.0, 7.0, 2.0));
        records.add(record(backStrength, today, 1, 420, 85.0, 3.0, 6.5));
        records.add(record(kneeFlexion, today, 2, 360, 90.0, 6.0, 1.0));
        records.add(record(lowerBodyStretch, today, 4, 600, 92.0, 2.0, 3.0));
        records.add(record(shoulder, today, 6, 480, 84.0, 4.0, 5.5));
        records.add(record(kneeFlexion, today, 9, 300, 86.0, 8.0, 2.5));
        records.add(record(backStrength, today, 12, 540, 83.0, 1.5, 7.0));
        records.add(record(kneeFlexion, today, 16, 300, 87.0, 6.5, 3.5));
        records.add(record(lowerBodyStretch, today, 20, 660, 89.0, 2.5, 4.0));
        records.add(record(shoulder, today, 24, 420, 82.0, 5.5, 6.0));
        return records;
    }

    // daysAgo일 전 오전 9시(한국 시간)에 시작한 기록, 반복 횟수는 목표 20회 기준 예시 값
    private static SessionRecord record(String exerciseName, LocalDate today, int daysAgo,
                                        int durationSeconds, double averageAccuracy,
                                        double kneeError, double backError) {
        Instant startedAt = today.minusDays(daysAgo).atTime(9, 0)
                .atZone(ReportStatsCalculator.KOREA_ZONE).toInstant();
        Map<String, Double> jointErrors = new HashMap<>();
        jointErrors.put("knee", kneeError);
        jointErrors.put("back", backError);
        return new SessionRecord(exerciseName, startedAt, durationSeconds, 18, 20,
                averageAccuracy, averageAccuracy - 1, averageAccuracy + 1, jointErrors);
    }
}

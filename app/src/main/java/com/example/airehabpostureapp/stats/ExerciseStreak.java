package com.example.airehabpostureapp.stats;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// 연속 운동 일수 계산 (날짜 기준: 한국 시간, ReportStatsCalculator와 같음)
public final class ExerciseStreak {

    private ExerciseStreak() {
    }

    // 오늘부터(오늘 기록이 없으면 어제부터) 거꾸로 하루도 빠짐없이 기록이 있는 날 수
    public static int consecutiveDays(List<SessionRecord> records, LocalDate today) {
        Set<LocalDate> exerciseDates = new HashSet<>();
        if (records != null) {
            for (SessionRecord record : records) {
                if (record != null && record.startedAt != null) {
                    exerciseDates.add(record.startedAt.atZone(ReportStatsCalculator.KOREA_ZONE).toLocalDate());
                }
            }
        }

        LocalDate date = exerciseDates.contains(today) ? today : today.minusDays(1);
        int days = 0;
        while (exerciseDates.contains(date)) {
            days++;
            date = date.minusDays(1);
        }
        return days;
    }
}

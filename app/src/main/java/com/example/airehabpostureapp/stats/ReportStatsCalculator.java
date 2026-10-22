package com.example.airehabpostureapp.stats;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 운동 기록 목록으로 주간 / 월간 리포트 통계를 계산 (날짜 기준: 한국 시간, 한 주는 월~일)
public class ReportStatsCalculator {

    public enum Period { WEEKLY, MONTHLY }

    public static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    // 관절 오차 절댓값이 이 값(도)을 넘으면 실수로 센다
    public static final double MISTAKE_THRESHOLD_DEGREES = 5.0;

    private static final int WEEKLY_CHART_SIZE = 7;
    private static final int MONTHLY_CHART_SIZE = 4;

    public ReportStats calculate(List<SessionRecord> records, Period period, LocalDate baseDate) {
        // 기간 범위: 주간은 기준 날짜가 속한 주의 월~일, 월간은 기준 날짜가 속한 달의 1일~말일
        LocalDate startDate;
        LocalDate endDate;
        int chartSize;
        if (period == Period.WEEKLY) {
            startDate = baseDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            endDate = startDate.plusDays(6);
            chartSize = WEEKLY_CHART_SIZE;
        } else {
            startDate = baseDate.withDayOfMonth(1);
            endDate = baseDate.with(TemporalAdjusters.lastDayOfMonth());
            chartSize = MONTHLY_CHART_SIZE;
        }

        int exerciseCount = 0;
        int totalSeconds = 0;
        double accuracySum = 0;
        int accuracyCount = 0;
        double[] chartSums = new double[chartSize];
        int[] chartCounts = new int[chartSize];
        Map<String, Integer> exerciseCounts = new LinkedHashMap<>();
        Map<String, Integer> mistakeCounts = new LinkedHashMap<>();

        if (records != null) {
            for (SessionRecord record : records) {
                if (record == null || record.startedAt == null) {
                    continue;
                }
                LocalDate date = record.startedAt.atZone(KOREA_ZONE).toLocalDate();
                if (date.isBefore(startDate) || date.isAfter(endDate)) {
                    continue;
                }

                exerciseCount++;
                if (record.durationSeconds != null) {
                    totalSeconds += record.durationSeconds;
                }

                if (record.averageAccuracy != null) {
                    accuracySum += record.averageAccuracy;
                    accuracyCount++;
                    int index = chartIndex(date, period);
                    chartSums[index] += record.averageAccuracy;
                    chartCounts[index]++;
                }

                if (record.exerciseName != null) {
                    exerciseCounts.merge(record.exerciseName, 1, Integer::sum);
                }

                // 한 기록에서 관절마다 최대 1번만 센다
                if (record.jointErrors != null) {
                    for (Map.Entry<String, Double> entry : record.jointErrors.entrySet()) {
                        if (entry.getKey() != null && entry.getValue() != null
                                && Math.abs(entry.getValue()) > MISTAKE_THRESHOLD_DEGREES) {
                            mistakeCounts.merge(entry.getKey(), 1, Integer::sum);
                        }
                    }
                }
            }
        }

        double[] chartAccuracies = new double[chartSize];
        for (int i = 0; i < chartSize; i++) {
            chartAccuracies[i] = chartCounts[i] == 0 ? 0 : chartSums[i] / chartCounts[i];
        }
        double averageAccuracy = accuracyCount == 0 ? 0 : accuracySum / accuracyCount;

        Map.Entry<String, Integer> mostPerformed = findMax(exerciseCounts);
        Map.Entry<String, Integer> frequentMistake = findMax(mistakeCounts);

        return new ReportStats(
                exerciseCount,
                totalSeconds / 60,
                averageAccuracy,
                chartAccuracies,
                mostPerformed == null ? null : mostPerformed.getKey(),
                mostPerformed == null ? 0 : mostPerformed.getValue(),
                frequentMistake == null ? null : frequentMistake.getKey(),
                frequentMistake == null ? 0 : frequentMistake.getValue());
    }

    // 주간: 월=0 ~ 일=6, 월간: 1~7일=0, 8~14일=1, 15~21일=2, 22일~말일=3
    private int chartIndex(LocalDate date, Period period) {
        if (period == Period.WEEKLY) {
            return date.getDayOfWeek().getValue() - 1;
        }
        return Math.min((date.getDayOfMonth() - 1) / 7, MONTHLY_CHART_SIZE - 1);
    }

    // 가장 큰 값을 가진 항목 (동률이면 먼저 나온 항목), 비어 있으면 null
    private Map.Entry<String, Integer> findMax(Map<String, Integer> counts) {
        Map.Entry<String, Integer> max = null;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (max == null || entry.getValue() > max.getValue()) {
                max = entry;
            }
        }
        return max;
    }
}

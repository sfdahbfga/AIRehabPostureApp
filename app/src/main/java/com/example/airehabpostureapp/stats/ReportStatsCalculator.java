package com.example.airehabpostureapp.stats;

import com.example.airehabpostureapp.pose.ExerciseCriteria;

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

    private static final int WEEKLY_CHART_SIZE = 7;
    private static final int MONTHLY_CHART_SIZE = 4;

    // 기간 범위 {시작일, 종료일}: 주간은 기준 날짜가 속한 주의 월~일, 월간은 기준 날짜가 속한 달의 1일~말일
    public static LocalDate[] periodRange(Period period, LocalDate baseDate) {
        if (period == Period.WEEKLY) {
            LocalDate monday = baseDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            return new LocalDate[]{monday, monday.plusDays(6)};
        }
        return new LocalDate[]{baseDate.withDayOfMonth(1), baseDate.with(TemporalAdjusters.lastDayOfMonth())};
    }

    public ReportStats calculate(List<SessionRecord> records, Period period, LocalDate baseDate) {
        LocalDate[] range = periodRange(period, baseDate);
        LocalDate startDate = range[0];
        LocalDate endDate = range[1];
        int chartSize = period == Period.WEEKLY ? WEEKLY_CHART_SIZE : MONTHLY_CHART_SIZE;

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

                // 한 기록에서 관절마다 최대 1번만 센다 (운동별 허용 범위를 벗어난 경우만)
                if (record.jointErrors != null) {
                    ExerciseCriteria criteria = ExerciseCriteria.forExerciseOrDefault(record.exerciseName);
                    for (Map.Entry<String, Double> entry : record.jointErrors.entrySet()) {
                        if (entry.getKey() != null && entry.getValue() != null
                                && isMistake(criteria, entry.getKey(), entry.getValue())) {
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
                totalSeconds,
                averageAccuracy,
                accuracyCount > 0,
                chartAccuracies,
                mostPerformed == null ? null : mostPerformed.getKey(),
                mostPerformed == null ? 0 : mostPerformed.getValue(),
                frequentMistake == null ? null : frequentMistake.getKey(),
                frequentMistake == null ? 0 : frequentMistake.getValue());
    }

    // 주간: 월=0 ~ 일=6, 월간: 1~7일=0, 8~14일=1, 15~21일=2, 22일~말일=3
    /**
     * 관절 오차가 운동별 허용 범위(ExerciseCriteria)를 벗어났으면 실수.
     * - knee / hip: 목표 각도와의 차이가 덜 굽힘 / 더 굽힘 허용 오차를 넘음 (= 그 관절 점수가 만점이 아님)
     * - back: 저장값이 이미 상체 허용 각도를 넘은 만큼이므로 0보다 크면 실수
     * 기준이 없는 운동 / 이름이 없는 기록은 무릎 굽힘 운동 기준 (JointAccuracyConverter와 같음)
     */
    static boolean isMistake(ExerciseCriteria criteria, String joint, double error) {
        if (JointKeys.BACK.equals(joint)) {
            return error > 0;
        }
        return criteria.jointScoreForError(error) < 100;
    }

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

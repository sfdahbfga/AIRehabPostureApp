package com.example.airehabpostureapp.stats;

import org.junit.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ReportStatsCalculatorTest {

    private static final double DELTA = 0.0001;
    private static final String KNEE_FLEXION = "무릎 굽히기";
    private static final String SQUAT = "스쿼트";
    private static final String PLANK = "플랭크";

    // 기준 날짜: 2026-10-22(목) → 주간 범위 10/19(월) ~ 10/25(일), 월간 범위 10/1 ~ 10/31
    private static final LocalDate BASE_DATE = LocalDate.of(2026, 10, 22);

    private final ReportStatsCalculator calculator = new ReportStatsCalculator();

    @Test
    public void weekly_sampleRecords() {
        ReportStats stats = calculator.calculate(sampleRecords(), ReportStatsCalculator.Period.WEEKLY, BASE_DATE);

        assertEquals(5, stats.exerciseCount);
        // 600 + 900 + 300 + 420 + 180 = 2400초
        assertEquals(40, stats.totalExerciseMinutes);
        assertEquals(2400, stats.totalExerciseSeconds);
        // 정확도 null 기록(스쿼트) 제외: (80 + 90 + 85 + 70) / 4
        assertEquals(81.25, stats.averageAccuracy, DELTA);
        // 월: (80 + 70) / 2, 수: 90 (스쿼트는 정확도 없음), 일: 85
        assertArrayEquals(new double[]{75, 0, 90, 0, 0, 0, 85}, stats.chartAccuracies, DELTA);
        assertEquals(KNEE_FLEXION, stats.mostPerformedExerciseName);
        assertEquals(3, stats.mostPerformedExerciseCount);
        // knee: 7, -6, 9 → 3회 (정확히 5.0은 제외), back: 6.5, -8 → 2회
        assertEquals("knee", stats.frequentMistakeJoint);
        assertEquals(3, stats.frequentMistakeCount);
    }

    @Test
    public void monthly_sampleRecords() {
        ReportStats stats = calculator.calculate(sampleRecords(), ReportStatsCalculator.Period.MONTHLY, BASE_DATE);

        // 9/30 기록만 제외
        assertEquals(7, stats.exerciseCount);
        // 2400 + 300 + 600 = 3300초
        assertEquals(55, stats.totalExerciseMinutes);
        assertEquals(3300, stats.totalExerciseSeconds);
        // (80 + 90 + 85 + 70 + 100 + 60) / 6
        assertEquals(485.0 / 6, stats.averageAccuracy, DELTA);
        // 1주차(1~7일): 60, 2주차: 없음, 3주차(15~21일): (80 + 90 + 70) / 3, 4주차(22일~): (85 + 100) / 2
        assertArrayEquals(new double[]{60, 0, 80, 92.5}, stats.chartAccuracies, DELTA);
        assertEquals(KNEE_FLEXION, stats.mostPerformedExerciseName);
        assertEquals(4, stats.mostPerformedExerciseCount);
        assertEquals("knee", stats.frequentMistakeJoint);
        assertEquals(3, stats.frequentMistakeCount);
    }

    @Test
    public void koreaTimeZone_decidesDate() {
        // UTC 일요일 15:30 = 한국 시간 월요일 00:30 → 이번 주 월요일 칸
        SessionRecord record = new SessionRecord(SQUAT, Instant.parse("2026-10-18T15:30:00Z"),
                60, 10, 10, 50.0, null, null, null);

        ReportStats stats = calculator.calculate(Collections.singletonList(record),
                ReportStatsCalculator.Period.WEEKLY, BASE_DATE);

        assertEquals(1, stats.exerciseCount);
        assertEquals(50, stats.chartAccuracies[0], DELTA);
    }

    @Test
    public void emptyOrNullRecords_returnZeroValues() {
        List<List<SessionRecord>> inputs = Arrays.asList(null, new ArrayList<>());
        for (List<SessionRecord> input : inputs) {
            for (ReportStatsCalculator.Period period : ReportStatsCalculator.Period.values()) {
                ReportStats stats = calculator.calculate(input, period, BASE_DATE);

                assertEquals(0, stats.exerciseCount);
                assertEquals(0, stats.totalExerciseMinutes);
                assertEquals(0, stats.averageAccuracy, DELTA);
                assertEquals(period == ReportStatsCalculator.Period.WEEKLY ? 7 : 4,
                        stats.chartAccuracies.length);
                for (double value : stats.chartAccuracies) {
                    assertEquals(0, value, DELTA);
                }
                assertNull(stats.mostPerformedExerciseName);
                assertEquals(0, stats.mostPerformedExerciseCount);
                assertNull(stats.frequentMistakeJoint);
                assertEquals(0, stats.frequentMistakeCount);
            }
        }
    }

    @Test
    public void recordWithMissingValues_isCountedWithoutError() {
        Map<String, Double> jointErrors = new HashMap<>();
        jointErrors.put("knee", null);
        List<SessionRecord> records = Arrays.asList(
                null,
                // 시작 시각이 없으면 기간을 알 수 없으므로 제외
                new SessionRecord(SQUAT, null, 600, 10, 10, 90.0, null, null, null),
                new SessionRecord(null, kst(2026, 10, 20, 10, 0), null, null, null, null, null, null, jointErrors));

        ReportStats stats = calculator.calculate(records, ReportStatsCalculator.Period.WEEKLY, BASE_DATE);

        assertEquals(1, stats.exerciseCount);
        assertEquals(0, stats.totalExerciseMinutes);
        assertEquals(0, stats.averageAccuracy, DELTA);
        assertFalse(stats.hasAccuracy);
        assertNull(stats.mostPerformedExerciseName);
        assertNull(stats.frequentMistakeJoint);
    }

    @Test
    public void hasAccuracy_onlyWhenSomeRecordHasAccuracy() {
        // 자세 인식 미지원 운동 기록: 정확도 / 관절 오차 null
        SessionRecord unsupported = new SessionRecord(SQUAT, kst(2026, 10, 20, 10, 0),
                300, 0, 20, null, null, null, null);
        ReportStats onlyUnsupported = calculator.calculate(Collections.singletonList(unsupported),
                ReportStatsCalculator.Period.WEEKLY, BASE_DATE);
        assertEquals(1, onlyUnsupported.exerciseCount);
        assertFalse(onlyUnsupported.hasAccuracy);

        assertTrue(calculator.calculate(sampleRecords(), ReportStatsCalculator.Period.WEEKLY, BASE_DATE).hasAccuracy);
        assertFalse(calculator.calculate(new ArrayList<>(), ReportStatsCalculator.Period.WEEKLY, BASE_DATE).hasAccuracy);
    }

    private List<SessionRecord> sampleRecords() {
        return Arrays.asList(
                // 9/30(수) - 월간/주간 모두 범위 밖
                new SessionRecord(PLANK, kst(2026, 9, 30, 23, 0), 1200, 1, 1, 10.0, null, null, null),
                // 10/3(토) - 월간 1주차
                new SessionRecord(PLANK, kst(2026, 10, 3, 9, 0), 600, 1, 1, 60.0, 62.0, 58.0, null),
                // 10/19(월) 00:30 - 이번 주 월요일
                new SessionRecord(SQUAT, kst(2026, 10, 19, 0, 30), 180, 10, 10, 70.0, 72.0, 68.0,
                        joints(9.0, -8.0)),
                // 10/19(월)
                new SessionRecord(KNEE_FLEXION, kst(2026, 10, 19, 9, 0), 600, 10, 10, 80.0, 82.0, 78.0,
                        joints(7.0, 2.0)),
                // 10/21(수)
                new SessionRecord(KNEE_FLEXION, kst(2026, 10, 21, 20, 0), 900, 12, 12, 90.0, 91.0, 89.0,
                        joints(-6.0, 6.5)),
                // 10/21(수) - 정확도 없음
                new SessionRecord(SQUAT, kst(2026, 10, 21, 21, 0), 300, 5, 10, null, null, null,
                        joints(3.0, null)),
                // 10/25(일) 23:30 - 이번 주 마지막, 오차가 정확히 5도라 실수 아님
                new SessionRecord(KNEE_FLEXION, kst(2026, 10, 25, 23, 30), 420, 10, 10, 85.0, 86.0, 84.0,
                        joints(5.0, -5.0)),
                // 10/26(월) 00:10 - 다음 주 (월간 4주차)
                new SessionRecord(KNEE_FLEXION, kst(2026, 10, 26, 0, 10), 300, 10, 10, 100.0, 100.0, 100.0,
                        null));
    }

    private Instant kst(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute)
                .atZone(ReportStatsCalculator.KOREA_ZONE).toInstant();
    }

    private Map<String, Double> joints(Double knee, Double back) {
        Map<String, Double> map = new HashMap<>();
        map.put("knee", knee);
        map.put("back", back);
        return map;
    }
}

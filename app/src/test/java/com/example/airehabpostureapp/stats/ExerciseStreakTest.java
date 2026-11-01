package com.example.airehabpostureapp.stats;

import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class ExerciseStreakTest {

    // 기준 날짜: 2026-11-04(수)
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 4);

    @Test
    public void todayIncluded_countsBackward() {
        // 11/4, 11/3, 11/2 연속 → 3일 (11/1 없음, 10/31은 끊긴 뒤라 제외)
        List<SessionRecord> records = Arrays.asList(
                record(2026, 11, 4, 9), record(2026, 11, 3, 20),
                record(2026, 11, 2, 7), record(2026, 10, 31, 9));
        assertEquals(3, ExerciseStreak.consecutiveDays(records, TODAY));
    }

    @Test
    public void noRecordToday_startsFromYesterday() {
        // 오늘 없음 → 어제(11/3)부터: 11/3, 11/2 → 2일
        List<SessionRecord> records = Arrays.asList(record(2026, 11, 3, 9), record(2026, 11, 2, 9));
        assertEquals(2, ExerciseStreak.consecutiveDays(records, TODAY));
    }

    @Test
    public void noRecordTodayOrYesterday_isZero() {
        List<SessionRecord> records = Collections.singletonList(record(2026, 11, 2, 9));
        assertEquals(0, ExerciseStreak.consecutiveDays(records, TODAY));
    }

    @Test
    public void sameDayRecords_countOnce() {
        List<SessionRecord> records = Arrays.asList(
                record(2026, 11, 4, 9), record(2026, 11, 4, 21), record(2026, 11, 3, 9));
        assertEquals(2, ExerciseStreak.consecutiveDays(records, TODAY));
    }

    @Test
    public void usesKoreaDate() {
        // 한국 시간 11/4 00:30 = UTC 11/3 15:30 → 한국 날짜 11/4로 셈
        List<SessionRecord> records = Collections.singletonList(recordAt(LocalDateTime.of(2026, 11, 4, 0, 30)));
        assertEquals(1, ExerciseStreak.consecutiveDays(records, TODAY));
    }

    @Test
    public void emptyOrNull_isZero() {
        assertEquals(0, ExerciseStreak.consecutiveDays(new ArrayList<>(), TODAY));
        assertEquals(0, ExerciseStreak.consecutiveDays(null, TODAY));
    }

    // 한국 시간 기준 해당 날짜 hour시에 시작한 기록
    private static SessionRecord record(int year, int month, int day, int hour) {
        return recordAt(LocalDateTime.of(year, month, day, hour, 0));
    }

    private static SessionRecord recordAt(LocalDateTime koreaTime) {
        return new SessionRecord("무릎 굽힘 운동", koreaTime.atZone(ReportStatsCalculator.KOREA_ZONE).toInstant(),
                300, 8, 20, 86.0, 91.0, 78.0, null);
    }
}

package com.example.airehabpostureapp;

import android.content.Context;

// 운동 시간 표시 형식 (기록 목록 / 상세, 주간 리포트, 목표, 운동 결과에서 공통 사용)
public final class DurationFormatter {

    private static final int MINUTES_PER_HOUR = 60;
    private static final int SECONDS_PER_MINUTE = 60;
    private static final int SECONDS_PER_HOUR = 3600;

    private DurationFormatter() {
    }

    // 분 단위: 1시간 미만 "28분", 정각 "1시간", 그 외 "1시간 5분"
    public static String formatMinutes(Context context, int totalMinutes) {
        int hours = totalMinutes / MINUTES_PER_HOUR;
        int minutes = totalMinutes % MINUTES_PER_HOUR;
        if (hours == 0) {
            return context.getString(R.string.duration_minutes_format, minutes);
        }
        if (minutes == 0) {
            return context.getString(R.string.duration_hours_format, hours);
        }
        return context.getString(R.string.hours_minutes_format, hours, minutes);
    }

    // 초 단위를 분까지만: 기록 / 리포트 / 목표용 (초는 버림, 1~59초는 "1분 미만", 0초는 "0분")
    public static String formatSecondsAsMinutes(Context context, int totalSeconds) {
        if (totalSeconds > 0 && totalSeconds < SECONDS_PER_MINUTE) {
            return context.getString(R.string.duration_under_one_minute);
        }
        return formatMinutes(context, totalSeconds / SECONDS_PER_MINUTE);
    }

    // 초까지 표시 (16_Result): 1분 미만 "28초", 1시간 미만 "3분 28초", 그 외 "1시간 5분 28초"
    public static String formatSeconds(Context context, int totalSeconds) {
        int hours = totalSeconds / SECONDS_PER_HOUR;
        int minutes = totalSeconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE;
        int seconds = totalSeconds % SECONDS_PER_MINUTE;
        if (hours > 0) {
            return context.getString(R.string.duration_hours_minutes_seconds_format, hours, minutes, seconds);
        }
        if (minutes > 0) {
            return context.getString(R.string.exercise_duration_format, minutes, seconds);
        }
        return context.getString(R.string.duration_seconds_format, seconds);
    }
}

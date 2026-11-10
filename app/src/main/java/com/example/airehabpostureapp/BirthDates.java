package com.example.airehabpostureapp;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

/**
 * 회원가입 생년월일 검사 (Android 의존성 없는 순수 Java). SignUpActivity에서 동작 그대로 옮김 (L15)
 * 입력 형식 YYYY.MM.DD → DB date 형식 YYYY-MM-DD, 허용 범위 1900-01-01 ~ 오늘
 */
public final class BirthDates {

    // 생년월일로 입력할 수 있는 가장 이른 날짜
    static final LocalDate MIN_BIRTH_DATE = LocalDate.of(1900, 1, 1);

    private static final DateTimeFormatter INPUT_FORMAT = DateTimeFormatter.ofPattern("uuuu.MM.dd")
            .withResolverStyle(ResolverStyle.STRICT);

    private BirthDates() {
    }

    // 입력 형식 YYYY.MM.DD → DB date 형식 YYYY-MM-DD. 존재하지 않는 날짜 / 형식이 다르면 null
    public static String toDbBirthDate(String input) {
        try {
            return LocalDate.parse(input, INPUT_FORMAT).toString();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    // 생년월일 허용 범위: 1900-01-01 ~ 오늘. dbBirthDate는 YYYY-MM-DD
    public static boolean isInRange(String dbBirthDate, LocalDate today) {
        LocalDate date = LocalDate.parse(dbBirthDate);
        return !date.isBefore(MIN_BIRTH_DATE) && !date.isAfter(today);
    }
}

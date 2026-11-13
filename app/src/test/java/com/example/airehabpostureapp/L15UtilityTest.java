package com.example.airehabpostureapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.airehabpostureapp.network.ExerciseSessionApi;

import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * L15: 날짜 파싱(RecordRow.startedAtInstant), 운동 시간 표시(DurationFormatter),
 * 생년월일 검사(BirthDates), 음성 문구 변환(VoiceGuide.toSpeechText)
 */
public class L15UtilityTest {

    // 실제 strings.xml의 문구로 DurationFormatter를 확인 (리소스 id → 이름 → 문구)
    private static final Map<Integer, String> STRINGS = new HashMap<>();

    @BeforeClass
    public static void loadStrings() throws Exception {
        Map<String, String> byName = new HashMap<>();
        Element root = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new File("src/main/res/values/strings.xml")).getDocumentElement();
        NodeList nodes = root.getElementsByTagName("string");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            byName.put(element.getAttribute("name"), element.getTextContent());
        }
        for (Field field : R.string.class.getFields()) {
            String value = byName.get(field.getName());
            if (value != null) {
                STRINGS.put(field.getInt(null), value);
            }
        }
    }

    private static final DurationFormatter.Strings STRINGS_SOURCE =
            (resId, args) -> String.format(STRINGS.get(resId), args);

    // --- RecordRow.startedAtInstant (Supabase timestamptz)

    private static Instant startedAt(String text) {
        ExerciseSessionApi.RecordRow row = new ExerciseSessionApi.RecordRow();
        row.startedAt = text;
        return row.startedAtInstant();
    }

    @Test
    public void startedAt_parsesSupabaseFormats() {
        assertEquals(Instant.parse("2026-11-01T14:11:13.756Z"), startedAt("2026-11-01T14:11:13.756+00:00"));
        assertEquals(Instant.parse("2026-11-01T05:00:00Z"), startedAt("2026-11-01T14:00:00+09:00"));
        assertEquals(Instant.parse("2026-11-01T03:00:00Z"), startedAt("2026-11-01T03:00:00Z"));
    }

    @Test
    public void startedAt_missingOrBroken_isNull() {
        assertNull(startedAt(null));
        assertNull(startedAt(""));
        assertNull(startedAt("2026-11-01 14:00"));
        assertNull(startedAt("not a date"));
    }

    // --- DurationFormatter

    @Test
    public void formatMinutes() {
        assertEquals("0분", DurationFormatter.formatMinutes(STRINGS_SOURCE, 0));
        assertEquals("28분", DurationFormatter.formatMinutes(STRINGS_SOURCE, 28));
        assertEquals("1시간", DurationFormatter.formatMinutes(STRINGS_SOURCE, 60));
        assertEquals("1시간 5분", DurationFormatter.formatMinutes(STRINGS_SOURCE, 65));
    }

    @Test
    public void formatSecondsAsMinutes_dropsSeconds_underOneMinuteLabel() {
        assertEquals("0분", DurationFormatter.formatSecondsAsMinutes(STRINGS_SOURCE, 0));
        assertEquals("1분 미만", DurationFormatter.formatSecondsAsMinutes(STRINGS_SOURCE, 1));
        assertEquals("1분 미만", DurationFormatter.formatSecondsAsMinutes(STRINGS_SOURCE, 59));
        assertEquals("1분", DurationFormatter.formatSecondsAsMinutes(STRINGS_SOURCE, 119));
        assertEquals("1시간 1분", DurationFormatter.formatSecondsAsMinutes(STRINGS_SOURCE, 3661));
    }

    @Test
    public void formatSeconds_showsSeconds() {
        assertEquals("28초", DurationFormatter.formatSeconds(STRINGS_SOURCE, 28));
        assertEquals("1시간 5분 28초", DurationFormatter.formatSeconds(STRINGS_SOURCE, 3928));
        // 1시간 미만은 exercise_duration_format (분 + 초)
        assertEquals(String.format(STRINGS.get(R.string.exercise_duration_format), 3, 28),
                DurationFormatter.formatSeconds(STRINGS_SOURCE, 208));
    }

    // --- BirthDates

    @Test
    public void birthDate_inputFormat_strict() {
        assertEquals("1990-03-07", BirthDates.toDbBirthDate("1990.03.07"));
        assertEquals("2000-02-29", BirthDates.toDbBirthDate("2000.02.29"));
        assertNull("없는 날짜", BirthDates.toDbBirthDate("2001.02.29"));
        assertNull("없는 날짜", BirthDates.toDbBirthDate("1990.13.01"));
        assertNull("구분자", BirthDates.toDbBirthDate("1990-03-07"));
        assertNull("자릿수", BirthDates.toDbBirthDate("1990.3.7"));
        assertNull(BirthDates.toDbBirthDate(""));
    }

    @Test
    public void birthDate_range_1900ToToday() {
        LocalDate today = LocalDate.of(2026, 11, 11);
        assertTrue(BirthDates.isInRange("1900-01-01", today));
        assertTrue(BirthDates.isInRange("2026-11-11", today));
        assertFalse(BirthDates.isInRange("1899-12-31", today));
        assertFalse(BirthDates.isInRange("2026-11-12", today));
    }

    // --- VoiceGuide.toSpeechText

    @Test
    public void speechText_dropsTrailingCountInParentheses() {
        assertEquals("좋아요! 그대로 유지하세요.", VoiceGuide.toSpeechText("좋아요! 그대로 유지하세요. (3/20초)"));
        assertEquals("무릎을 더 굽혀 주세요.", VoiceGuide.toSpeechText("무릎을 더 굽혀 주세요."));
        // 끝이 아닌 괄호도 끝까지 이어지면 함께 빠짐 (기존 동작 그대로)
        assertEquals("앉기", VoiceGuide.toSpeechText("앉기 (의자) 천천히 (1/3)"));
        assertEquals("", VoiceGuide.toSpeechText("(1/3)"));
    }
}

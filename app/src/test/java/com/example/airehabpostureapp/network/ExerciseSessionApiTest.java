package com.example.airehabpostureapp.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import retrofit2.http.POST;

/**
 * 체크인 저장 요청이 PGRST102("All object keys must match")를 내지 않도록 하는 설정을 검증한다.
 * (운동 전 메모는 비고 운동 후 메모만 있으면 두 행의 JSON 키가 달라지는 문제)
 */
public class ExerciseSessionApiTest {

    private static final String SESSION_ID = "00000000-0000-0000-0000-000000000000";
    private static final String PRE_ID = "00000000-0000-0000-0000-000000000001";
    private static final String POST_ID = "00000000-0000-0000-0000-000000000002";

    @Test
    public void gsonOmitsNullNote_soRowsCanHaveDifferentKeys() {
        // 문제가 되는 상황 재현: 같은 요청 안에서 한 행만 note 키를 가짐
        List<ExerciseSessionApi.CheckinInsert> rows = Arrays.asList(
                new ExerciseSessionApi.CheckinInsert(PRE_ID, SESSION_ID, "pre", 2, "good", null),
                new ExerciseSessionApi.CheckinInsert(POST_ID, SESSION_ID, "post", 2, "good", "test"));
        String json = new Gson().toJson(rows);
        String preRow = json.substring(0, json.indexOf("},") + 1);
        String postRow = json.substring(json.indexOf("},") + 2);
        assertFalse(preRow.contains("\"note\""));
        assertTrue(postRow.contains("\"note\":\"test\""));
    }

    @Test
    public void insertCheckins_specifiesColumns() throws NoSuchMethodException {
        // columns를 지정해야 PostgREST가 빠진 note 키를 null로 채운다
        Method method = ExerciseSessionApi.class.getMethod("insertCheckins", String.class, List.class);
        String path = method.getAnnotation(POST.class).value();
        assertEquals("rest/v1/session_checkins?on_conflict=id&columns=" + ExerciseSessionApi.CHECKIN_COLUMNS, path);
    }

    @Test
    public void checkinColumns_matchAllSerializedFields() {
        // 나중에 CheckinInsert에 필드를 추가하고 columns에 빠뜨리면 그 값이 저장되지 않으므로 함께 맞춘다
        Set<String> serializedNames = new HashSet<>();
        for (Field field : ExerciseSessionApi.CheckinInsert.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            SerializedName name = field.getAnnotation(SerializedName.class);
            assertTrue("@SerializedName이 없는 필드: " + field.getName(), name != null);
            serializedNames.add(name.value());
        }
        Set<String> columns = new HashSet<>(Arrays.asList(ExerciseSessionApi.CHECKIN_COLUMNS.split(",")));
        assertEquals(serializedNames, columns);
    }
}

package com.example.airehabpostureapp;

/**
 * 운동 흐름(ExerciseDetail → PreCheck → CameraSetup → Exercise → PostCheck)에서
 * Intent로 넘기는 값의 key. 각 화면은 받은 Intent를 그대로 다음 화면에 넘기고(putExtras),
 * PostCheck에서 "완료"를 누를 때 모아서 한 번에 Supabase에 저장한다.
 */
public final class ExerciseFlowExtras {

    private ExerciseFlowExtras() {
    }

    private static final String PREFIX = "com.example.airehabpostureapp.flow.";

    // ExerciseDetail에서 고른 운동 이름
    public static final String EXERCISE_NAME = PREFIX + "EXERCISE_NAME";

    // 11_PreCheck 입력값
    public static final String PRE_DISCOMFORT_LEVEL = PREFIX + "PRE_DISCOMFORT_LEVEL";
    public static final String PRE_BODY_CONDITION = PREFIX + "PRE_BODY_CONDITION";
    public static final String PRE_NOTE = PREFIX + "PRE_NOTE";

    // 13_Exercise 결과 (시각은 epoch milliseconds)
    public static final String STARTED_AT_MILLIS = PREFIX + "STARTED_AT_MILLIS";
    public static final String ENDED_AT_MILLIS = PREFIX + "ENDED_AT_MILLIS";
    public static final String DURATION_SECONDS = PREFIX + "DURATION_SECONDS";
    public static final String COMPLETED_REPS = PREFIX + "COMPLETED_REPS";
    public static final String TARGET_REPS = PREFIX + "TARGET_REPS";
    public static final String AVERAGE_ACCURACY = PREFIX + "AVERAGE_ACCURACY";
    // 자세 인식 측정값 (정수). 측정값이 없으면(0회 / 미지원 운동) 키를 넣지 않는다 → 저장 시 null
    public static final String FIRST_HALF_ACCURACY = PREFIX + "FIRST_HALF_ACCURACY";
    public static final String SECOND_HALF_ACCURACY = PREFIX + "SECOND_HALF_ACCURACY";
    // 관절 평균 오차 (도). 무릎: 가장 많이 굽힌 각도 - 목표 각도 (+ 덜 굽힘 / - 더 굽힘), 허리: 상체 숙임 허용 초과량
    public static final String KNEE_ERROR_DEGREES = PREFIX + "KNEE_ERROR_DEGREES";
    public static final String BACK_ERROR_DEGREES = PREFIX + "BACK_ERROR_DEGREES";
    // 엉덩이 각도 오차 (도, 하체 스트레칭): 유지 중 평균 엉덩이 각도 - 목표 각도 (+ 덜 숙임 / - 더 숙임)
    public static final String HIP_ERROR_DEGREES = PREFIX + "HIP_ERROR_DEGREES";
}

package com.example.airehabpostureapp.stats;

/**
 * exercise_sessions.joint_errors(관절별 평균 오차)의 키 (L12)
 * DB에 저장되는 값이므로 바꾸면 과거 기록을 읽지 못한다.
 */
public final class JointKeys {

    /** 무릎: 가장 많이 굽힌 각도 - 목표 각도 (+ 덜 굽힘 / - 더 굽힘) */
    public static final String KNEE = "knee";
    /** 허리(상체): 상체 숙임 허용 각도를 넘은 만큼 */
    public static final String BACK = "back";
    /** 엉덩이(하체 스트레칭): 유지 중 평균 엉덩이 각도 - 목표 각도 (+ 덜 숙임 / - 더 숙임) */
    public static final String HIP = "hip";

    private JointKeys() {
    }
}

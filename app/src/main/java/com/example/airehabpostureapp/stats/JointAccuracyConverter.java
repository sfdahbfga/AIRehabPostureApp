package com.example.airehabpostureapp.stats;

/**
 * 저장된 관절별 평균 오차(joint_errors, 단위: 도)를 정확도(0~100점)로 바꾼다. (Android 의존성 없는 순수 Java)
 *
 * 기준은 자세 인식(feature/pose-knee-reps)의 ExerciseCriteria.KNEE_FLEXION 회차 점수 계산과 같아야 한다.
 * (운동 화면 / 결과 / 기록 상세의 점수가 서로 다르게 보이지 않도록) 기준값을 바꾸면 두 곳을 함께 바꾼다.
 * - 무릎(knee): (가장 많이 굽힌 각도 - 목표 각도). +: 덜 굽힘 10도까지, -: 더 굽힘 20도까지 만점, 넘으면 1도당 2점 감점
 * - 허리(back): 상체 허용 각도(15도)를 넘은 만큼만 저장되므로 1도당 4점 감점
 *
 * TODO: 자세 인식 브랜치를 합칠 때 ExerciseCriteria를 쓰도록 바꾸고,
 *       회차별 관절 점수(joint_scores)를 저장해서 운동 화면과 기록 상세의 점수를 맞출 것
 */
public final class JointAccuracyConverter {

    static final double KNEE_UNDER_BEND_TOLERANCE = 10;
    static final double KNEE_OVER_BEND_TOLERANCE = 20;
    static final double KNEE_POINTS_PER_DEGREE = 2;
    static final double BACK_POINTS_PER_DEGREE = 4;

    private JointAccuracyConverter() {
    }

    /** 무릎 오차(도, 부호 있음) → 정확도. 값이 없으면 null */
    public static Integer kneeAccuracy(Double kneeError) {
        if (kneeError == null) {
            return null;
        }
        double overDegrees = kneeError >= 0
                ? Math.max(0, kneeError - KNEE_UNDER_BEND_TOLERANCE)
                : Math.max(0, -kneeError - KNEE_OVER_BEND_TOLERANCE);
        return clampAndRound(100 - overDegrees * KNEE_POINTS_PER_DEGREE);
    }

    /** 허리 오차(허용 각도 초과량, 도) → 정확도. 값이 없으면 null */
    public static Integer backAccuracy(Double backError) {
        if (backError == null) {
            return null;
        }
        return clampAndRound(100 - Math.max(0, backError) * BACK_POINTS_PER_DEGREE);
    }

    private static int clampAndRound(double score) {
        return (int) Math.round(Math.max(0, Math.min(100, score)));
    }
}

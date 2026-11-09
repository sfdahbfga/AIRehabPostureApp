package com.example.airehabpostureapp.stats;

import com.example.airehabpostureapp.pose.ExerciseCriteria;

/**
 * 저장된 관절별 평균 오차(joint_errors, 단위: 도)를 정확도(0~100점)로 바꾼다. (Android 의존성 없는 순수 Java)
 *
 * 운동 화면 / 결과 / 기록 상세의 점수가 서로 다르게 보이지 않도록
 * 기준값과 계산은 자세 인식의 ExerciseCriteria(회차 점수 계산)를 그대로 쓴다.
 * - 무릎(knee): (가장 많이 굽힌 각도 - 목표 각도). ExerciseCriteria.jointScoreForError
 * - 허리(back): 상체 허용 각도를 넘은 만큼만 저장됨. ExerciseCriteria.trunkScore
 * - 엉덩이(hip, 하체 스트레칭): (유지 중 평균 엉덩이 각도 - 목표 각도). ExerciseCriteria.jointScoreForError
 * 기준은 기록의 운동 이름으로 찾은 운동별 기준(ExerciseCriteria.forExercise)을 쓰고,
 * 기준이 없는 운동 / 이름이 없는 기록은 무릎 굽힘 운동 기준을 쓴다.
 *
 * TODO: 회차별 관절 점수(joint_scores)를 저장해서 운동 화면과 기록 상세의 점수를 맞출 것
 *       (지금은 평균 오차를 점수로 바꾸므로 회차 점수의 평균과 다를 수 있음)
 */
public final class JointAccuracyConverter {

    private JointAccuracyConverter() {
    }

    /** 무릎 오차(도, 부호 있음) → 정확도 (무릎 굽힘 운동 기준). 값이 없으면 null */
    public static Integer kneeAccuracy(Double kneeError) {
        return kneeAccuracy(null, kneeError);
    }

    /** 허리 오차(허용 각도 초과량, 도) → 정확도 (무릎 굽힘 운동 기준). 값이 없으면 null */
    public static Integer backAccuracy(Double backError) {
        return backAccuracy(null, backError);
    }

    /** 무릎 오차(도, 부호 있음) → 운동별 기준의 정확도. 값이 없으면 null */
    public static Integer kneeAccuracy(String exerciseName, Double kneeError) {
        return kneeError == null ? null
                : (int) Math.round(ExerciseCriteria.forExerciseOrDefault(exerciseName).jointScoreForError(kneeError));
    }

    /** 허리 오차(허용 각도 초과량, 도) → 운동별 기준의 정확도. 값이 없으면 null */
    public static Integer backAccuracy(String exerciseName, Double backError) {
        return backError == null ? null
                : (int) Math.round(ExerciseCriteria.forExerciseOrDefault(exerciseName).trunkScore(backError));
    }

    /** 엉덩이 오차(도, 부호 있음, + 덜 숙임 / - 더 숙임) → 운동별 기준의 정확도. 값이 없으면 null */
    public static Integer hipAccuracy(String exerciseName, Double hipError) {
        return hipError == null ? null
                : (int) Math.round(ExerciseCriteria.forExerciseOrDefault(exerciseName).jointScoreForError(hipError));
    }
}

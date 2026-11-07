package com.example.airehabpostureapp.stats;

import com.example.airehabpostureapp.pose.ExerciseCriteria;

/**
 * 저장된 관절별 평균 오차(joint_errors, 단위: 도)를 정확도(0~100점)로 바꾼다. (Android 의존성 없는 순수 Java)
 *
 * 운동 화면 / 결과 / 기록 상세의 점수가 서로 다르게 보이지 않도록
 * 기준값과 계산은 자세 인식의 ExerciseCriteria(회차 점수 계산)를 그대로 쓴다.
 * - 무릎(knee): (가장 많이 굽힌 각도 - 목표 각도). ExerciseCriteria.jointScoreForError
 * - 허리(back): 상체 허용 각도를 넘은 만큼만 저장됨. ExerciseCriteria.trunkScore
 * 기준은 현재 자세 인식을 지원하는 무릎 굽힘 운동(KNEE_FLEXION) 하나뿐이라 모든 기록에 이 기준을 쓴다.
 *
 * TODO: 회차별 관절 점수(joint_scores)를 저장해서 운동 화면과 기록 상세의 점수를 맞출 것
 *       (지금은 평균 오차를 점수로 바꾸므로 회차 점수의 평균과 다를 수 있음)
 */
public final class JointAccuracyConverter {

    private static final ExerciseCriteria CRITERIA = ExerciseCriteria.KNEE_FLEXION;

    private JointAccuracyConverter() {
    }

    /** 무릎 오차(도, 부호 있음) → 정확도. 값이 없으면 null */
    public static Integer kneeAccuracy(Double kneeError) {
        return kneeError == null ? null : (int) Math.round(CRITERIA.jointScoreForError(kneeError));
    }

    /** 허리 오차(허용 각도 초과량, 도) → 정확도. 값이 없으면 null */
    public static Integer backAccuracy(Double backError) {
        return backError == null ? null : (int) Math.round(CRITERIA.trunkScore(backError));
    }
}

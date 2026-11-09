package com.example.airehabpostureapp.pose;

import java.util.List;

/**
 * 운동 화면이 쓰는 측정기 공통 기능 (Android 의존성 없는 순수 Java)
 * - 횟수형: RepetitionCounter (편 상태 → 굽힘 → 편 상태 = 1회)
 * - 유지 시간형: 기준 각도 안에서 정해진 시간을 버티면 1회
 * 어느 쪽이든 1회 결과는 RepetitionCounter.RepResult로 돌려주므로 SessionMetrics / 저장 흐름은 같다.
 */
public interface ExerciseCounter {

    /**
     * 프레임 하나를 반영한다.
     *
     * @param jointAngle 판정 관절 각도 (안 보이면 NaN)
     * @param trunkLean  상체가 수직에서 기운 각도 (안 보이면 NaN)
     * @param auxAngle   보조 규칙용 각도 (횟수형: 엉덩이 각도, 유지형: 펴야 하는 다리의 무릎 각도). 안 보이면 NaN
     * @return 이 프레임에서 1회가 끝났으면 그 결과, 아니면 null
     */
    RepetitionCounter.RepResult onFrame(long timestampMs, double jointAngle, double trunkLean, double auxAngle);

    RepetitionCounter.RejectedAttempt takeRejectedAttempt();

    boolean takeMoveSlowlyHint();

    /** 동작(또는 유지) 진행 중이면 true (이 동안에는 디버그 집계를 동작 단위로 유지) */
    boolean isRepInProgress();

    RepetitionCounter.Feedback getFeedback();

    int getCompletedReps();

    List<RepetitionCounter.RepResult> getResults();

    int getSkippedFrameCount();

    void reset();
}

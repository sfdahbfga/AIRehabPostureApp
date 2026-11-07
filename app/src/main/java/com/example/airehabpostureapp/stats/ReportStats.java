package com.example.airehabpostureapp.stats;

// 주간 / 월간 리포트 계산 결과
public class ReportStats {

    public final int exerciseCount;
    public final int totalExerciseMinutes;
    // 화면 표시용 초 합계 (1분 미만 구분)
    public final int totalExerciseSeconds;
    // 정확도 값이 있는 기록이 없으면 0 (화면에서는 hasAccuracy로 구분해 "-" 표시)
    public final double averageAccuracy;
    // 정확도 값이 있는 기록이 하나라도 있으면 true (자세 인식 미지원 운동만 있으면 false)
    public final boolean hasAccuracy;
    // 주간: 월~일 7개, 월간: 1~4주차 4개 (기록이 없는 칸은 0)
    public final double[] chartAccuracies;
    // 기록이 없으면 null / 0
    public final String mostPerformedExerciseName;
    public final int mostPerformedExerciseCount;
    // 5도 초과 오차가 없으면 null / 0
    public final String frequentMistakeJoint;
    public final int frequentMistakeCount;

    public ReportStats(int exerciseCount, int totalExerciseMinutes, int totalExerciseSeconds,
                       double averageAccuracy, boolean hasAccuracy, double[] chartAccuracies,
                       String mostPerformedExerciseName, int mostPerformedExerciseCount,
                       String frequentMistakeJoint, int frequentMistakeCount) {
        this.exerciseCount = exerciseCount;
        this.totalExerciseMinutes = totalExerciseMinutes;
        this.totalExerciseSeconds = totalExerciseSeconds;
        this.averageAccuracy = averageAccuracy;
        this.hasAccuracy = hasAccuracy;
        this.chartAccuracies = chartAccuracies;
        this.mostPerformedExerciseName = mostPerformedExerciseName;
        this.mostPerformedExerciseCount = mostPerformedExerciseCount;
        this.frequentMistakeJoint = frequentMistakeJoint;
        this.frequentMistakeCount = frequentMistakeCount;
    }
}

package com.example.airehabpostureapp.stats;

// 주간 / 월간 리포트 계산 결과
public class ReportStats {

    public final int exerciseCount;
    public final int totalExerciseMinutes;
    // 정확도 값이 있는 기록이 없으면 0
    public final double averageAccuracy;
    // 주간: 월~일 7개, 월간: 1~4주차 4개 (기록이 없는 칸은 0)
    public final double[] chartAccuracies;
    // 기록이 없으면 null / 0
    public final String mostPerformedExerciseName;
    public final int mostPerformedExerciseCount;
    // 5도 초과 오차가 없으면 null / 0
    public final String frequentMistakeJoint;
    public final int frequentMistakeCount;

    public ReportStats(int exerciseCount, int totalExerciseMinutes, double averageAccuracy,
                       double[] chartAccuracies,
                       String mostPerformedExerciseName, int mostPerformedExerciseCount,
                       String frequentMistakeJoint, int frequentMistakeCount) {
        this.exerciseCount = exerciseCount;
        this.totalExerciseMinutes = totalExerciseMinutes;
        this.averageAccuracy = averageAccuracy;
        this.chartAccuracies = chartAccuracies;
        this.mostPerformedExerciseName = mostPerformedExerciseName;
        this.mostPerformedExerciseCount = mostPerformedExerciseCount;
        this.frequentMistakeJoint = frequentMistakeJoint;
        this.frequentMistakeCount = frequentMistakeCount;
    }
}

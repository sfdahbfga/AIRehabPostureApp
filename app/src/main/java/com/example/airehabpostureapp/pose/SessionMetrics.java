package com.example.airehabpostureapp.pose;

import java.util.List;

/**
 * 회차별 결과로 운동 기록에 저장할 값을 계산한다. (Android 의존성 없는 순수 Java)
 *
 * - averageAccuracy: 회차 정확도 평균
 * - firstHalfAccuracy / secondHalfAccuracy: 앞쪽 n/2회(내림) / 나머지 회차의 정확도 평균 (1회면 둘 다 같은 값)
 * - jointError: 회차별 (가장 많이 굽힌 각도 - 목표 각도) 평균 (+: 덜 굽힘, -: 더 굽힘)
 * - trunkError: 회차별 상체 허용 각도 초과량 평균 (상체가 보인 회차만)
 * 0회면 횟수 외의 값은 모두 null
 */
public final class SessionMetrics {

    public final int repetitions;
    public final Double averageAccuracy;
    public final Double firstHalfAccuracy;
    public final Double secondHalfAccuracy;
    public final Double jointError;
    public final Double trunkError;

    private SessionMetrics(int repetitions, Double averageAccuracy, Double firstHalfAccuracy,
                           Double secondHalfAccuracy, Double jointError, Double trunkError) {
        this.repetitions = repetitions;
        this.averageAccuracy = averageAccuracy;
        this.firstHalfAccuracy = firstHalfAccuracy;
        this.secondHalfAccuracy = secondHalfAccuracy;
        this.jointError = jointError;
        this.trunkError = trunkError;
    }

    public static SessionMetrics from(List<RepetitionCounter.RepResult> results) {
        int n = results.size();
        if (n == 0) {
            return new SessionMetrics(0, null, null, null, null, null);
        }
        int firstHalfSize = n == 1 ? 1 : n / 2;
        int secondHalfStart = n == 1 ? 0 : firstHalfSize;

        double jointErrorSum = 0;
        double trunkErrorSum = 0;
        int trunkCount = 0;
        for (RepetitionCounter.RepResult result : results) {
            jointErrorSum += result.jointError;
            if (!Double.isNaN(result.trunkError)) {
                trunkErrorSum += result.trunkError;
                trunkCount++;
            }
        }
        return new SessionMetrics(n,
                averageAccuracy(results, 0, n),
                averageAccuracy(results, 0, firstHalfSize),
                averageAccuracy(results, secondHalfStart, n),
                jointErrorSum / n,
                trunkCount == 0 ? null : trunkErrorSum / trunkCount);
    }

    private static double averageAccuracy(List<RepetitionCounter.RepResult> results, int from, int to) {
        double sum = 0;
        for (int i = from; i < to; i++) {
            sum += results.get(i).accuracy;
        }
        return sum / (to - from);
    }

    /** 저장용 정수 반올림 (null이면 null) */
    public static Integer round(Double value) {
        return value == null ? null : (int) Math.round(value);
    }
}

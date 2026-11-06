package com.example.airehabpostureapp.pose;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 운동별 자세 판정 기준값 (사용할 관절, 각도 기준, 정확도 감점 규칙)을 한 곳에 모아둔다.
 * 새 운동을 추가할 때는 아래 CRITERIA_BY_NAME에 기준을 하나 더 등록하면 된다.
 *
 * 각도 기준은 Java에서 계산한 관절 각도와 비교하는 값이다. (MediaPipe는 관절 위치만 제공)
 */
public final class ExerciseCriteria {

    // MediaPipe Pose 관절 번호
    public static final int LEFT_SHOULDER = 11;
    public static final int RIGHT_SHOULDER = 12;
    public static final int LEFT_HIP = 23;
    public static final int RIGHT_HIP = 24;
    public static final int LEFT_KNEE = 25;
    public static final int RIGHT_KNEE = 26;
    public static final int LEFT_ANKLE = 27;
    public static final int RIGHT_ANKLE = 28;

    /**
     * 한쪽 몸의 관절 번호 묶음
     * - 판정 관절 각도: first - middle - last (무릎 굽힘: 엉덩이 - 무릎 - 발목)
     * - 상체 기울기: shoulder - first(엉덩이)가 수직선과 이루는 각도
     * - 엉덩이 각도: shoulder - first(엉덩이) - middle(무릎)
     */
    public static final class Side {
        public final String label;
        public final int shoulder;
        public final int first;
        public final int middle;
        public final int last;

        public Side(String label, int shoulder, int first, int middle, int last) {
            this.label = label;
            this.shoulder = shoulder;
            this.first = first;
            this.middle = middle;
            this.last = last;
        }
    }

    public static final Side LEFT_LEG = new Side("L", LEFT_SHOULDER, LEFT_HIP, LEFT_KNEE, LEFT_ANKLE);
    public static final Side RIGHT_LEG = new Side("R", RIGHT_SHOULDER, RIGHT_HIP, RIGHT_KNEE, RIGHT_ANKLE);

    /**
     * 무릎 굽힘 운동 (서서 하기): 의자를 잡고 서서 카메라 쪽 다리의 발뒤꿈치를 엉덩이 쪽으로 들어 올린다.
     * 무릎 각도 = 엉덩이 - 무릎 - 발목 (똑바로 서면 약 180도, 굽힐수록 작아짐)
     */
    public static final ExerciseCriteria KNEE_FLEXION = new ExerciseCriteria(
            "무릎 굽힘 운동",
            LEFT_LEG, RIGHT_LEG,
            160,    // 이 각도 이상이면 다리를 편 상태
            130,    // 이 각도 이하까지 굽혀야 1회 동작으로 인정
            90,     // 목표 각도 (종아리와 허벅지가 직각)
            10,     // 목표보다 덜 굽혀도 10도까지는 만점
            20,     // 목표보다 더 굽혀도 20도까지는 만점 (서서 하는 동작이라 더 굽혀도 위험이 적음)
            2,      // 허용 범위를 넘으면 1도당 감점
            15,     // 상체 숙임 허용 각도 (의자를 잡으면 살짝 숙여짐)
            4,      // 상체 허용 각도를 넘으면 1도당 감점
            0.8,    // 회차 정확도 = 무릎 점수 x 0.8 + 상체 점수 x 0.2
            140,    // 엉덩이 각도(어깨-엉덩이-무릎)가 이보다 작아지면 스쿼트처럼 엉덩이를 굽힌 것으로 보고 세지 않음
                    // (A안은 허벅지가 수직이라 약 160~175도, 상체만 숙여도 약 150도 이상 / 2차 측정 기준 추정값)
            5,      // 최근 5프레임 평균 각도로 판정 (흔들림 완화)
            500,    // 굽히기 시작해서 다시 펼 때까지 0.5초 미만이면 흔들림으로 보고 세지 않음
                    // (2차 측정에서 정상 동작이 0.81초까지 나와 0.8초에서 낮춤)
            1000,   // 관절이 1초 넘게 안 보이면 진행 중인 동작을 버림
            20,     // 각도 연속성: 직전 값과 20도 + (초당 200도 x 지난 시간)보다 크게 다르면 추적 오류로 보고 건너뜀
            200);   // (한 프레임(30fps) 약 27도: 실제 동작은 프레임당 10도 안팎이라 막지 않음.
                    //  서 있는 다리 값으로 60도 넘게 튀는 것은 약 0.2초까지 걸러내고, 0.3초 뒤엔 약 80도까지 허용)

    private static final Map<String, ExerciseCriteria> CRITERIA_BY_NAME;

    static {
        Map<String, ExerciseCriteria> map = new HashMap<>();
        map.put(KNEE_FLEXION.exerciseName, KNEE_FLEXION);
        CRITERIA_BY_NAME = Collections.unmodifiableMap(map);
    }

    /** 운동 이름으로 기준 찾기. 아직 자세 인식을 지원하지 않는 운동이면 null */
    public static ExerciseCriteria forExercise(String exerciseName) {
        return exerciseName == null ? null : CRITERIA_BY_NAME.get(exerciseName);
    }

    public final String exerciseName;
    public final Side leftSide;
    public final Side rightSide;
    public final double extendedAngle;
    public final double bentAngle;
    public final double targetAngle;
    public final double underBendTolerance;
    public final double overBendTolerance;
    public final double pointsPerDegree;
    public final double maxTrunkLean;
    public final double trunkPointsPerDegree;
    public final double jointWeight;
    // 동작 중 엉덩이 각도가 이 값보다 작아지면 그 동작은 세지 않음 (0이면 검사하지 않음)
    public final double minHipAngle;
    public final int smoothingWindow;
    public final long minRepDurationMs;
    public final long maxInvisibleMs;
    // 각도 연속성 검사: 허용 변화량 = maxJumpDegrees + maxDegreesPerSecond x 지난 시간(초)
    public final double maxJumpDegrees;
    public final double maxDegreesPerSecond;

    public ExerciseCriteria(String exerciseName, Side leftSide, Side rightSide,
                            double extendedAngle, double bentAngle, double targetAngle,
                            double underBendTolerance, double overBendTolerance, double pointsPerDegree,
                            double maxTrunkLean, double trunkPointsPerDegree, double jointWeight,
                            double minHipAngle,
                            int smoothingWindow, long minRepDurationMs, long maxInvisibleMs,
                            double maxJumpDegrees, double maxDegreesPerSecond) {
        this.exerciseName = exerciseName;
        this.leftSide = leftSide;
        this.rightSide = rightSide;
        this.extendedAngle = extendedAngle;
        this.bentAngle = bentAngle;
        this.targetAngle = targetAngle;
        this.underBendTolerance = underBendTolerance;
        this.overBendTolerance = overBendTolerance;
        this.pointsPerDegree = pointsPerDegree;
        this.maxTrunkLean = maxTrunkLean;
        this.trunkPointsPerDegree = trunkPointsPerDegree;
        this.jointWeight = jointWeight;
        this.minHipAngle = minHipAngle;
        this.smoothingWindow = smoothingWindow;
        this.minRepDurationMs = minRepDurationMs;
        this.maxInvisibleMs = maxInvisibleMs;
        this.maxJumpDegrees = maxJumpDegrees;
        this.maxDegreesPerSecond = maxDegreesPerSecond;
    }

    /** 직전 값에서 elapsedMs 동안 바뀔 수 있는 최대 각도 (넘으면 추적 오류로 봄) */
    public double maxAngleChange(long elapsedMs) {
        return maxJumpDegrees + maxDegreesPerSecond * Math.max(0, elapsedMs) / 1000.0;
    }

    /** 관절 오차 = 가장 많이 굽힌 각도 - 목표 각도 (+: 덜 굽힘, -: 더 굽힘) */
    public double jointError(double minAngle) {
        return minAngle - targetAngle;
    }

    /** 관절 점수 0~100: 허용 범위 안이면 100, 넘은 만큼 1도당 pointsPerDegree 감점 */
    public double jointScore(double minAngle) {
        double error = jointError(minAngle);
        double overDegrees = error >= 0
                ? Math.max(0, error - underBendTolerance)
                : Math.max(0, -error - overBendTolerance);
        return clampScore(100 - overDegrees * pointsPerDegree);
    }

    /** 상체 오차 = 허용 각도를 넘은 만큼 (넘지 않으면 0). 상체가 안 보였으면 NaN */
    public double trunkError(double maxTrunkLeanDegrees) {
        if (Double.isNaN(maxTrunkLeanDegrees)) {
            return Double.NaN;
        }
        return Math.max(0, maxTrunkLeanDegrees - maxTrunkLean);
    }

    /** 회차 정확도 0~100. 상체가 안 보였으면 관절 점수만 사용 */
    public double repAccuracy(double minAngle, double maxTrunkLeanDegrees) {
        double jointScore = jointScore(minAngle);
        double trunkError = trunkError(maxTrunkLeanDegrees);
        if (Double.isNaN(trunkError)) {
            return jointScore;
        }
        double trunkScore = clampScore(100 - trunkError * trunkPointsPerDegree);
        return jointScore * jointWeight + trunkScore * (1 - jointWeight);
    }

    /** 엉덩이를 기준보다 많이 굽혔으면 true (엉덩이가 안 보였거나 검사하지 않는 운동이면 false) */
    public boolean isHipTooBent(double minHipAngleDegrees) {
        return minHipAngle > 0 && !Double.isNaN(minHipAngleDegrees) && minHipAngleDegrees < minHipAngle;
    }

    private static double clampScore(double score) {
        return Math.max(0, Math.min(100, score));
    }
}

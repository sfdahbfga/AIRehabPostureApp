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

    // 운동 구분 키 (화면에서 운동별 안내 문구를 고를 때 사용)
    public static final String ID_KNEE_FLEXION = "knee_flexion";
    public static final String ID_CHAIR_SQUAT = "chair_squat";
    public static final String ID_LUNGE = "lunge";
    public static final String ID_SEATED_HAMSTRING_STRETCH = "seated_hamstring_stretch";

    /** 판정 관절: 무릎(엉덩이-무릎-발목) 또는 엉덩이(어깨-엉덩이-무릎) */
    public enum JudgedJoint {
        KNEE,
        HIP
    }

    /**
     * 무릎 굽힘 운동 (서서 하기): 의자를 잡고 서서 카메라 쪽 다리의 발뒤꿈치를 엉덩이 쪽으로 들어 올린다.
     * 무릎 각도 = 엉덩이 - 무릎 - 발목 (똑바로 서면 약 180도, 굽힐수록 작아짐)
     * 아래 Builder 기본값(흔들림 / 연속성 검사)은 이 운동의 실측으로 정한 값이다.
     */
    public static final ExerciseCriteria KNEE_FLEXION = new Builder(ID_KNEE_FLEXION, "무릎 굽힘 운동")
            .targetReps(20)            // 운동 상세의 20회 x 3세트 중 1세트
            .extendedAngle(160)        // 이 각도 이상이면 다리를 편 상태
            .bentAngle(130)            // 이 각도 이하까지 굽혀야 1회 동작으로 인정
            .targetAngle(90)           // 목표 각도 (종아리와 허벅지가 직각)
            .underBendTolerance(10)    // 목표보다 덜 굽혀도 10도까지는 만점
            .overBendTolerance(20)     // 목표보다 더 굽혀도 20도까지는 만점 (서서 하는 동작이라 더 굽혀도 위험이 적음)
            .maxTrunkLean(15)          // 상체 숙임 허용 각도 (의자를 잡으면 살짝 숙여짐)
            .jointWeight(0.8)          // 회차 정확도 = 무릎 점수 x 0.8 + 상체 점수 x 0.2
            .minHipAngle(140)          // 엉덩이 각도(어깨-엉덩이-무릎)가 이보다 작아지면 스쿼트처럼 엉덩이를 굽힌 것으로 보고 세지 않음
                                       // (A안은 허벅지가 수직이라 약 160~175도, 상체만 숙여도 약 150도 이상 / 2차 측정 기준 추정값)
            .minRepDurationMs(500)     // 굽히기 시작해서 다시 펼 때까지 0.5초 미만이면 흔들림으로 보고 세지 않음
                                       // (2차 측정에서 정상 동작이 0.81초까지 나와 0.8초에서 낮춤)
            .build();

    /**
     * 의자 스쿼트 (서서 하기, 양쪽 다리): 의자를 몸 뒤에 두고 정옆으로 서서,
     * 엉덩이가 의자에 살짝 닿을 때까지 앉았다가 일어선다. 판정은 카메라 쪽 다리의 무릎 각도.
     * - 목표 90도: 표준 의자 높이(약 45cm)에 앉으면 무릎이 약 90도. 재활 스쿼트는 보통 무릎 60~90도 굽힘 범위
     * - 90도보다 깊게 앉으면 무릎 부담이 커지므로 더 굽힘 허용폭은 무릎 굽힘 운동(20도)보다 좁은 15도
     * - 스쿼트는 몸의 중심을 맞추려고 상체가 30~45도 앞으로 기우는 것이 정상이라 상체 허용은 45도
     */
    public static final ExerciseCriteria CHAIR_SQUAT = new Builder(ID_CHAIR_SQUAT, "의자 스쿼트")
            .targetReps(10)            // 10회 x 3세트 중 1세트
            .extendedAngle(160)        // 이 각도 이상이면 일어선 상태
            .bentAngle(120)            // 이 각도 이하까지 앉아야 1회 동작으로 인정 (목표까지 30도 이내)
            .targetAngle(90)
            .underBendTolerance(10)    // 100도까지는 만점
            .overBendTolerance(15)     // 75도까지는 만점
            .maxTrunkLean(45)
            .jointWeight(0.7)          // 회차 정확도 = 무릎 점수 x 0.7 + 상체 점수 x 0.3 (스쿼트는 상체 자세도 중요)
            .maxHipAngleAtDepth(150)   // 동작 중 엉덩이 각도가 한 번도 150도 아래로 내려가지 않으면 무릎만 접은 것으로 보고 세지 않음
                                       // (서 있으면 약 170~180도, 무릎 90도 스쿼트에서는 약 80~110도)
            .minRepDurationMs(1000)    // 앉았다 일어서기가 1초 미만이면 흔들림 / 너무 빠른 동작으로 봄
            .build();

    /**
     * 런지 (제자리 스플릿 스쿼트, 서서 하기): 발을 앞뒤로 벌린 채 정옆으로 서서 위아래로만 움직인다.
     * 다리를 바꿀 때는 몸을 돌리지 않고 앞뒤 발만 바꾼다. (몸을 돌리면 카메라 쪽 다리가 바뀌어 고정한 다리가 가려짐)
     * 판정은 카메라 쪽 다리의 무릎 각도. 바르게 내려가면 앞다리 / 뒷다리 무릎이 모두 약 90도가 되므로
     * 카메라 쪽 다리가 앞다리든 뒷다리든 같은 기준을 쓴다.
     * - 목표 90도, 더 굽힘 허용 15도: 의자 스쿼트와 같은 근거 (재활 런지는 무릎 90도까지)
     * - 상체는 세운 채 내려가는 운동이라 상체 허용은 20도
     * - 뒷다리는 엉덩이가 펴지는 것이 정상이라 엉덩이 규칙은 쓰지 않음
     */
    public static final ExerciseCriteria LUNGE = new Builder(ID_LUNGE, "런지")
            .targetReps(8)             // 한쪽 8회 x 2세트 중 1세트
            .extendedAngle(160)        // 이 각도 이상이면 올라온 상태
            .bentAngle(120)            // 이 각도 이하까지 내려가야 1회 동작으로 인정
            .targetAngle(90)
            .underBendTolerance(10)
            .overBendTolerance(15)
            .maxTrunkLean(20)
            .jointWeight(0.8)          // 회차 정확도 = 무릎 점수 x 0.8 + 상체 점수 x 0.2
            .minRepDurationMs(800)     // 중급 운동이라 무릎 굽힘(0.5초)보다 길게, 스쿼트(1초)보다 짧게
            .build();

    /**
     * 하체 스트레칭 (앉아서 하는 허벅지 뒤 스트레칭, 유지 시간형): 의자 끝에 카메라 쪽으로 옆으로 앉아,
     * 카메라 쪽 다리를 앞으로 쭉 펴고 뒤꿈치를 바닥에 댄 채, 허리를 편 상태로 상체를 앞으로 숙여 유지한다.
     * 판정은 편 다리 쪽 엉덩이 각도(어깨-엉덩이-무릎). 작을수록 많이 숙인 것 (고관절 굽힘 = 180 - 엉덩이 각도)
     * - 뒤꿈치를 바닥에 대면 허벅지가 약 20~35도 아래로 기울어, 똑바로 앉았을 때 엉덩이 각도가 약 115~125도
     * - 목표 95도 (고관절 굽힘 85도): 무릎을 편 채 고관절을 굽히는 정상 범위(다리 들어 올리기 기준 약 70~90도)의 중간
     *   → 똑바로 앉은 자세에서 상체를 약 20~30도 숙이면 닿는 정도
     * - 유지 인정 110도 이하 (고관절 굽힘 70도, 정상 범위의 아래쪽): 이보다 덜 숙이면 유지 시간이 쌓이지 않음
     * - 덜 숙임 허용 5도(100도까지 만점), 더 숙임 허용 20도(75도까지 만점, 스트레칭은 더 숙여도 위험이 적음)
     * - 편 다리 무릎 160도 이상: 무릎을 20도 넘게 굽히면 허벅지 뒤가 늘어나지 않으므로 유지 시간을 멈춤
     * - 20초 유지: 정적 스트레칭 권장 시간(15~30초)의 중간값
     * 상체 기울기는 등을 둥글게 말아도 커지므로 점수에 쓰지 않는다. (back은 저장하지 않음)
     */
    public static final ExerciseCriteria SEATED_HAMSTRING_STRETCH =
            new Builder(ID_SEATED_HAMSTRING_STRETCH, "하체 스트레칭")
            .judgedJoint(JudgedJoint.HIP)
            .targetReps(3)             // 20초 유지 x 3회
            .holdDurationMs(20000)
            .lockMinAngle(0)           // 앉은 자세라 각도는 보지 않고, 정옆으로 앉아 visibility 차이만으로 다리를 정함
            .extendedAngle(120)        // 다시 세운 상태 (1회를 마친 뒤 이 각도 이상 또는 직전 유지 평균 + 15도 이상)
            .releaseRiseDegrees(15)
            .bentAngle(110)            // 이 각도 이하에서 유지 시간이 쌓임
            .targetAngle(95)
            .underBendTolerance(5)
            .overBendTolerance(20)
            .minKneeAngle(160)
            .holdGraceMs(1000)         // 기준 밖으로 1초까지는 유지가 이어짐 (시간은 멈춤)
            .maxInvisibleMs(1500)      // 발끝 쪽으로 손을 뻗으면 무릎·발목이 가려질 수 있어 무릎 굽힘(1초)보다 길게
            .build();

    private static final Map<String, ExerciseCriteria> CRITERIA_BY_NAME;

    static {
        Map<String, ExerciseCriteria> map = new HashMap<>();
        map.put(KNEE_FLEXION.exerciseName, KNEE_FLEXION);
        map.put(CHAIR_SQUAT.exerciseName, CHAIR_SQUAT);
        map.put(LUNGE.exerciseName, LUNGE);
        map.put(SEATED_HAMSTRING_STRETCH.exerciseName, SEATED_HAMSTRING_STRETCH);
        CRITERIA_BY_NAME = Collections.unmodifiableMap(map);
    }

    /** 운동 이름으로 기준 찾기. 아직 자세 인식을 지원하지 않는 운동이면 null */
    public static ExerciseCriteria forExercise(String exerciseName) {
        return exerciseName == null ? null : CRITERIA_BY_NAME.get(exerciseName);
    }

    /** 운동 이름으로 기준 찾기. 없으면 무릎 굽힘 운동 기준 (예전 기록 점수 계산용) */
    public static ExerciseCriteria forExerciseOrDefault(String exerciseName) {
        ExerciseCriteria criteria = forExercise(exerciseName);
        return criteria != null ? criteria : KNEE_FLEXION;
    }

    public final String id;
    public final String exerciseName;
    // 1세트 목표 횟수 (유지 시간형은 유지 횟수)
    public final int targetReps;
    // 운동하는 다리를 정할 때 "자세를 잡았다"고 보는 판정 각도 (이 각도 이상). 0이면 각도는 보지 않음
    public final double lockMinAngle;
    // 판정 관절 (joint_errors 저장 키: KNEE → knee, HIP → hip)
    public final JudgedJoint judgedJoint;
    // 유지 시간형이면 1회로 인정하는 유지 시간 (0이면 횟수형)
    public final long holdDurationMs;
    // 유지 중 기준 밖으로 이 시간까지 벗어나도 유지가 이어짐 (그동안 시간은 멈춤)
    public final long holdGraceMs;
    // 유지형: 펴야 하는 다리의 무릎 각도가 이보다 작으면 유지 시간을 멈춤 (0이면 검사하지 않음)
    public final double minKneeAngle;
    // 유지형: 1회를 마친 뒤 직전 유지 평균보다 이만큼 올라오면 다시 세운 것으로 봄
    public final double releaseRiseDegrees;
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
    // 동작 중 가장 작았던 엉덩이 각도가 이 값보다 크면(엉덩이를 굽히지 않음) 그 동작은 세지 않음 (0이면 검사하지 않음)
    public final double maxHipAngleAtDepth;
    public final int smoothingWindow;
    public final long minRepDurationMs;
    public final long maxInvisibleMs;
    // 각도 연속성 검사: 허용 변화량 = maxJumpDegrees + maxDegreesPerSecond x 지난 시간(초)
    public final double maxJumpDegrees;
    public final double maxDegreesPerSecond;
    // 한 번의 동작 중 건너뛴 프레임(연속성 검사 + 안 보임) 비율이 이 값 이상이면 "천천히" 안내
    public final double maxMissedFrameRatio;
    // 동작 진행 중 연속성 검사 허용 폭 계산에 쓰는 최대 경과 시간 (이보다 오래 건너뛰어도 허용 폭이 더 커지지 않음)
    public final long inRepMaxGapMs;
    // 1회가 끝났다고 보려면 편 기준 이상이 이만큼 이어져야 함
    public final long extendedHoldMs;

    private ExerciseCriteria(Builder b) {
        this.id = b.id;
        this.exerciseName = b.exerciseName;
        this.targetReps = b.targetReps;
        // 지정하지 않으면 편 상태 각도 이상일 때 다리를 정함 (무릎 굽힘과 같음)
        this.lockMinAngle = Double.isNaN(b.lockMinAngle) ? b.extendedAngle : b.lockMinAngle;
        this.judgedJoint = b.judgedJoint;
        this.holdDurationMs = b.holdDurationMs;
        this.holdGraceMs = b.holdGraceMs;
        this.minKneeAngle = b.minKneeAngle;
        this.releaseRiseDegrees = b.releaseRiseDegrees;
        this.leftSide = b.leftSide;
        this.rightSide = b.rightSide;
        this.extendedAngle = b.extendedAngle;
        this.bentAngle = b.bentAngle;
        this.targetAngle = b.targetAngle;
        this.underBendTolerance = b.underBendTolerance;
        this.overBendTolerance = b.overBendTolerance;
        this.pointsPerDegree = b.pointsPerDegree;
        this.maxTrunkLean = b.maxTrunkLean;
        this.trunkPointsPerDegree = b.trunkPointsPerDegree;
        this.jointWeight = b.jointWeight;
        this.minHipAngle = b.minHipAngle;
        this.maxHipAngleAtDepth = b.maxHipAngleAtDepth;
        this.smoothingWindow = b.smoothingWindow;
        this.minRepDurationMs = b.minRepDurationMs;
        this.maxInvisibleMs = b.maxInvisibleMs;
        this.maxJumpDegrees = b.maxJumpDegrees;
        this.maxDegreesPerSecond = b.maxDegreesPerSecond;
        this.maxMissedFrameRatio = b.maxMissedFrameRatio;
        this.inRepMaxGapMs = b.inRepMaxGapMs;
        this.extendedHoldMs = b.extendedHoldMs;
    }

    /**
     * 운동별 기준 만들기. 지정하지 않은 값은 아래 기본값(무릎 굽힘 운동 실측 기준)을 쓴다.
     */
    public static final class Builder {
        private final String id;
        private final String exerciseName;
        private int targetReps = 10;
        private double lockMinAngle = Double.NaN;
        private JudgedJoint judgedJoint = JudgedJoint.KNEE;
        private long holdDurationMs = 0;
        private long holdGraceMs = 1000;
        private double minKneeAngle = 0;
        private double releaseRiseDegrees = 15;
        private Side leftSide = LEFT_LEG;
        private Side rightSide = RIGHT_LEG;
        private double extendedAngle = 160;
        private double bentAngle = 130;
        private double targetAngle = 90;
        private double underBendTolerance = 10;
        private double overBendTolerance = 20;
        // 허용 범위를 넘으면 1도당 감점
        private double pointsPerDegree = 2;
        private double maxTrunkLean = 15;
        // 상체 허용 각도를 넘으면 1도당 감점
        private double trunkPointsPerDegree = 4;
        private double jointWeight = 0.8;
        // 0이면 엉덩이 규칙을 검사하지 않음
        private double minHipAngle = 0;
        // 0이면 "엉덩이를 굽혀야 함" 규칙을 검사하지 않음
        private double maxHipAngleAtDepth = 0;
        // 최근 5프레임 평균 각도로 판정 (흔들림 완화)
        private int smoothingWindow = 5;
        private long minRepDurationMs = 500;
        // 관절이 1초 넘게 안 보이면 진행 중인 동작을 버림
        private long maxInvisibleMs = 1000;
        // 각도 연속성: 직전 값과 20도 + (초당 200도 x 지난 시간)보다 크게 다르면 추적 오류로 보고 건너뜀
        // (한 프레임(30fps) 약 27도: 실제 동작은 프레임당 10도 안팎이라 막지 않음.
        //  서 있는 다리 값으로 60도 넘게 튀는 것은 약 0.2초까지 걸러내고, 0.3초 뒤엔 약 80도까지 허용)
        private double maxJumpDegrees = 20;
        private double maxDegreesPerSecond = 200;
        // 한 번의 동작 중 건너뛴 프레임이 30% 이상이면 "천천히 움직여 주세요" 안내
        private double maxMissedFrameRatio = 0.3;
        // 동작 진행 중에는 연속성 검사의 허용 폭을 최대 0.1초분(약 40도)까지만 키움
        // (굽히는 다리가 가려졌을 때 서 있는 다리 값이 시간이 지나 받아들여지는 것을 막음)
        private long inRepMaxGapMs = 100;
        // 평균 각도가 편 기준 이상으로 0.2초 이어져야 1회가 끝난 것으로 봄 (짧은 튐으로 동작이 쪼개지는 것 방지)
        private long extendedHoldMs = 200;

        public Builder(String id, String exerciseName) {
            this.id = id;
            this.exerciseName = exerciseName;
        }

        public Builder targetReps(int v) { targetReps = v; return this; }
        public Builder lockMinAngle(double v) { lockMinAngle = v; return this; }
        public Builder judgedJoint(JudgedJoint v) { judgedJoint = v; return this; }
        public Builder holdDurationMs(long v) { holdDurationMs = v; return this; }
        public Builder holdGraceMs(long v) { holdGraceMs = v; return this; }
        public Builder minKneeAngle(double v) { minKneeAngle = v; return this; }
        public Builder releaseRiseDegrees(double v) { releaseRiseDegrees = v; return this; }
        public Builder sides(Side left, Side right) { leftSide = left; rightSide = right; return this; }
        public Builder extendedAngle(double v) { extendedAngle = v; return this; }
        public Builder bentAngle(double v) { bentAngle = v; return this; }
        public Builder targetAngle(double v) { targetAngle = v; return this; }
        public Builder underBendTolerance(double v) { underBendTolerance = v; return this; }
        public Builder overBendTolerance(double v) { overBendTolerance = v; return this; }
        public Builder pointsPerDegree(double v) { pointsPerDegree = v; return this; }
        public Builder maxTrunkLean(double v) { maxTrunkLean = v; return this; }
        public Builder trunkPointsPerDegree(double v) { trunkPointsPerDegree = v; return this; }
        public Builder jointWeight(double v) { jointWeight = v; return this; }
        public Builder minHipAngle(double v) { minHipAngle = v; return this; }
        public Builder maxHipAngleAtDepth(double v) { maxHipAngleAtDepth = v; return this; }
        public Builder smoothingWindow(int v) { smoothingWindow = v; return this; }
        public Builder minRepDurationMs(long v) { minRepDurationMs = v; return this; }
        public Builder maxInvisibleMs(long v) { maxInvisibleMs = v; return this; }
        public Builder maxJumpDegrees(double v) { maxJumpDegrees = v; return this; }
        public Builder maxDegreesPerSecond(double v) { maxDegreesPerSecond = v; return this; }
        public Builder maxMissedFrameRatio(double v) { maxMissedFrameRatio = v; return this; }
        public Builder inRepMaxGapMs(long v) { inRepMaxGapMs = v; return this; }
        public Builder extendedHoldMs(long v) { extendedHoldMs = v; return this; }

        public ExerciseCriteria build() {
            return new ExerciseCriteria(this);
        }
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
        return jointScoreForError(jointError(minAngle));
    }

    /** 관절 오차(+: 덜 굽힘, -: 더 굽힘)로 관절 점수 계산. 저장된 joint_errors를 점수로 바꿀 때도 사용 */
    public double jointScoreForError(double error) {
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
        return jointScore * jointWeight + trunkScore(trunkError) * (1 - jointWeight);
    }

    /** 상체 점수 0~100: 상체 오차(허용 각도 초과량) 1도당 trunkPointsPerDegree 감점 */
    public double trunkScore(double trunkError) {
        return clampScore(100 - Math.max(0, trunkError) * trunkPointsPerDegree);
    }

    /** 엉덩이를 기준보다 많이 굽혔으면 true (엉덩이가 안 보였거나 검사하지 않는 운동이면 false) */
    public boolean isHipTooBent(double minHipAngleDegrees) {
        return minHipAngle > 0 && !Double.isNaN(minHipAngleDegrees) && minHipAngleDegrees < minHipAngle;
    }

    /** 유지 시간형 운동이면 true (HoldCounter로 측정) */
    public boolean isHoldType() {
        return holdDurationMs > 0;
    }

    /** 유지형: 펴야 하는 무릎이 기준보다 굽었으면 true (무릎이 안 보였거나 검사하지 않는 운동이면 false) */
    public boolean isKneeBent(double kneeAngleDegrees) {
        return minKneeAngle > 0 && !Double.isNaN(kneeAngleDegrees) && kneeAngleDegrees < minKneeAngle;
    }

    /** 엉덩이를 기준만큼 굽히지 않았으면 true (엉덩이가 안 보였거나 검사하지 않는 운동이면 false) */
    public boolean isHipNotBent(double minHipAngleDegrees) {
        return maxHipAngleAtDepth > 0 && !Double.isNaN(minHipAngleDegrees) && minHipAngleDegrees > maxHipAngleAtDepth;
    }

    private static double clampScore(double score) {
        return Math.max(0, Math.min(100, score));
    }
}

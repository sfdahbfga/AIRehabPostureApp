package com.example.airehabpostureapp.pose;

/**
 * 휴대폰 기울기 보정 계산 (Android 의존성 없는 순수 Java)
 *
 * 상체 기울기는 이미지의 위쪽을 수직으로 보고 계산하므로, 휴대폰이 화면 안에서 좌우로 돌아가 있으면(roll)
 * 그만큼 틀어진다. 중력 센서 값으로 그 각도를 재서 보정한다.
 * - 센서 좌표: x = 화면 오른쪽, y = 화면 위쪽, z = 화면 밖. 똑바로 세우면 중력 값은 (0, +9.8, 0)
 * - roll = 실제 위쪽이 화면 위쪽에서 화면 오른쪽(+x)으로 기운 각도 = atan2(gx, gy)
 * - 전면 카메라 이미지(회전으로 바로 세우고 좌우 반전 없음)에서 이미지 오른쪽 = 화면 왼쪽(-x)이므로
 *   실제 위쪽은 이미지 위쪽에서 이미지 왼쪽으로 roll만큼 기울어 보인다.
 *   → 실제 기울기(이미지 오른쪽 +) = 이미지에서 잰 기울기 + roll  (PoseMeasurer.signedForwardLean의 tiltDegrees)
 */
public final class PhoneTilt {

    /** 이보다 많이 기울어 있으면 "휴대폰을 똑바로 세워 주세요" */
    public static final double MAX_ROLL_DEGREES = 15;
    // 화면이 하늘 / 바닥을 향해 많이 젖혀져(중력이 거의 화면 수직 방향) 좌우 기울기를 믿을 수 없는 경우: 평면 성분 비율
    private static final double MIN_PLANAR_RATIO = 0.5;
    /** 가속도 센서로 대신 잴 때 저역 통과 필터 비율 (이전 값 비중) */
    public static final float LOW_PASS_ALPHA = 0.8f;

    private PhoneTilt() {
    }

    /**
     * 중력 벡터(센서 좌표)로 화면이 수직에서 좌우로 기운 각도(도). 실제 위쪽이 화면 오른쪽으로 기울면 +
     * 값이 없거나 휴대폰을 많이 젖혀 좌우 기울기를 믿을 수 없으면 NaN
     */
    public static double rollDegrees(float gx, float gy, float gz) {
        double total = Math.sqrt(gx * gx + gy * gy + gz * gz);
        double planar = Math.sqrt(gx * gx + gy * gy);
        if (total <= 0 || Double.isNaN(total) || planar < total * MIN_PLANAR_RATIO) {
            return Double.NaN;
        }
        return Math.toDegrees(Math.atan2(gx, gy));
    }

    /** 상체 기울기 보정값 (PoseMeasurer.measureForwardLean의 tiltDegrees). 기울기를 모르면 0 (보정 없음) */
    public static double imageVerticalOffset(double rollDegrees) {
        return Double.isNaN(rollDegrees) ? 0 : rollDegrees;
    }

    /** 15도 넘게 기울어 있으면 true (기울기를 모르면 false) */
    public static boolean isTooTilted(double rollDegrees) {
        return !Double.isNaN(rollDegrees) && Math.abs(rollDegrees) > MAX_ROLL_DEGREES;
    }

    /** 가속도 센서 값에서 중력만 남기는 저역 통과 필터. previous가 null이면 current를 그대로 */
    public static float[] lowPass(float[] previous, float[] current) {
        if (previous == null) {
            return current.clone();
        }
        float[] out = new float[3];
        for (int i = 0; i < 3; i++) {
            out[i] = LOW_PASS_ALPHA * previous[i] + (1 - LOW_PASS_ALPHA) * current[i];
        }
        return out;
    }
}

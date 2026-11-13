package com.example.airehabpostureapp.pose;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * 휴대폰 기울기 보정: 중력 벡터 → 좌우 기울기(roll) → 상체 기울기 보정
 * 센서 좌표: x = 화면 오른쪽, y = 화면 위쪽. 똑바로 세우면 (0, 9.8, 0)
 */
public class PhoneTiltTest {

    private static final float G = 9.81f;
    private static final double DELTA = 0.01;

    // roll도만큼 기운 휴대폰의 중력 값 (실제 위쪽이 화면 오른쪽으로 기울면 +), 화면을 뒤로 pitch도 젖힘
    private static float[] gravity(double rollDegrees, double pitchDegrees) {
        double roll = Math.toRadians(rollDegrees);
        double pitch = Math.toRadians(pitchDegrees);
        double planar = G * Math.cos(pitch);
        return new float[]{(float) (planar * Math.sin(roll)), (float) (planar * Math.cos(roll)),
                (float) (G * Math.sin(pitch))};
    }

    private static double roll(float[] g) {
        return PhoneTilt.rollDegrees(g[0], g[1], g[2]);
    }

    @Test
    public void upright_zero() {
        assertEquals(0, roll(gravity(0, 0)), DELTA);
    }

    @Test
    public void tiltedEitherWay_measured() {
        assertEquals(10, roll(gravity(10, 0)), DELTA);
        assertEquals(-12, roll(gravity(-12, 0)), DELTA);
        // 화면을 뒤로 조금 젖혀 세워도(거치대) 좌우 기울기는 그대로
        assertEquals(10, roll(gravity(10, 20)), DELTA);
    }

    @Test
    public void lyingAlmostFlat_unknown_noCorrection() {
        // 화면이 거의 하늘을 향함 (70도 젖힘) → 좌우 기울기를 믿을 수 없음
        assertTrue(Double.isNaN(roll(gravity(10, 70))));
        assertEquals(0, PhoneTilt.imageVerticalOffset(Double.NaN), DELTA);
        assertFalse(PhoneTilt.isTooTilted(Double.NaN));
        assertTrue(Double.isNaN(PhoneTilt.rollDegrees(0, 0, 0)));
    }

    @Test
    public void tooTilted_over15() {
        assertFalse(PhoneTilt.isTooTilted(15));
        assertFalse(PhoneTilt.isTooTilted(-15));
        assertTrue(PhoneTilt.isTooTilted(15.5));
        assertTrue(PhoneTilt.isTooTilted(-20));
    }

    @Test
    public void lowPass_keepsGravity_dropsShake() {
        float[] filtered = PhoneTilt.lowPass(null, new float[]{0, G, 0});
        assertArrayEquals(new float[]{0, G, 0}, filtered, 0.001f);
        // 한 번 크게 흔들려도 조금만 따라감
        filtered = PhoneTilt.lowPass(filtered, new float[]{5, G, 0});
        assertEquals(1.0, filtered[0], 0.01);
    }

    // ---------- 상체 기울기 보정 ----------

    /**
     * 전면 카메라 이미지(좌우 반전 없음)에서 실제 각도가 leanDegrees인 상체가 보이는 위치:
     * 휴대폰이 roll만큼 기울면 실제 위쪽이 이미지 왼쪽으로 roll만큼 기울어 보임
     */
    private static double measuredLean(double trueLeanDegrees, int forward, double rollDegrees, double correction) {
        double imageAngle = Math.toRadians(trueLeanDegrees * forward - rollDegrees);
        double hipX = 240;
        double hipY = 400;
        double shoulderX = hipX + 200 * Math.sin(imageAngle);
        double shoulderY = hipY - 200 * Math.cos(imageAngle);
        return PoseMeasurer.signedForwardLean(shoulderX, shoulderY, hipX, hipY, forward, correction);
    }

    @Test
    public void phoneTilted_withoutCorrection_leanIsOff_withCorrection_restored() {
        for (int forward : new int[]{1, -1}) {
            for (double rollDegrees : new double[]{-12, 8}) {
                // 보정하지 않으면 휴대폰이 기운 만큼(앞쪽 방향에 따라 +/-) 틀어짐
                double raw = measuredLean(30, forward, rollDegrees, 0);
                assertEquals(30 - rollDegrees * forward, raw, DELTA);
                // 보정하면 실제 각도
                double corrected = measuredLean(30, forward, rollDegrees, PhoneTilt.imageVerticalOffset(rollDegrees));
                assertEquals("forward " + forward + " roll " + rollDegrees, 30, corrected, DELTA);
            }
        }
    }

    @Test
    public void uprightPerson_tiltedPhone_correctedToZero() {
        assertEquals(0, measuredLean(0, 1, 10, PhoneTilt.imageVerticalOffset(10)), DELTA);
        assertEquals(0, measuredLean(0, -1, -10, PhoneTilt.imageVerticalOffset(-10)), DELTA);
    }
}

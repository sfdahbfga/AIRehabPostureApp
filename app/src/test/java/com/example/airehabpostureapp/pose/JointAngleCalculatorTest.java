package com.example.airehabpostureapp.pose;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class JointAngleCalculatorTest {

    private static final double DELTA = 1e-6;

    @Test
    public void rightAngle_returns90() {
        // A(0,0) - B(1,0) - C(1,1)
        assertEquals(90.0, JointAngleCalculator.calculateAngle(0, 0, 1, 0, 1, 1), DELTA);
    }

    @Test
    public void straightLine_returns180() {
        // 다리를 곧게 편 경우: 엉덩이 - 무릎 - 발목이 일직선
        assertEquals(180.0, JointAngleCalculator.calculateAngle(0, 0, 0, 1, 0, 2), DELTA);
    }

    @Test
    public void sameDirection_returns0() {
        // A와 C가 B에서 같은 방향
        assertEquals(0.0, JointAngleCalculator.calculateAngle(2, 0, 0, 0, 1, 0), DELTA);
    }

    @Test
    public void diagonal_returns45() {
        assertEquals(45.0, JointAngleCalculator.calculateAngle(1, 0, 0, 0, 1, 1), DELTA);
    }

    @Test
    public void bentKnee_inPixelCoordinates() {
        // 이미지 좌표계(y가 아래로 증가): 엉덩이(100,100) - 무릎(100,200) - 발목(200,200) → 90도
        assertEquals(90.0, JointAngleCalculator.calculateAngle(100, 100, 100, 200, 200, 200), DELTA);
    }

    @Test
    public void swappingAAndC_givesSameAngle() {
        double forward = JointAngleCalculator.calculateAngle(3, 1, 0, 0, 1, 4);
        double backward = JointAngleCalculator.calculateAngle(1, 4, 0, 0, 3, 1);
        assertEquals(forward, backward, DELTA);
    }

    @Test
    public void nearlyStraight_doesNotReturnNaN() {
        // 부동소수 오차로 cos가 1을 살짝 넘는 경우에도 NaN이 아니어야 함
        double angle = JointAngleCalculator.calculateAngle(0.1, 0.3, 0.2, 0.6, 0.3, 0.9);
        assertEquals(180.0, angle, 1e-4);
    }

    @Test
    public void overlappingPoints_returnsNaN() {
        assertTrue(Double.isNaN(JointAngleCalculator.calculateAngle(1, 1, 1, 1, 2, 2)));
        assertTrue(Double.isNaN(JointAngleCalculator.calculateAngle(0, 0, 1, 1, 1, 1)));
    }
}

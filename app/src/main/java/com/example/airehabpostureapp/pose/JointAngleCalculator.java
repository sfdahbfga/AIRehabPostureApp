package com.example.airehabpostureapp.pose;

/**
 * 세 관절 점(A-B-C)으로 가운데 관절 B의 각도를 계산한다. (Android 의존성 없는 순수 Java)
 *
 * 예: 무릎 각도 = 엉덩이(A) - 무릎(B) - 발목(C)
 * 결과는 0~180도. 다리를 곧게 펴면 180도에 가깝고, 많이 굽힐수록 작아진다.
 *
 * 주의: MediaPipe의 정규화 좌표(0~1)는 가로/세로 길이가 다른 영상에서 비율이 달라지므로,
 * 이미지 너비/높이를 곱한 픽셀 좌표로 바꾼 뒤 넘겨야 각도가 왜곡되지 않는다.
 */
public final class JointAngleCalculator {

    private JointAngleCalculator() {
    }

    /**
     * @return B에서의 각도(도, 0~180). A 또는 C가 B와 같은 위치라 방향을 정할 수 없으면 Double.NaN
     */
    public static double calculateAngle(double ax, double ay,
                                        double bx, double by,
                                        double cx, double cy) {
        // B → A, B → C 벡터
        double bax = ax - bx;
        double bay = ay - by;
        double bcx = cx - bx;
        double bcy = cy - by;

        double lengthBa = Math.hypot(bax, bay);
        double lengthBc = Math.hypot(bcx, bcy);
        if (lengthBa == 0 || lengthBc == 0) {
            return Double.NaN;
        }

        // 두 벡터 사이 각도: cos θ = (BA · BC) / (|BA| |BC|)
        double cos = (bax * bcx + bay * bcy) / (lengthBa * lengthBc);
        // 부동소수 오차로 1을 살짝 넘으면 acos가 NaN이 되므로 범위를 고정
        cos = Math.max(-1.0, Math.min(1.0, cos));
        return Math.toDegrees(Math.acos(cos));
    }
}

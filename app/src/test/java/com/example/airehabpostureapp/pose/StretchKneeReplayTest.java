package com.example.airehabpostureapp.pose;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 2차 실측(2026-11-13) 하체 스트레칭에서 앉거나 일어설 때 "무릎을 펴 주세요"가 짧게 깜빡인 구간 재현
 * - 15:27:47~56 의자에 앉음: 무릎을 굽혀 앉았다가(약 105도) 다리를 쭉 폄(174~179도)
 *   → 15:27:48.5 "무릎을 펴" (실제로 굽힘, 정상) / 15:27:54.589 → 54.654 0.065초 만에 다시 바뀜 (한두 프레임 튐)
 * - 15:28:17~21 1회를 마치고 세움: 무릎 171~178도인데 15:28:20.909 → 21.010 0.1초 동안 "무릎을 펴"
 * 프레임 단위 무릎 값은 로그에 없어 1초 기록 + 튀는 프레임(한쪽 다리만 보여 150도 안팎)으로 다시 만든다.
 * 예전 규칙(프레임마다, 160도 미만이면 굽힘 / 165도 이상이면 폄)과 새 규칙(5프레임 평균, 160 / 155)을 비교한다.
 */
public class StretchKneeReplayTest {

    private static final long FRAME_MS = 33;

    /** {상체 기울기, 무릎} 프레임 목록 */
    private static List<double[]> sittingDownThenStretchingLeg() {
        List<double[]> frames = new ArrayList<>();
        // 15:27:47~49 앉으며 무릎을 굽힘 (175 → 105)
        ramp(frames, 11, 175, -5, 105, 1500);
        // 15:27:49~50 무릎을 굽힌 채 잠깐 (정상 경고 구간)
        ramp(frames, -5, 105, -5, 105, 700);
        // 15:27:50~54 다리를 쭉 펴고 뒤로 기대 앉음 (179도)
        ramp(frames, -5, 105, -26, 178, 800);
        ramp(frames, -26, 178, -27, 178, 3500);
        // 15:27:54.6 한두 프레임만 무릎이 150도로 튐
        frames.add(new double[]{-11, 150});
        frames.add(new double[]{-11, 152});
        ramp(frames, -11, 178, -6, 178, 2000);
        return frames;
    }

    private static List<double[]> releaseAfterHold() {
        List<double[]> frames = new ArrayList<>();
        // 15:28:17~20 1회를 마치고 28도로 숙인 채 (무릎 171~178)
        ramp(frames, 28, 175, 28, 175, 3000);
        // 15:28:20.9 세우면서 세 프레임 동안 무릎이 148도로 튐
        for (int i = 0; i < 3; i++) {
            frames.add(new double[]{20, 148});
        }
        ramp(frames, 20, 175, -20, 177, 800);
        ramp(frames, -20, 177, -20, 177, 2000);
        return frames;
    }

    private static void ramp(List<double[]> frames, double fromLean, double fromKnee, double toLean, double toKnee,
                             long ms) {
        int n = (int) (ms / FRAME_MS);
        for (int i = 1; i <= n; i++) {
            double r = (double) i / n;
            frames.add(new double[]{fromLean + (toLean - fromLean) * r, fromKnee + (toKnee - fromKnee) * r});
        }
    }

    // 새 규칙: HoldCounter에 넣고 "무릎을 펴 주세요"가 새로 나타난 횟수
    private static int kneeWarningsNew(List<double[]> frames) {
        HoldCounter counter = new HoldCounter(ExerciseCriteria.SEATED_HAMSTRING_STRETCH);
        long t = 0;
        int count = 0;
        RepetitionCounter.Feedback previous = null;
        for (double[] f : frames) {
            counter.onFrame(t, f[0], Double.NaN, f[1]);
            t += FRAME_MS;
            RepetitionCounter.Feedback now = counter.getFeedback();
            if (now == RepetitionCounter.Feedback.STRAIGHTEN_KNEE && previous != now) {
                count++;
            }
            previous = now;
        }
        return count;
    }

    // 예전 규칙: 프레임마다 160도 미만이면 굽힘, 굽힌 뒤 165도 이상이면 폄 → 굽힘으로 바뀐 횟수
    private static int kneeWarningsOld(List<double[]> frames) {
        boolean bent = false;
        int count = 0;
        for (double[] f : frames) {
            boolean next = bent ? f[1] < 165 : f[1] < 160;
            if (next && !bent) {
                count++;
            }
            bent = next;
        }
        return count;
    }

    @Test
    public void sittingDown_realBendWarnedOnce_singleFrameDipIgnored() {
        List<double[]> frames = sittingDownThenStretchingLeg();
        int before = kneeWarningsOld(frames);
        int after = kneeWarningsNew(frames);
        System.out.println("앉을 때: 예전 규칙 " + before + "번, 새 규칙 " + after + "번");
        assertEquals(2, before);   // 실제로 굽힘 + 한두 프레임 튐
        assertEquals(1, after);    // 실제로 굽혔을 때만
    }

    @Test
    public void releasingAfterHold_threeFrameDip_noWarning() {
        List<double[]> frames = releaseAfterHold();
        int before = kneeWarningsOld(frames);
        int after = kneeWarningsNew(frames);
        System.out.println("세울 때: 예전 규칙 " + before + "번, 새 규칙 " + after + "번");
        assertEquals(1, before);
        assertEquals(0, after);
    }

    @Test
    public void realBend_stillDetected_within5Frames() {
        // 정말로 무릎을 140도로 굽히면 평균에도 바로 반영 (5프레임 = 약 0.17초 안)
        List<double[]> frames = new ArrayList<>();
        ramp(frames, 30, 175, 30, 175, 2000);
        for (int i = 0; i < 6; i++) {
            frames.add(new double[]{30, 140});
        }
        assertTrue(kneeWarningsNew(frames) == 1);
    }
}

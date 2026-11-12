package com.example.airehabpostureapp.pose;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.airehabpostureapp.R;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 1차 실측(2026-11-12 23:19:07~28) 하체 스트레칭 "살짝 숙이기(B)" 구간의 음성 재현
 * 수정 전: 실제 로그의 화면 문구 변경 75번을 예전 음성 규칙(0.4초 안정 → 읽던 것을 끊고 새 문구)으로 재생
 * 수정 후: (1) 같은 문구 변경에 새 음성 규칙만 적용
 *         (2) 로그의 1초 기록(상체 기울기 / 무릎)으로 각도를 다시 만들어 새 측정기 + 새 음성 규칙 전체로 재생
 * 음성 길이는 글자당 0.13초로 가정 (한국어 TTS 보통 속도)
 */
public class StretchVoiceReplayTest {

    private static final ExerciseCriteria STRETCH = ExerciseCriteria.SEATED_HAMSTRING_STRETCH;
    private static final int K = R.string.feedback_stretch_straighten_knee;
    private static final int M = R.string.feedback_stretch_lean_more;
    private static final int S = R.string.feedback_stretch_start;
    private static final int H = R.string.feedback_stretch_hold;

    private static String text(int res) {
        if (res == K) return "앞으로 편 다리의 무릎을 펴 주세요.";
        if (res == M) return "조금 더 숙여 주세요.";
        if (res == S) return "허리를 편 채 상체를 천천히 앞으로 숙여 주세요.";
        if (res == H) return "좋아요! 그대로 유지하세요.";
        return "res" + res;
    }

    // 실제 로그: 23:19:07.000 기준 ms, 바뀐 문구
    private static final long[][] LOG = {
            {209, K}, {422, M}, {734, S}, {971, K}, {1159, M}, {1260, S}, {1468, K}, {1704, M}, {1760, K},
            {1831, M}, {2066, K}, {2098, M}, {2834, K}, {3073, M}, {3330, H}, {3527, K}, {3627, M}, {3988, H},
            {4032, K}, {4061, H}, {4096, K}, {4569, H}, {4623, K}, {4788, H}, {4859, K}, {4956, H}, {5109, K},
            {5132, H}, {5169, K}, {5427, H}, {5464, K}, {5494, H}, {5530, K}, {5931, H}, {6363, K}, {6660, H},
            {6701, K}, {7139, H}, {7293, K}, {7345, H}, {7671, K}, {7702, H}, {7809, K}, {7867, H}, {8265, K},
            {8329, H}, {9001, K}, {9198, H}, {9236, M}, {10199, S}, {10817, M}, {10906, S}, {10982, M},
            {11134, H}, {11539, M}, {13007, S}, {13075, M}, {13239, S}, {15372, M}, {15610, S}, {15843, M},
            {16053, S}, {16143, M}, {16874, S}, {17180, M}, {17551, S}, {17772, M}, {18144, S}, {18924, M},
            {19178, S}, {19456, M}, {19563, S}, {21111, M}, {21342, S}, {21424, K}, {21481, S}
    };

    /** 음성 재생 모델: 예전 규칙(oldRules) 또는 새 규칙(FeedbackVoiceFilter + VoicePacer) */
    private static final class Voice {
        final boolean oldRules;
        final FeedbackVoiceFilter filter = new FeedbackVoiceFilter();
        final VoicePacer pacer = new VoicePacer();
        final List<String> spoken = new ArrayList<>();
        int cut;
        long now;
        String settleText;
        long settleAt = -1;
        String lastSpoken;
        long lastSpokenAt = -100000;
        long speakingUntil = -1;

        Voice(boolean oldRules) {
            this.oldRules = oldRules;
        }

        void change(long t, int res) {
            advance(t);
            if (!oldRules && !filter.shouldSpeak(res)) {
                return;
            }
            settleText = text(res);
            settleAt = t + 400;
        }

        void advance(long until) {
            for (; now <= until; now += 10) {
                if (settleAt >= 0 && now >= settleAt) {
                    settleAt = -1;
                    if (oldRules) {
                        speak(settleText);
                    } else {
                        pacer.offer(settleText);
                    }
                }
                if (!oldRules) {
                    String message = pacer.poll(now, now < speakingUntil);
                    if (message != null) {
                        speak(message);
                    }
                }
            }
        }

        private void speak(String message) {
            if (message.equals(lastSpoken) && now - lastSpokenAt < 3000) {
                return;
            }
            if (now < speakingUntil) {
                cut++;   // 읽던 음성을 끊음 (예전 규칙)
            }
            spoken.add(message);
            lastSpoken = message;
            lastSpokenAt = now;
            speakingUntil = now + message.length() * 130L;
        }
    }

    private static Voice replayLog(boolean oldRules) {
        Voice v = new Voice(oldRules);
        for (long[] change : LOG) {
            v.change(change[0], (int) change[1]);
        }
        v.advance(25000);
        return v;
    }

    // 로그의 1초 기록: 23:19:06~28 (초, 앞으로 숙인 상체 기울기, 더 펴진 다리 무릎). 09초는 기록이 없어 앞뒤 사이 값
    private static final double[][] SAMPLES = {
            {6, 14.3, 166.3}, {7, 0.5, 156.0}, {8, 0.8, 162.8}, {9, 1.7, 160.4}, {10, 2.6, 158.0},
            {11, 3.8, 160.6}, {12, 5.6, 162.0}, {13, 4.5, 162.8}, {14, 5.0, 158.7}, {15, 5.5, 160.9},
            {16, 0.7, 158.0}, {17, 3.3, 169.7}, {18, 12.3, 176.2}, {19, 8.7, 179.0}, {20, 8.0, 178.9},
            {21, 8.1, 179.0}, {22, 8.8, 179.8}, {23, 9.6, 172.2}, {24, 10.1, 177.8}, {25, 8.3, 179.3},
            {26, 8.5, 177.5}, {27, 8.4, 178.6}, {28, 0.2, 161.6}
    };

    private static Voice replayAngles() {
        Voice v = new Voice(false);
        HoldCounter counter = new HoldCounter(STRETCH);
        Random noise = new Random(7);
        int shown = 0;
        for (int i = 0; i + 1 < SAMPLES.length; i++) {
            for (long ms = 0; ms < 1000; ms += 33) {
                double r = ms / 1000.0;
                double lean = SAMPLES[i][1] + (SAMPLES[i + 1][1] - SAMPLES[i][1]) * r + (noise.nextDouble() * 2 - 1) * 1.5;
                double knee = SAMPLES[i][2] + (SAMPLES[i + 1][2] - SAMPLES[i][2]) * r + (noise.nextDouble() * 2 - 1) * 3;
                long t = (long) ((SAMPLES[i][0] - 6) * 1000) + ms;
                counter.onFrame(t, lean, Double.NaN, knee);
                if (!counter.isRepInProgress()) {
                    v.filter.onMotionEnded();
                }
                int res = FeedbackMessages.forFeedback(STRETCH, counter.getFeedback(), 0);
                if (res != shown) {
                    shown = res;
                    v.change(t, res);
                }
            }
        }
        v.advance(25000);
        return v;
    }

    @Test
    public void segmentB_voiceCount_beforeAndAfter() {
        Voice before = replayLog(true);
        Voice voiceRulesOnly = replayLog(false);
        Voice after = replayAngles();
        System.out.println("B 구간 수정 전: 화면 변경 " + LOG.length + "번, 음성 시작 " + before.spoken.size()
                + "번 (그중 앞 음성을 끊음 " + before.cut + "번) " + before.spoken);
        System.out.println("B 구간 새 음성 규칙만: 음성 " + voiceRulesOnly.spoken.size() + "번 " + voiceRulesOnly.spoken);
        System.out.println("B 구간 수정 후(측정기 + 음성): 음성 " + after.spoken.size() + "번 " + after.spoken);

        // 새 규칙에서는 음성이 서로 끊지 않고, 시작 간격이 2초 이상
        assertEquals(0, voiceRulesOnly.cut);
        assertEquals(0, after.cut);
        assertTrue(before.spoken.size() > voiceRulesOnly.spoken.size());
        assertTrue(after.spoken.size() <= voiceRulesOnly.spoken.size());
        // "좋아요! 그대로 유지하세요."는 동작 하나에 한 번
        assertTrue(voiceRulesOnly.spoken.stream().filter(text(H)::equals).count() <= 1);
    }
}

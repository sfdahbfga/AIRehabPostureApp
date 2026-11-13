package com.example.airehabpostureapp.pose;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.airehabpostureapp.R;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 음성 안내 규칙 (화면 문구는 그대로, 판정 / 감점과 무관)
 * - 상체 / 허벅지 경고는 같은 동작 안에서 경고마다 한 번만 읽음
 * - 경고가 풀려 경고 전 문구("좋아요" 등)로 돌아오면 다시 읽지 않음
 * - 동작이 끝나면 다음 동작에서 다시 읽음
 */
public class FeedbackVoiceFilterTest {

    private static final int START = R.string.feedback_lunge_start;
    private static final int GOOD = R.string.feedback_lunge_good_come_up;
    private static final int TRUNK = R.string.feedback_keep_trunk_upright;
    private static final int THIGH = R.string.feedback_thigh_upright;

    // ---------- 규칙 단위 ----------

    @Test
    public void warningThenBackToGood_goodNotSpokenAgain() {
        // 실측 런지 A 2회째: 좋아요 → 상체를 곧게 → 좋아요
        FeedbackVoiceFilter f = new FeedbackVoiceFilter();
        assertTrue(f.shouldSpeak(START));
        assertTrue(f.shouldSpeak(GOOD));
        assertTrue(f.shouldSpeak(TRUNK));
        assertFalse(f.shouldSpeak(GOOD));
    }

    @Test
    public void sameWarningTwiceInOneMotion_spokenOnce() {
        FeedbackVoiceFilter f = new FeedbackVoiceFilter();
        f.shouldSpeak(START);
        assertTrue(f.shouldSpeak(TRUNK));
        assertFalse(f.shouldSpeak(START));
        assertFalse(f.shouldSpeak(TRUNK));
        // 경고 전과 다른 새 문구는 읽음
        assertTrue(f.shouldSpeak(GOOD));
        assertFalse(f.shouldSpeak(TRUNK));
        assertFalse(f.shouldSpeak(GOOD));
    }

    @Test
    public void differentWarnings_eachSpokenOnce() {
        FeedbackVoiceFilter f = new FeedbackVoiceFilter();
        f.shouldSpeak(START);
        assertTrue(f.shouldSpeak(THIGH));
        assertTrue(f.shouldSpeak(TRUNK));
        assertFalse(f.shouldSpeak(THIGH));
        assertFalse(f.shouldSpeak(START));
    }

    @Test
    public void nextMotion_warningSpokenAgain() {
        FeedbackVoiceFilter f = new FeedbackVoiceFilter();
        f.shouldSpeak(START);
        assertTrue(f.shouldSpeak(TRUNK));
        f.onMotionEnded();
        // 동작이 끝난 뒤 안내는 읽음
        assertTrue(f.shouldSpeak(START));
        assertTrue(f.shouldSpeak(TRUNK));
    }

    @Test
    public void motionEndsDuringWarning_nextMessageSpoken() {
        FeedbackVoiceFilter f = new FeedbackVoiceFilter();
        f.shouldSpeak(START);
        f.shouldSpeak(TRUNK);
        f.onMotionEnded();
        assertTrue(f.shouldSpeak(START));
    }

    @Test
    public void screenOnlyMessage_neverSpoken_otherMessagesSpoken() {
        FeedbackVoiceFilter f = new FeedbackVoiceFilter();
        assertFalse(f.shouldSpeak(R.string.feedback_squat_rising));
        assertTrue(f.shouldSpeak(R.string.feedback_squat_shallow));
        assertTrue(f.shouldSpeak(R.string.feedback_squat_hips_back));
        assertTrue(f.shouldSpeak(R.string.feedback_full_body_visible));
    }

    @Test
    public void onlyTrunkAndThighAreOncePerMotionWarnings() {
        assertTrue(FeedbackVoiceFilter.isPostureWarning(TRUNK));
        assertTrue(FeedbackVoiceFilter.isPostureWarning(THIGH));
        assertFalse(FeedbackVoiceFilter.isPostureWarning(R.string.feedback_squat_hips_back));
        assertFalse(FeedbackVoiceFilter.isPostureWarning(GOOD));
    }

    // ---------- 측정기와 함께 (ExerciseActivity의 프레임 처리와 같은 순서) ----------

    /** 30fps로 측정기에 각도를 넣고, 화면 문구가 바뀔 때 음성으로 읽힌 문구를 모음 */
    private static final class VoiceSim {
        final ExerciseCriteria criteria;
        final RepetitionCounter counter;
        final FeedbackVoiceFilter filter = new FeedbackVoiceFilter();
        final List<Integer> spoken = new ArrayList<>();
        final List<Integer> shownHistory = new ArrayList<>();
        int shown;
        long t;

        VoiceSim(ExerciseCriteria criteria) {
            this.criteria = criteria;
            this.counter = new RepetitionCounter(criteria);
        }

        void frame(double joint, double trunk, double hip) {
            t += 33;
            counter.onFrame(t, joint, trunk, hip);
            if (!counter.isRepInProgress()) {
                filter.onMotionEnded();
            }
            int res = FeedbackMessages.forFeedback(criteria, counter.getFeedback(), 1);
            if (res != shown) {
                shown = res;
                shownHistory.add(res);
                if (filter.shouldSpeak(res)) {
                    spoken.add(res);
                }
            }
        }

        void hold(double joint, double trunk, double hip, long ms) {
            for (long i = 0; i < ms; i += 33) {
                frame(joint, trunk, hip);
            }
        }

        void move(double fromJoint, double toJoint, double fromHip, double toHip, double trunk, long ms) {
            int n = (int) (ms / 33);
            for (int i = 1; i <= n; i++) {
                double r = (double) i / n;
                frame(fromJoint + (toJoint - fromJoint) * r, trunk, fromHip + (toHip - fromHip) * r);
            }
        }
    }

    @Test
    public void lunge_trunkOverBriefly_atBottom_trunkOnce_goodNotRepeated() {
        VoiceSim s = new VoiceSim(ExerciseCriteria.LUNGE);
        s.hold(170, 5, 170, 500);
        s.move(170, 88, 170, 90, 5, 1000);
        s.hold(88, 5, 90, 300);
        // 가장 깊은 곳에서 상체가 잠깐 30도 → 다시 세움
        s.hold(88, 30, 90, 300);
        s.hold(88, 5, 90, 300);
        s.move(88, 170, 90, 170, 5, 1000);
        s.hold(170, 5, 170, 400);
        assertEquals(1, s.counter.getCompletedReps());
        // 화면: 좋아요 → 상체를 곧게 → 좋아요 (화면은 그대로 바뀜)
        assertEquals(2, Collections.frequency(s.shownHistory, GOOD));
        assertEquals(1, Collections.frequency(s.shownHistory, TRUNK));
        // 음성: 좋아요 1번, 상체 1번
        assertEquals(1, Collections.frequency(s.spoken, GOOD));
        assertEquals(1, Collections.frequency(s.spoken, TRUNK));
    }

    @Test
    public void chairSquat_trunkFlickersInOneRep_spokenOnce_againNextRep() {
        VoiceSim s = new VoiceSim(ExerciseCriteria.CHAIR_SQUAT);
        s.hold(175, 30, 175, 500);
        for (int rep = 0; rep < 2; rep++) {
            s.move(175, 105, 175, 80, 30, 1200);
            // 상체가 허용 50도를 넘었다 돌아오기를 두 번
            for (int i = 0; i < 2; i++) {
                s.hold(105, 55, 80, 300);
                s.hold(105, 30, 80, 300);
            }
            s.move(105, 175, 80, 175, 30, 1200);
            s.hold(175, 30, 175, 400);
        }
        assertEquals(2, s.counter.getCompletedReps());
        assertEquals(4, Collections.frequency(s.shownHistory, TRUNK));
        // 동작마다 한 번 → 2회 동안 2번
        assertEquals(2, Collections.frequency(s.spoken, TRUNK));
        assertEquals(2, Collections.frequency(s.spoken, R.string.feedback_squat_good_stand_up));
    }

    @Test
    public void kneeFlexion_thighWarningFlickers_spokenOnce() {
        VoiceSim s = new VoiceSim(ExerciseCriteria.KNEE_FLEXION);
        s.hold(175, 5, 170, 500);
        s.move(175, 95, 170, 170, 5, 1000);
        // 엉덩이가 140도 아래로 두 번 굽혀짐 (허벅지 경고)
        for (int i = 0; i < 2; i++) {
            s.hold(95, 5, 125, 300);
            s.hold(95, 5, 170, 300);
        }
        s.move(95, 175, 170, 170, 5, 1000);
        s.hold(175, 5, 170, 400);
        assertEquals(2, Collections.frequency(s.shownHistory, THIGH));
        assertEquals(1, Collections.frequency(s.spoken, THIGH));
        assertEquals(1, Collections.frequency(s.spoken, R.string.feedback_good_straighten));
    }
}

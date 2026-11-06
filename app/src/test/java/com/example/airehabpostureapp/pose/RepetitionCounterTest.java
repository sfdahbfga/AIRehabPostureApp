package com.example.airehabpostureapp.pose;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 가짜 각도 데이터(30fps)로 무릎 굽힘 운동 반복 횟수 세기를 검증한다.
 * 기준: 편 상태 >= 160도, 굽힘 인정 <= 130도, 목표 90도, 최소 동작 시간 0.5초, 엉덩이 각도 >= 140도
 */
public class RepetitionCounterTest {

    private static final long FRAME_MS = 33;
    private static final double UPRIGHT_TRUNK = 5;
    // A안처럼 허벅지가 수직일 때의 엉덩이 각도
    private static final double UPRIGHT_HIP = 170;
    private static final double DELTA = 1e-6;

    private RepetitionCounter counter;
    private long now;
    private List<RepetitionCounter.RepResult> completed;
    private List<RepetitionCounter.RejectedAttempt> rejected;
    private List<RepetitionCounter.Feedback> feedbackHistory;
    // 다음 프레임들에 쓸 엉덩이 각도 (기본: 허벅지 수직)
    private double hipAngle = UPRIGHT_HIP;

    @Before
    public void setUp() {
        counter = new RepetitionCounter(ExerciseCriteria.KNEE_FLEXION);
        now = 0;
        completed = new ArrayList<>();
        rejected = new ArrayList<>();
        feedbackHistory = new ArrayList<>();
        hipAngle = UPRIGHT_HIP;
    }

    // ---------- 가짜 동작 만들기 ----------

    private void frame(double kneeAngle, double trunkLean) {
        double hip = Double.isNaN(kneeAngle) ? Double.NaN : hipAngle;
        RepetitionCounter.RepResult result = counter.onFrame(now, kneeAngle, trunkLean, hip);
        if (result != null) {
            completed.add(result);
        }
        RepetitionCounter.RejectedAttempt attempt = counter.takeRejectedAttempt();
        if (attempt != null) {
            rejected.add(attempt);
        }
        feedbackHistory.add(counter.getFeedback());
        now += FRAME_MS;
    }

    private void hold(double kneeAngle, long durationMs) {
        hold(kneeAngle, UPRIGHT_TRUNK, durationMs);
    }

    private void hold(double kneeAngle, double trunkLean, long durationMs) {
        for (long t = 0; t < durationMs; t += FRAME_MS) {
            frame(kneeAngle, trunkLean);
        }
    }

    private void move(double from, double to, long durationMs, double trunkLean, double noise, Random random) {
        int frames = (int) (durationMs / FRAME_MS);
        for (int i = 0; i <= frames; i++) {
            double angle = from + (to - from) * i / frames;
            if (random != null) {
                angle += (random.nextDouble() * 2 - 1) * noise;
            }
            frame(angle, trunkLean);
        }
    }

    /** 1회: 편 상태(175) → 1초 동안 minAngle까지 굽힘 → 0.3초 유지 → 1초 동안 폄 → 0.4초 유지 */
    private void rep(double minAngle) {
        rep(minAngle, UPRIGHT_TRUNK, 0, null);
    }

    private void rep(double minAngle, double trunkLean, double noise, Random random) {
        move(175, minAngle, 1000, trunkLean, noise, random);
        hold(minAngle, trunkLean, 300);
        move(minAngle, 175, 1000, trunkLean, noise, random);
        hold(175, 400);
    }

    // ---------- 정상 동작 ----------

    @Test
    public void normalFiveReps_countsFive_withFullAccuracy() {
        hold(175, 500);
        for (int i = 0; i < 5; i++) {
            rep(90);
        }
        assertEquals(5, counter.getCompletedReps());
        for (int i = 0; i < 5; i++) {
            RepetitionCounter.RepResult r = completed.get(i);
            assertEquals(i + 1, r.repNumber);
            assertEquals(90, r.minJointAngle, 0.5);
            assertEquals(0, r.jointError, 0.5);
            assertEquals(0, r.trunkError, DELTA);
            assertEquals(100, r.accuracy, DELTA);
        }
    }

    @Test
    public void feedback_followsMovement() {
        hold(175, 300);
        assertEquals(RepetitionCounter.Feedback.START_BENDING, counter.getFeedback());
        move(175, 140, 600, UPRIGHT_TRUNK, 0, null);
        assertEquals(RepetitionCounter.Feedback.BEND_MORE, counter.getFeedback());
        move(140, 90, 600, UPRIGHT_TRUNK, 0, null);
        hold(90, 300);
        assertEquals(RepetitionCounter.Feedback.GOOD_STRAIGHTEN, counter.getFeedback());
        move(90, 175, 800, UPRIGHT_TRUNK, 0, null);
        hold(175, 300);
        assertEquals(RepetitionCounter.Feedback.START_BENDING, counter.getFeedback());
    }

    // ---------- 흔들림 ----------

    @Test
    public void jitteryFiveReps_countsFive() {
        Random random = new Random(42);
        hold(175, 500);
        for (int i = 0; i < 5; i++) {
            rep(92, UPRIGHT_TRUNK, 8, random);
        }
        assertEquals(5, counter.getCompletedReps());
        for (RepetitionCounter.RepResult r : completed) {
            assertTrue("흔들림이 있어도 최소 각도는 목표 근처: " + r.minJointAngle,
                    Math.abs(r.minJointAngle - 92) < 6);
        }
    }

    @Test
    public void hoveringAroundThresholds_doesNotDoubleCount() {
        hold(175, 500);
        move(175, 90, 1000, UPRIGHT_TRUNK, 0, null);
        // 굽힘 기준(130) 근처에서 흔들리며 올라옴
        for (int i = 0; i < 10; i++) {
            hold(125, 100);
            hold(135, 100);
        }
        // 편 기준(160) 근처에서 흔들림
        for (int i = 0; i < 10; i++) {
            hold(155, 100);
            hold(165, 100);
        }
        hold(175, 500);
        assertEquals(1, counter.getCompletedReps());
    }

    @Test
    public void singleFrameSpike_doesNotCountRep() {
        hold(175, 500);
        // 한 프레임만 100도로 튐 → 5프레임 평균은 130 아래로 내려가지 않음
        frame(100, UPRIGHT_TRUNK);
        hold(175, 1500);
        assertEquals(0, counter.getCompletedReps());
    }

    // ---------- 덜 굽힌 동작 ----------

    @Test
    public void shallowBend_aboveBentThreshold_isNotCounted() {
        hold(175, 500);
        rep(140);
        rep(140);
        assertEquals(0, counter.getCompletedReps());
        assertTrue(feedbackHistory.contains(RepetitionCounter.Feedback.BEND_MORE));
    }

    @Test
    public void partialBend_belowBentThreshold_isCountedWithLowAccuracy() {
        hold(175, 500);
        rep(120);
        assertEquals(1, counter.getCompletedReps());
        RepetitionCounter.RepResult r = completed.get(0);
        assertEquals(30, r.jointError, 0.5);
        // 무릎 점수 100 - (30 - 10) x 2 = 60, 상체 100 → 60 x 0.8 + 100 x 0.2 = 68
        assertEquals(68, r.accuracy, 0.5);
    }

    @Test
    public void tooFastMovement_isNotCounted() {
        hold(175, 500);
        // 약 0.35초 만에 굽혔다 폄 (최소 0.5초 미만)
        move(175, 90, 150, UPRIGHT_TRUNK, 0, null);
        move(90, 175, 150, UPRIGHT_TRUNK, 0, null);
        hold(175, 500);
        assertEquals(0, counter.getCompletedReps());
        assertEquals(1, rejected.size());
        assertEquals(RepetitionCounter.RejectReason.TOO_FAST, rejected.get(0).reason);
    }

    @Test
    public void quickButRealMovement_0_7s_isCounted() {
        // 2차 측정에서 정상 동작이 0.81초까지 나옴 → 0.7초 동작도 세져야 함
        hold(175, 500);
        move(175, 90, 330, UPRIGHT_TRUNK, 0, null);
        move(90, 175, 330, UPRIGHT_TRUNK, 0, null);
        hold(175, 500);
        assertEquals(1, counter.getCompletedReps());
        assertTrue(completed.get(0).durationMs < 800);
    }

    // ---------- 각도 연속성 검사 ----------

    @Test
    public void standingLegValueMidRep_isSkipped_andRepNotSplit() {
        // 4차 측정에서 본 현상: 굽히는 다리가 잠깐 가려져 서 있는 다리 값(약 165도)이 들어옴
        hold(175, 500);
        move(175, 100, 700, UPRIGHT_TRUNK, 0, null);
        hold(165, 200);   // 6프레임 튐 → 연속성 검사로 건너뜀
        move(100, 90, 300, UPRIGHT_TRUNK, 0, null);
        hold(90, 200);
        move(90, 175, 1000, UPRIGHT_TRUNK, 0, null);
        hold(175, 400);
        assertEquals(1, counter.getCompletedReps());
        assertTrue("쪼개진 조각이 버려진 동작으로 남으면 안 됨", rejected.isEmpty());
        assertEquals(90, completed.get(0).minJointAngle, 0.5);
        assertTrue(counter.getSkippedFrameCount() >= 1);
    }

    @Test
    public void realValueAfterLongGap_isAcceptedAgain() {
        // 0.4초 동안 안 보이다가 그사이 실제로 다리를 폈다면, 허용 폭이 커져서 새 값을 받아들임
        hold(175, 500);
        move(175, 90, 1000, UPRIGHT_TRUNK, 0, null);
        hold(90, 300);
        hold(Double.NaN, Double.NaN, 400);
        hold(175, 400);
        assertEquals(1, counter.getCompletedReps());
    }

    @Test
    public void jumpThatPersists_isEventuallyAccepted() {
        // 계속 같은 값이면 허용 폭이 시간에 따라 커져서 결국 받아들임 (영원히 건너뛰지 않음)
        hold(175, 500);
        hold(100, 1000);
        assertTrue(counter.getSmoothedJointAngle() < 110);
    }

    // ---------- 엉덩이 각도 (스쿼트 구분) ----------

    @Test
    public void squatLikeMovement_hipBent_isNotCounted_andAsksThighUpright() {
        hold(175, 500);
        // 스쿼트: 무릎과 함께 엉덩이도 굽혀지고 상체가 숙여짐
        hipAngle = 100;
        rep(110, 35, 0, null);
        hipAngle = UPRIGHT_HIP;
        assertEquals(0, counter.getCompletedReps());
        assertTrue(feedbackHistory.contains(RepetitionCounter.Feedback.THIGH_UPRIGHT));
        assertEquals(1, rejected.size());
        assertEquals(RepetitionCounter.RejectReason.HIP_BENT, rejected.get(0).reason);
        assertTrue(rejected.get(0).minHipAngle < 140);
    }

    @Test
    public void leaningTrunkWithUprightThigh_isCounted_withTrunkPenalty() {
        hold(175, 500);
        // A안을 하며 상체만 30도 숙임: 엉덩이 각도 약 150도 → 세지고 상체만 감점
        hipAngle = 150;
        rep(90, 30, 0, null);
        assertEquals(1, counter.getCompletedReps());
        RepetitionCounter.RepResult r = completed.get(0);
        assertEquals(150, r.minHipAngle, 0.5);
        assertEquals(15, r.trunkError, 0.5);
        assertTrue(r.accuracy < 100);
    }

    @Test
    public void hipNotVisible_skipsHipRule() {
        hold(175, 500);
        hipAngle = Double.NaN;
        rep(90);
        assertEquals(1, counter.getCompletedReps());
        assertTrue(Double.isNaN(completed.get(0).minHipAngle));
    }

    // ---------- 버려진 동작 기록 ----------

    @Test
    public void shallowAttempt_isRecordedWithReason() {
        hold(175, 500);
        rep(140);
        assertEquals(1, rejected.size());
        RepetitionCounter.RejectedAttempt attempt = rejected.get(0);
        assertEquals(RepetitionCounter.RejectReason.SHALLOW, attempt.reason);
        assertEquals(140, attempt.minJointAngle, 0.5);
    }

    @Test
    public void smallSway_isNotRecordedAsAttempt() {
        hold(175, 500);
        // 편 기준(160) 바로 아래까지만 흔들림 → 동작 시도로 기록하지 않음
        move(175, 155, 300, UPRIGHT_TRUNK, 0, null);
        move(155, 175, 300, UPRIGHT_TRUNK, 0, null);
        hold(175, 500);
        assertEquals(0, counter.getCompletedReps());
        assertTrue(rejected.isEmpty());
    }

    // ---------- 관절이 안 보이는 구간 ----------

    @Test
    public void shortInvisibleGap_midRep_stillCounts() {
        hold(175, 500);
        move(175, 90, 1000, UPRIGHT_TRUNK, 0, null);
        hold(Double.NaN, Double.NaN, 400);
        hold(90, 300);
        move(90, 175, 1000, UPRIGHT_TRUNK, 0, null);
        hold(175, 400);
        assertEquals(1, counter.getCompletedReps());
    }

    @Test
    public void longInvisibleGap_midRep_discardsRepAndAsksForFullBody() {
        hold(175, 500);
        move(175, 90, 1000, UPRIGHT_TRUNK, 0, null);
        hold(Double.NaN, Double.NaN, 1500);
        assertEquals(RepetitionCounter.Feedback.NOT_VISIBLE, counter.getFeedback());
        assertFalse(counter.isRepInProgress());
        assertEquals(1, rejected.size());
        assertEquals(RepetitionCounter.RejectReason.LOST_TRACKING, rejected.get(0).reason);
        // 다시 보였을 때 굽힌 상태라면 먼저 펴야 함 → 이 동작은 세지 않음
        hold(90, 300);
        assertEquals(RepetitionCounter.Feedback.STAND_STRAIGHT, counter.getFeedback());
        move(90, 175, 1000, UPRIGHT_TRUNK, 0, null);
        hold(175, 400);
        assertEquals(0, counter.getCompletedReps());
        // 그다음 정상 동작은 셈
        rep(90);
        assertEquals(1, counter.getCompletedReps());
    }

    @Test
    public void neverVisible_showsNotVisible() {
        hold(Double.NaN, Double.NaN, 500);
        assertEquals(RepetitionCounter.Feedback.NOT_VISIBLE, counter.getFeedback());
        assertEquals(0, counter.getCompletedReps());
    }

    @Test
    public void startingBent_waitsForStraightLegFirst() {
        hold(100, 500);
        assertEquals(RepetitionCounter.Feedback.STAND_STRAIGHT, counter.getFeedback());
        hold(175, 500);
        assertEquals(0, counter.getCompletedReps());
        rep(90);
        assertEquals(1, counter.getCompletedReps());
    }

    // ---------- 상체 숙임 ----------

    @Test
    public void trunkLean_reducesAccuracy_andShowsFeedback() {
        hold(175, 500);
        rep(90, 25, 0, null);
        RepetitionCounter.RepResult r = completed.get(0);
        // 상체 오차 25 - 15 = 10, 상체 점수 100 - 10 x 4 = 60 → 100 x 0.8 + 60 x 0.2 = 92
        assertEquals(10, r.trunkError, 0.5);
        assertEquals(92, r.accuracy, 0.5);
        assertTrue(feedbackHistory.contains(RepetitionCounter.Feedback.KEEP_TRUNK_UPRIGHT));
    }

    @Test
    public void trunkNotVisible_usesJointScoreOnly() {
        hold(175, 500);
        rep(120, Double.NaN, 0, null);
        RepetitionCounter.RepResult r = completed.get(0);
        assertTrue(Double.isNaN(r.trunkError));
        assertEquals(60, r.accuracy, 0.5);
    }

    // ---------- 초기화 ----------

    @Test
    public void reset_clearsCount() {
        hold(175, 500);
        rep(90);
        rep(90);
        assertEquals(2, counter.getCompletedReps());
        counter.reset();
        assertEquals(0, counter.getCompletedReps());
        assertEquals(RepetitionCounter.Feedback.NOT_VISIBLE, counter.getFeedback());
        hold(175, 500);
        rep(90);
        assertEquals(1, counter.getCompletedReps());
        assertEquals(1, counter.getResults().get(0).repNumber);
    }
}

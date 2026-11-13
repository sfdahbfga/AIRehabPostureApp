package com.example.airehabpostureapp.pose;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 운동별 단위 테스트용 가짜 각도 데이터(30fps) 공급기.
 * 판정 각도(joint), 상체 기울기(trunk), 보조 각도(aux: 엉덩이 각도 또는 펴야 하는 무릎 각도)를
 * 프레임마다 측정기에 넣고, 끝난 회차 / 버린 동작 / 안내 기록을 모은다.
 */
final class CounterHarness {

    static final long FRAME_MS = 33;

    final ExerciseCounter counter;
    final List<RepetitionCounter.RepResult> completed = new ArrayList<>();
    final List<RepetitionCounter.RejectedAttempt> rejected = new ArrayList<>();
    final List<RepetitionCounter.Feedback> feedbackHistory = new ArrayList<>();
    long now;

    CounterHarness(ExerciseCounter counter) {
        this.counter = counter;
    }

    void frame(double joint, double trunk, double aux) {
        RepetitionCounter.RepResult result = counter.onFrame(now, joint, trunk, aux);
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

    /** 같은 자세를 durationMs 동안 유지 */
    void hold(double joint, double trunk, double aux, long durationMs) {
        for (long t = 0; t < durationMs; t += FRAME_MS) {
            frame(joint, trunk, aux);
        }
    }

    /** 관절이 안 보이는 구간 (모든 값 NaN) */
    void invisible(long durationMs) {
        hold(Double.NaN, Double.NaN, Double.NaN, durationMs);
    }

    /** 판정 각도와 보조 각도를 함께 일정한 속도로 바꿈. random이 있으면 판정 각도에 ±noise 흔들림 */
    void move(double fromJoint, double toJoint, double fromAux, double toAux, long durationMs,
              double trunk, double noise, Random random) {
        int frames = (int) (durationMs / FRAME_MS);
        for (int i = 0; i <= frames; i++) {
            double ratio = (double) i / frames;
            double joint = fromJoint + (toJoint - fromJoint) * ratio;
            if (random != null) {
                joint += (random.nextDouble() * 2 - 1) * noise;
            }
            frame(joint, trunk, fromAux + (toAux - fromAux) * ratio);
        }
    }

    void move(double fromJoint, double toJoint, double fromAux, double toAux, long durationMs, double trunk) {
        move(fromJoint, toJoint, fromAux, toAux, durationMs, trunk, 0, null);
    }

    boolean sawFeedback(RepetitionCounter.Feedback feedback) {
        return feedbackHistory.contains(feedback);
    }

    RepetitionCounter.RejectReason lastRejectReason() {
        return rejected.isEmpty() ? null : rejected.get(rejected.size() - 1).reason;
    }
}

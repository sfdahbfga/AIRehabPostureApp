package com.example.airehabpostureapp.pose;

/**
 * 목표 횟수 도달 안내 시점 (Android 의존성 없는 순수 Java)
 * 1회가 끝날 때마다 호출해, 이번 회차로 목표 횟수를 처음 채웠으면 true.
 * 목표를 넘긴 뒤에도 횟수는 계속 세고 운동은 자동으로 끝내지 않으므로, 안내는 정확히 목표 회차에서 한 번뿐이다.
 * (화면 재생성 / 일시정지 후에도 이미 넘긴 횟수에서는 다시 안내하지 않음)
 */
public final class RepGoal {

    private RepGoal() {
    }

    public static boolean isJustReached(int completedReps, int targetReps) {
        return targetReps > 0 && completedReps == targetReps;
    }
}

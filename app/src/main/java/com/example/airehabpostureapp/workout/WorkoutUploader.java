package com.example.airehabpostureapp.workout;

import androidx.annotation.StringRes;

import com.example.airehabpostureapp.R;
import com.example.airehabpostureapp.network.AuthErrorMapper;
import com.example.airehabpostureapp.network.ExerciseSessionApi;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 임시 저장한 운동 결과(PendingWorkout)를 서버에 저장 / 삭제한다. (운동 후 체크, 홈의 "저장하지 못한 기록" 공통)
 *
 * 저장 순서: exercise_sessions → session_checkins(pre, post) → 성공하면 임시 기록 삭제
 * - id를 미리 정해 두고 ignore-duplicates로 보내므로, 몇 번을 다시 보내도 중복 저장되지 않는다.
 * - 체크인 저장이 실패하면 방금 저장한 세션을 지워(롤백) 체크인 없는 반쪽 기록이 남지 않게 한다.
 *   롤백 삭제도 실패하면 sessionMayExistOnServer = true로 남겨 두고, 다시 시도하면 세션(중복 무시) → 체크인 순서로 이어서 저장한다.
 * - 실패해도 임시 기록은 남아 있으므로 다음에 다시 저장할 수 있다.
 */
public class WorkoutUploader {

    public interface Listener {
        void onSaved(String sessionId);

        void onFailed(@StringRes int messageRes);
    }

    private final ExerciseSessionApi api;
    private final PendingWorkoutStore store;
    private final String bearerToken;
    // 진행 중인 요청 (화면이 닫힐 때 취소용)
    private Call<?> currentCall;

    public WorkoutUploader(ExerciseSessionApi api, PendingWorkoutStore store, String bearerToken) {
        this.api = api;
        this.store = store;
        this.bearerToken = bearerToken;
    }

    /** 서버 저장 (운동 후 체크까지 끝난 기록만) */
    public void upload(PendingWorkout workout, Listener listener) {
        if (!workout.postChecked) {
            listener.onFailed(R.string.error_exercise_save_failed);
            return;
        }
        // 요청을 보내기 전에 표시: 응답을 못 받으면 서버에 행이 있을 수도 있음
        workout.sessionMayExistOnServer = true;
        store.save(workout);
        Call<Void> call = api.insertSession(bearerToken, workout.toSessionInsert());
        currentCall = call;
        call.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (call.isCanceled()) {
                    return;
                }
                if (!response.isSuccessful()) {
                    // 서버가 거부 → 세션 행은 만들어지지 않음
                    workout.sessionMayExistOnServer = false;
                    store.save(workout);
                    listener.onFailed(R.string.error_exercise_save_failed);
                    return;
                }
                saveCheckins(workout, listener);
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                if (call.isCanceled()) {
                    return;
                }
                listener.onFailed(failureMessage(t));
            }
        });
    }

    private void saveCheckins(PendingWorkout workout, Listener listener) {
        Call<Void> call = api.insertCheckins(bearerToken, workout.toCheckinInserts());
        currentCall = call;
        call.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (call.isCanceled()) {
                    return;
                }
                if (!response.isSuccessful()) {
                    rollbackSession(workout, R.string.error_exercise_save_failed, listener);
                    return;
                }
                store.remove(workout);
                listener.onSaved(workout.sessionId);
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                if (call.isCanceled()) {
                    return;
                }
                rollbackSession(workout, failureMessage(t), listener);
            }
        });
    }

    // 체크인 없는 반쪽 기록이 남지 않도록 세션 삭제 (실패하면 sessionMayExistOnServer 유지)
    private void rollbackSession(PendingWorkout workout, @StringRes int errorMessageRes, Listener listener) {
        Call<Void> call = api.deleteSession(bearerToken, "eq." + workout.sessionId);
        currentCall = call;
        call.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (call.isCanceled()) {
                    return;
                }
                if (response.isSuccessful()) {
                    workout.sessionMayExistOnServer = false;
                    store.save(workout);
                }
                listener.onFailed(errorMessageRes);
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                if (call.isCanceled()) {
                    return;
                }
                listener.onFailed(errorMessageRes);
            }
        });
    }

    /**
     * 사용자가 "삭제"를 고른 경우: 서버에 반쪽 기록이 있을 수 있으면 서버에서도 지운 뒤 임시 기록 삭제.
     * 서버 삭제에 실패하면 임시 기록을 남겨 두고 onFailed (다음에 다시 물어봄)
     */
    public void discard(PendingWorkout workout, Listener listener) {
        if (!workout.sessionMayExistOnServer) {
            store.remove(workout);
            listener.onSaved(workout.sessionId);
            return;
        }
        Call<Void> call = api.deleteSession(bearerToken, "eq." + workout.sessionId);
        currentCall = call;
        call.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (call.isCanceled()) {
                    return;
                }
                if (!response.isSuccessful()) {
                    listener.onFailed(R.string.error_pending_workout_discard_failed);
                    return;
                }
                store.remove(workout);
                listener.onSaved(workout.sessionId);
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                if (call.isCanceled()) {
                    return;
                }
                listener.onFailed(R.string.error_pending_workout_discard_failed);
            }
        });
    }

    /** 진행 중인 요청 취소 (화면이 닫힐 때). 취소된 요청은 임시 기록에 그대로 남는다. */
    public void cancel() {
        if (currentCall != null) {
            currentCall.cancel();
        }
    }

    @StringRes
    private static int failureMessage(Throwable t) {
        return AuthErrorMapper.fromFailure(t, R.string.error_exercise_save_failed);
    }
}

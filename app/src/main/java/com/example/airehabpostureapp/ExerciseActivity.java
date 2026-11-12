package com.example.airehabpostureapp;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.activity.SystemBarStyle;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.core.resolutionselector.AspectRatioStrategy;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.airehabpostureapp.network.SessionManager;
import com.example.airehabpostureapp.pose.ExerciseCounter;
import com.example.airehabpostureapp.pose.ExerciseCriteria;
import com.example.airehabpostureapp.pose.FeedbackMessages;
import com.example.airehabpostureapp.pose.FeedbackVoiceFilter;
import com.example.airehabpostureapp.pose.HoldCounter;
import com.example.airehabpostureapp.pose.LegSelector;
import com.example.airehabpostureapp.pose.PausableCounter;
import com.example.airehabpostureapp.pose.PoseLandmarkerHelper;
import com.example.airehabpostureapp.pose.PoseMeasurer;
import com.example.airehabpostureapp.pose.PoseOverlayView;
import com.example.airehabpostureapp.pose.RepGoal;
import com.example.airehabpostureapp.pose.RepetitionCounter;
import com.example.airehabpostureapp.pose.SessionMetrics;
import com.example.airehabpostureapp.stats.JointKeys;
import com.example.airehabpostureapp.workout.PendingWorkout;
import com.example.airehabpostureapp.workout.PendingWorkoutStore;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// 운동 화면: 카메라 영상 + 관절 뼈대 표시, 반복 횟수 / 정확도 실시간 측정
// 흐름: CameraX → MediaPipe Landmark → Java 관절 각도 계산 → 운동별 기준 범위 비교(ExerciseCriteria)
//       → 자세 피드백 → 반복 횟수 측정(RepetitionCounter)
// MediaPipe는 관절 위치 추출에만 사용하고, 판정은 Java 코드에서 한다.
public class ExerciseActivity extends AppCompatActivity {

    private static final long TIMER_INTERVAL_MS = 1000L;

    // 디버그용: 무릎 각도(엉덩이-무릎-발목)를 화면 구석과 Logcat(태그 PoseDebug)에 1초 간격으로 표시.
    // 기준값 조정이 끝나면 false로 끄거나 관련 코드를 삭제한다.
    // M9: 디버그 로그 / 화면 표시는 debug 빌드(BuildConfig.DEBUG)에서만 켜진다. release에서는 항상 꺼짐
    // DEBUG_SHOW_KNEE_ANGLE: 화면 구석 표시 (debug에서도 꺼 둠, 측정할 때 SHOW_KNEE_ANGLE_IN_DEBUG를 true로 켬)
    // DEBUG_LOG_KNEE_ANGLE: Logcat 1초 기록 (각도 / visibility / fps / 건너뛴 프레임 수)
    private static final boolean SHOW_KNEE_ANGLE_IN_DEBUG = false;
    private static final boolean DEBUG_SHOW_KNEE_ANGLE = BuildConfig.DEBUG && SHOW_KNEE_ANGLE_IN_DEBUG;
    private static final boolean DEBUG_LOG_KNEE_ANGLE = BuildConfig.DEBUG;
    // 디버그용: 1회가 끝날 때마다 회차 / 가장 많이 굽힌 각도 / 정확도를 Logcat(태그 PoseDebug)에 기록.
    // 기준값 조정이 끝나면 false로 끈다.
    private static final boolean DEBUG_LOG_REPS = BuildConfig.DEBUG;
    private static final String DEBUG_TAG = "PoseDebug";
    private static final long DEBUG_LOG_INTERVAL_MS = 1000L;

    private static final String TAG = "ExerciseActivity";

    // 기본은 전면 카메라. 전면은 미리보기가 거울처럼 반전되므로 뼈대도 반전해서 그린다.
    private static final boolean USE_FRONT_CAMERA = true;

    // MediaPipe 분석용 해상도 (센서 기준 가로 x 세로). 미리보기와 같은 4:3 비율이어야 뼈대가 정확히 겹친다.
    private static final Size ANALYSIS_TARGET_SIZE = new Size(640, 480);

    // 목표 횟수: 운동 상세의 권장 횟수 중 1세트 기준 (운동별 값은 ExerciseCriteria.targetReps)
    private int targetReps = ExerciseCriteria.KNEE_FLEXION.targetReps;

    // 자세 인식 기준 / 반복 횟수 측정 (아직 기준이 없는 운동이면 null → 측정하지 않음)
    @Nullable
    private ExerciseCriteria exerciseCriteria;
    // 횟수형 / 유지 시간형 측정기 (운동별로 createCounter에서 고름)
    @Nullable
    private ExerciseCounter repetitionCounter;
    // 프레임은 이것을 거쳐 측정기에 넘김 ("나갈까요?" 확인 창이 떠 있는 동안 일시정지)
    @Nullable
    private PausableCounter pausableCounter;
    // "나갈까요?" 확인 창이 떠 있음 → 타이머 / 자세 분석 / 음성 안내 일시정지
    private boolean exitDialogShowing = false;
    // 운동하는 다리 정하기 / 고르기 (정옆으로 섰을 때 visibility가 더 높은 카메라 쪽 다리로 고정)
    private final LegSelector legSelector = new LegSelector();
    private TextView postureFeedbackText;
    // 지금 표시 중인 안내 문구 (같으면 다시 그리지 않음)
    @Nullable
    private String shownFeedbackText;
    // 지금 표시 중인 안내 문구의 리소스 (바뀔 때만 로그 / 음성. 유지형의 초 숫자만 바뀌는 경우는 제외)
    @StringRes
    private int shownFeedbackRes;
    // 안내 문구 / 횟수 음성 안내 (한국어 음성이 없으면 아무것도 읽지 않음)
    private VoiceGuide voiceGuide;

    // 운동 진행 시간 (초), 화면이 보이는 동안만 1초씩 증가
    private int elapsedSeconds = 0;
    // 운동 시작 시각 (epoch milliseconds), "처음부터 다시" 시 다시 기록
    private long startedAtMillis;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private TextView exerciseTimerText;
    private TextView exerciseNameText;

    // 카메라 / 자세 인식
    private PreviewView cameraPreviewView;
    private PoseOverlayView skeletonOverlayView;
    private TextView cameraMessageText;
    private TextView debugKneeAngleText;
    @Nullable
    private ProcessCameraProvider cameraProvider;
    @Nullable
    private PoseLandmarkerHelper poseLandmarkerHelper;
    // 카메라 프레임 분석 (화면을 닫을 때 clearAnalyzer로 먼저 분리)
    @Nullable
    private ImageAnalysis imageAnalysis;
    // MediaPipe 분석 전용 스레드 (UI 스레드를 막지 않도록)
    @Nullable
    private ExecutorService analysisExecutor;
    private long lastDebugLogAtMs = 0;
    private int lastLoggedSkippedFrames = 0;
    // 디버그용: 지난 1초 기록 이후 받은 MediaPipe 결과 수 (fps), 사람 인식 안 됨 / 다리 visibility 부족 프레임 수
    private int debugFrameCount = 0;
    private int debugNoPoseFrames = 0;
    private int debugLowVisibilityFrames = 0;
    // 디버그용: 한쪽 다리만 visibility 부족이었던 프레임 수 (1초 기록용 / 동작 단위 기록용)
    private int debugOneLegLowVisFrames = 0;
    private int oneLegLowVisFramesInRep = 0;

    // "천천히 움직여 주세요" 안내를 이 시각(uptime ms)까지 다른 안내 대신 표시
    private static final long MOVE_SLOWLY_HINT_MS = 2000L;
    private long moveSlowlyUntilMs = 0;
    // 운동하는 다리가 정해진 뒤 "카메라 쪽 다리로 운동해 주세요" 안내를 이 시각(uptime ms)까지 표시
    private static final long LEG_LOCKED_HINT_MS = 2000L;
    private long legLockedHintUntilMs = 0;
    // 목표 횟수를 처음 채웠을 때 "목표 횟수를 채웠어요…" 안내를 이 시각(uptime ms)까지 다른 안내 대신 표시
    // ("N회" 뒤에 이어 읽는 긴 문장이라 다음 안내가 끊지 않도록 넉넉히)
    private static final long GOAL_REACHED_HINT_MS = 6000L;
    private long goalReachedUntilMs = 0;
    // 안내 문구를 음성으로 읽을지 정함 (자세 경고는 동작마다 한 번 등)
    private final FeedbackVoiceFilter voiceFilter = new FeedbackVoiceFilter();

    // 카메라 권한 요청 결과: 허용하면 카메라 시작, 거부하면 안내 + 다시 요청 / 설정 열기 (운동 화면은 그대로 유지)
    private final ActivityResultLauncher<String> cameraPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startCamera();
                } else {
                    onCameraPermissionDenied();
                }
            });
    // startCamera를 이미 불렀으면 true (권한 결과 / 설정 화면 복귀 때 중복 시작 방지)
    private boolean cameraStarted = false;
    @Nullable
    private Snackbar cameraPermissionSnackbar;

    // 14_Pause 결과: "처음부터 다시"는 운동 상태 초기화, "운동 종료"는 PostCheck로 이동
    // (계속하기 / Back은 그대로 이어서 진행)
    private final ActivityResultLauncher<Intent> pauseLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == PauseActivity.RESULT_RESTART) {
                    restartExercise();
                } else if (result.getResultCode() == PauseActivity.RESULT_END_EXERCISE) {
                    endExercise();
                }
            });

    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            elapsedSeconds++;
            updateTimerText();
            timerHandler.postDelayed(this, TIMER_INTERVAL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 흰 배경 위에서 상태바 아이콘이 어둡게 보이도록 light 스타일 사용
        EdgeToEdge.enable(this,
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT));
        setContentView(R.layout.activity_exercise);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.exerciseRoot), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 운동 중 화면 꺼짐 방지
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        exerciseTimerText = findViewById(R.id.exerciseTimerText);
        exerciseNameText = findViewById(R.id.exerciseNameText);
        // ExerciseDetail에서 고른 운동 이름이 있으면 표시 (없으면 레이아웃 기본값 유지)
        String exerciseName = getIntent().getStringExtra(ExerciseFlowExtras.EXERCISE_NAME);
        if (exerciseName != null) {
            exerciseNameText.setText(exerciseName);
        }
        startedAtMillis = System.currentTimeMillis();

        // 운동 이름으로 자세 인식 기준을 찾고, 없으면 측정하지 않고 안내만 표시
        postureFeedbackText = findViewById(R.id.postureFeedbackText);
        voiceGuide = new VoiceGuide(this);
        // L11: 고정 키로 운동 기준을 찾고, 키가 없으면(예전 흐름) 이름으로 찾음
        String exerciseId = getIntent().getStringExtra(ExerciseFlowExtras.EXERCISE_ID);
        exerciseCriteria = exerciseId != null
                ? ExerciseCriteria.forId(exerciseId)
                : ExerciseCriteria.forExercise(exerciseNameText.getText().toString());
        if (exerciseCriteria != null) {
            repetitionCounter = createCounter(exerciseCriteria);
            pausableCounter = new PausableCounter(repetitionCounter);
            targetReps = exerciseCriteria.targetReps;
            showFeedback(R.string.feedback_full_body_visible);
        } else {
            showFeedback(R.string.feedback_pose_not_supported);
        }

        // 화면 재생성 / 시스템이 앱을 종료했다가 되살린 경우: 끝난 회차 결과, 경과 시간, 시작 시각 복구
        // (진행 중이던 1회 동작과 운동하는 다리 고정은 복구하지 않음 → 다시 정옆으로 서면 다리를 다시 고름)
        if (savedInstanceState != null) {
            restoreState(savedInstanceState);
        }

        updateTimerText();
        updateExerciseStatus();

        // 이전 화면(CameraSetup)으로 돌아가기 (버튼 / 시스템 Back): 측정 결과가 버려지므로 먼저 확인
        findViewById(R.id.backButton).setOnClickListener(v -> confirmExitWithoutSaving());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                confirmExitWithoutSaving();
            }
        });

        // 일시정지 화면(14_Pause)으로 이동: 이 화면은 그대로 두고 위에 열리므로 복귀 시 중복 생성되지 않음
        // (Pause 화면이 보이는 동안 onPause()에서 Timer가 멈추고, 복귀하면 onResume()에서 다시 시작)
        findViewById(R.id.pauseButton).setOnClickListener(v -> {
            Intent intent = new Intent(this, PauseActivity.class);
            intent.putExtra(PauseActivity.EXTRA_EXERCISE_NAME, exerciseNameText.getText().toString());
            intent.putExtra(PauseActivity.EXTRA_CURRENT_REPS, getCompletedReps());
            intent.putExtra(PauseActivity.EXTRA_TARGET_REPS, targetReps);
            intent.putExtra(PauseActivity.EXTRA_ELAPSED_SECONDS, elapsedSeconds);
            pauseLauncher.launch(intent);
        });

        findViewById(R.id.endExerciseButton).setOnClickListener(v -> endExercise());

        // 카메라 / 뼈대 표시
        cameraPreviewView = findViewById(R.id.cameraPreviewView);
        skeletonOverlayView = findViewById(R.id.skeletonOverlayView);
        cameraMessageText = findViewById(R.id.cameraMessageText);
        debugKneeAngleText = findViewById(R.id.debugKneeAngleText);
        // 카메라 영상이 둥근 모서리 배경(bg_camera_preview) 밖으로 나가지 않도록 잘라냄
        findViewById(R.id.cameraPreviewContainer).setClipToOutline(true);

        if (hasCameraPermission()) {
            startCamera();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    // 화면을 나갈 때 카메라와 MediaPipe 정리 (배터리 / 메모리 누수 방지)
    // 화면이 가려질 때(onStop)는 bindToLifecycle 덕분에 CameraX가 알아서 카메라를 멈춘다.
    @Override
    protected void onDestroy() {
        super.onDestroy();
        voiceGuide.shutdown();
        // L8: 분석기를 먼저 떼어 새 프레임이 분석 스레드로 넘어오지 않게 한 뒤 카메라 해제 → landmarker 닫기 → 스레드 종료
        if (imageAnalysis != null) {
            imageAnalysis.clearAnalyzer();
            imageAnalysis = null;
        }
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
            cameraProvider = null;
        }
        if (analysisExecutor != null) {
            // landmarker는 분석 스레드에서 닫아야 처리 중인 프레임과 겹치지 않는다.
            // 대기 중인 작업 뒤에 close를 넣고, 끝나면 스레드도 종료
            PoseLandmarkerHelper helper = poseLandmarkerHelper;
            analysisExecutor.execute(() -> {
                if (helper != null) {
                    helper.close();
                }
            });
            analysisExecutor.shutdown();
            analysisExecutor = null;
        } else if (poseLandmarkerHelper != null) {
            poseLandmarkerHelper.close();
        }
        poseLandmarkerHelper = null;
    }

    private static final String STATE_ELAPSED_SECONDS = "state_elapsed_seconds";
    private static final String STATE_STARTED_AT_MILLIS = "state_started_at_millis";
    private static final String STATE_REP_RESULTS = "state_rep_results";

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_ELAPSED_SECONDS, elapsedSeconds);
        outState.putLong(STATE_STARTED_AT_MILLIS, startedAtMillis);
        if (repetitionCounter != null) {
            outState.putDoubleArray(STATE_REP_RESULTS,
                    RepetitionCounter.RepResult.toArray(repetitionCounter.getResults()));
        }
    }

    private void restoreState(Bundle savedInstanceState) {
        elapsedSeconds = savedInstanceState.getInt(STATE_ELAPSED_SECONDS, 0);
        startedAtMillis = savedInstanceState.getLong(STATE_STARTED_AT_MILLIS, startedAtMillis);
        if (repetitionCounter != null) {
            repetitionCounter.restoreResults(RepetitionCounter.RepResult.listFromArray(
                    savedInstanceState.getDoubleArray(STATE_REP_RESULTS)));
        }
    }

    // H3: "운동 기록이 저장되지 않아요. 나갈까요?" → 나가기를 누르면 현재 화면만 종료
    // 창이 떠 있는 동안은 일시정지 버튼처럼 타이머 / 자세 분석 / 음성 안내를 멈추고,
    // "취소"(또는 창 밖 터치 / Back)로 닫히면 이어서 진행한다.
    private void confirmExitWithoutSaving() {
        if (exitDialogShowing) {
            return;
        }
        pauseForExitDialog();
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setMessage(R.string.exit_without_saving_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.exit_without_saving_confirm, (d, which) -> finish())
                .create();
        dialog.setOnDismissListener(d -> {
            if (!isFinishing()) {
                resumeAfterExitDialog();
            }
        });
        dialog.show();
    }

    private void pauseForExitDialog() {
        exitDialogShowing = true;
        if (pausableCounter != null) {
            pausableCounter.pause();
        }
        timerHandler.removeCallbacks(timerRunnable);
        voiceGuide.stop();
        if (DEBUG_LOG_REPS) {
            Log.d(DEBUG_TAG, String.format(Locale.US, "[%s] exit dialog: paused", debugExerciseId()));
        }
    }

    // 진행 중이던 1회 동작은 버리고(끝난 회차는 유지) 다시 편 상태부터 측정
    private void resumeAfterExitDialog() {
        exitDialogShowing = false;
        if (pausableCounter != null) {
            pausableCounter.resume();
        }
        timerHandler.removeCallbacks(timerRunnable);
        timerHandler.postDelayed(timerRunnable, TIMER_INTERVAL_MS);
        if (DEBUG_LOG_REPS) {
            Log.d(DEBUG_TAG, String.format(Locale.US, "[%s] exit dialog: resumed", debugExerciseId()));
        }
    }

    // 운동 방식에 맞는 측정기 (횟수형: RepetitionCounter, 유지 시간형: HoldCounter)
    private static ExerciseCounter createCounter(ExerciseCriteria criteria) {
        return criteria.isHoldType() ? new HoldCounter(criteria) : new RepetitionCounter(criteria);
    }

    // CameraX 시작: 미리보기(Preview) + MediaPipe 분석(ImageAnalysis)을 이 화면의 Lifecycle에 연결
    private void startCamera() {
        if (cameraStarted) {
            return;
        }
        cameraStarted = true;
        if (cameraPermissionSnackbar != null) {
            cameraPermissionSnackbar.dismiss();
            cameraPermissionSnackbar = null;
        }
        cameraMessageText.setOnClickListener(null);
        cameraMessageText.setVisibility(View.GONE);

        // L9: 모델(약 5.7MB)은 분석 전용 스레드에서 불러오고, 그동안 카메라 미리보기는 먼저 보여준다.
        // 다 불러오면 메인 스레드에서 분석(ImageAnalysis)을 붙인다. 실패해도 미리보기는 유지 (뼈대만 표시하지 않음)
        analysisExecutor = Executors.newSingleThreadExecutor();
        Context appContext = getApplicationContext();
        analysisExecutor.execute(() -> {
            PoseLandmarkerHelper loaded;
            try {
                loaded = new PoseLandmarkerHelper(appContext, poseListener);
            } catch (RuntimeException e) {
                Log.e(TAG, "Pose Landmarker 생성 실패", e);
                loaded = null;
            }
            PoseLandmarkerHelper helper = loaded;
            runOnUiThread(() -> onPoseModelLoaded(helper));
        });

        ListenableFuture<ProcessCameraProvider> providerFuture = ProcessCameraProvider.getInstance(this);
        providerFuture.addListener(() -> {
            // 카메라 준비 전에 화면을 나간 경우 바인딩하지 않음
            if (isFinishing() || isDestroyed()) {
                return;
            }
            try {
                cameraProvider = providerFuture.get();
                bindCameraUseCases(cameraProvider);
            } catch (Exception e) {
                // 전면 카메라가 없거나 카메라를 다른 앱이 사용 중인 경우 등
                Log.e(TAG, "카메라 시작 실패", e);
                showCameraMessage(R.string.camera_start_failed_message);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    // 메인 스레드: 모델 로드가 끝난 뒤 분석을 카메라에 연결 (카메라가 아직 준비 전이면 준비될 때 함께 연결됨)
    private void onPoseModelLoaded(@Nullable PoseLandmarkerHelper helper) {
        if (isDestroyed()) {
            // 화면이 먼저 닫힘: 분석 스레드는 이미 종료됐으므로 여기서 닫음 (프레임을 받은 적 없음)
            if (helper != null) {
                helper.close();
            }
            return;
        }
        if (helper == null) {
            showCameraMessage(R.string.pose_model_load_failed_message);
            return;
        }
        poseLandmarkerHelper = helper;
        if (cameraProvider != null) {
            try {
                bindCameraUseCases(cameraProvider);
            } catch (RuntimeException e) {
                Log.e(TAG, "자세 분석 연결 실패", e);
                showCameraMessage(R.string.camera_start_failed_message);
            }
        }
    }

    private void bindCameraUseCases(ProcessCameraProvider provider) {
        // Preview와 ImageAnalysis 모두 4:3 비율로 통일 (비율이 다르면 영상과 뼈대 위치가 어긋남)
        AspectRatioStrategy aspectRatio = AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY;

        Preview preview = new Preview.Builder()
                .setResolutionSelector(new ResolutionSelector.Builder()
                        .setAspectRatioStrategy(aspectRatio)
                        .build())
                .build();
        preview.setSurfaceProvider(cameraPreviewView.getSurfaceProvider());

        CameraSelector cameraSelector = USE_FRONT_CAMERA
                ? CameraSelector.DEFAULT_FRONT_CAMERA
                : CameraSelector.DEFAULT_BACK_CAMERA;

        provider.unbindAll();
        if (poseLandmarkerHelper == null || analysisExecutor == null) {
            provider.bindToLifecycle(this, cameraSelector, preview);
            return;
        }

        // 분석은 640x480 정도면 충분 (해상도가 높을수록 MediaPipe 처리 시간과 배터리 소모 증가)
        // 처리 중에 들어온 프레임은 버리고 가장 최근 프레임만 분석 (지연 누적 방지)
        imageAnalysis = new ImageAnalysis.Builder()
                .setResolutionSelector(new ResolutionSelector.Builder()
                        .setAspectRatioStrategy(aspectRatio)
                        .setResolutionStrategy(new ResolutionStrategy(ANALYSIS_TARGET_SIZE,
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                        .build())
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build();
        PoseLandmarkerHelper helper = poseLandmarkerHelper;
        imageAnalysis.setAnalyzer(analysisExecutor, helper::detectLiveStream);

        provider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
    }

    // MediaPipe 결과 / 오류 (MediaPipe 내부 스레드에서 호출됨)
    private final PoseLandmarkerHelper.Listener poseListener = new PoseLandmarkerHelper.Listener() {
        @Override
        public void onPoseResult(PoseLandmarkerResult result, int imageWidth, int imageHeight) {
            // M1: 결과 처리 중 예외가 나면 그 프레임만 건너뜀 (MediaPipe 스레드에서 예외가 새면 앱이 종료됨)
            try {
                handlePoseResult(result, imageWidth, imageHeight);
            } catch (RuntimeException e) {
                Log.w(TAG, "자세 결과 처리 실패, 이 프레임은 건너뜀", e);
            }
        }

        private void handlePoseResult(PoseLandmarkerResult result, int imageWidth, int imageHeight) {
            List<NormalizedLandmark> pose = result.landmarks().isEmpty() ? null : result.landmarks().get(0);

            // Java 관절 각도 계산 (무릎 굽힘 운동 기준이 없어도 디버그 표시용으로 무릎 각도는 계산)
            ExerciseCriteria criteria = exerciseCriteria != null ? exerciseCriteria : ExerciseCriteria.KNEE_FLEXION;
            boolean judgeHip = criteria.judgedJoint == ExerciseCriteria.JudgedJoint.HIP;
            LegSelector.LegSample left = PoseMeasurer.measureSide(pose, criteria.leftSide, judgeHip, imageWidth, imageHeight);
            LegSelector.LegSample right = PoseMeasurer.measureSide(pose, criteria.rightSide, judgeHip, imageWidth, imageHeight);
            long timestampMs = result.timestampMs();

            runOnUiThread(() -> {
                if (isDestroyed()) {
                    return;
                }
                skeletonOverlayView.setPose(pose, imageWidth, imageHeight, USE_FRONT_CAMERA);
                LegSelector.LegSample chosen = countRepetition(timestampMs, left, right);
                countDebugFrame(pose == null, chosen == null, left.isCandidate() != right.isCandidate());
                showDebugKneeAngles(left.jointAngle, right.jointAngle,
                        left.minVisibility, right.minVisibility, chosen);
            });
        }

        @Override
        public void onPoseError(RuntimeException error) {
            Log.e(TAG, "Pose Landmarker 오류", error);
        }
    };

    // 반복 횟수 측정 (UI 스레드)
    // 1) 운동하는 다리 정하기: 정옆으로 서서 두 다리의 visibility 차이가 0.2 이상인 상태가 1초 이어지면
    //    더 잘 보이는 다리(카메라 쪽)로 정한다. 그동안은 세지 않음 (LegSelector.observeForLock)
    // 2) 그 뒤로는 정해진 다리만 판정 (카메라에서 먼 다리는 가려져 값이 튀기 쉬우므로 쓰지 않음)
    //    정해진 다리가 안 보이면 NaN → RepetitionCounter가 1초 넘게 이어질 때 "전신이 보이게" 안내
    // 평균(흔들림 완화)은 고른 뒤의 각도에 RepetitionCounter가 적용한다.
    // 반환값: 이번 프레임에 고른 다리 (디버그 표시용, 측정하지 않거나 후보가 없으면 null)
    @Nullable
    private LegSelector.LegSample countRepetition(long timestampMs, LegSelector.LegSample left,
                                                  LegSelector.LegSample right) {
        if (repetitionCounter == null || pausableCounter == null || exerciseCriteria == null) {
            return null;
        }
        // "나갈까요?" 확인 창이 떠 있는 동안: 측정 / 안내 모두 멈춤
        if (pausableCounter.isPaused()) {
            return null;
        }
        if (!legSelector.isLocked()) {
            if (!legSelector.observeForLock(left, right, timestampMs, exerciseCriteria.lockMinAngle)) {
                // 다리를 정하는 중: 사람(다리)이 보이면 정옆으로 서도록(앉는 운동은 앉도록), 아니면 전신이 보이도록 안내
                showFeedback(left.isCandidate() || right.isCandidate()
                        ? FeedbackMessages.sideways(exerciseCriteria)
                        : R.string.feedback_full_body_visible);
                return null;
            }
            legLockedHintUntilMs = SystemClock.uptimeMillis() + LEG_LOCKED_HINT_MS;
            logLegLocked();
        }
        LegSelector.LegSample chosen = legSelector.selectLocked(left, right);
        // 디버그용: 한쪽 다리만 visibility 부족이었던 프레임 수 (동작 단위)
        if (repetitionCounter.isRepInProgress() && left.isCandidate() != right.isCandidate()) {
            oneLegLowVisFramesInRep++;
        }
        RepetitionCounter.RepResult rep;
        if (chosen == null) {
            // 정해진 다리가 잘 안 보임 → 이 프레임은 건너뜀 (1초 넘게 이어지면 카운터가 "전신이 보이게" 안내)
            rep = pausableCounter.onFrame(timestampMs, Double.NaN, Double.NaN, Double.NaN);
        } else {
            // 보조 각도: 횟수형은 엉덩이 각도(엉덩이 규칙), 유지형은 펴야 하는 다리의 무릎 각도
            double auxAngle = exerciseCriteria.isHoldType() ? chosen.kneeAngle : chosen.hipAngle;
            rep = pausableCounter.onFrame(timestampMs, chosen.jointAngle, chosen.trunkLean, auxAngle);
        }
        if (rep != null) {
            logRepResult(rep);
            updateExerciseStatus();
            // 횟수는 안내 문구보다 먼저 (이 뒤에 바뀌는 안내 문구는 횟수 다음에 읽힘)
            voiceGuide.speakCount(getString(R.string.voice_rep_count, repetitionCounter.getCompletedReps()));
            // 목표 횟수를 처음 채운 회차: "N회" 다음에 이어서 목표 도달 안내 (횟수는 계속 세고 자동 종료하지 않음)
            if (RepGoal.isJustReached(repetitionCounter.getCompletedReps(), targetReps)) {
                goalReachedUntilMs = SystemClock.uptimeMillis() + GOAL_REACHED_HINT_MS;
            }
        }
        RepetitionCounter.RejectedAttempt rejected = repetitionCounter.takeRejectedAttempt();
        if (rejected != null) {
            logRejectedAttempt(rejected);
        }
        // 동작이 너무 빨랐거나 동작 중 건너뛴 프레임이 많았으면 잠시 "천천히 움직여 주세요"
        long now = SystemClock.uptimeMillis();
        if (repetitionCounter.takeMoveSlowlyHint()) {
            moveSlowlyUntilMs = now + MOVE_SLOWLY_HINT_MS;
        }
        // L↔R 전환 횟수 / 한쪽 다리만 안 보인 프레임 수는 동작 하나 단위로 기록 (서 있는 동안은 세지 않음)
        if (!repetitionCounter.isRepInProgress()) {
            legSelector.resetSwitchCount();
            oneLegLowVisFramesInRep = 0;
            voiceFilter.onMotionEnded();
        }
        RepetitionCounter.Feedback feedback = repetitionCounter.getFeedback();
        if (now < legLockedHintUntilMs && feedback != RepetitionCounter.Feedback.NOT_VISIBLE) {
            // 다리가 정해진 직후 2초: 운동별 첫 안내 (무릎 굽힘: "카메라 쪽 다리로 운동해 주세요.")
            // (다리가 안 보이면 "전신이 보이게"가 우선)
            // 왼쪽/오른쪽은 표시하지 않음 (옆모습에서는 MediaPipe의 L/R 이름이 실제 다리와 다를 수 있음)
            showFeedback(FeedbackMessages.legLocked(exerciseCriteria));
        } else if (now < goalReachedUntilMs) {
            showFeedback(R.string.feedback_goal_reached);
        } else {
            showFeedback(now < moveSlowlyUntilMs
                    ? R.string.feedback_move_slowly
                    : FeedbackMessages.forFeedback(exerciseCriteria, feedback, getCompletedReps()));
        }
        return chosen;
    }

    // 디버그용: 로그에 붙일 운동 키 (기준이 없는 운동이면 "-")
    private String debugExerciseId() {
        return exerciseCriteria == null ? "-" : exerciseCriteria.id;
    }

    // 디버그용: 판정에 쓰는 관절 이름 (로그의 각도 이름)
    private String debugJointName() {
        return exerciseCriteria != null && exerciseCriteria.judgedJoint == ExerciseCriteria.JudgedJoint.HIP
                ? JointKeys.HIP : JointKeys.KNEE;
    }

    // 디버그용: 유지형이면 1초 기록 끝에 고른 다리의 무릎 각도와 지금까지 유지한 시간을 붙임
    private String debugHoldSuffix(@Nullable LegSelector.LegSample chosen) {
        if (!(repetitionCounter instanceof HoldCounter)) {
            return "";
        }
        return String.format(Locale.US, " knee=%s held=%.1fs",
                chosen == null ? "-" : formatAngle(chosen.kneeAngle),
                ((HoldCounter) repetitionCounter).getHeldMs() / 1000.0);
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    // 디버그용: 운동하는 다리가 정해졌을 때 다리별 평균 visibility 기록
    private void logLegLocked() {
        if (!DEBUG_LOG_REPS) {
            return;
        }
        Log.d(DEBUG_TAG, String.format(Locale.US, "[%s] leg locked=%s avgVis L=%.2f R=%.2f",
                debugExerciseId(), legSelector.getLockedLabel(), legSelector.getLockedLeftAverage(),
                legSelector.getLockedRightAverage()));
    }

    // 같은 문구면 다시 그리지 않음 (매 프레임 호출됨)
    // 유지형의 "유지하세요" 문구는 유지한 시간 / 목표 시간(초)을 넣어 표시
    private void showFeedback(@StringRes int messageRes) {
        String text;
        if (messageRes == R.string.feedback_stretch_hold && repetitionCounter instanceof HoldCounter
                && exerciseCriteria != null) {
            text = getString(messageRes, (int) (((HoldCounter) repetitionCounter).getHeldMs() / 1000),
                    (int) (exerciseCriteria.holdDurationMs / 1000));
        } else {
            text = getString(messageRes);
        }
        if (text.equals(shownFeedbackText)) {
            return;
        }
        shownFeedbackText = text;
        postureFeedbackText.setText(text);
        if (messageRes != shownFeedbackRes) {
            shownFeedbackRes = messageRes;
            if (DEBUG_LOG_REPS) {
                Log.d(DEBUG_TAG, String.format(Locale.US, "[%s] feedback=\"%s\"", debugExerciseId(), text));
            }
            // 일어서는 중 안내는 화면에만, 자세 경고는 동작마다 한 번, 경고가 풀려 돌아온 문구는 다시 읽지 않음
            if (voiceFilter.shouldSpeak(messageRes)) {
                voiceGuide.speakMessage(VoiceGuide.toSpeechText(text));
            }
        }
    }

    // 디버그용: 회차별 결과를 Logcat에 기록 (기준값 조정용, DEBUG_LOG_REPS로 끔)
    // (leg = 마지막으로 고른 다리 이름, switches = 이번 동작 중 고른 다리가 L↔R로 바뀐 횟수)
    private void logRepResult(RepetitionCounter.RepResult rep) {
        if (!DEBUG_LOG_REPS) {
            return;
        }
        if (repetitionCounter instanceof HoldCounter) {
            // 유지형: avg = 유지 중 평균 판정 각도, minKnee = 유지 중 가장 작았던 편 다리 무릎 각도, held = 유지 시간
            Log.d(DEBUG_TAG, String.format(Locale.US,
                    "[%s] hold #%d leg=%s avg%s=%.1f %sErr=%+.1f minKnee=%s acc=%.0f held=%dms %s",
                    debugExerciseId(), rep.repNumber, legSelector.getLastLabel(),
                    capitalize(debugJointName()), rep.minJointAngle, debugJointName(), rep.jointError,
                    formatAngle(((HoldCounter) repetitionCounter).getLastHoldMinKneeAngle()),
                    rep.accuracy, rep.durationMs, formatFrames(rep.frames)));
            return;
        }
        Log.d(DEBUG_TAG, String.format(Locale.US,
                "[%s] rep #%d leg=%s switches=%d min%s=%.1f %sErr=%+.1f minHip=%s maxTrunk=%s backErr=%s acc=%.0f dur=%dms %s",
                debugExerciseId(), rep.repNumber, legSelector.getLastLabel(), legSelector.getSwitchCount(),
                capitalize(debugJointName()), rep.minJointAngle, debugJointName(), rep.jointError,
                formatAngle(rep.minHipAngle),
                formatAngle(rep.maxTrunkLean), formatAngle(rep.trunkError), rep.accuracy, rep.durationMs,
                formatFrames(rep.frames)));
    }

    // frames=판정에 쓴/연속성 검사로 건너뛴/안 보여서 건너뛴 프레임 수, missed=건너뛴 비율,
    // oneLeg=동작 중 한쪽 다리만 visibility 부족이었던 프레임 수
    private String formatFrames(RepetitionCounter.FrameStats frames) {
        return String.format(Locale.US, "frames=%d/%d/%d missed=%.0f%% oneLeg=%d",
                frames.accepted, frames.skippedByJump, frames.invisible, frames.missedRatio() * 100,
                oneLegLowVisFramesInRep);
    }

    // 디버그용: 세지 않고 버린 동작과 이유를 Logcat에 기록 (기준값 조정용, DEBUG_LOG_REPS로 끔)
    private void logRejectedAttempt(RepetitionCounter.RejectedAttempt attempt) {
        if (!DEBUG_LOG_REPS) {
            return;
        }
        if (repetitionCounter instanceof HoldCounter) {
            Log.d(DEBUG_TAG, String.format(Locale.US,
                    "[%s] hold rejected reason=%s leg=%s avg%s=%.1f held=%dms %s",
                    debugExerciseId(), attempt.reason, legSelector.getLastLabel(),
                    capitalize(debugJointName()), attempt.minJointAngle, attempt.durationMs,
                    formatFrames(attempt.frames)));
            return;
        }
        Log.d(DEBUG_TAG, String.format(Locale.US,
                "[%s] rejected reason=%s leg=%s switches=%d min%s=%.1f minHip=%s maxTrunk=%s dur=%dms %s",
                debugExerciseId(), attempt.reason, legSelector.getLastLabel(), legSelector.getSwitchCount(),
                capitalize(debugJointName()),
                attempt.minJointAngle, formatAngle(attempt.minHipAngle),
                formatAngle(attempt.maxTrunkLean), attempt.durationMs, formatFrames(attempt.frames)));
    }

    // 디버그용: 1초 기록에 쓸 프레임 수 집계 (UI 스레드)
    private void countDebugFrame(boolean noPose, boolean noCandidateLeg, boolean oneLegLowVis) {
        debugFrameCount++;
        if (noPose) {
            debugNoPoseFrames++;
        } else if (noCandidateLeg) {
            debugLowVisibilityFrames++;
        } else if (oneLegLowVis) {
            debugOneLegLowVisFrames++;
        }
    }

    // 디버그용: 무릎 각도를 화면 구석에 표시(DEBUG_SHOW_KNEE_ANGLE)하고 Logcat에 1초에 한 번 기록(DEBUG_LOG_KNEE_ANGLE)
    // sel = 이번 프레임에 판정에 쓴 다리와 각도
    // fps = 지난 기록 이후 초당 MediaPipe 결과 수
    // noPose = 사람 인식 안 됨, lowVis = 두 다리 모두 visibility 0.5 미만,
    // oneLeg = 한쪽 다리만 visibility 0.5 미만, jump = 연속성 검사로 건너뜀 (지난 기록 이후 프레임 수)
    // vis = 그 순간 다리별 엉덩이 / 무릎 / 발목 visibility 중 가장 낮은 값 (높은 쪽이 카메라 쪽 다리)
    private void showDebugKneeAngles(double leftKneeAngle, double rightKneeAngle,
                                     float leftVisibility, float rightVisibility,
                                     @Nullable LegSelector.LegSample chosen) {
        String left = formatAngle(leftKneeAngle);
        String right = formatAngle(rightKneeAngle);
        if (DEBUG_SHOW_KNEE_ANGLE) {
            debugKneeAngleText.setVisibility(View.VISIBLE);
            debugKneeAngleText.setText(getString(R.string.debug_knee_angle_format, left, right));
        }
        if (!DEBUG_LOG_KNEE_ANGLE) {
            return;
        }

        long now = SystemClock.uptimeMillis();
        long elapsedMs = now - lastDebugLogAtMs;
        if (elapsedMs >= DEBUG_LOG_INTERVAL_MS) {
            String selected = chosen == null ? "-" : chosen.label + ":" + formatAngle(chosen.jointAngle);
            int skippedTotal = repetitionCounter == null ? 0 : repetitionCounter.getSkippedFrameCount();
            int jumpSinceLastLog = skippedTotal - lastLoggedSkippedFrames;
            double fps = lastDebugLogAtMs == 0 ? 0 : debugFrameCount * 1000.0 / elapsedMs;
            Log.d(DEBUG_TAG, String.format(Locale.US,
                    "[%s] %s L=%s R=%s vis L=%.2f R=%.2f sel=%s fps=%.1f frames=%d noPose=%d lowVis=%d oneLeg=%d jump=%d%s",
                    debugExerciseId(), debugJointName(), left, right, leftVisibility, rightVisibility, selected, fps, debugFrameCount,
                    debugNoPoseFrames, debugLowVisibilityFrames, debugOneLegLowVisFrames, jumpSinceLastLog,
                    debugHoldSuffix(chosen)));
            lastDebugLogAtMs = now;
            lastLoggedSkippedFrames = skippedTotal;
            debugFrameCount = 0;
            debugNoPoseFrames = 0;
            debugLowVisibilityFrames = 0;
            debugOneLegLowVisFrames = 0;
        }
    }

    private String formatAngle(double angle) {
        return Double.isNaN(angle)
                ? getString(R.string.debug_angle_unknown)
                : String.format(Locale.US, "%.1f", angle);
    }

    // L1: 권한 거부 → 다시 물을 수 있으면 "다시 요청", "다시 묻지 않음" 상태면 "설정 열기"
    // (설정에서 허용하고 돌아오면 onResume에서 카메라 시작)
    private void onCameraPermissionDenied() {
        showCameraMessage(R.string.camera_permission_denied_message);
        boolean canAskAgain = shouldShowRequestPermissionRationale(Manifest.permission.CAMERA);
        View.OnClickListener action = v -> {
            if (canAskAgain) {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
            } else {
                openAppSettings();
            }
        };
        cameraMessageText.setOnClickListener(action);
        cameraPermissionSnackbar = Snackbar.make(findViewById(R.id.exerciseRoot),
                        R.string.camera_permission_snackbar, Snackbar.LENGTH_INDEFINITE)
                .setAction(canAskAgain ? R.string.camera_permission_retry : R.string.camera_permission_open_settings,
                        action);
        cameraPermissionSnackbar.show();
    }

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null));
        startActivity(intent);
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void showCameraMessage(@StringRes int messageRes) {
        cameraMessageText.setText(messageRes);
        cameraMessageText.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // L1: 설정 화면에서 카메라 권한을 허용하고 돌아온 경우
        if (!cameraStarted && hasCameraPermission()) {
            startCamera();
        }
        // "나갈까요?" 창이 떠 있는 채로 돌아온 경우(홈에 다녀옴 등)는 창이 닫힐 때까지 멈춘 상태 유지
        if (exitDialogShowing) {
            return;
        }
        timerHandler.postDelayed(timerRunnable, TIMER_INTERVAL_MS);
        // 일시정지 화면 등에서 돌아오면 지금 안내를 다시 읽음
        if (shownFeedbackText != null && !FeedbackMessages.isScreenOnly(shownFeedbackRes)) {
            voiceGuide.speakMessage(VoiceGuide.toSpeechText(shownFeedbackText));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        timerHandler.removeCallbacks(timerRunnable);
        voiceGuide.stop();
    }

    // 운동 후 컨디션 체크(15_PostCheck)로 이동하고 운동 화면은 종료 (Back Stack에 남기지 않음)
    // 운동 결과는 저장하지 않고 넘기기만 함 (PostCheck "완료" 시 한 번에 저장)
    private void endExercise() {
        Intent intent = new Intent(this, PostCheckActivity.class);
        intent.putExtras(getIntent());
        intent.putExtra(ExerciseFlowExtras.EXERCISE_NAME, exerciseNameText.getText().toString());
        intent.putExtra(ExerciseFlowExtras.STARTED_AT_MILLIS, startedAtMillis);
        intent.putExtra(ExerciseFlowExtras.ENDED_AT_MILLIS, System.currentTimeMillis());
        // 운동 시간은 Timer로 센 실제 진행 시간 (일시정지 화면이 떠 있던 시간은 제외)
        intent.putExtra(ExerciseFlowExtras.DURATION_SECONDS, elapsedSeconds);
        // 자세 인식으로 측정한 값. 측정값이 없으면(0회 / 미지원 운동) 키를 넣지 않음 → 저장 시 null
        SessionMetrics metrics = currentMetrics();
        intent.putExtra(ExerciseFlowExtras.COMPLETED_REPS, metrics.repetitions);
        intent.putExtra(ExerciseFlowExtras.TARGET_REPS, targetReps);
        putOptionalInt(intent, ExerciseFlowExtras.AVERAGE_ACCURACY, SessionMetrics.round(metrics.averageAccuracy));
        putOptionalInt(intent, ExerciseFlowExtras.FIRST_HALF_ACCURACY, SessionMetrics.round(metrics.firstHalfAccuracy));
        putOptionalInt(intent, ExerciseFlowExtras.SECOND_HALF_ACCURACY, SessionMetrics.round(metrics.secondHalfAccuracy));
        // 판정 관절 오차: 무릎을 판정하는 운동은 knee, 엉덩이를 판정하는 운동(하체 스트레칭)은 hip으로 저장
        boolean judgeHip = exerciseCriteria != null
                && exerciseCriteria.judgedJoint == ExerciseCriteria.JudgedJoint.HIP;
        Integer jointError = SessionMetrics.round(metrics.jointError);
        putOptionalInt(intent, ExerciseFlowExtras.KNEE_ERROR_DEGREES, judgeHip ? null : jointError);
        putOptionalInt(intent, ExerciseFlowExtras.HIP_ERROR_DEGREES, judgeHip ? jointError : null);
        putOptionalInt(intent, ExerciseFlowExtras.BACK_ERROR_DEGREES, SessionMetrics.round(metrics.trunkError));
        // H2: 운동이 끝나는 순간 측정 결과를 휴대폰에 임시 저장 (서버 저장에 성공하면 지움)
        PendingWorkout pending = ExerciseFlowExtras.toPendingWorkout(new SessionManager(this).getUserId(), intent);
        PendingWorkoutStore.create(this).save(pending);
        intent.putExtra(ExerciseFlowExtras.PENDING_SESSION_ID, pending.sessionId);
        startActivity(intent);
        finish();
    }

    // 이전 화면에서 같은 키가 넘어왔더라도 이번 측정값만 남기도록 먼저 지움
    private static void putOptionalInt(Intent intent, String key, @Nullable Integer value) {
        intent.removeExtra(key);
        if (value != null) {
            intent.putExtra(key, value.intValue());
        }
    }

    private SessionMetrics currentMetrics() {
        return SessionMetrics.from(repetitionCounter == null
                ? Collections.emptyList() : repetitionCounter.getResults());
    }

    private int getCompletedReps() {
        return repetitionCounter == null ? 0 : repetitionCounter.getCompletedReps();
    }

    // 반복 횟수 / 진행률 / 운동 시간을 0으로 초기화 (Timer는 화면 복귀 시 onResume()에서 다시 시작)
    private void restartExercise() {
        if (repetitionCounter != null) {
            repetitionCounter.reset();
            showFeedback(R.string.feedback_full_body_visible);
        }
        // 운동하는 다리도 처음부터 다시 정함
        legSelector.reset();
        lastLoggedSkippedFrames = 0;
        moveSlowlyUntilMs = 0;
        legLockedHintUntilMs = 0;
        goalReachedUntilMs = 0;
        elapsedSeconds = 0;
        startedAtMillis = System.currentTimeMillis();
        updateTimerText();
        updateExerciseStatus();
    }

    private void updateTimerText() {
        exerciseTimerText.setText(getString(R.string.exercise_timer_format,
                elapsedSeconds / 60, elapsedSeconds % 60));
    }

    // 반복 횟수 / 정확도 / 진행률 표시 (1회가 끝날 때마다 호출)
    private void updateExerciseStatus() {
        TextView repetitionCountText = findViewById(R.id.repetitionCountText);
        TextView accuracyText = findViewById(R.id.accuracyText);
        TextView exerciseProgressPercentText = findViewById(R.id.exerciseProgressPercentText);
        LinearProgressIndicator exerciseProgressBar = findViewById(R.id.exerciseProgressBar);

        SessionMetrics metrics = currentMetrics();
        int completedReps = metrics.repetitions;
        // 진행률은 목표 횟수를 넘어도 100%까지만
        int progressPercent = targetReps > 0 ? Math.min(100, completedReps * 100 / targetReps) : 0;
        Integer averageAccuracy = SessionMetrics.round(metrics.averageAccuracy);

        repetitionCountText.setText(getString(R.string.repetition_count_format, completedReps, targetReps));
        // 첫 회 전 / 미지원 운동은 "-"
        accuracyText.setText(averageAccuracy == null ? getString(R.string.empty_value)
                : getString(R.string.percent_format, averageAccuracy));
        exerciseProgressPercentText.setText(getString(R.string.percent_format, progressPercent));
        exerciseProgressBar.setProgress(progressPercent);
    }
}

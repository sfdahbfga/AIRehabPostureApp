package com.example.airehabpostureapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
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
import com.example.airehabpostureapp.pose.HoldCounter;
import com.example.airehabpostureapp.pose.JointAngleCalculator;
import com.example.airehabpostureapp.pose.LegSelector;
import com.example.airehabpostureapp.pose.PoseLandmarkerHelper;
import com.example.airehabpostureapp.pose.PoseOverlayView;
import com.example.airehabpostureapp.pose.RepetitionCounter;
import com.example.airehabpostureapp.pose.SessionMetrics;
import com.example.airehabpostureapp.workout.PendingWorkout;
import com.example.airehabpostureapp.workout.PendingWorkoutStore;
import com.google.android.material.progressindicator.LinearProgressIndicator;
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
    // DEBUG_SHOW_KNEE_ANGLE: 화면 구석 표시 (꺼 둠, 측정할 때 true로 켬)
    // DEBUG_LOG_KNEE_ANGLE: Logcat 1초 기록 (각도 / visibility / fps / 건너뛴 프레임 수)
    private static final boolean DEBUG_SHOW_KNEE_ANGLE = false;
    private static final boolean DEBUG_LOG_KNEE_ANGLE = true;
    // 디버그용: 1회가 끝날 때마다 회차 / 가장 많이 굽힌 각도 / 정확도를 Logcat(태그 PoseDebug)에 기록.
    // 기준값 조정이 끝나면 false로 끈다.
    private static final boolean DEBUG_LOG_REPS = true;
    private static final String DEBUG_TAG = "PoseDebug";
    private static final long DEBUG_LOG_INTERVAL_MS = 1000L;

    private static final String TAG = "ExerciseActivity";

    // 기본은 전면 카메라. 전면은 미리보기가 거울처럼 반전되므로 뼈대도 반전해서 그린다.
    private static final boolean USE_FRONT_CAMERA = true;

    // MediaPipe 분석용 해상도 (센서 기준 가로 x 세로). 미리보기와 같은 4:3 비율이어야 뼈대가 정확히 겹친다.
    private static final Size ANALYSIS_TARGET_SIZE = new Size(640, 480);

    // 상체 기울기 계산용: 엉덩이 바로 위 가상의 점까지 거리 (픽셀, 방향만 쓰므로 값은 상관없음)
    private static final double VERTICAL_REFERENCE_PX = 100;

    // 목표 횟수: 운동 상세의 권장 횟수 중 1세트 기준 (운동별 값은 ExerciseCriteria.targetReps)
    private int targetReps = ExerciseCriteria.KNEE_FLEXION.targetReps;

    // 자세 인식 기준 / 반복 횟수 측정 (아직 기준이 없는 운동이면 null → 측정하지 않음)
    @Nullable
    private ExerciseCriteria exerciseCriteria;
    // 횟수형 / 유지 시간형 측정기 (운동별로 createCounter에서 고름)
    @Nullable
    private ExerciseCounter repetitionCounter;
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

    // 카메라 권한 요청 결과: 허용하면 카메라 시작, 거부하면 안내 문구만 표시 (운동 화면은 그대로 유지)
    private final ActivityResultLauncher<String> cameraPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startCamera();
                } else {
                    showCameraMessage(R.string.camera_permission_denied_message);
                }
            });

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
        exerciseCriteria =ExerciseCriteria.forExercise(exerciseNameText.getText().toString());
        if (exerciseCriteria != null) {
            repetitionCounter = createCounter(exerciseCriteria);
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

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
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
    private void confirmExitWithoutSaving() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.exit_without_saving_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.exit_without_saving_confirm, (dialog, which) -> finish())
                .show();
    }

    // 운동 방식에 맞는 측정기 (횟수형: RepetitionCounter, 유지 시간형: HoldCounter)
    private static ExerciseCounter createCounter(ExerciseCriteria criteria) {
        return criteria.isHoldType() ? new HoldCounter(criteria) : new RepetitionCounter(criteria);
    }

    // CameraX 시작: 미리보기(Preview) + MediaPipe 분석(ImageAnalysis)을 이 화면의 Lifecycle에 연결
    private void startCamera() {
        cameraMessageText.setVisibility(View.GONE);

        // 모델 로드 실패 시에도 카메라 미리보기는 보여준다 (뼈대만 표시하지 않음)
        try {
            poseLandmarkerHelper = new PoseLandmarkerHelper(this, poseListener);
        } catch (RuntimeException e) {
            Log.e(TAG, "Pose Landmarker 생성 실패", e);
            poseLandmarkerHelper = null;
            showCameraMessage(R.string.pose_model_load_failed_message);
        }
        if (poseLandmarkerHelper != null) {
            analysisExecutor = Executors.newSingleThreadExecutor();
        }

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
        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
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
            List<NormalizedLandmark> pose = result.landmarks().isEmpty() ? null : result.landmarks().get(0);

            // Java 관절 각도 계산 (무릎 굽힘 운동 기준이 없어도 디버그 표시용으로 무릎 각도는 계산)
            ExerciseCriteria criteria = exerciseCriteria != null ? exerciseCriteria : ExerciseCriteria.KNEE_FLEXION;
            boolean judgeHip = criteria.judgedJoint == ExerciseCriteria.JudgedJoint.HIP;
            LegSelector.LegSample left = measureSide(pose, criteria.leftSide, judgeHip, imageWidth, imageHeight);
            LegSelector.LegSample right = measureSide(pose, criteria.rightSide, judgeHip, imageWidth, imageHeight);
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

    // 한쪽 다리의 측정값: 판정 각도 / 상체 기울기 / 엉덩이 각도 / 무릎 각도 + 판정 관절 세 점 중 가장 낮은 visibility
    // judgeHip: 판정 각도가 엉덩이 각도(어깨-엉덩이-무릎)인 운동 (하체 스트레칭). 아니면 무릎 각도(엉덩이-무릎-발목)
    private static LegSelector.LegSample measureSide(@Nullable List<NormalizedLandmark> pose,
                                                     ExerciseCriteria.Side side, boolean judgeHip,
                                                     int imageWidth, int imageHeight) {
        if (pose == null) {
            return new LegSelector.LegSample(side.label, Double.NaN, Double.NaN, Double.NaN, 0f, 0f, Double.NaN);
        }
        double kneeAngle = calculateJointAngle(pose, side.first, side.middle, side.last, imageWidth, imageHeight);
        double trunkLean = calculateTrunkLean(pose, side.shoulder, side.first, imageWidth, imageHeight);
        double hipAngle = calculateJointAngle(pose, side.shoulder, side.first, side.middle, imageWidth, imageHeight);
        // 판정 관절 세 점의 visibility (무릎 판정: 엉덩이·무릎·발목, 엉덩이 판정: 어깨·엉덩이·무릎)
        float firstVisibility = visibility(pose, judgeHip ? side.shoulder : side.first);
        float middleVisibility = visibility(pose, judgeHip ? side.first : side.middle);
        float lastVisibility = visibility(pose, judgeHip ? side.middle : side.last);
        float minVisibility = Math.min(firstVisibility, Math.min(middleVisibility, lastVisibility));
        float averageVisibility = (firstVisibility + middleVisibility + lastVisibility) / 3f;
        return new LegSelector.LegSample(side.label, judgeHip ? hipAngle : kneeAngle, trunkLean, hipAngle,
                minVisibility, averageVisibility, kneeAngle);
    }

    private static float visibility(List<NormalizedLandmark> pose, int index) {
        return index < pose.size() ? pose.get(index).visibility().orElse(0f) : 0f;
    }

    // 상체 기울기: 엉덩이 → 어깨 선이 수직선과 이루는 각도 (똑바로 서면 0도)
    // 엉덩이 바로 위에 가상의 점을 두고 어깨 - 엉덩이 - 가상의 점 각도를 계산 (휴대폰을 세워 둔다고 가정)
    private static double calculateTrunkLean(List<NormalizedLandmark> pose, int shoulderIndex, int hipIndex,
                                             int imageWidth, int imageHeight) {
        if (pose.size() <= Math.max(shoulderIndex, hipIndex)) {
            return Double.NaN;
        }
        NormalizedLandmark shoulder = pose.get(shoulderIndex);
        NormalizedLandmark hip = pose.get(hipIndex);
        if (!PoseOverlayView.isVisible(shoulder) || !PoseOverlayView.isVisible(hip)) {
            return Double.NaN;
        }
        double hipX = hip.x() * imageWidth;
        double hipY = hip.y() * imageHeight;
        // 이미지 좌표는 y가 아래로 증가하므로 "위"는 y가 작은 쪽
        return JointAngleCalculator.calculateAngle(
                shoulder.x() * imageWidth, shoulder.y() * imageHeight,
                hipX, hipY,
                hipX, hipY - VERTICAL_REFERENCE_PX);
    }

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
        if (repetitionCounter == null || exerciseCriteria == null) {
            return null;
        }
        if (!legSelector.isLocked()) {
            if (!legSelector.observeForLock(left, right, timestampMs, exerciseCriteria.lockMinAngle)) {
                // 다리를 정하는 중: 사람(다리)이 보이면 정옆으로 서도록(앉는 운동은 앉도록), 아니면 전신이 보이도록 안내
                showFeedback(left.isCandidate() || right.isCandidate()
                        ? sidewaysMessage()
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
            rep = repetitionCounter.onFrame(timestampMs, Double.NaN, Double.NaN, Double.NaN);
        } else {
            // 보조 각도: 횟수형은 엉덩이 각도(엉덩이 규칙), 유지형은 펴야 하는 다리의 무릎 각도
            double auxAngle = exerciseCriteria.isHoldType() ? chosen.kneeAngle : chosen.hipAngle;
            rep = repetitionCounter.onFrame(timestampMs, chosen.jointAngle, chosen.trunkLean, auxAngle);
        }
        if (rep != null) {
            logRepResult(rep);
            updateExerciseStatus();
            // 횟수는 안내 문구보다 먼저 (이 뒤에 바뀌는 안내 문구는 횟수 다음에 읽힘)
            voiceGuide.speakCount(getString(R.string.voice_rep_count, repetitionCounter.getCompletedReps()));
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
        }
        RepetitionCounter.Feedback feedback = repetitionCounter.getFeedback();
        if (now < legLockedHintUntilMs && feedback != RepetitionCounter.Feedback.NOT_VISIBLE) {
            // 다리가 정해진 직후 2초: 운동별 첫 안내 (무릎 굽힘: "카메라 쪽 다리로 운동해 주세요.")
            // (다리가 안 보이면 "전신이 보이게"가 우선)
            // 왼쪽/오른쪽은 표시하지 않음 (옆모습에서는 MediaPipe의 L/R 이름이 실제 다리와 다를 수 있음)
            showFeedback(legLockedMessage());
        } else {
            showFeedback(now < moveSlowlyUntilMs
                    ? R.string.feedback_move_slowly
                    : feedbackMessage(feedback));
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
                ? "hip" : "knee";
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

    // 운동별 안내 문구. 운동마다 다른 문구가 있으면 그 문구, 없으면 공통(무릎 굽힘) 문구
    @StringRes
    private int feedbackMessage(RepetitionCounter.Feedback feedback) {
        int exerciseMessage = exerciseFeedbackMessage(feedback);
        return exerciseMessage != 0 ? exerciseMessage : defaultFeedbackMessage(feedback);
    }

    // 운동별로 바꿔 쓰는 안내 문구 (없으면 0 → 공통 문구 사용)
    @StringRes
    private int exerciseFeedbackMessage(RepetitionCounter.Feedback feedback) {
        if (exerciseCriteria == null) {
            return 0;
        }
        switch (exerciseCriteria.id) {
            case ExerciseCriteria.ID_CHAIR_SQUAT:
                return chairSquatFeedback(feedback);
            case ExerciseCriteria.ID_LUNGE:
                return lungeFeedback(feedback);
            case ExerciseCriteria.ID_SEATED_HAMSTRING_STRETCH:
                return hamstringStretchFeedback(feedback);
            default:
                return 0;
        }
    }

    // 의자 스쿼트 안내
    @StringRes
    private int chairSquatFeedback(RepetitionCounter.Feedback feedback) {
        switch (feedback) {
            case BEND_MORE:
                return R.string.feedback_squat_sit_more;
            case GOOD_STRAIGHTEN:
                return R.string.feedback_squat_good_stand_up;
            case HIPS_BACK:
                return R.string.feedback_squat_hips_back;
            case START_BENDING:
                return R.string.feedback_squat_start;
            default:
                return 0;
        }
    }

    // 런지 안내
    @StringRes
    private int lungeFeedback(RepetitionCounter.Feedback feedback) {
        switch (feedback) {
            case BEND_MORE:
                return R.string.feedback_lunge_lower_more;
            case GOOD_STRAIGHTEN:
                return R.string.feedback_lunge_good_come_up;
            case START_BENDING:
                return R.string.feedback_lunge_start;
            default:
                return 0;
        }
    }

    // 하체 스트레칭 안내 (유지 중 문구에는 유지 시간 표시)
    @StringRes
    private int hamstringStretchFeedback(RepetitionCounter.Feedback feedback) {
        switch (feedback) {
            case START_BENDING:
                return R.string.feedback_stretch_start;
            case BEND_MORE:
                return R.string.feedback_stretch_lean_more;
            case HOLD:
                return R.string.feedback_stretch_hold;
            case STRAIGHTEN_KNEE:
                return R.string.feedback_stretch_straighten_knee;
            case RELEASE:
                return R.string.feedback_stretch_release;
            default:
                return 0;
        }
    }

    // 다리를 정하기 전 "옆으로 서 주세요" 안내 (운동별)
    @StringRes
    private int sidewaysMessage() {
        if (exerciseCriteria != null) {
            switch (exerciseCriteria.id) {
                case ExerciseCriteria.ID_CHAIR_SQUAT:
                    return R.string.feedback_squat_stand_sideways;
                case ExerciseCriteria.ID_LUNGE:
                    return R.string.feedback_lunge_stand_sideways;
                case ExerciseCriteria.ID_SEATED_HAMSTRING_STRETCH:
                    return R.string.feedback_stretch_sit_sideways;
                default:
                    break;
            }
        }
        return R.string.feedback_stand_sideways;
    }

    // 다리가 정해진 직후 / 첫 동작 전 안내 (운동별)
    @StringRes
    private int legLockedMessage() {
        if (exerciseCriteria != null) {
            switch (exerciseCriteria.id) {
                case ExerciseCriteria.ID_CHAIR_SQUAT:
                    // 양쪽 다리로 하는 운동이라 "카메라 쪽 다리" 안내 대신 시작 안내
                    return R.string.feedback_squat_start;
                case ExerciseCriteria.ID_LUNGE:
                    // 다리가 정해진 직후: 다리를 바꿀 때 몸을 돌리지 않도록 안내 (돌리면 고정한 다리가 가려짐)
                    return R.string.feedback_lunge_switch_feet;
                case ExerciseCriteria.ID_SEATED_HAMSTRING_STRETCH:
                    return R.string.feedback_stretch_extend_leg;
                default:
                    break;
            }
        }
        return R.string.feedback_use_camera_side_leg;
    }

    @StringRes
    private int defaultFeedbackMessage(RepetitionCounter.Feedback feedback) {
        switch (feedback) {
            case NOT_VISIBLE:
                return R.string.feedback_full_body_visible;
            case STAND_STRAIGHT:
                return R.string.feedback_stand_straight;
            case BEND_MORE:
                return R.string.feedback_bend_knee_more;
            case GOOD_STRAIGHTEN:
                return R.string.feedback_good_straighten;
            case KEEP_TRUNK_UPRIGHT:
                return R.string.feedback_keep_trunk_upright;
            case THIGH_UPRIGHT:
                return R.string.feedback_thigh_upright;
            case START_BENDING:
            default:
                // 첫 동작 전에는 어느 다리로 할지 안내
                return getCompletedReps() == 0
                        ? legLockedMessage()
                        : R.string.feedback_start_bending;
        }
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
            Log.d(DEBUG_TAG, String.format(Locale.US, "[%s] feedback=\"%s\"", debugExerciseId(), text));
            voiceGuide.speakMessage(speechText(text));
        }
    }

    // 음성으로 읽을 문구: 유지형의 "(3/20초)"처럼 괄호 안 숫자는 읽지 않음
    private static String speechText(String text) {
        return text.replaceAll("\\s*\\(.*\\)\\s*$", "");
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

    // 세 관절 중 하나라도 잘 안 보이면(visibility < 0.5) NaN
    // 정규화 좌표(0~1)를 픽셀 좌표로 바꿔 계산 (4:3 이미지에서 가로/세로 비율 왜곡 방지)
    private static double calculateJointAngle(@Nullable List<NormalizedLandmark> pose,
                                              int first, int middle, int last,
                                              int imageWidth, int imageHeight) {
        if (pose == null || pose.size() <= Math.max(first, Math.max(middle, last))) {
            return Double.NaN;
        }
        NormalizedLandmark a = pose.get(first);
        NormalizedLandmark b = pose.get(middle);
        NormalizedLandmark c = pose.get(last);
        if (!PoseOverlayView.isVisible(a) || !PoseOverlayView.isVisible(b) || !PoseOverlayView.isVisible(c)) {
            return Double.NaN;
        }
        return JointAngleCalculator.calculateAngle(
                a.x() * imageWidth, a.y() * imageHeight,
                b.x() * imageWidth, b.y() * imageHeight,
                c.x() * imageWidth, c.y() * imageHeight);
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

    private void showCameraMessage(@StringRes int messageRes) {
        cameraMessageText.setText(messageRes);
        cameraMessageText.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        timerHandler.postDelayed(timerRunnable, TIMER_INTERVAL_MS);
        // 일시정지 화면 등에서 돌아오면 지금 안내를 다시 읽음
        if (shownFeedbackText != null) {
            voiceGuide.speakMessage(speechText(shownFeedbackText));
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

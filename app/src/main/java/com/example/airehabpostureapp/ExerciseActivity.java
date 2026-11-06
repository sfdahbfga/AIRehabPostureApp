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

import com.example.airehabpostureapp.pose.ExerciseCriteria;
import com.example.airehabpostureapp.pose.JointAngleCalculator;
import com.example.airehabpostureapp.pose.LegSelector;
import com.example.airehabpostureapp.pose.PoseLandmarkerHelper;
import com.example.airehabpostureapp.pose.PoseOverlayView;
import com.example.airehabpostureapp.pose.RepetitionCounter;
import com.example.airehabpostureapp.pose.SessionMetrics;
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
    private static final boolean DEBUG_SHOW_KNEE_ANGLE = true;
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

    // 목표 횟수: 운동 상세의 권장 횟수(20회 x 3세트) 중 1세트 기준
    private int targetReps = 20;

    // 자세 인식 기준 / 반복 횟수 측정 (아직 기준이 없는 운동이면 null → 측정하지 않음)
    @Nullable
    private ExerciseCriteria exerciseCriteria;
    @Nullable
    private RepetitionCounter repetitionCounter;
    // 매 프레임 판정할 다리 고르기 (잘 보이는 다리 중 더 많이 굽혀진 쪽)
    private final LegSelector legSelector = new LegSelector();
    private TextView postureFeedbackText;
    @StringRes
    private int shownFeedbackRes;

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

    // "천천히 움직여 주세요" 안내를 이 시각(uptime ms)까지 다른 안내 대신 표시
    private static final long MOVE_SLOWLY_HINT_MS = 2000L;
    private long moveSlowlyUntilMs = 0;

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
        exerciseCriteria = ExerciseCriteria.forExercise(exerciseNameText.getText().toString());
        if (exerciseCriteria != null) {
            repetitionCounter = new RepetitionCounter(exerciseCriteria);
            showFeedback(R.string.feedback_full_body_visible);
        } else {
            showFeedback(R.string.feedback_pose_not_supported);
        }

        updateTimerText();
        updateExerciseStatus();

        // 이전 화면(CameraSetup)으로 돌아가기: 새 Activity를 만들지 않고 현재 화면만 종료
        findViewById(R.id.backButton).setOnClickListener(v -> finish());

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
            LegSelector.LegSample left = measureSide(pose, criteria.leftSide, imageWidth, imageHeight);
            LegSelector.LegSample right = measureSide(pose, criteria.rightSide, imageWidth, imageHeight);
            long timestampMs = result.timestampMs();

            runOnUiThread(() -> {
                if (isDestroyed()) {
                    return;
                }
                skeletonOverlayView.setPose(pose, imageWidth, imageHeight, USE_FRONT_CAMERA);
                LegSelector.LegSample chosen = countRepetition(timestampMs, left, right);
                countDebugFrame(pose == null, chosen == null);
                showDebugKneeAngles(left.jointAngle, right.jointAngle, chosen);
            });
        }

        @Override
        public void onPoseError(RuntimeException error) {
            Log.e(TAG, "Pose Landmarker 오류", error);
        }
    };

    // 한쪽 다리의 측정값: 무릎 각도 / 상체 기울기 / 엉덩이 각도 + 엉덩이·무릎·발목 중 가장 낮은 visibility
    private static LegSelector.LegSample measureSide(@Nullable List<NormalizedLandmark> pose,
                                                     ExerciseCriteria.Side side,
                                                     int imageWidth, int imageHeight) {
        if (pose == null) {
            return new LegSelector.LegSample(side.label, Double.NaN, Double.NaN, Double.NaN, 0f);
        }
        double jointAngle = calculateJointAngle(pose, side.first, side.middle, side.last, imageWidth, imageHeight);
        double trunkLean = calculateTrunkLean(pose, side.shoulder, side.first, imageWidth, imageHeight);
        double hipAngle = calculateJointAngle(pose, side.shoulder, side.first, side.middle, imageWidth, imageHeight);
        float minVisibility = Math.min(visibility(pose, side.first),
                Math.min(visibility(pose, side.middle), visibility(pose, side.last)));
        return new LegSelector.LegSample(side.label, jointAngle, trunkLean, hipAngle, minVisibility);
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
    // 옆모습에서는 MediaPipe가 L/R 이름을 순간적으로 바꿔 붙일 수 있어, 이름으로 다리를 고정하지 않고
    // 매 프레임 "잘 보이는 다리 중 더 많이 굽혀진 쪽"을 움직이는 다리로 본다. (LegSelector)
    // 평균(흔들림 완화)은 고른 뒤의 각도에 RepetitionCounter가 적용한다.
    // 반환값: 이번 프레임에 고른 다리 (디버그 표시용, 측정하지 않거나 후보가 없으면 null)
    @Nullable
    private LegSelector.LegSample countRepetition(long timestampMs, LegSelector.LegSample left,
                                                  LegSelector.LegSample right) {
        if (repetitionCounter == null || exerciseCriteria == null) {
            return null;
        }
        LegSelector.LegSample chosen = legSelector.select(left, right);
        RepetitionCounter.RepResult rep;
        if (chosen == null) {
            // 두 다리 모두 잘 안 보임 → 이 프레임은 건너뜀 (오래 이어지면 카운터가 "전신이 보이게" 안내)
            rep = repetitionCounter.onFrame(timestampMs, Double.NaN, Double.NaN, Double.NaN);
        } else {
            rep = repetitionCounter.onFrame(timestampMs, chosen.jointAngle, chosen.trunkLean, chosen.hipAngle);
        }
        if (rep != null) {
            logRepResult(rep);
            updateExerciseStatus();
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
        // L↔R 전환 횟수는 동작 하나 단위로 기록 (서 있는 동안의 전환은 세지 않음)
        if (!repetitionCounter.isRepInProgress()) {
            legSelector.resetSwitchCount();
        }
        showFeedback(now < moveSlowlyUntilMs
                ? R.string.feedback_move_slowly
                : feedbackMessage(repetitionCounter.getFeedback()));
        return chosen;
    }

    @StringRes
    private int feedbackMessage(RepetitionCounter.Feedback feedback) {
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
                        ? R.string.feedback_use_camera_side_leg
                        : R.string.feedback_start_bending;
        }
    }

    // 같은 문구면 다시 그리지 않음 (매 프레임 호출됨)
    private void showFeedback(@StringRes int messageRes) {
        if (messageRes == shownFeedbackRes) {
            return;
        }
        shownFeedbackRes = messageRes;
        postureFeedbackText.setText(messageRes);
    }

    // 디버그용: 회차별 결과를 Logcat에 기록 (기준값 조정용, DEBUG_LOG_REPS로 끔)
    // (leg = 마지막으로 고른 다리 이름, switches = 이번 동작 중 고른 다리가 L↔R로 바뀐 횟수)
    private void logRepResult(RepetitionCounter.RepResult rep) {
        if (!DEBUG_LOG_REPS) {
            return;
        }
        Log.d(DEBUG_TAG, String.format(Locale.US,
                "rep #%d leg=%s switches=%d minKnee=%.1f kneeErr=%+.1f minHip=%s maxTrunk=%s backErr=%s acc=%.0f dur=%dms %s",
                rep.repNumber, legSelector.getLastLabel(), legSelector.getSwitchCount(),
                rep.minJointAngle, rep.jointError, formatAngle(rep.minHipAngle),
                formatAngle(rep.maxTrunkLean), formatAngle(rep.trunkError), rep.accuracy, rep.durationMs,
                formatFrames(rep.frames)));
    }

    // frames=판정에 쓴/연속성 검사로 건너뛴/안 보여서 건너뛴 프레임 수, missed=건너뛴 비율
    private static String formatFrames(RepetitionCounter.FrameStats frames) {
        return String.format(Locale.US, "frames=%d/%d/%d missed=%.0f%%",
                frames.accepted, frames.skippedByJump, frames.invisible, frames.missedRatio() * 100);
    }

    // 디버그용: 세지 않고 버린 동작과 이유를 Logcat에 기록 (기준값 조정용, DEBUG_LOG_REPS로 끔)
    private void logRejectedAttempt(RepetitionCounter.RejectedAttempt attempt) {
        if (!DEBUG_LOG_REPS) {
            return;
        }
        Log.d(DEBUG_TAG, String.format(Locale.US,
                "rejected reason=%s leg=%s switches=%d minKnee=%.1f minHip=%s maxTrunk=%s dur=%dms %s",
                attempt.reason, legSelector.getLastLabel(), legSelector.getSwitchCount(),
                attempt.minJointAngle, formatAngle(attempt.minHipAngle),
                formatAngle(attempt.maxTrunkLean), attempt.durationMs, formatFrames(attempt.frames)));
    }

    // 디버그용: 1초 기록에 쓸 프레임 수 집계 (UI 스레드)
    private void countDebugFrame(boolean noPose, boolean noCandidateLeg) {
        debugFrameCount++;
        if (noPose) {
            debugNoPoseFrames++;
        } else if (noCandidateLeg) {
            debugLowVisibilityFrames++;
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

    // 디버그용: 무릎 각도를 화면 구석에 표시하고 Logcat에 1초에 한 번 기록 (DEBUG_SHOW_KNEE_ANGLE로 끔)
    // sel = 이번 프레임에 판정에 쓴 다리와 각도
    // fps = 지난 기록 이후 초당 MediaPipe 결과 수
    // noPose = 사람 인식 안 됨, lowVis = 두 다리 모두 visibility 0.5 미만, jump = 연속성 검사로 건너뜀 (지난 기록 이후 프레임 수)
    private void showDebugKneeAngles(double leftKneeAngle, double rightKneeAngle,
                                     @Nullable LegSelector.LegSample chosen) {
        if (!DEBUG_SHOW_KNEE_ANGLE) {
            return;
        }
        String left = formatAngle(leftKneeAngle);
        String right = formatAngle(rightKneeAngle);
        debugKneeAngleText.setVisibility(View.VISIBLE);
        debugKneeAngleText.setText(getString(R.string.debug_knee_angle_format, left, right));

        long now = SystemClock.uptimeMillis();
        long elapsedMs = now - lastDebugLogAtMs;
        if (elapsedMs >= DEBUG_LOG_INTERVAL_MS) {
            String selected = chosen == null ? "-" : chosen.label + ":" + formatAngle(chosen.jointAngle);
            int skippedTotal = repetitionCounter == null ? 0 : repetitionCounter.getSkippedFrameCount();
            int jumpSinceLastLog = skippedTotal - lastLoggedSkippedFrames;
            double fps = lastDebugLogAtMs == 0 ? 0 : debugFrameCount * 1000.0 / elapsedMs;
            Log.d(DEBUG_TAG, String.format(Locale.US,
                    "knee L=%s R=%s sel=%s fps=%.1f frames=%d noPose=%d lowVis=%d jump=%d",
                    left, right, selected, fps, debugFrameCount,
                    debugNoPoseFrames, debugLowVisibilityFrames, jumpSinceLastLog));
            lastDebugLogAtMs = now;
            lastLoggedSkippedFrames = skippedTotal;
            debugFrameCount = 0;
            debugNoPoseFrames = 0;
            debugLowVisibilityFrames = 0;
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
    }

    @Override
    protected void onPause() {
        super.onPause();
        timerHandler.removeCallbacks(timerRunnable);
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
        putOptionalInt(intent, ExerciseFlowExtras.KNEE_ERROR_DEGREES, SessionMetrics.round(metrics.jointError));
        putOptionalInt(intent, ExerciseFlowExtras.BACK_ERROR_DEGREES, SessionMetrics.round(metrics.trunkError));
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
        legSelector.reset();
        lastLoggedSkippedFrames = 0;
        moveSlowlyUntilMs = 0;
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

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

import com.example.airehabpostureapp.pose.JointAngleCalculator;
import com.example.airehabpostureapp.pose.PoseLandmarkerHelper;
import com.example.airehabpostureapp.pose.PoseOverlayView;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// 자세 인식 1단계: 카메라 영상 + 관절 뼈대 실시간 표시 (+ 디버그용 무릎 각도)
// 흐름: CameraX → MediaPipe Landmark → Java 관절 각도 계산 → (다음 단계) 기준 범위 비교 → 피드백 / 반복 횟수
// MediaPipe는 관절 위치 추출에만 사용하고, 판정은 Java 코드에서 한다.
public class ExerciseActivity extends AppCompatActivity {

    private static final long TIMER_INTERVAL_MS = 1000L;

    // 디버그용: 무릎 각도(엉덩이-무릎-발목)를 화면 구석과 Logcat(태그 PoseDebug)에 표시.
    // 횟수 / 정확도 판정이 들어가는 다음 단계 이후 false로 끄거나 관련 코드를 삭제한다.
    private static final boolean DEBUG_SHOW_KNEE_ANGLE = true;
    private static final String DEBUG_TAG = "PoseDebug";
    private static final long DEBUG_LOG_INTERVAL_MS = 1000L;

    private static final String TAG = "ExerciseActivity";

    // MediaPipe Pose 관절 번호 (엉덩이 / 무릎 / 발목)
    private static final int LEFT_HIP = 23;
    private static final int RIGHT_HIP = 24;
    private static final int LEFT_KNEE = 25;
    private static final int RIGHT_KNEE = 26;
    private static final int LEFT_ANKLE = 27;
    private static final int RIGHT_ANKLE = 28;

    // 기본은 전면 카메라. 전면은 미리보기가 거울처럼 반전되므로 뼈대도 반전해서 그린다.
    private static final boolean USE_FRONT_CAMERA = true;

    // MediaPipe 분석용 해상도 (센서 기준 가로 x 세로). 미리보기와 같은 4:3 비율이어야 뼈대가 정확히 겹친다.
    private static final Size ANALYSIS_TARGET_SIZE = new Size(640, 480);

    // TODO: MediaPipe 연동 후 실제 값으로 교체 (반복 횟수 / 목표 횟수 / 정확도 임시 값)
    private int currentReps = 8;
    private int targetReps = 20;
    private int currentAccuracy = 86;

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
        updateTimerText();
        updateExerciseStatus();

        // 이전 화면(CameraSetup)으로 돌아가기: 새 Activity를 만들지 않고 현재 화면만 종료
        findViewById(R.id.backButton).setOnClickListener(v -> finish());

        // 일시정지 화면(14_Pause)으로 이동: 이 화면은 그대로 두고 위에 열리므로 복귀 시 중복 생성되지 않음
        // (Pause 화면이 보이는 동안 onPause()에서 Timer가 멈추고, 복귀하면 onResume()에서 다시 시작)
        findViewById(R.id.pauseButton).setOnClickListener(v -> {
            Intent intent = new Intent(this, PauseActivity.class);
            intent.putExtra(PauseActivity.EXTRA_EXERCISE_NAME, exerciseNameText.getText().toString());
            intent.putExtra(PauseActivity.EXTRA_CURRENT_REPS, currentReps);
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

            // Java 관절 각도 계산 (다음 단계에서 기준 범위 비교 / 반복 횟수 측정에 사용)
            double leftKneeAngle = calculateJointAngle(pose, LEFT_HIP, LEFT_KNEE, LEFT_ANKLE, imageWidth, imageHeight);
            double rightKneeAngle = calculateJointAngle(pose, RIGHT_HIP, RIGHT_KNEE, RIGHT_ANKLE, imageWidth, imageHeight);

            runOnUiThread(() -> {
                if (isDestroyed()) {
                    return;
                }
                skeletonOverlayView.setPose(pose, imageWidth, imageHeight, USE_FRONT_CAMERA);
                showDebugKneeAngles(leftKneeAngle, rightKneeAngle);
            });
        }

        @Override
        public void onPoseError(RuntimeException error) {
            Log.e(TAG, "Pose Landmarker 오류", error);
        }
    };

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
    private void showDebugKneeAngles(double leftKneeAngle, double rightKneeAngle) {
        if (!DEBUG_SHOW_KNEE_ANGLE) {
            return;
        }
        String left = formatAngle(leftKneeAngle);
        String right = formatAngle(rightKneeAngle);
        debugKneeAngleText.setVisibility(View.VISIBLE);
        debugKneeAngleText.setText(getString(R.string.debug_knee_angle_format, left, right));

        long now = SystemClock.uptimeMillis();
        if (now - lastDebugLogAtMs >= DEBUG_LOG_INTERVAL_MS) {
            lastDebugLogAtMs = now;
            Log.d(DEBUG_TAG, "knee L=" + left + " R=" + right);
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
        // TODO: MediaPipe 연동 후 실제 값으로 교체 (currentReps / targetReps / currentAccuracy가 아직 임시 값)
        intent.putExtra(ExerciseFlowExtras.COMPLETED_REPS, currentReps);
        intent.putExtra(ExerciseFlowExtras.TARGET_REPS, targetReps);
        intent.putExtra(ExerciseFlowExtras.AVERAGE_ACCURACY, currentAccuracy);
        startActivity(intent);
        finish();
    }

    // 반복 횟수 / 진행률 / 운동 시간을 0으로 초기화 (Timer는 화면 복귀 시 onResume()에서 다시 시작)
    private void restartExercise() {
        currentReps = 0;
        elapsedSeconds = 0;
        startedAtMillis = System.currentTimeMillis();
        updateTimerText();
        updateExerciseStatus();
    }

    private void updateTimerText() {
        exerciseTimerText.setText(getString(R.string.exercise_timer_format,
                elapsedSeconds / 60, elapsedSeconds % 60));
    }

    // 반복 횟수 / 정확도 / 진행률 표시 (향후 자세 판정 결과가 바뀔 때마다 호출)
    private void updateExerciseStatus() {
        TextView repetitionCountText = findViewById(R.id.repetitionCountText);
        TextView accuracyText = findViewById(R.id.accuracyText);
        TextView exerciseProgressPercentText = findViewById(R.id.exerciseProgressPercentText);
        LinearProgressIndicator exerciseProgressBar = findViewById(R.id.exerciseProgressBar);

        int progressPercent = targetReps > 0 ? currentReps * 100 / targetReps : 0;

        repetitionCountText.setText(getString(R.string.repetition_count_format, currentReps, targetReps));
        accuracyText.setText(getString(R.string.percent_format, currentAccuracy));
        exerciseProgressPercentText.setText(getString(R.string.percent_format, progressPercent));
        exerciseProgressBar.setProgress(progressPercent);
    }
}

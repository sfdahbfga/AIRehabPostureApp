package com.example.airehabpostureapp.pose;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.SystemClock;

import androidx.camera.core.ImageProxy;

import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.core.Delegate;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;

/**
 * MediaPipe Pose Landmarker(LIVE_STREAM 모드) 래퍼.
 *
 * 역할: CameraX 프레임에서 신체 관절 33개의 위치(Landmark)를 추출하는 것까지만 한다.
 * 자세가 올바른지는 여기서 판단하지 않는다. (관절 각도 계산 / 기준 범위 비교는 Java 코드에서 수행)
 *
 * 스레드: detectLiveStream()은 CameraX 분석 스레드에서, Listener는 MediaPipe 내부 스레드에서 호출된다.
 */
public class PoseLandmarkerHelper {

    public interface Listener {
        // imageWidth / imageHeight: 회전 보정 후(화면 방향 기준) 분석 이미지 크기
        void onPoseResult(PoseLandmarkerResult result, int imageWidth, int imageHeight);

        void onPoseError(RuntimeException error);
    }

    private static final String MODEL_ASSET_PATH = "pose_landmarker_lite.task";
    private static final int MAX_POSES = 1;

    private PoseLandmarker poseLandmarker;
    // LIVE_STREAM 모드는 timestamp가 반드시 계속 증가해야 하므로 마지막 값을 기억
    private long lastTimestampMs = -1;

    /**
     * 모델을 불러와 Pose Landmarker를 만든다. 모델 파일이 없거나 손상되면 RuntimeException.
     */
    public PoseLandmarkerHelper(Context context, Listener listener) {
        BaseOptions baseOptions = BaseOptions.builder()
                .setModelAssetPath(MODEL_ASSET_PATH)
                .setDelegate(Delegate.CPU)
                .build();
        PoseLandmarker.PoseLandmarkerOptions options = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumPoses(MAX_POSES)
                .setResultListener((PoseLandmarkerResult result, MPImage input) ->
                        listener.onPoseResult(result, input.getWidth(), input.getHeight()))
                .setErrorListener(listener::onPoseError)
                .build();
        poseLandmarker = PoseLandmarker.createFromOptions(context.getApplicationContext(), options);
    }

    /**
     * CameraX ImageAnalysis 프레임 하나를 비동기로 분석한다. imageProxy는 여기서 닫는다.
     * (CameraX 분석 스레드에서만 호출)
     */
    public void detectLiveStream(ImageProxy imageProxy) {
        if (poseLandmarker == null) {
            imageProxy.close();
            return;
        }
        int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();
        Bitmap bitmap = imageProxy.toBitmap();
        // Bitmap으로 복사했으므로 카메라 버퍼는 바로 반납 (반납하지 않으면 다음 프레임이 오지 않음)
        imageProxy.close();

        // 센서 방향 → 화면(세로) 방향으로 회전. 좌우 반전은 하지 않고 그릴 때만 반전한다.
        // (MediaPipe의 왼쪽/오른쪽 관절이 실제 사람의 왼쪽/오른쪽과 일치하도록 원본 방향 유지)
        Bitmap uprightBitmap = bitmap;
        if (rotationDegrees != 0) {
            Matrix matrix = new Matrix();
            matrix.postRotate(rotationDegrees);
            uprightBitmap = Bitmap.createBitmap(bitmap, 0, 0,
                    bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        }

        long timestampMs = SystemClock.uptimeMillis();
        if (timestampMs <= lastTimestampMs) {
            timestampMs = lastTimestampMs + 1;
        }
        lastTimestampMs = timestampMs;

        MPImage mpImage = new BitmapImageBuilder(uprightBitmap).build();
        poseLandmarker.detectAsync(mpImage, timestampMs);
    }

    /**
     * 네이티브 리소스 해제. detectLiveStream()과 같은 분석 스레드에서 호출해야
     * 처리 중인 프레임과 겹치지 않는다.
     */
    public void close() {
        if (poseLandmarker != null) {
            poseLandmarker.close();
            poseLandmarker = null;
        }
    }
}

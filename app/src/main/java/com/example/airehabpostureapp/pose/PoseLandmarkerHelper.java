package com.example.airehabpostureapp.pose;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.os.SystemClock;
import android.util.Log;

import androidx.camera.core.ImageProxy;

import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.core.Delegate;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;

import java.nio.ByteBuffer;

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
        // M1: 분석 중 예외가 나면 그 프레임만 건너뜀 (분석 스레드에서 예외가 새면 앱이 종료됨)
        try {
            detect(imageProxy);
        } catch (RuntimeException e) {
            skippedErrorFrames++;
            Log.w(TAG, "프레임 분석 실패, 이 프레임은 건너뜀 (누적 " + skippedErrorFrames + "개)", e);
        } finally {
            // 이미 닫힌 경우에도 다시 close해도 안전함
            imageProxy.close();
        }
    }

    private static final String TAG = "PoseLandmarkerHelper";
    // 예외로 건너뛴 프레임 수 (로그용)
    private int skippedErrorFrames = 0;

    private void detect(ImageProxy imageProxy) {
        if (poseLandmarker == null) {
            return;
        }
        int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();
        Bitmap bitmap = copyFrame(imageProxy);
        // Bitmap으로 복사했으므로 카메라 버퍼는 바로 반납 (반납하지 않으면 다음 프레임이 오지 않음)
        imageProxy.close();

        // 센서 방향 → 화면(세로) 방향으로 회전. 좌우 반전은 하지 않고 그릴 때만 반전한다.
        // (MediaPipe의 왼쪽/오른쪽 관절이 실제 사람의 왼쪽/오른쪽과 일치하도록 원본 방향 유지)
        Bitmap uprightBitmap = bitmap;
        if (rotationDegrees != 0) {
            uprightBitmap = rotateIntoReusable(bitmap, rotationDegrees);
        }

        long timestampMs = SystemClock.uptimeMillis();
        if (timestampMs <= lastTimestampMs) {
            timestampMs = lastTimestampMs + 1;
        }
        lastTimestampMs = timestampMs;

        MPImage mpImage = new BitmapImageBuilder(uprightBitmap).build();
        poseLandmarker.detectAsync(mpImage, timestampMs);
    }

    // M7: 프레임마다 Bitmap을 새로 만들지 않고 아래 두 Bitmap을 재사용 (크기가 바뀔 때만 새로 만듦)
    // MediaPipe는 detectAsync 안에서 픽셀을 자체 메모리로 복사하므로, 다음 프레임에서 덮어써도 된다.
    // (분석은 한 스레드에서 차례로만 실행됨)
    private Bitmap frameBitmap;
    private Bitmap uprightBitmap;
    private Canvas uprightCanvas;
    private final Matrix rotationMatrix = new Matrix();

    // 카메라 RGBA 버퍼 → 재사용 Bitmap. 행 끝에 여백(padding)이 있는 기기는 기존 toBitmap()으로 복사
    private Bitmap copyFrame(ImageProxy imageProxy) {
        ImageProxy.PlaneProxy plane = imageProxy.getPlanes()[0];
        int width = imageProxy.getWidth();
        int height = imageProxy.getHeight();
        if (plane.getPixelStride() != 4 || plane.getRowStride() != width * 4) {
            return imageProxy.toBitmap();
        }
        if (frameBitmap == null || frameBitmap.getWidth() != width || frameBitmap.getHeight() != height) {
            frameBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        }
        ByteBuffer buffer = plane.getBuffer();
        buffer.rewind();
        frameBitmap.copyPixelsFromBuffer(buffer);
        return frameBitmap;
    }

    // 90 / 180 / 270도 회전 결과를 재사용 Bitmap에 그린다
    private Bitmap rotateIntoReusable(Bitmap source, int rotationDegrees) {
        boolean swap = rotationDegrees == 90 || rotationDegrees == 270;
        int width = swap ? source.getHeight() : source.getWidth();
        int height = swap ? source.getWidth() : source.getHeight();
        if (uprightBitmap == null || uprightBitmap.getWidth() != width || uprightBitmap.getHeight() != height) {
            uprightBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            uprightCanvas = new Canvas(uprightBitmap);
        }
        rotate(source, rotationDegrees, uprightCanvas, rotationMatrix);
        return uprightBitmap;
    }

    /** source를 rotationDegrees(0/90/180/270)만큼 시계 방향으로 돌려 canvas(회전 후 크기)에 그린다. 테스트용 공개 */
    public static void rotate(Bitmap source, int rotationDegrees, Canvas canvas, Matrix matrix) {
        int w = source.getWidth();
        int h = source.getHeight();
        matrix.reset();
        matrix.postRotate(rotationDegrees);
        switch (rotationDegrees) {
            case 90:
                matrix.postTranslate(h, 0);
                break;
            case 180:
                matrix.postTranslate(w, h);
                break;
            case 270:
                matrix.postTranslate(0, w);
                break;
            default:
                break;
        }
        canvas.drawBitmap(source, matrix, null);
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

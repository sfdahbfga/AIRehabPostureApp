package com.example.airehabpostureapp.pose;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.airehabpostureapp.R;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;

import java.util.List;

/**
 * MediaPipe 관절 좌표(정규화 0~1)를 카메라 미리보기 위에 점과 선으로 그리는 뷰.
 *
 * PreviewView 기본 scaleType(FILL_CENTER)과 같은 방식으로 좌표를 맞춘다:
 * 분석 이미지를 뷰를 꽉 채우도록 확대(긴 쪽은 잘림)하고 가운데 정렬.
 * (Preview와 ImageAnalysis의 비율을 같게(4:3) 맞춰야 영상과 뼈대가 정확히 겹친다)
 */
public class PoseOverlayView extends View {

    // 이 값보다 visibility가 낮은 관절은 그리지 않는다
    public static final float MIN_VISIBILITY = PoseMeasurer.MIN_VISIBILITY;

    private static final float POINT_RADIUS_DP = 4f;
    private static final float LINE_WIDTH_DP = 3f;

    // MediaPipe Pose 33개 관절 연결 (공식 POSE_LANDMARKS 연결과 동일)
    // tasks-vision의 PoseLandmarksConnections는 package-private이라 직접 정의
    private static final int[][] CONNECTIONS = {
            // 얼굴
            {0, 1}, {1, 2}, {2, 3}, {3, 7}, {0, 4}, {4, 5}, {5, 6}, {6, 8}, {9, 10},
            // 어깨 / 팔 / 손
            {11, 12}, {11, 13}, {13, 15}, {15, 17}, {15, 19}, {15, 21}, {17, 19},
            {12, 14}, {14, 16}, {16, 18}, {16, 20}, {16, 22}, {18, 20},
            // 몸통
            {11, 23}, {12, 24}, {23, 24},
            // 다리 / 발
            {23, 25}, {24, 26}, {25, 27}, {26, 28}, {27, 29}, {28, 30},
            {29, 31}, {30, 32}, {27, 31}, {28, 32}
    };

    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float pointRadiusPx;

    @Nullable
    private List<NormalizedLandmark> landmarks;
    private int imageWidth;
    private int imageHeight;
    private boolean mirrored;

    public PoseOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        float density = getResources().getDisplayMetrics().density;
        pointRadiusPx = POINT_RADIUS_DP * density;

        pointPaint.setColor(ContextCompat.getColor(context, R.color.white));
        pointPaint.setStyle(Paint.Style.FILL);

        linePaint.setColor(ContextCompat.getColor(context, R.color.primary));
        linePaint.setStrokeWidth(LINE_WIDTH_DP * density);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
    }

    /**
     * 그릴 관절을 바꾼다. (UI 스레드에서 호출)
     *
     * @param landmarks   한 사람의 관절 33개. null이면 지운다.
     * @param imageWidth  분석 이미지 너비(회전 보정 후)
     * @param imageHeight 분석 이미지 높이(회전 보정 후)
     * @param mirrored    전면 카메라처럼 미리보기가 좌우 반전돼 있으면 true
     */
    public void setPose(@Nullable List<NormalizedLandmark> landmarks,
                        int imageWidth, int imageHeight, boolean mirrored) {
        this.landmarks = landmarks;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.mirrored = mirrored;
        invalidate();
    }

    public void clear() {
        setPose(null, 0, 0, false);
    }

    public static boolean isVisible(NormalizedLandmark landmark) {
        return PoseMeasurer.isVisible(landmark);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        List<NormalizedLandmark> pose = landmarks;
        if (pose == null || imageWidth <= 0 || imageHeight <= 0) {
            return;
        }

        // FILL_CENTER: 뷰를 꽉 채우는 배율과 가운데 정렬 여백(음수면 잘린 부분)
        float scale = Math.max((float) getWidth() / imageWidth, (float) getHeight() / imageHeight);
        float offsetX = (getWidth() - imageWidth * scale) / 2f;
        float offsetY = (getHeight() - imageHeight * scale) / 2f;

        for (int[] connection : CONNECTIONS) {
            if (connection[0] >= pose.size() || connection[1] >= pose.size()) {
                continue;
            }
            NormalizedLandmark start = pose.get(connection[0]);
            NormalizedLandmark end = pose.get(connection[1]);
            if (!isVisible(start) || !isVisible(end)) {
                continue;
            }
            canvas.drawLine(toViewX(start, scale, offsetX), toViewY(start, scale, offsetY),
                    toViewX(end, scale, offsetX), toViewY(end, scale, offsetY), linePaint);
        }

        for (NormalizedLandmark landmark : pose) {
            if (!isVisible(landmark)) {
                continue;
            }
            canvas.drawCircle(toViewX(landmark, scale, offsetX), toViewY(landmark, scale, offsetY),
                    pointRadiusPx, pointPaint);
        }
    }

    private float toViewX(NormalizedLandmark landmark, float scale, float offsetX) {
        // 미리보기가 거울처럼 반전돼 있으므로 x도 반전해야 몸 위에 겹친다
        float x = mirrored ? 1f - landmark.x() : landmark.x();
        return x * imageWidth * scale + offsetX;
    }

    private float toViewY(NormalizedLandmark landmark, float scale, float offsetY) {
        return landmark.y() * imageHeight * scale + offsetY;
    }
}

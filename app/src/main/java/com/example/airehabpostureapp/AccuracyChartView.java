package com.example.airehabpostureapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

// 20_WeeklyReport 정확도 변화용 간단한 선 그래프 (외부 Chart Library 없이 Canvas로 그림)
// 각 지점: 위에 정확도 %, 아래에 라벨(요일 / 주차) 표시
public class AccuracyChartView extends View {

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valueTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private String[] labels = new String[0];
    private int[] values = new int[0];
    private float[] xs = new float[0];
    private float[] ys = new float[0];

    public AccuracyChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        float density = getResources().getDisplayMetrics().density;
        int grayDark = ContextCompat.getColor(context, R.color.gray_dark);
        int gray = ContextCompat.getColor(context, R.color.gray);

        linePaint.setColor(ContextCompat.getColor(context, R.color.primary));
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(2 * density);

        pointPaint.setColor(grayDark);
        pointPaint.setStyle(Paint.Style.FILL);

        gridPaint.setColor(ContextCompat.getColor(context, R.color.gray_light));
        gridPaint.setStrokeWidth(1 * density);

        valueTextPaint.setColor(grayDark);
        valueTextPaint.setTextSize(spToPx(11));
        valueTextPaint.setTextAlign(Paint.Align.CENTER);

        labelTextPaint.setColor(gray);
        labelTextPaint.setTextSize(spToPx(12));
        labelTextPaint.setTextAlign(Paint.Align.CENTER);
    }

    private float spToPx(float sp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, getResources().getDisplayMetrics());
    }

    // labels와 values의 길이는 같아야 함
    public void setData(String[] labels, int[] values) {
        this.labels = labels;
        this.values = values;
        // 지점 좌표 배열은 onDraw에서 매번 만들지 않도록 데이터 변경 시에만 생성
        xs = new float[values.length];
        ys = new float[values.length];
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int count = Math.min(labels.length, values.length);
        if (count == 0) {
            return;
        }

        float density = getResources().getDisplayMetrics().density;
        float labelAreaHeight = 24 * density;
        float valueAreaHeight = 20 * density;
        float horizontalPadding = 16 * density;

        float chartTop = getPaddingTop() + valueAreaHeight;
        float chartBottom = getHeight() - getPaddingBottom() - labelAreaHeight;
        float chartLeft = getPaddingLeft() + horizontalPadding;
        float chartRight = getWidth() - getPaddingRight() - horizontalPadding;

        // 값 범위: 최소 / 최대 값 기준으로 위아래 여유를 두어 변화가 보이도록 함
        int minValue = values[0];
        int maxValue = values[0];
        for (int i = 1; i < count; i++) {
            minValue = Math.min(minValue, values[i]);
            maxValue = Math.max(maxValue, values[i]);
        }
        float rangeMin = minValue - 2;
        float rangeMax = maxValue + 2;

        // 기준선 (하단)
        canvas.drawLine(chartLeft - horizontalPadding, chartBottom,
                chartRight + horizontalPadding, chartBottom, gridPaint);

        for (int i = 0; i < count; i++) {
            xs[i] = count == 1 ? (chartLeft + chartRight) / 2
                    : chartLeft + (chartRight - chartLeft) * i / (count - 1);
            ys[i] = chartBottom - (chartBottom - chartTop) * (values[i] - rangeMin) / (rangeMax - rangeMin);
        }

        for (int i = 0; i < count - 1; i++) {
            canvas.drawLine(xs[i], ys[i], xs[i + 1], ys[i + 1], linePaint);
        }

        float pointRadius = 4 * density;
        for (int i = 0; i < count; i++) {
            canvas.drawCircle(xs[i], ys[i], pointRadius, pointPaint);
            canvas.drawText(getContext().getString(R.string.percent_format, values[i]),
                    xs[i], ys[i] - 8 * density, valueTextPaint);
            canvas.drawText(labels[i], xs[i], chartBottom + 18 * density, labelTextPaint);
        }
    }
}

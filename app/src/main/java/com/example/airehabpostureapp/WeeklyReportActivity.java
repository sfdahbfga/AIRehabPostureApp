package com.example.airehabpostureapp;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

// 주간 / 월간 리포트 (현재는 고정 예시 데이터만 표시, Supabase 통계 연동 전)
public class WeeklyReportActivity extends AppCompatActivity {

    private static final int PERIOD_WEEKLY = 0;
    private static final int PERIOD_MONTHLY = 1;

    // TODO: Supabase 운동 기록 통계 연동 후 교체 (현재는 임시 예시 데이터)
    // 인덱스 = PERIOD_WEEKLY / PERIOD_MONTHLY
    private final int[] periodResIds = {R.string.sample_weekly_period, R.string.sample_monthly_period};
    private final int[] exerciseCounts = {4, 14};
    private final int[] totalExerciseMinutes = {72, 275};
    private final int[] averageAccuracies = {87, 86};
    private final int[] chartLabelArrayResIds = {R.array.weekday_labels, R.array.month_week_labels};
    private final int[][] chartAccuracies = {
            {82, 84, 84, 86, 87, 89, 90},
            {84, 85, 87, 88}
    };
    private final int[] mostPerformedExerciseResIds = {R.string.exercise_name_knee_flexion,
            R.string.exercise_name_knee_flexion};
    private final int[] mostPerformedExerciseCounts = {3, 8};
    private final int[] frequentMistakeResIds = {R.string.sample_mistake_knee_angle,
            R.string.sample_mistake_knee_angle};
    private final int[] frequentMistakeCounts = {3, 5};

    private View weeklyTabButton;
    private View monthlyTabButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 흰 배경 위에서 상태바 아이콘이 어둡게 보이도록 light 스타일 사용
        EdgeToEdge.enable(this,
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT));
        setContentView(R.layout.activity_weekly_report);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.weeklyReportRoot), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 이전 화면(RecordList)으로 돌아가기: 새 Activity를 만들지 않고 현재 화면만 종료
        findViewById(R.id.backButton).setOnClickListener(v -> finish());

        // 주간 / 월간 탭: 단일 선택 (기본 주간)
        weeklyTabButton = findViewById(R.id.weeklyTabButton);
        monthlyTabButton = findViewById(R.id.monthlyTabButton);
        weeklyTabButton.setOnClickListener(v -> selectPeriod(PERIOD_WEEKLY));
        monthlyTabButton.setOnClickListener(v -> selectPeriod(PERIOD_MONTHLY));
        selectPeriod(PERIOD_WEEKLY);
    }

    // 선택한 탭만 selected로 바꾸고 같은 화면 안에서 예시 데이터만 교체
    private void selectPeriod(int period) {
        weeklyTabButton.setSelected(period == PERIOD_WEEKLY);
        monthlyTabButton.setSelected(period == PERIOD_MONTHLY);

        TextView reportPeriodText = findViewById(R.id.reportPeriodText);
        TextView exerciseCountValueText = findViewById(R.id.exerciseCountValueText);
        TextView totalExerciseTimeValueText = findViewById(R.id.totalExerciseTimeValueText);
        TextView averageAccuracyValueText = findViewById(R.id.averageAccuracyValueText);
        AccuracyChartView accuracyChartView = findViewById(R.id.accuracyChartView);
        TextView mostPerformedExerciseNameText = findViewById(R.id.mostPerformedExerciseNameText);
        TextView mostPerformedExerciseCountText = findViewById(R.id.mostPerformedExerciseCountText);
        TextView frequentMistakeText = findViewById(R.id.frequentMistakeText);
        TextView frequentMistakeCountText = findViewById(R.id.frequentMistakeCountText);

        reportPeriodText.setText(periodResIds[period]);
        exerciseCountValueText.setText(getString(R.string.count_times_format, exerciseCounts[period]));
        int minutes = totalExerciseMinutes[period];
        totalExerciseTimeValueText.setText(getString(R.string.hours_minutes_format, minutes / 60, minutes % 60));
        averageAccuracyValueText.setText(getString(R.string.percent_format, averageAccuracies[period]));
        accuracyChartView.setData(getResources().getStringArray(chartLabelArrayResIds[period]),
                chartAccuracies[period]);
        mostPerformedExerciseNameText.setText(mostPerformedExerciseResIds[period]);
        mostPerformedExerciseCountText.setText(getString(R.string.count_times_format,
                mostPerformedExerciseCounts[period]));
        frequentMistakeText.setText(frequentMistakeResIds[period]);
        frequentMistakeCountText.setText(getString(R.string.count_times_format, frequentMistakeCounts[period]));
    }
}

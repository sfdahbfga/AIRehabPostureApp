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

import com.example.airehabpostureapp.stats.ReportStats;
import com.example.airehabpostureapp.stats.ReportStatsCalculator;
import com.example.airehabpostureapp.stats.ReportStatsCalculator.Period;
import com.example.airehabpostureapp.stats.SampleSessionData;
import com.example.airehabpostureapp.stats.SessionRecord;

import java.time.LocalDate;
import java.util.List;

// 주간 / 월간 리포트 (운동 기록으로 ReportStatsCalculator 통계를 계산해 표시)
public class WeeklyReportActivity extends AppCompatActivity {

    private final ReportStatsCalculator calculator = new ReportStatsCalculator();
    private List<SessionRecord> sessionRecords;

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

        sessionRecords = loadSessionRecords();

        // 주간 / 월간 탭: 단일 선택 (기본 주간)
        weeklyTabButton = findViewById(R.id.weeklyTabButton);
        monthlyTabButton = findViewById(R.id.monthlyTabButton);
        weeklyTabButton.setOnClickListener(v -> selectPeriod(Period.WEEKLY));
        monthlyTabButton.setOnClickListener(v -> selectPeriod(Period.MONTHLY));
        selectPeriod(Period.WEEKLY);
    }

    // TODO: Supabase에서 운동 기록 불러오기로 교체
    private List<SessionRecord> loadSessionRecords() {
        return SampleSessionData.create(this, LocalDate.now(ReportStatsCalculator.KOREA_ZONE));
    }

    // 선택한 탭만 selected로 바꾸고 오늘 날짜 기준으로 통계를 계산해 같은 화면 안에서 교체
    private void selectPeriod(Period period) {
        weeklyTabButton.setSelected(period == Period.WEEKLY);
        monthlyTabButton.setSelected(period == Period.MONTHLY);

        LocalDate today = LocalDate.now(ReportStatsCalculator.KOREA_ZONE);
        ReportStats stats = calculator.calculate(sessionRecords, period, today);

        TextView reportPeriodText = findViewById(R.id.reportPeriodText);
        TextView exerciseCountValueText = findViewById(R.id.exerciseCountValueText);
        TextView totalExerciseTimeValueText = findViewById(R.id.totalExerciseTimeValueText);
        TextView averageAccuracyValueText = findViewById(R.id.averageAccuracyValueText);
        AccuracyChartView accuracyChartView = findViewById(R.id.accuracyChartView);
        TextView mostPerformedExerciseNameText = findViewById(R.id.mostPerformedExerciseNameText);
        TextView mostPerformedExerciseCountText = findViewById(R.id.mostPerformedExerciseCountText);
        TextView frequentMistakeText = findViewById(R.id.frequentMistakeText);
        TextView frequentMistakeCountText = findViewById(R.id.frequentMistakeCountText);

        reportPeriodText.setText(formatPeriod(period, today));
        exerciseCountValueText.setText(getString(R.string.count_times_format, stats.exerciseCount));
        int minutes = stats.totalExerciseMinutes;
        totalExerciseTimeValueText.setText(getString(R.string.hours_minutes_format, minutes / 60, minutes % 60));
        averageAccuracyValueText.setText(getString(R.string.percent_format,
                (int) Math.round(stats.averageAccuracy)));

        int[] chartValues = new int[stats.chartAccuracies.length];
        for (int i = 0; i < chartValues.length; i++) {
            chartValues[i] = (int) Math.round(stats.chartAccuracies[i]);
        }
        int labelArrayResId = period == Period.WEEKLY ? R.array.weekday_labels : R.array.month_week_labels;
        accuracyChartView.setData(getResources().getStringArray(labelArrayResId), chartValues);

        // 기록이 없으면 이름 / 실수는 "-", 횟수는 0
        mostPerformedExerciseNameText.setText(stats.mostPerformedExerciseName == null
                ? getString(R.string.empty_value) : stats.mostPerformedExerciseName);
        mostPerformedExerciseCountText.setText(getString(R.string.count_times_format,
                stats.mostPerformedExerciseCount));
        frequentMistakeText.setText(stats.frequentMistakeJoint == null
                ? getString(R.string.empty_value) : jointMistakeLabel(stats.frequentMistakeJoint));
        frequentMistakeCountText.setText(getString(R.string.count_times_format, stats.frequentMistakeCount));
    }

    // 주간: "10월 19일 ~ 10월 25일", 월간: "2026년 10월"
    private String formatPeriod(Period period, LocalDate today) {
        if (period == Period.MONTHLY) {
            return getString(R.string.report_monthly_period_format, today.getYear(), today.getMonthValue());
        }
        LocalDate[] range = ReportStatsCalculator.periodRange(period, today);
        return getString(R.string.report_weekly_period_format,
                range[0].getMonthValue(), range[0].getDayOfMonth(),
                range[1].getMonthValue(), range[1].getDayOfMonth());
    }

    // 관절 키(knee, back)를 한글 표시로 변환, 모르는 키는 그대로 표시
    private String jointMistakeLabel(String joint) {
        switch (joint) {
            case "knee":
                return getString(R.string.mistake_joint_knee);
            case "back":
                return getString(R.string.mistake_joint_back);
            default:
                return joint;
        }
    }
}

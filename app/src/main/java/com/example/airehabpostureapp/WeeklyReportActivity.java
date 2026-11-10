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

import com.example.airehabpostureapp.network.ApiCalls;
import com.example.airehabpostureapp.network.AuthErrorMapper;
import com.example.airehabpostureapp.network.ExerciseSessionApi;
import com.example.airehabpostureapp.network.SessionManager;
import com.example.airehabpostureapp.network.SupabaseClient;
import com.example.airehabpostureapp.stats.JointKeys;
import com.example.airehabpostureapp.stats.ReportStats;
import com.example.airehabpostureapp.stats.ReportStatsCalculator;
import com.example.airehabpostureapp.stats.ReportStatsCalculator.Period;
import com.example.airehabpostureapp.stats.SessionRecord;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

// 주간 / 월간 리포트 (Supabase에서 불러온 운동 기록으로 ReportStatsCalculator 통계를 계산해 표시)
public class WeeklyReportActivity extends AppCompatActivity {

    // L7: 화면이 닫힐 때 진행 중인 요청 취소
    private final ApiCalls apiCalls = ApiCalls.bind(this);

    private final ReportStatsCalculator calculator = new ReportStatsCalculator();
    // 불러오기 전에는 null
    private List<SessionRecord> sessionRecords = null;
    private Period selectedPeriod = Period.WEEKLY;

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

        // 주간 / 월간 탭: 단일 선택 (기본 주간), 통계는 기록을 불러온 뒤 표시
        weeklyTabButton = findViewById(R.id.weeklyTabButton);
        monthlyTabButton = findViewById(R.id.monthlyTabButton);
        weeklyTabButton.setOnClickListener(v -> selectPeriod(Period.WEEKLY));
        monthlyTabButton.setOnClickListener(v -> selectPeriod(Period.MONTHLY));
        selectPeriod(Period.WEEKLY);

        loadSessionRecords();
    }

    // 이번 주(월~일)와 이번 달을 모두 포함하는 기간의 내 기록을 Supabase에서 불러옴
    // (주가 지난달에 걸칠 수 있어 두 범위를 합쳐 조회하고, 기간별 계산은 ReportStatsCalculator가 다시 거름)
    private void loadSessionRecords() {
        LocalDate today = LocalDate.now(ReportStatsCalculator.KOREA_ZONE);
        LocalDate[] week = ReportStatsCalculator.periodRange(Period.WEEKLY, today);
        LocalDate[] month = ReportStatsCalculator.periodRange(Period.MONTHLY, today);
        LocalDate startDate = week[0].isBefore(month[0]) ? week[0] : month[0];
        LocalDate endDate = week[1].isAfter(month[1]) ? week[1] : month[1];
        // 끝 날짜 다음 날 0시(한국 시간) 전까지
        Instant start = startDate.atStartOfDay(ReportStatsCalculator.KOREA_ZONE).toInstant();
        Instant end = endDate.plusDays(1).atStartOfDay(ReportStatsCalculator.KOREA_ZONE).toInstant();

        SessionManager sessionManager = new SessionManager(this);
        ExerciseSessionApi api = SupabaseClient.getRetrofit().create(ExerciseSessionApi.class);
        apiCalls.track(api.getRecordsBetween(sessionManager.getBearerToken(), "eq." + sessionManager.getUserId(),
                        Arrays.asList("gte." + start, "lt." + end)))
                .enqueue(new Callback<List<ExerciseSessionApi.RecordRow>>() {
                    @Override
                    public void onResponse(Call<List<ExerciseSessionApi.RecordRow>> call,
                                           Response<List<ExerciseSessionApi.RecordRow>> response) {
                        if (!response.isSuccessful() || response.body() == null) {
                            RetrySnackbar.show(WeeklyReportActivity.this, R.string.error_record_load_failed,
                                    WeeklyReportActivity.this::loadSessionRecords);
                            return;
                        }
                        List<SessionRecord> loaded = new ArrayList<>();
                        for (ExerciseSessionApi.RecordRow row : response.body()) {
                            loaded.add(row.toSessionRecord());
                        }
                        sessionRecords = loaded;
                        showStats();
                    }

                    @Override
                    public void onFailure(Call<List<ExerciseSessionApi.RecordRow>> call, Throwable t) {
                        RetrySnackbar.show(WeeklyReportActivity.this,
                                AuthErrorMapper.fromFailure(t, R.string.error_record_load_failed),
                                WeeklyReportActivity.this::loadSessionRecords);
                    }
                });
    }

    // 선택한 탭만 selected로 바꾸고 통계를 같은 화면 안에서 교체
    private void selectPeriod(Period period) {
        selectedPeriod = period;
        weeklyTabButton.setSelected(period == Period.WEEKLY);
        monthlyTabButton.setSelected(period == Period.MONTHLY);
        showStats();
    }

    // 오늘 날짜 기준으로 통계를 계산해 표시 (기록을 불러오기 전이면 아무것도 하지 않음)
    private void showStats() {
        if (sessionRecords == null) {
            return;
        }
        Period period = selectedPeriod;
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
        totalExerciseTimeValueText.setText(DurationFormatter.formatSecondsAsMinutes(this, stats.totalExerciseSeconds));
        // 정확도가 있는 기록이 없으면(자세 인식 미지원 운동만 한 경우 포함) "-"
        averageAccuracyValueText.setText(stats.hasAccuracy
                ? getString(R.string.percent_format, (int) Math.round(stats.averageAccuracy))
                : getString(R.string.empty_value));

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

    // 관절 키(knee, back, hip)를 한글 표시로 변환, 모르는 키는 그대로 표시
    private String jointMistakeLabel(String joint) {
        switch (joint) {
            case JointKeys.KNEE:
                return getString(R.string.mistake_joint_knee);
            case JointKeys.BACK:
                return getString(R.string.mistake_joint_back);
            case JointKeys.HIP:
                return getString(R.string.mistake_joint_hip);
            default:
                return joint;
        }
    }
}

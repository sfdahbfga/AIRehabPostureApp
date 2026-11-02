package com.example.airehabpostureapp;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.airehabpostureapp.network.AuthErrorMapper;
import com.example.airehabpostureapp.network.ExerciseSessionApi;
import com.example.airehabpostureapp.network.SessionManager;
import com.example.airehabpostureapp.network.SupabaseClient;
import com.example.airehabpostureapp.stats.ReportStatsCalculator;
import com.example.airehabpostureapp.stats.ReportStatsCalculator.Period;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

// 운동 기록 목록: exercise_sessions의 내 기록을 최신순으로 표시
// 주간 / 월간 필터는 ReportStatsCalculator와 같은 기준 (한국 시간, 이번 주 월~일 / 이번 달)
public class RecordListActivity extends AppCompatActivity {

    private static final int EMPTY_TEXT_MARGIN_TOP_DP = 48;

    private View[] filterButtons;
    // null = 전체, 그 외 주간 / 월간
    private Period selectedPeriod = null;
    // 불러오기 전에는 null
    private List<ExerciseSessionApi.RecordRow> records = null;
    private LinearLayout recordListContainer;
    // 코드에서 추가한 카드 / 안내 문구 (필터를 바꿀 때 지우고 다시 추가)
    private final List<View> addedViews = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 흰 배경 위에서 상태바 아이콘이 어둡게 보이도록 light 스타일 사용
        EdgeToEdge.enable(this,
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT));
        setContentView(R.layout.activity_record_list);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.recordListRoot), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 레이아웃의 예시 카드 4개는 숨기고, 불러온 기록 수만큼 item_record_card를 추가
        recordListContainer = findViewById(R.id.recordListContainer);
        int[] sampleCardIds = {R.id.recordCard1, R.id.recordCard2, R.id.recordCard3, R.id.recordCard4};
        for (int id : sampleCardIds) {
            findViewById(id).setVisibility(View.GONE);
        }

        // 기간 필터: 단일 선택 (기본 전체) → 전체 / 주간 / 월간
        filterButtons = new View[]{
                findViewById(R.id.filterAllButton),
                findViewById(R.id.filterWeeklyButton),
                findViewById(R.id.filterMonthlyButton)
        };
        Period[] filterPeriods = {null, Period.WEEKLY, Period.MONTHLY};
        for (int i = 0; i < filterButtons.length; i++) {
            final Period period = filterPeriods[i];
            filterButtons[i].setOnClickListener(v -> selectPeriodFilter(v, period));
        }
        selectPeriodFilter(filterButtons[0], null);

        // 제목 옆 링크: 20_WeeklyReport로 이동 (뒤로가기 시 이 화면으로 복귀)
        findViewById(R.id.weeklyReportLinkText).setOnClickListener(v ->
                startActivity(new Intent(this, WeeklyReportActivity.class)));

        // Bottom Navigation: 현재 화면은 기록
        findViewById(R.id.bottomNavRecord).setSelected(true);
        // 홈 / 운동 / 마이: Back Stack에 이미 있으면 그 화면을 재사용하고 위의 화면은 종료 (화면 중복 방지)
        findViewById(R.id.bottomNavHome).setOnClickListener(v -> moveToTab(HomeActivity.class));
        findViewById(R.id.bottomNavExercise).setOnClickListener(v -> moveToTab(ExerciseListActivity.class));
        findViewById(R.id.bottomNavMyPage).setOnClickListener(v -> moveToTab(MyPageActivity.class));

        loadRecords();
    }

    private void loadRecords() {
        SessionManager sessionManager = new SessionManager(this);
        ExerciseSessionApi api = SupabaseClient.getRetrofit().create(ExerciseSessionApi.class);
        api.getRecords(sessionManager.getBearerToken(), "eq." + sessionManager.getUserId())
                .enqueue(new Callback<List<ExerciseSessionApi.RecordRow>>() {
                    @Override
                    public void onResponse(Call<List<ExerciseSessionApi.RecordRow>> call,
                                           Response<List<ExerciseSessionApi.RecordRow>> response) {
                        if (!response.isSuccessful() || response.body() == null) {
                            Toast.makeText(RecordListActivity.this, R.string.error_record_load_failed,
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        records = response.body();
                        showRecords();
                    }

                    @Override
                    public void onFailure(Call<List<ExerciseSessionApi.RecordRow>> call, Throwable t) {
                        Toast.makeText(RecordListActivity.this,
                                AuthErrorMapper.fromFailure(t, R.string.error_record_load_failed),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // 누른 필터만 selected, 나머지는 해제하고 목록을 다시 표시
    private void selectPeriodFilter(View clickedButton, Period period) {
        for (View button : filterButtons) {
            button.setSelected(button == clickedButton);
        }
        selectedPeriod = period;
        showRecords();
    }

    // 선택한 기간의 기록만 카드로 표시 (불러오기 전이면 아무것도 하지 않음)
    private void showRecords() {
        if (records == null) {
            return;
        }
        for (View view : addedViews) {
            recordListContainer.removeView(view);
        }
        addedViews.clear();

        LayoutInflater inflater = LayoutInflater.from(this);
        for (ExerciseSessionApi.RecordRow record : records) {
            LocalDate date = koreaDate(record.startedAtInstant());
            if (!isInSelectedPeriod(date)) {
                continue;
            }
            View card = inflater.inflate(R.layout.item_record_card, recordListContainer, false);
            TextView dateText = card.findViewById(R.id.recordDateText);
            TextView nameText = card.findViewById(R.id.recordExerciseNameText);
            TextView summaryText = card.findViewById(R.id.recordSummaryText);

            dateText.setText(date == null ? getString(R.string.empty_value)
                    : getString(R.string.record_date_format, date.getMonthValue(), date.getDayOfMonth()));
            nameText.setText(record.exerciseName);
            int durationSeconds = record.durationSeconds == null ? 0 : record.durationSeconds;
            int accuracy = record.averageAccuracy == null ? 0 : (int) Math.round(record.averageAccuracy);
            summaryText.setText(getString(R.string.record_summary_time_format,
                    DurationFormatter.formatSecondsAsMinutes(this, durationSeconds), accuracy));
            card.setOnClickListener(v -> openRecordDetail(record.id));

            recordListContainer.addView(card);
            addedViews.add(card);
        }

        if (addedViews.isEmpty()) {
            showEmptyText(selectedPeriod == null
                    ? R.string.record_list_empty_all : R.string.record_list_empty_period);
        }
    }

    // 기록이 없을 때 안내 문구 (레이아웃에 안내 TextView가 없어 코드에서 추가)
    private void showEmptyText(int messageRes) {
        TextView emptyText = new TextView(this);
        emptyText.setText(messageRes);
        emptyText.setTextColor(ContextCompat.getColor(this, R.color.gray));
        emptyText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        emptyText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                EMPTY_TEXT_MARGIN_TOP_DP, getResources().getDisplayMetrics()));
        recordListContainer.addView(emptyText, params);
        addedViews.add(emptyText);
    }

    private boolean isInSelectedPeriod(LocalDate date) {
        if (selectedPeriod == null) {
            return true;
        }
        if (date == null) {
            return false;
        }
        LocalDate[] range = ReportStatsCalculator.periodRange(selectedPeriod,
                LocalDate.now(ReportStatsCalculator.KOREA_ZONE));
        return !date.isBefore(range[0]) && !date.isAfter(range[1]);
    }

    private static LocalDate koreaDate(Instant instant) {
        return instant == null ? null : instant.atZone(ReportStatsCalculator.KOREA_ZONE).toLocalDate();
    }

    private void openRecordDetail(String sessionId) {
        Intent intent = new Intent(this, RecordDetailActivity.class);
        intent.putExtra(RecordDetailActivity.EXTRA_SESSION_ID, sessionId);
        startActivity(intent);
    }

    private void moveToTab(Class<?> activityClass) {
        Intent intent = new Intent(this, activityClass);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }
}

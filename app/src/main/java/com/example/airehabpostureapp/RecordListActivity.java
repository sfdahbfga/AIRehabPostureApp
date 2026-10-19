package com.example.airehabpostureapp;

import android.content.Intent;
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

// 운동 기록 목록 (현재는 고정 예시 데이터 4개만 표시, Supabase 연동 전)
public class RecordListActivity extends AppCompatActivity {

    // TODO: Supabase 운동 기록 연동 후 교체 (현재는 임시 예시 데이터, 날짜 / 운동명은 strings.xml)
    // 인덱스 = 기록 카드 순서 (recordCard1 ~ recordCard4)
    private final int[] recordNameResIds = {R.string.record1_exercise_name, R.string.record2_exercise_name,
            R.string.record3_exercise_name, R.string.record4_exercise_name};
    private final int[] recordDetailDateResIds = {R.string.record1_detail_date, R.string.record2_detail_date,
            R.string.record3_detail_date, R.string.record4_detail_date};
    private final int[] recordDurationMinutes = {15, 12, 15, 15};
    private final int[] recordAccuracies = {88, 85, 90, 82};

    private View[] filterButtons;

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

        // 기간 필터: 단일 선택 (기본 전체)
        // TODO: 기록 데이터 연동 후 주간 / 월간 필터링 적용 (현재는 선택 상태만 변경)
        filterButtons = new View[]{
                findViewById(R.id.filterAllButton),
                findViewById(R.id.filterWeeklyButton),
                findViewById(R.id.filterMonthlyButton)
        };
        for (View button : filterButtons) {
            button.setOnClickListener(this::selectPeriodFilter);
        }
        selectPeriodFilter(filterButtons[0]);

        // 제목 옆 링크: 20_WeeklyReport로 이동 (뒤로가기 시 이 화면으로 복귀)
        findViewById(R.id.weeklyReportLinkText).setOnClickListener(v ->
                startActivity(new Intent(this, WeeklyReportActivity.class)));

        // 기록 카드: 요약 정보 표시 + 클릭 시 19_RecordDetail로 이동 (선택한 기록 정보 전달)
        TextView[] recordSummaryTexts = {
                findViewById(R.id.recordSummaryText1), findViewById(R.id.recordSummaryText2),
                findViewById(R.id.recordSummaryText3), findViewById(R.id.recordSummaryText4)
        };
        int[] recordCardIds = {R.id.recordCard1, R.id.recordCard2, R.id.recordCard3, R.id.recordCard4};
        for (int i = 0; i < recordCardIds.length; i++) {
            recordSummaryTexts[i].setText(getString(R.string.record_summary_format,
                    recordDurationMinutes[i], recordAccuracies[i]));
            final int recordIndex = i;
            findViewById(recordCardIds[i]).setOnClickListener(v -> openRecordDetail(recordIndex));
        }

        // Bottom Navigation: 현재 화면은 기록
        findViewById(R.id.bottomNavRecord).setSelected(true);
        // 홈 / 운동 / 마이: Back Stack에 이미 있으면 그 화면을 재사용하고 위의 화면은 종료 (화면 중복 방지)
        findViewById(R.id.bottomNavHome).setOnClickListener(v -> moveToTab(HomeActivity.class));
        findViewById(R.id.bottomNavExercise).setOnClickListener(v -> moveToTab(ExerciseListActivity.class));
        findViewById(R.id.bottomNavMyPage).setOnClickListener(v -> moveToTab(MyPageActivity.class));
    }

    // 누른 필터만 selected, 나머지는 해제
    private void selectPeriodFilter(View clickedButton) {
        for (View button : filterButtons) {
            button.setSelected(button == clickedButton);
        }
    }

    private void openRecordDetail(int recordIndex) {
        Intent intent = new Intent(this, RecordDetailActivity.class);
        intent.putExtra(RecordDetailActivity.EXTRA_EXERCISE_NAME, getString(recordNameResIds[recordIndex]));
        intent.putExtra(RecordDetailActivity.EXTRA_EXERCISE_DATE, getString(recordDetailDateResIds[recordIndex]));
        intent.putExtra(RecordDetailActivity.EXTRA_DURATION, recordDurationMinutes[recordIndex]);
        intent.putExtra(RecordDetailActivity.EXTRA_ACCURACY, recordAccuracies[recordIndex]);
        startActivity(intent);
    }

    private void moveToTab(Class<?> activityClass) {
        Intent intent = new Intent(this, activityClass);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }
}

package com.example.airehabpostureapp;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.View;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

// 현재는 고정 예시 운동 4개를 화면에 두고, 필터 조건에 따라 표시/숨김만 처리 (DB 연동 전)
public class ExerciseListActivity extends AppCompatActivity {

    // 목적 / 난이도 선택지: 화면 문자열과 필터 키를 같은 순서로 관리 (null = 전체)
    private static final int[] PURPOSE_LABELS = {
            R.string.filter_all, R.string.purpose_pain_relief, R.string.purpose_range_of_motion,
            R.string.purpose_strength, R.string.purpose_flexibility
    };
    private static final String[] PURPOSE_KEYS = {
            null, "pain_relief", "range_of_motion", "strength", "flexibility"
    };
    private static final int[] DIFFICULTY_LABELS = {
            R.string.filter_all, R.string.experience_beginner,
            R.string.experience_intermediate, R.string.experience_advanced
    };
    private static final String[] DIFFICULTY_KEYS = {
            null, "beginner", "intermediate", "advanced"
    };

    // 예시 운동 1개의 필터용 정보
    private static class ExerciseItem {
        final View card;
        final TextView nameText;
        final Set<String> bodyParts;
        final String purpose;
        final String difficulty;

        ExerciseItem(View card, TextView nameText, String purpose, String difficulty, String... bodyParts) {
            this.card = card;
            this.nameText = nameText;
            this.purpose = purpose;
            this.difficulty = difficulty;
            this.bodyParts = new HashSet<>(Arrays.asList(bodyParts));
        }
    }

    private ExerciseItem[] exercises;
    private View[] bodyPartFilterButtons;
    private TextView purposeFilterButton;
    private TextView difficultyFilterButton;
    private TextView emptyText;

    // 현재 필터 상태 (null = 전체)
    private String searchQuery = "";
    private String selectedBodyPart = null;
    private String selectedPurpose = null;
    private String selectedDifficulty = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 흰 배경 위에서 상태바 아이콘이 어둡게 보이도록 light 스타일 사용
        EdgeToEdge.enable(this,
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT));
        setContentView(R.layout.activity_exercise_list);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.exerciseListRoot), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 이전 화면으로 돌아가기: 새 Activity를 만들지 않고 현재 화면만 종료
        findViewById(R.id.backButton).setOnClickListener(v -> finish());

        // 예시 데이터: 신체 부위 / 목적 / 난이도 키 (DB 연동 시 교체)
        exercises = new ExerciseItem[]{
                new ExerciseItem(findViewById(R.id.exerciseCard1), findViewById(R.id.exerciseNameText1),
                        "range_of_motion", "beginner", "knee"),
                new ExerciseItem(findViewById(R.id.exerciseCard2), findViewById(R.id.exerciseNameText2),
                        "strength", "beginner", "knee"),
                new ExerciseItem(findViewById(R.id.exerciseCard3), findViewById(R.id.exerciseNameText3),
                        "flexibility", "beginner", "knee", "waist"),
                new ExerciseItem(findViewById(R.id.exerciseCard4), findViewById(R.id.exerciseNameText4),
                        "strength", "intermediate", "knee")
        };
        emptyText = findViewById(R.id.exerciseListEmptyText);

        // 상세 데이터가 있는 운동은 상세 화면으로 이동, 없으면 선택한 운동 이름만 안내
        for (ExerciseItem exercise : exercises) {
            exercise.card.setOnClickListener(v -> {
                String name = exercise.nameText.getText().toString();
                if (ExerciseDetailActivity.hasDetail(name)) {
                    Intent intent = new Intent(this, ExerciseDetailActivity.class);
                    intent.putExtra(ExerciseDetailActivity.EXTRA_EXERCISE_NAME, name);
                    startActivity(intent);
                } else {
                    Toast.makeText(this, getString(R.string.exercise_selected, name),
                            Toast.LENGTH_SHORT).show();
                }
            });
        }

        // 검색: 입력할 때마다 이름에 검색어가 포함된 운동만 표시
        EditText searchEditText = findViewById(R.id.exerciseSearchEditText);
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                searchQuery = s.toString().trim();
                applyFilters();
            }
        });

        // 신체 부위: 단일 선택 (기본 = 전체)
        bodyPartFilterButtons = new View[]{
                findViewById(R.id.filterAllButton), findViewById(R.id.filterNeckButton),
                findViewById(R.id.filterShoulderButton), findViewById(R.id.filterBackButton),
                findViewById(R.id.filterKneeButton)
        };
        for (View button : bodyPartFilterButtons) {
            button.setOnClickListener(this::selectBodyPartFilter);
        }
        selectBodyPartFilter(findViewById(R.id.filterAllButton));

        // 목적 / 난이도: 버튼을 누르면 선택 메뉴 표시
        purposeFilterButton = findViewById(R.id.purposeFilterButton);
        difficultyFilterButton = findViewById(R.id.difficultyFilterButton);
        updateFilterButton(purposeFilterButton, R.string.filter_purpose, R.string.filter_all, false);
        updateFilterButton(difficultyFilterButton, R.string.filter_difficulty, R.string.filter_all, false);

        purposeFilterButton.setOnClickListener(v ->
                showFilterMenu(purposeFilterButton, PURPOSE_LABELS, index -> {
                    selectedPurpose = PURPOSE_KEYS[index];
                    updateFilterButton(purposeFilterButton, R.string.filter_purpose,
                            PURPOSE_LABELS[index], selectedPurpose != null);
                }));
        difficultyFilterButton.setOnClickListener(v ->
                showFilterMenu(difficultyFilterButton, DIFFICULTY_LABELS, index -> {
                    selectedDifficulty = DIFFICULTY_KEYS[index];
                    updateFilterButton(difficultyFilterButton, R.string.filter_difficulty,
                            DIFFICULTY_LABELS[index], selectedDifficulty != null);
                }));

        // Bottom Navigation: 현재 화면은 운동
        findViewById(R.id.bottomNavExercise).setSelected(true);
        // 홈은 이전 화면(Home)이므로 현재 화면만 종료
        findViewById(R.id.bottomNavHome).setOnClickListener(v -> finish());
        findViewById(R.id.bottomNavRecord).setOnClickListener(v ->
                startActivity(new Intent(this, RecordListActivity.class)));
        findViewById(R.id.bottomNavMyPage).setOnClickListener(v ->
                startActivity(new Intent(this, MyPageActivity.class)));
    }

    // 누른 Chip만 selected, 나머지는 해제 ("all" 태그 = 전체)
    private void selectBodyPartFilter(View clickedButton) {
        for (View button : bodyPartFilterButtons) {
            button.setSelected(button == clickedButton);
        }
        String tag = (String) clickedButton.getTag();
        selectedBodyPart = "all".equals(tag) ? null : tag;
        applyFilters();
    }

    private interface OnFilterSelected {
        void onSelected(int index);
    }

    private void showFilterMenu(View anchor, int[] labels, OnFilterSelected listener) {
        PopupMenu popupMenu = new PopupMenu(this, anchor);
        for (int i = 0; i < labels.length; i++) {
            popupMenu.getMenu().add(Menu.NONE, i, i, labels[i]);
        }
        popupMenu.setOnMenuItemClickListener(item -> {
            listener.onSelected(item.getItemId());
            applyFilters();
            return true;
        });
        popupMenu.show();
    }

    // 버튼 문구를 "목적: 전체" 형태로 바꾸고, 전체가 아니면 selected로 강조
    private void updateFilterButton(TextView button, @StringRes int formatRes,
                                    @StringRes int valueRes, boolean isActive) {
        button.setText(getString(formatRes, getString(valueRes)));
        button.setSelected(isActive);
    }

    // 검색어 + 신체 부위 + 목적 + 난이도를 모두 만족하는 운동만 표시
    private void applyFilters() {
        int visibleCount = 0;
        for (ExerciseItem exercise : exercises) {
            boolean matches = exercise.nameText.getText().toString().contains(searchQuery)
                    && (selectedBodyPart == null || exercise.bodyParts.contains(selectedBodyPart))
                    && (selectedPurpose == null || selectedPurpose.equals(exercise.purpose))
                    && (selectedDifficulty == null || selectedDifficulty.equals(exercise.difficulty));
            exercise.card.setVisibility(matches ? View.VISIBLE : View.GONE);
            if (matches) {
                visibleCount++;
            }
        }
        emptyText.setVisibility(visibleCount == 0 ? View.VISIBLE : View.GONE);
    }
}

package com.example.airehabpostureapp;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.airehabpostureapp.pose.ExerciseCriteria;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

// 운동 상세: 운동 이름으로 아래 DETAILS에서 설명 / 목적 / 관절 / 시간 / 횟수 / 난이도 문자열을 찾아 표시
// (DB 연동 전 고정 데이터. 데이터가 없는 운동은 레이아웃 기본값(무릎 굽힘 운동)이 그대로 보임)
public class ExerciseDetailActivity extends AppCompatActivity {

    // 이전 화면에서 선택한 운동 이름 (화면 표시용)
    public static final String EXTRA_EXERCISE_NAME = "com.example.airehabpostureapp.EXTRA_EXERCISE_NAME";
    // 이전 화면에서 선택한 운동의 고정 키 (ExerciseCriteria.id). 없으면 이름으로 키를 찾음 (L11)
    public static final String EXTRA_EXERCISE_ID = "com.example.airehabpostureapp.EXTRA_EXERCISE_ID";

    // 운동 하나의 상세 문자열
    private static final class Detail {
        @StringRes final int description;
        @StringRes final int purpose;
        @StringRes final int joints;
        @StringRes final int duration;
        @StringRes final int repetitions;
        @StringRes final int difficulty;

        Detail(int description, int purpose, int joints, int duration, int repetitions, int difficulty) {
            this.description = description;
            this.purpose = purpose;
            this.joints = joints;
            this.duration = duration;
            this.repetitions = repetitions;
            this.difficulty = difficulty;
        }
    }

    // 운동 고정 키(ExerciseCriteria.id) → 상세 문자열 (L11)
    private static final Map<String, Detail> DETAILS;

    static {
        Map<String, Detail> map = new HashMap<>();
        map.put(ExerciseCriteria.ID_KNEE_FLEXION, new Detail(R.string.exercise_description_knee_flexion,
                R.string.exercise_purpose_knee_flexion, R.string.exercise_joints_knee_flexion,
                R.string.exercise_duration_knee_flexion, R.string.exercise_repetitions_knee_flexion,
                R.string.exercise_difficulty_knee_flexion));
        map.put(ExerciseCriteria.ID_CHAIR_SQUAT, new Detail(R.string.exercise_description_chair_squat,
                R.string.exercise_purpose_chair_squat, R.string.exercise_joints_chair_squat,
                R.string.exercise_duration_chair_squat, R.string.exercise_repetitions_chair_squat,
                R.string.exercise_difficulty_chair_squat));
        map.put(ExerciseCriteria.ID_LUNGE, new Detail(R.string.exercise_description_lunge,
                R.string.exercise_purpose_lunge, R.string.exercise_joints_lunge,
                R.string.exercise_duration_lunge, R.string.exercise_repetitions_lunge,
                R.string.exercise_difficulty_lunge));
        map.put(ExerciseCriteria.ID_SEATED_HAMSTRING_STRETCH, new Detail(R.string.exercise_description_hamstring_stretch,
                R.string.exercise_purpose_hamstring_stretch, R.string.exercise_joints_hamstring_stretch,
                R.string.exercise_duration_hamstring_stretch, R.string.exercise_repetitions_hamstring_stretch,
                R.string.exercise_difficulty_hamstring_stretch));
        DETAILS = Collections.unmodifiableMap(map);
    }

    /** 상세 데이터가 있는 운동(고정 키)이면 true (운동 목록에서 상세 화면으로 보낼지 정할 때 사용) */
    public static boolean hasDetail(@Nullable String exerciseId) {
        return exerciseId != null && DETAILS.containsKey(exerciseId);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 흰 배경 위에서 상태바 아이콘이 어둡게 보이도록 light 스타일 사용
        EdgeToEdge.enable(this,
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT));
        setContentView(R.layout.activity_exercise_detail);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.exerciseDetailRoot), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 전달받은 운동 이름이 있으면 표시 (없으면 레이아웃 기본값 유지)
        TextView exerciseTitleText = findViewById(R.id.exerciseTitleText);
        String exerciseName = getIntent().getStringExtra(EXTRA_EXERCISE_NAME);
        if (exerciseName != null) {
            exerciseTitleText.setText(exerciseName);
        }
        // 키로 상세 / 운동 기준을 찾음. 이름만 받은 경우(홈의 추천 운동)는 이름으로 키를 찾음
        String exerciseId = getIntent().getStringExtra(EXTRA_EXERCISE_ID);
        if (exerciseId == null) {
            exerciseId = ExerciseCriteria.idForName(exerciseName);
        }
        String selectedExerciseId = exerciseId;
        Detail detail = exerciseId == null ? null : DETAILS.get(exerciseId);
        if (detail != null) {
            ((TextView) findViewById(R.id.exerciseDescriptionText)).setText(detail.description);
            ((TextView) findViewById(R.id.exercisePurposeText)).setText(detail.purpose);
            ((TextView) findViewById(R.id.exerciseJointsText)).setText(detail.joints);
            ((TextView) findViewById(R.id.exerciseDurationText)).setText(detail.duration);
            ((TextView) findViewById(R.id.exerciseRepetitionText)).setText(detail.repetitions);
            ((TextView) findViewById(R.id.exerciseDifficultyText)).setText(detail.difficulty);
        }

        // 이전 화면(ExerciseList)으로 돌아가기: 새 Activity를 만들지 않고 현재 화면만 종료
        findViewById(R.id.backButton).setOnClickListener(v -> finish());

        // 운동 전 컨디션 체크(11_PreCheck)로 이동 (화면에 표시된 운동 이름을 운동 흐름 끝까지 전달)
        findViewById(R.id.startExerciseButton).setOnClickListener(v -> {
            Intent intent = new Intent(this, PreCheckActivity.class);
            intent.putExtra(ExerciseFlowExtras.EXERCISE_NAME, exerciseTitleText.getText().toString());
            if (selectedExerciseId != null) {
                intent.putExtra(ExerciseFlowExtras.EXERCISE_ID, selectedExerciseId);
            }
            startActivity(intent);
        });
    }
}

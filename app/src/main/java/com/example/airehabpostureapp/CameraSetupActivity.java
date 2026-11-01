package com.example.airehabpostureapp;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

// 현재는 UI와 화면 흐름만 구현 (CameraX Preview / MediaPipe 전신 인식 연동 전)
public class CameraSetupActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 흰 배경 위에서 상태바 아이콘이 어둡게 보이도록 light 스타일 사용
        EdgeToEdge.enable(this,
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT));
        setContentView(R.layout.activity_camera_setup);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.cameraSetupRoot), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 이전 화면(PreCheck)으로 돌아가기: 새 Activity를 만들지 않고 현재 화면만 종료
        findViewById(R.id.backButton).setOnClickListener(v -> finish());

        // TODO: CameraX PreviewView 연결 후 MediaPipe 결과로 상태 확인 항목(전신/위치/관절 인식) 갱신
        // 실시간 운동 화면(13_Exercise)으로 이동 (운동 이름 / PreCheck 입력값은 그대로 전달)
        findViewById(R.id.startExerciseButton).setOnClickListener(v -> {
            Intent intent = new Intent(this, ExerciseActivity.class);
            intent.putExtras(getIntent());
            startActivity(intent);
        });
    }
}

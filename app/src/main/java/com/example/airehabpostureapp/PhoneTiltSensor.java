package com.example.airehabpostureapp;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import com.example.airehabpostureapp.pose.PhoneTilt;

/**
 * 휴대폰이 화면 안에서 좌우로 기운 각도(roll)를 잰다. 중력 센서(TYPE_GRAVITY)를 쓰고,
 * 없으면 가속도 센서에 저역 통과 필터를 걸어 쓴다. 둘 다 없으면 항상 NaN (보정 없이 동작)
 * 계산은 PhoneTilt(순수 Java). getRollDegrees()는 어느 스레드에서 읽어도 된다.
 */
public class PhoneTiltSensor implements SensorEventListener {

    private final SensorManager sensorManager;
    private final Sensor sensor;
    private final boolean usingGravity;
    private float[] filtered;
    private volatile double rollDegrees = Double.NaN;

    public PhoneTiltSensor(Context context) {
        sensorManager = (SensorManager) context.getApplicationContext().getSystemService(Context.SENSOR_SERVICE);
        Sensor gravity = sensorManager == null ? null : sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        usingGravity = gravity != null;
        sensor = gravity != null ? gravity
                : sensorManager == null ? null : sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    public void start() {
        if (sensor != null) {
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI);
        }
    }

    public void stop() {
        if (sensor != null) {
            sensorManager.unregisterListener(this);
        }
        filtered = null;
    }

    /** 실제 위쪽이 화면 오른쪽으로 기운 각도(도). 센서가 없거나 아직 값이 없으면 NaN */
    public double getRollDegrees() {
        return rollDegrees;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float[] values = {event.values[0], event.values[1], event.values[2]};
        if (!usingGravity) {
            filtered = PhoneTilt.lowPass(filtered, values);
            values = filtered;
        }
        rollDegrees = PhoneTilt.rollDegrees(values[0], values[1], values[2]);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}

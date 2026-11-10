package com.example.airehabpostureapp.pose;

import static org.junit.Assert.assertEquals;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * M7: 재사용 Bitmap에 그리는 회전(PoseLandmarkerHelper.rotate)이
 * 기존 방식(Bitmap.createBitmap + Matrix.postRotate)과 픽셀 단위로 같은지 확인
 */
@RunWith(AndroidJUnit4.class)
public class PoseRotationTest {

    // 가로 6 x 세로 4, 픽셀마다 다른 색
    private static Bitmap sample() {
        int width = 6;
        int height = 4;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                bitmap.setPixel(x, y, 0xFF000000 | (x * 40) << 16 | (y * 60) << 8 | (x + y) * 10);
            }
        }
        return bitmap;
    }

    private static void assertSameAsCreateBitmap(int degrees) {
        Bitmap source = sample();
        Matrix oldMatrix = new Matrix();
        oldMatrix.postRotate(degrees);
        Bitmap expected = Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), oldMatrix, true);

        Bitmap actual = Bitmap.createBitmap(expected.getWidth(), expected.getHeight(), Bitmap.Config.ARGB_8888);
        PoseLandmarkerHelper.rotate(source, degrees, new Canvas(actual), new Matrix());

        assertEquals(expected.getWidth(), actual.getWidth());
        assertEquals(expected.getHeight(), actual.getHeight());
        for (int y = 0; y < expected.getHeight(); y++) {
            for (int x = 0; x < expected.getWidth(); x++) {
                assertEquals(degrees + "도 (" + x + "," + y + ")", expected.getPixel(x, y), actual.getPixel(x, y));
            }
        }
    }

    @Test
    public void rotate90_matchesCreateBitmap() {
        assertSameAsCreateBitmap(90);
    }

    @Test
    public void rotate180_matchesCreateBitmap() {
        assertSameAsCreateBitmap(180);
    }

    @Test
    public void rotate270_matchesCreateBitmap() {
        assertSameAsCreateBitmap(270);
    }
}

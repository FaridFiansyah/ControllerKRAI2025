package com.example.basket;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;


public class DialView extends View {

    private Paint textPaint;
    private Paint linePaint;
    private Paint indicatorPaint;

    private float yaw = 0f; // Nilai Yaw saat ini (0-360)

    public DialView(Context context) {
        super(context);
        init();
    }
    public DialView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DialView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // Paint untuk teks (angka derajat)
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(55f); // Ukuran teks bisa disesuaikan
        textPaint.setTextAlign(Paint.Align.CENTER);

        // Paint untuk garis-garis penanda
        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(Color.WHITE);
        linePaint.setStrokeWidth(4f);

        // Paint untuk indikator segitiga di tengah
        indicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        indicatorPaint.setColor(Color.WHITE);
        indicatorPaint.setStyle(Paint.Style.FILL);
    }

    /**
     * Fungsi untuk mengupdate nilai yaw dan menggambar ulang view.
     * @param newYaw Nilai yaw baru antara 0 dan 360.
     */
    public void setYaw(float newYaw) {
        // Normalisasi yaw agar selalu dalam rentang 0-360
        this.yaw = (newYaw % 360 + 360) % 360;
        invalidate(); // Meminta view untuk digambar ulang (memanggil onDraw)
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int viewWidth = getWidth();
        int viewHeight = getHeight();
        int centerX = viewWidth / 2;

        // Gambar indikator segitiga di tengah atas
        android.graphics.Path indicatorPath = new android.graphics.Path();
        indicatorPath.moveTo(centerX - 20, 5);
        indicatorPath.lineTo(centerX + 20, 5);
        indicatorPath.lineTo(centerX, 45);
        indicatorPath.close();
        canvas.drawPath(indicatorPath, indicatorPaint);

        // --- Logika Menggambar Dial ---
        final float degreesToShow = 90f; // Jumlah derajat yang terlihat di layar
        final float pixelsPerDegree = viewWidth / degreesToShow;

        // Hitung rentang derajat yang akan digambar (dengan buffer)
        float startDegree = yaw - (degreesToShow / 2) - 30;
        float endDegree = yaw + (degreesToShow / 2) + 30;

        // Loop untuk setiap derajat dalam rentang
        for (float degree = startDegree; degree <= endDegree; degree += 1) {
            float normalizedDegree = (degree % 360 + 360) % 360;

            // Hitung posisi X di canvas berdasarkan yaw saat ini
            float xPos = centerX + (degree - yaw) * pixelsPerDegree;

            // Hanya gambar jika berada dalam area pandang (plus buffer)
            if (xPos > -50 && xPos < viewWidth + 50) {
                // Gambar garis penanda panjang dan teks setiap 15 derajat
                if ((int) normalizedDegree % 15 == 0) {
                    canvas.drawLine(xPos, 60, xPos, 120, linePaint); // Garis panjang
                    String textToShow = String.valueOf((int) normalizedDegree);
                    canvas.drawText(textToShow, xPos, 165, textPaint); // Gambar angka
                }
                // Gambar garis penanda pendek setiap 5 derajat
                else if ((int) normalizedDegree % 5 == 0) {
                    canvas.drawLine(xPos, 60, xPos, 90, linePaint); // Garis pendek
                }
            }
        }
    }
}

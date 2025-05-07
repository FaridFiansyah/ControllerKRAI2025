package com.example.basket;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class JoystickView extends View {
    // Variables
    private Paint backgroundPaint;
    private Paint handlePaint;
    private Paint borderPaint;

    private int centerX;
    private int centerY;
    private int baseRadius;
    private int handleRadius;

    private int movementRadius;
    private float posX;
    private float posY;

    private OnJoystickMoveListener moveListener;
    private float lastAngle = 0;
    private float lastPower = 0;

    public JoystickView(Context context) {
        super(context);
        initJoystick();
    }

    public JoystickView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initJoystick();
    }

    public JoystickView(Context context, AttributeSet attrs, int defaultStyle) {
        super(context, attrs, defaultStyle);
        initJoystick();
    }

    private void initJoystick() {
        backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        backgroundPaint.setColor(Color.DKGRAY);
        backgroundPaint.setAlpha(100);
        backgroundPaint.setStyle(Paint.Style.FILL);

        handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        handlePaint.setColor(Color.LTGRAY);
        handlePaint.setAlpha(200);
        handlePaint.setStyle(Paint.Style.FILL);

        borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setColor(Color.WHITE);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(3);

        posX = 0;
        posY = 0;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);

        // Calculate the center of the view
        centerX = w / 2;
        centerY = h / 2;

        // Calculate size based on shorter dimension
        int minDimension = Math.min(w, h);
        baseRadius = minDimension / 3;
        handleRadius = minDimension / 6;
        movementRadius = baseRadius - handleRadius;

        posX = centerX;
        posY = centerY;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // Draw the base circle
        canvas.drawCircle(centerX, centerY, baseRadius, backgroundPaint);
        canvas.drawCircle(centerX, centerY, baseRadius, borderPaint);

        // Draw the handle
        canvas.drawCircle(posX, posY, handleRadius, handlePaint);
        canvas.drawCircle(posX, posY, handleRadius, borderPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float newX = event.getX();
        float newY = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                // Calculate the distance from center
                float dx = newX - centerX;
                float dy = newY - centerY;
                float distance = (float) Math.sqrt(dx * dx + dy * dy);

                // Calculate angle and power
                float angle = (float) Math.toDegrees(Math.atan2(dy, dx));
                if (angle < 0) angle += 360;

                // Limit the joystick movement to the defined movementRadius
                if (distance > movementRadius) {
                    posX = (float) (centerX + (movementRadius * Math.cos(Math.toRadians(angle))));
                    posY = (float) (centerY + (movementRadius * Math.sin(Math.toRadians(angle))));
                } else {
                    posX = newX;
                    posY = newY;
                }

                // Calculate power (0-100%)
                float power = distance / movementRadius * 100;
                if (power > 100) power = 100;

                // Notify listener if values changed or to ensure continuous updates
                if (moveListener != null) {
                    moveListener.onValueChanged(angle, power);
                    lastAngle = angle;
                    lastPower = power;
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                // Return to center position
                posX = centerX;
                posY = centerY;

                // Notify with 0 power
                if (moveListener != null) {
                    moveListener.onValueChanged(0, 0);
                    lastAngle = 0;
                    lastPower = 0;
                }
                break;
        }

        invalidate();
        return true;
    }

    public void setOnJoystickMoveListener(OnJoystickMoveListener listener) {
        this.moveListener = listener;
    }

    public interface OnJoystickMoveListener {
        void onValueChanged(float angle, float power);
    }
}
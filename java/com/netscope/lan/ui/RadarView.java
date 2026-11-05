package com.netscope.lan.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import com.netscope.lan.R;

public final class RadarView extends View {
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint crossPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sweepPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final int primary;
    private float angle;
    private boolean scanning;
    private int deviceCount;
    private long lastFrame;

    public RadarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        primary = context.getColor(R.color.primary);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(dp(1));
        ringPaint.setColor(Color.argb(70, Color.red(primary), Color.green(primary), Color.blue(primary)));
        crossPaint.setStrokeWidth(dp(1));
        crossPaint.setColor(Color.argb(38, Color.red(primary), Color.green(primary), Color.blue(primary)));
        sweepPaint.setStrokeWidth(dp(2));
        sweepPaint.setColor(primary);
        dotPaint.setColor(context.getColor(R.color.accent));
        textPaint.setColor(Color.argb(120, Color.red(primary), Color.green(primary), Color.blue(primary)));
        textPaint.setTextSize(dp(10));
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setScanning(boolean value) {
        if (scanning != value) {
            scanning = value;
            lastFrame = 0;
            invalidate();
        }
    }

    public void setDeviceCount(int count) {
        if (deviceCount != count) {
            deviceCount = count;
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = Math.min(getWidth(), getHeight()) / 2f - dp(10);
        if (radius <= 0) return;

        for (int i = 1; i <= 3; i++) {
            canvas.drawCircle(cx, cy, radius * i / 3f, ringPaint);
        }
        canvas.drawLine(cx - radius, cy, cx + radius, cy, crossPaint);
        canvas.drawLine(cx, cy - radius, cx, cy + radius, crossPaint);

        if (scanning) {
            long now = System.currentTimeMillis();
            float delta = lastFrame == 0 ? 0 : (now - lastFrame) * 0.12f;
            lastFrame = now;
            angle = (angle + delta) % 360f;
            for (int i = 0; i < 8; i++) {
                float a = angle - i * 9f;
                sweepPaint.setAlpha(170 - i * 20);
                double rad = Math.toRadians(a);
                canvas.drawLine(cx, cy,
                        cx + (float) Math.cos(rad) * radius,
                        cy + (float) Math.sin(rad) * radius, sweepPaint);
            }
            sweepPaint.setAlpha(255);
            postInvalidateOnAnimation();
        } else {
            lastFrame = 0;
        }

        int dots = Math.min(deviceCount, 24);
        for (int i = 0; i < dots; i++) {
            double golden = 2.399963229728653;
            double a = i * golden;
            float r = radius * (0.2f + 0.75f * ((i * 37 % 100) / 100f));
            float x = cx + (float) Math.cos(a) * r;
            float y = cy + (float) Math.sin(a) * r;
            canvas.drawCircle(x, y, dp(3), dotPaint);
        }

        if (deviceCount > 0) {
            canvas.drawText(String.valueOf(deviceCount), cx, cy - dp(5), textPaint);
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}

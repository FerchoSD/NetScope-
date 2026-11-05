package com.netscope.lan.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.netscope.lan.R;
import com.netscope.lan.core.model.Device;
import com.netscope.lan.core.model.PortResult;

public final class PortGridView extends View {
    public interface OnPortTapListener {
        void onPortTapped(int port, int state);
    }

    private static final int COLUMNS = 128;
    private static final int ROWS = 32;
    private static final int BLOCK = 16;
    private static final int BLOCKS = COLUMNS * ROWS;

    private final Paint paint = new Paint();
    private final RectF rect = new RectF();
    private final int[] blocks = new int[BLOCKS];
    private final int colorOpen;
    private final int colorClosed;
    private final int colorFiltered;
    private final int colorUnscanned;

    private Device device;
    private OnPortTapListener listener;

    public PortGridView(Context context, AttributeSet attrs) {
        super(context, attrs);
        colorOpen = context.getColor(R.color.open);
        colorClosed = context.getColor(R.color.closed);
        colorFiltered = context.getColor(R.color.filtered);
        colorUnscanned = context.getColor(R.color.unscanned);
        setClickable(true);
    }

    public void setOnPortTapListener(OnPortTapListener listener) {
        this.listener = listener;
    }

    public void setDevice(Device device) {
        this.device = device;
        rebuild();
        invalidate();
    }

    public void refresh() {
        rebuild();
        invalidate();
    }

    private void rebuild() {
        for (int block = 0; block < BLOCKS; block++) {
            int state = PortResult.STATE_UNSCANNED;
            boolean closed = false;
            boolean filtered = false;
            for (int i = 0; i < BLOCK; i++) {
                int port = block * BLOCK + i + 1;
                if (port > 65535) break;
                int s = device == null ? PortResult.STATE_UNSCANNED : device.getPortState(port);
                if (s == PortResult.STATE_OPEN) {
                    state = PortResult.STATE_OPEN;
                    break;
                }
                if (s == PortResult.STATE_FILTERED) filtered = true;
                if (s == PortResult.STATE_CLOSED) closed = true;
            }
            if (state != PortResult.STATE_OPEN) {
                if (filtered) state = PortResult.STATE_FILTERED;
                else if (closed) state = PortResult.STATE_CLOSED;
            }
            blocks[block] = state;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cellWidth = getWidth() / (float) COLUMNS;
        float cellHeight = getHeight() / (float) ROWS;
        float inset = Math.min(cellWidth, cellHeight) > 3f ? 1f : 0f;
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLUMNS; col++) {
                int state = blocks[row * COLUMNS + col];
                int color;
                switch (state) {
                    case PortResult.STATE_OPEN: color = colorOpen; break;
                    case PortResult.STATE_CLOSED: color = colorClosed; break;
                    case PortResult.STATE_FILTERED: color = colorFiltered; break;
                    default: color = colorUnscanned; break;
                }
                paint.setColor(color);
                float left = col * cellWidth + inset;
                float top = row * cellHeight + inset;
                rect.set(left, top, left + cellWidth - inset * 2, top + cellHeight - inset * 2);
                canvas.drawRect(rect, paint);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            if (getWidth() <= 0 || getHeight() <= 0) return false;
            int col = (int) (event.getX() * COLUMNS / getWidth());
            int row = (int) (event.getY() * ROWS / getHeight());
            col = Math.max(0, Math.min(COLUMNS - 1, col));
            row = Math.max(0, Math.min(ROWS - 1, row));
            int block = row * COLUMNS + col;
            int port = findPort(block);
            int state = device == null ? PortResult.STATE_UNSCANNED : device.getPortState(port);
            performClick();
            if (listener != null) listener.onPortTapped(port, state);
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private int findPort(int block) {
        int base = block * BLOCK + 1;
        if (device != null) {
            for (int i = 0; i < BLOCK; i++) {
                int port = base + i;
                if (port > 65535) break;
                if (device.getPortState(port) == PortResult.STATE_OPEN) return port;
            }
            for (int i = 0; i < BLOCK; i++) {
                int port = base + i;
                if (port > 65535) break;
                if (device.getPortState(port) != PortResult.STATE_UNSCANNED) return port;
            }
        }
        return Math.min(base, 65535);
    }
}

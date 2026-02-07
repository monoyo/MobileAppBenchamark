package com.jossy.android.mobilebenchmarkappjava.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Custom View optimized for batch rendering of simple objects.
 * Used for Stress Test to measure rendering performance.
 */
public class StressTestView extends View {

    private final List<RectF> objects = new ArrayList<>();
    private final List<Integer> colors = new ArrayList<>();
    private final Paint paint = new Paint();
    private final Random random = new Random();

    public StressTestView(Context context) {
        super(context);
        init();
    }

    public StressTestView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public StressTestView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint.setStyle(Paint.Style.FILL);
        paint.setAntiAlias(false); // Disable AA for raw throughput
    }

    public void setObjects(List<RectF> newObjects, List<Integer> newColors) {
        objects.clear();
        objects.addAll(newObjects);
        colors.clear();
        colors.addAll(newColors);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Draw all objects as fast as possible
        for (int i = 0; i < objects.size(); i++) {
            paint.setColor(colors.get(i));
            canvas.drawRect(objects.get(i), paint);
        }
    }

    public int getObjectCount() {
        return objects.size();
    }
}

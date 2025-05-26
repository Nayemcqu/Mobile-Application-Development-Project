package com.cqu.genaiexpensetracker.month;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.core.content.res.ResourcesCompat;

import com.cqu.genaiexpensetracker.R;

/**
 * Custom BarChartView to display animated income, expense, and saving bars.
 * - On first render: bars animate from bottom to top.
 * - On data change: bars animate only between previous and new heights.
 * - Labels (Income, Expense, Saving) remain static and never flicker.
 */
public class BarChartView extends View {

    private final Paint barPaint = new Paint();
    private final Paint labelPaint = new Paint();
    private final Paint valuePaint = new Paint();
    private final Paint axisPaint = new Paint();

    private final int[] colors = {
            Color.parseColor("#00C853"), // Green
            Color.parseColor("#D32F2F"), // Red
            Color.parseColor("#FBC02D")  // Yellow
    };
    private final String[] labels = {"Income", "Expense", "Saving"}; // Static x-axis labels
    private float[] animatedHeights = new float[3]; // Current animated bar heights
    private int[] targetValues = {0, 0, 0};          // Target data values
    private boolean firstRender = true;
    private float[] labelAlphas = {0f, 0f, 0f}; // For Income, Expense, Saving labels

    public BarChartView(Context context) {
        super(context);
        init();
    }

    public BarChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    /**
     * Initialize paint objects and fonts.
     */
    private void init() {
        barPaint.setStyle(Paint.Style.FILL);

        Typeface regularFont = ResourcesCompat.getFont(getContext(), R.font.arial_regular);
        Typeface boldFont = ResourcesCompat.getFont(getContext(), R.font.montserrat_bold);

        valuePaint.setColor(Color.parseColor("#545454"));
        valuePaint.setTextSize(40f);
        valuePaint.setTextAlign(Paint.Align.CENTER);
        if (boldFont != null) valuePaint.setTypeface(boldFont);

        labelPaint.setColor(Color.parseColor("#1A1A1A"));
        labelPaint.setTextSize(40f);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        if (regularFont != null) labelPaint.setTypeface(regularFont);

        axisPaint.setColor(Color.parseColor("#4DA6A6A6")); // 30% opacity
        axisPaint.setStrokeWidth(2f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();

        int topPadding = 50;
        int bottomPadding = 100;
        int barHeightArea = height - topPadding - bottomPadding - 100;

        int maxValue = getMaxValue();
        float barWidth = dpToPx(75);
        float groupSpacing = dpToPx(30);
        float totalBarsWidth = barWidth * 3 + groupSpacing * 2;
        float startX = (width - totalBarsWidth) / 2;
        int axisY = height - bottomPadding;

        // Draw X-axis baseline
        canvas.drawLine(0, axisY, width, axisY, axisPaint);

        // Draw bars, labels, and values
        for (int i = 0; i < 3; i++) {
            float left = startX + i * (barWidth + groupSpacing);
            float right = left + barWidth;
            float barHeight = (animatedHeights[i] / maxValue) * barHeightArea;
            float top = axisY - barHeight;

            // Draw bar
            barPaint.setColor(colors[i]);
            canvas.drawRect(left, top, right, axisY, barPaint);

            // Fade-in Top value label
            valuePaint.setAlpha((int) (labelAlphas[i] * 255)); // Apply alpha for fade-in
            canvas.drawText("$" + targetValues[i], left + barWidth / 2f, top - 30, valuePaint);
            valuePaint.setAlpha(255); // Reset alpha for next draw

            // X-axis label (static, no animation)
            canvas.drawText(labels[i], left + barWidth / 2f, axisY + 85, labelPaint);
        }

    }

    /**
     * Update bar chart with new values.
     *
     * @param newValues Target income, expense, and saving values.
     */
    public void setChartValues(int[] newValues) {
        if (newValues.length != 3) return;

        for (int i = 0; i < 3; i++) {
            final int index = i;
            final float start = firstRender ? 0 : animatedHeights[i];
            final float end = newValues[i];

            if (start != end) {
                ValueAnimator animator = ValueAnimator.ofFloat(start, end);
                animator.setDuration(600);
                animator.setStartDelay(i * 100);
                animator.setInterpolator(new DecelerateInterpolator());
                animator.addUpdateListener(animation -> {
                    animatedHeights[index] = (float) animation.getAnimatedValue();
                    invalidate();
                });
                animator.start();
            }

            // Reset alpha and fade in value text
            labelAlphas[i] = 0f;
            ValueAnimator alphaAnim = ValueAnimator.ofFloat(0f, 1f);
            alphaAnim.setDuration(500);
            alphaAnim.setStartDelay(i * 150);
            alphaAnim.addUpdateListener(animation -> {
                labelAlphas[index] = (float) animation.getAnimatedValue();
                invalidate();
            });
            alphaAnim.start();

            targetValues[i] = newValues[i];
        }

        firstRender = false;
    }


    /**
     * Convert dp to px based on display density.
     */
    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    /**
     * Get the max value among all 3 to scale bars proportionally.
     */
    private int getMaxValue() {
        int max = 1;
        for (int val : targetValues) {
            if (val > max) max = val;
        }
        return max;
    }
}

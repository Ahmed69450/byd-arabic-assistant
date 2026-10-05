package com.byd.assistant.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

public class FloatingAssistantView extends FrameLayout {
    public enum State {
        IDLE,
        LISTENING,
        PROCESSING,
        SPEAKING
    }

    private final WindowManager windowManager;
    private WindowManager.LayoutParams layoutParams;
    private final FrameLayout buttonContainer;
    private final TextView statusText;
    private OnAssistantClickListener clickListener;

    private int initialX, initialY;
    private float initialTouchX, initialTouchY;
    private boolean isDragging = false;

    public interface OnAssistantClickListener {
        void onMicClicked();
    }

    public FloatingAssistantView(Context context) {
        super(context);
        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

        // Circular mic container
        buttonContainer = new FrameLayout(context);
        int size = dpToPx(context, 68);
        FrameLayout.LayoutParams btnParams = new FrameLayout.LayoutParams(size, size);
        btnParams.gravity = Gravity.CENTER;
        buttonContainer.setLayoutParams(btnParams);

        // Gradient Background
        updateStateAppearance(State.IDLE);

        // Status Text Label below button
        statusText = new TextView(context);
        statusText.setTextColor(Color.WHITE);
        statusText.setTextSize(12);
        statusText.setGravity(Gravity.CENTER);
        statusText.setText("BYD");
        statusText.setShadowLayer(3, 0, 0, Color.BLACK);
        FrameLayout.LayoutParams textParams = new FrameLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        textParams.gravity = Gravity.CENTER;
        buttonContainer.addView(statusText, textParams);

        addView(buttonContainer);

        setupTouchListener();
    }

    public void setOnAssistantClickListener(OnAssistantClickListener listener) {
        this.clickListener = listener;
    }

    public void setState(State state) {
        post(() -> {
            updateStateAppearance(state);
            switch (state) {
                case IDLE:
                    statusText.setText("BYD");
                    break;
                case LISTENING:
                    statusText.setText("أَسْتَمِعُ...");
                    break;
                case PROCESSING:
                    statusText.setText("جَارٍ...");
                    break;
                case SPEAKING:
                    statusText.setText("أُجِيبُ...");
                    break;
            }
        });
    }

    private void updateStateAppearance(State state) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        switch (state) {
            case LISTENING:
                bg.setColor(Color.parseColor("#00E676")); // Neon Green
                bg.setStroke(4, Color.WHITE);
                break;
            case PROCESSING:
                bg.setColor(Color.parseColor("#FFD600")); // Yellow
                bg.setStroke(4, Color.WHITE);
                break;
            case SPEAKING:
                bg.setColor(Color.parseColor("#2979FF")); // Electric Blue
                bg.setStroke(4, Color.WHITE);
                break;
            case IDLE:
            default:
                bg.setColor(Color.parseColor("#1E293B")); // Dark Blue Grey
                bg.setStroke(3, Color.parseColor("#38BDF8")); // Cyan border
                break;
        }
        buttonContainer.setBackground(bg);
    }

    public void attachToWindow() {
        int overlayType;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            overlayType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            overlayType = WindowManager.LayoutParams.TYPE_PHONE;
        }

        layoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );

        layoutParams.gravity = Gravity.TOP | Gravity.START;
        layoutParams.x = 40;
        layoutParams.y = 200;

        try {
            windowManager.addView(this, layoutParams);
        } catch (Exception ignored) {}
    }

    public void detachFromWindow() {
        try {
            windowManager.removeView(this);
        } catch (Exception ignored) {}
    }

    private void setupTouchListener() {
        setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initialX = layoutParams.x;
                    initialY = layoutParams.y;
                    initialTouchX = event.getRawX();
                    initialTouchY = event.getRawY();
                    isDragging = false;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    int dx = (int) (event.getRawX() - initialTouchX);
                    int dy = (int) (event.getRawY() - initialTouchY);
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        isDragging = true;
                        layoutParams.x = initialX + dx;
                        layoutParams.y = initialY + dy;
                        windowManager.updateViewLayout(FloatingAssistantView.this, layoutParams);
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    if (!isDragging && clickListener != null) {
                        clickListener.onMicClicked();
                    }
                    return true;
            }
            return false;
        });
    }

    private static int dpToPx(Context context, int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}

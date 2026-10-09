package com.cheer_team.remotor_lala;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

/**
 * 可复用的摇杆控件，同时支持二维与一维：
 *
 * <ul>
 *   <li>{@link #AXIS_BOTH}：圆形底盘 + 摇杆头，输出 x、y（如底盘平移）</li>
 *   <li>{@link #AXIS_VERTICAL} / {@link #AXIS_HORIZONTAL}：长条轨道 + 滑块，只输出一个轴
 *       （如机械臂升降 / 开合）</li>
 * </ul>
 *
 * <p>大小、颜色、量程、死区、方向、文字都可以在 XML 里用 app:jv* 属性指定，
 * 代码里也有对应的 setter，方便在 Java 中复用；config.yaml 的 controls 段会覆盖这些值。
 *
 * <p>松开手指后回到中心并回调 {@link JoystickListener#onRelease()}（适合速度型控制）。
 */
public class JoystickView extends View {

    public static final int AXIS_BOTH = 0;
    public static final int AXIS_HORIZONTAL = 1;
    public static final int AXIS_VERTICAL = 2;

    public interface JoystickListener {
        void onMove(int x, int y);

        void onRelease();
    }

    private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF trackRect = new RectF();

    private int axisMode = AXIS_BOTH;
    private int maxValue = 100;
    private float deadZone = 0f;
    private boolean invertX = false;
    private boolean invertY = false;
    private float baseRadiusRatio = 0.4f;
    private float stickRadiusRatio = 0.4f;
    private String label = null;

    private int baseColor = 0xFF333333;
    private int stickColor = 0xFF00FF00;
    private int labelColor = 0xFFCCCCCC;

    private float centerX, centerY, baseRadius, stickRadius;
    private float trackHalfWidth, trackHalfHeight;
    private float stickX, stickY;
    private int outputX = 0, outputY = 0;
    private JoystickListener listener;

    public JoystickView(Context context) {
        super(context);
        init(context, null);
    }

    public JoystickView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public JoystickView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        float density = context.getResources().getDisplayMetrics().density;
        markPaint.setStyle(Paint.Style.STROKE);
        markPaint.setStrokeWidth(Math.max(1f, density * 2f));
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f,
                context.getResources().getDisplayMetrics()));

        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.JoystickView);
            try {
                axisMode = a.getInt(R.styleable.JoystickView_jvAxis, AXIS_BOTH);
                maxValue = a.getInt(R.styleable.JoystickView_jvMaxValue, maxValue);
                deadZone = a.getFloat(R.styleable.JoystickView_jvDeadZone, deadZone);
                invertX = a.getBoolean(R.styleable.JoystickView_jvInvertX, invertX);
                invertY = a.getBoolean(R.styleable.JoystickView_jvInvertY, invertY);
                baseColor = a.getColor(R.styleable.JoystickView_jvBaseColor, baseColor);
                stickColor = a.getColor(R.styleable.JoystickView_jvStickColor, stickColor);
                labelColor = a.getColor(R.styleable.JoystickView_jvLabelColor, labelColor);
                labelPaint.setTextSize(a.getDimension(R.styleable.JoystickView_jvLabelSize, labelPaint.getTextSize()));
                baseRadiusRatio = a.getFloat(R.styleable.JoystickView_jvBaseRadiusRatio, baseRadiusRatio);
                stickRadiusRatio = a.getFloat(R.styleable.JoystickView_jvStickRadiusRatio, stickRadiusRatio);
                label = a.getString(R.styleable.JoystickView_jvLabel);
            } finally {
                a.recycle();
            }
        }
        if (maxValue <= 0) {
            maxValue = 100;
        }
        applyColors();
    }

    // ------------------------------------------------------------------ 配置

    public void setJoystickListener(JoystickListener listener) {
        this.listener = listener;
    }

    public void setAxisMode(int axisMode) {
        if (axisMode < AXIS_BOTH || axisMode > AXIS_VERTICAL) {
            return;
        }
        this.axisMode = axisMode;
        reset();
    }

    public int getAxisMode() {
        return axisMode;
    }

    public void setMaxValue(int maxValue) {
        if (maxValue > 0) {
            this.maxValue = maxValue;
        }
    }

    public int getMaxValue() {
        return maxValue;
    }

    /** 死区，取值 0~0.9，表示中心多少比例内不输出。 */
    public void setDeadZone(float deadZone) {
        this.deadZone = Math.max(0f, Math.min(0.9f, deadZone));
    }

    public float getDeadZone() {
        return deadZone;
    }

    public void setInvert(boolean invertX, boolean invertY) {
        this.invertX = invertX;
        this.invertY = invertY;
    }

    public void setColors(int baseColor, int stickColor) {
        this.baseColor = baseColor;
        this.stickColor = stickColor;
        applyColors();
        invalidate();
    }

    public void setLabel(String label) {
        this.label = label;
        requestLayout();
        invalidate();
    }

    public void setLabelColor(int labelColor) {
        this.labelColor = labelColor;
        applyColors();
        invalidate();
    }

    public int getOutputX() {
        return outputX;
    }

    public int getOutputY() {
        return outputY;
    }

    /** 回到中心并清零输出（不会触发回调）。 */
    public void reset() {
        stickX = centerX;
        stickY = centerY;
        outputX = 0;
        outputY = 0;
        invalidate();
    }

    private void applyColors() {
        basePaint.setColor(baseColor);
        stickPaint.setColor(stickColor);
        labelPaint.setColor(labelColor);
        markPaint.setColor(Color.argb(80, Color.red(stickColor), Color.green(stickColor), Color.blue(stickColor)));
    }

    // ------------------------------------------------------------------ 绘制

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        recomputeGeometry(w, h);
    }

    private void recomputeGeometry(int w, int h) {
        float labelReserve = (label == null || label.isEmpty()) ? 0f : labelPaint.getTextSize() * 1.9f;
        float contentBottom = Math.max(1f, h - labelReserve);
        float padding = Math.min(w, contentBottom) * 0.06f;

        centerX = w / 2f;
        centerY = contentBottom / 2f;

        if (axisMode == AXIS_BOTH) {
            baseRadius = Math.max(1f, Math.min(w, contentBottom) * baseRadiusRatio);
            stickRadius = Math.max(1f, baseRadius * stickRadiusRatio);
            trackHalfWidth = 0f;
            trackHalfHeight = 0f;
        } else if (axisMode == AXIS_VERTICAL) {
            trackHalfWidth = Math.max(1f, w * 0.5f * 0.45f);
            float travelLimit = contentBottom / 2f;
            stickRadius = Math.max(1f, Math.min(trackHalfWidth * 0.95f, travelLimit * 0.5f));
            baseRadius = Math.max(1f, travelLimit - stickRadius - padding);
            trackHalfHeight = 0f;
        } else {
            trackHalfHeight = Math.max(1f, contentBottom * 0.5f * 0.45f);
            float travelLimit = w / 2f;
            stickRadius = Math.max(1f, Math.min(trackHalfHeight * 0.95f, travelLimit * 0.5f));
            baseRadius = Math.max(1f, travelLimit - stickRadius - padding);
            trackHalfWidth = 0f;
        }
        stickX = centerX;
        stickY = centerY;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (axisMode == AXIS_BOTH) {
            canvas.drawCircle(centerX, centerY, baseRadius, basePaint);
            canvas.drawCircle(stickX, stickY, stickRadius, stickPaint);
        } else if (axisMode == AXIS_VERTICAL) {
            float span = baseRadius + stickRadius;
            trackRect.set(centerX - trackHalfWidth, centerY - span, centerX + trackHalfWidth, centerY + span);
            canvas.drawRoundRect(trackRect, trackHalfWidth, trackHalfWidth, basePaint);
            canvas.drawLine(centerX - trackHalfWidth * 0.55f, centerY,
                    centerX + trackHalfWidth * 0.55f, centerY, markPaint);
            canvas.drawCircle(stickX, stickY, stickRadius, stickPaint);
        } else {
            float span = baseRadius + stickRadius;
            trackRect.set(centerX - span, centerY - trackHalfHeight, centerX + span, centerY + trackHalfHeight);
            canvas.drawRoundRect(trackRect, trackHalfHeight, trackHalfHeight, basePaint);
            canvas.drawLine(centerX, centerY - trackHalfHeight * 0.55f,
                    centerX, centerY + trackHalfHeight * 0.55f, markPaint);
            canvas.drawCircle(stickX, stickY, stickRadius, stickPaint);
        }

        if (label != null && !label.isEmpty()) {
            canvas.drawText(label, getWidth() / 2f, getHeight() - labelPaint.getTextSize() * 0.4f, labelPaint);
        }
    }

    // ------------------------------------------------------------------ 触摸

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                // fall through
            case MotionEvent.ACTION_MOVE:
                updateFromTouch(event.getX(), event.getY());
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                reset();
                if (listener != null) {
                    listener.onRelease();
                }
                return true;

            default:
                return super.onTouchEvent(event);
        }
    }

    private void updateFromTouch(float touchX, float touchY) {
        float dx = touchX - centerX;
        float dy = touchY - centerY;
        if (axisMode == AXIS_VERTICAL) {
            dx = 0f;
        } else if (axisMode == AXIS_HORIZONTAL) {
            dy = 0f;
        }

        float distance = (float) Math.hypot(dx, dy);
        if (distance > baseRadius && distance > 0f) {
            dx = dx / distance * baseRadius;
            dy = dy / distance * baseRadius;
        }
        stickX = centerX + dx;
        stickY = centerY + dy;

        float normalizedX = dx / baseRadius;
        // 屏幕坐标 y 向下为正，取负号让"往上推"输出正值
        float normalizedY = -dy / baseRadius;
        if (Math.abs(normalizedX) < deadZone) {
            normalizedX = 0f;
        }
        if (Math.abs(normalizedY) < deadZone) {
            normalizedY = 0f;
        }
        if (invertX) {
            normalizedX = -normalizedX;
        }
        if (invertY) {
            normalizedY = -normalizedY;
        }

        outputX = Math.round(normalizedX * maxValue);
        outputY = Math.round(normalizedY * maxValue);
        if (axisMode == AXIS_VERTICAL) {
            outputX = 0;
        } else if (axisMode == AXIS_HORIZONTAL) {
            outputY = 0;
        }

        if (listener != null) {
            listener.onMove(outputX, outputY);
        }
        invalidate();
    }
}

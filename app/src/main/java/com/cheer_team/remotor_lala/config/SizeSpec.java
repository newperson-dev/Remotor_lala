package com.cheer_team.remotor_lala.config;

import android.view.ViewGroup;

/**
 * 尺寸描述，统一解析 yaml / xml 里写的大小。
 *
 * <p>支持的写法：
 * <pre>
 *   "220dp" / "220"      固定 dp（只写数字默认按 dp）
 *   "120px"              固定像素
 *   "25%"                相对父容器百分比
 *   "0dp"                约束布局里表示“撑满剩余空间”（match constraint）
 *   match_parent / wrap_content
 * </pre>
 *
 * <p>纯 Java 实现（不依赖 Context），像素换算由调用方传入 density，方便单元测试。
 */
public final class SizeSpec {

    public enum Unit { DP, PX, PERCENT, MATCH_PARENT, WRAP_CONTENT }

    public final Unit unit;
    public final float value;
    public final String raw;

    private SizeSpec(Unit unit, float value, String raw) {
        this.unit = unit;
        this.value = value;
        this.raw = raw;
    }

    /** 解析失败返回 null（调用方负责给出配置警告）。 */
    public static SizeSpec parse(String text) {
        if (text == null) {
            return null;
        }
        String s = text.trim();
        if (s.isEmpty()) {
            return null;
        }
        String lower = s.toLowerCase();
        if (lower.equals("match_parent") || lower.equals("fill_parent") || lower.equals("match")) {
            return new SizeSpec(Unit.MATCH_PARENT, 0f, s);
        }
        if (lower.equals("wrap_content") || lower.equals("wrap")) {
            return new SizeSpec(Unit.WRAP_CONTENT, 0f, s);
        }
        try {
            if (lower.endsWith("%")) {
                return new SizeSpec(Unit.PERCENT, Float.parseFloat(lower.substring(0, lower.length() - 1).trim()) / 100f, s);
            }
            if (lower.endsWith("dip")) {
                return new SizeSpec(Unit.DP, Float.parseFloat(lower.substring(0, lower.length() - 3).trim()), s);
            }
            if (lower.endsWith("dp")) {
                return new SizeSpec(Unit.DP, Float.parseFloat(lower.substring(0, lower.length() - 2).trim()), s);
            }
            if (lower.endsWith("px")) {
                return new SizeSpec(Unit.PX, Float.parseFloat(lower.substring(0, lower.length() - 2).trim()), s);
            }
            return new SizeSpec(Unit.DP, Float.parseFloat(lower), s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public boolean isPercent() {
        return unit == Unit.PERCENT;
    }

    public boolean isMatchParent() {
        return unit == Unit.MATCH_PARENT;
    }

    public boolean isWrapContent() {
        return unit == Unit.WRAP_CONTENT;
    }

    /**
     * 换算成布局用的大小。
     *
     * @param density  屏幕密度（dp -> px）
     * @param scale    全局缩放系数（layout.scale）
     * @param parentPx 父容器在对应方向上的像素尺寸，仅百分比需要；未知时传 0
     * @return 像素值，或 {@link ViewGroup.LayoutParams#MATCH_PARENT} / {@code WRAP_CONTENT}
     */
    public int resolvePx(float density, float scale, int parentPx) {
        switch (unit) {
            case DP:
                return Math.round(value * density * scale);
            case PX:
                return Math.round(value * scale);
            case PERCENT:
                return parentPx > 0 ? Math.round(parentPx * value * scale) : 0;
            case MATCH_PARENT:
                return ViewGroup.LayoutParams.MATCH_PARENT;
            default:
                return ViewGroup.LayoutParams.WRAP_CONTENT;
        }
    }

    /** 百分比是否需要等父容器测量完成后再换算。 */
    public boolean needsParentSize(int parentPx) {
        return unit == Unit.PERCENT && parentPx <= 0;
    }

    @Override
    public String toString() {
        return raw;
    }
}

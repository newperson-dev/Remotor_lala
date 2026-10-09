package com.cheer_team.remotor_lala.config;

import java.util.List;
import java.util.Map;

/**
 * ui 段：配色、字号、回传区行数、全屏方式。
 *
 * <p>颜色以字符串形式保存（如 "#121212"），由界面层负责转成颜色值，
 * 这样配置模型保持纯 Java、方便单元测试。
 */
public final class UiConfig {

    private static final String COLOR_PATTERN = "#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})";

    public final boolean showLastSentFrame;
    public final int receivedMaxLines;
    public final float statusTextSizeSp;
    public final float receivedTextSizeSp;
    public final String colorBackground;
    public final String colorPanel;
    public final String colorStatusBar;
    public final String colorTextPrimary;
    public final String colorTextReceived;
    public final FullscreenConfig fullscreen;

    private UiConfig(boolean showLastSentFrame, int receivedMaxLines, float statusTextSizeSp, float receivedTextSizeSp,
                     String colorBackground, String colorPanel, String colorStatusBar,
                     String colorTextPrimary, String colorTextReceived, FullscreenConfig fullscreen) {
        this.showLastSentFrame = showLastSentFrame;
        this.receivedMaxLines = receivedMaxLines;
        this.statusTextSizeSp = statusTextSizeSp;
        this.receivedTextSizeSp = receivedTextSizeSp;
        this.colorBackground = colorBackground;
        this.colorPanel = colorPanel;
        this.colorStatusBar = colorStatusBar;
        this.colorTextPrimary = colorTextPrimary;
        this.colorTextReceived = colorTextReceived;
        this.fullscreen = fullscreen;
    }

    public static UiConfig fromYaml(Map<String, Object> yaml, List<String> warnings) {
        boolean showLastSentFrame = SimpleYaml.getBoolean(yaml, "showLastSentFrame", true);
        int maxLines = SimpleYaml.getInt(yaml, "receivedMaxLines", 300);
        if (maxLines < 10) {
            warnings.add("ui.receivedMaxLines=" + maxLines + " 太小，已按 10 处理");
            maxLines = 10;
        }
        float statusSize = SimpleYaml.getFloat(yaml, "statusTextSizeSp", 14f);
        float receivedSize = SimpleYaml.getFloat(yaml, "receivedTextSizeSp", 14f);

        return new UiConfig(
                showLastSentFrame,
                maxLines,
                statusSize,
                receivedSize,
                color(yaml, "colors.background", "#121212", "ui.colors.background", warnings),
                color(yaml, "colors.panel", "#222222", "ui.colors.panel", warnings),
                color(yaml, "colors.statusBar", "#333333", "ui.colors.statusBar", warnings),
                color(yaml, "colors.textPrimary", "#FFFFFF", "ui.colors.textPrimary", warnings),
                color(yaml, "colors.textReceived", "#00FF00", "ui.colors.textReceived", warnings),
                FullscreenConfig.fromYaml(SimpleYaml.getMap(yaml, "fullscreen"), warnings));
    }

    private static String color(Map<String, Object> yaml, String path, String fallback, String label, List<String> warnings) {
        String value = SimpleYaml.getString(yaml, path, fallback);
        if (value == null || !value.trim().matches(COLOR_PATTERN)) {
            warnings.add(label + " 不是合法颜色: \"" + value + "\"，已按 " + fallback + " 处理");
            return fallback;
        }
        return value.trim();
    }
}

package com.cheer_team.remotor_lala.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * protocol 段：帧头 / 帧尾 / 结束符 / 数值范围 / 指令模板。
 *
 * <p>模板里用 {@code <名字>} 作占位符，可用占位符见 {@link ControlConfig#placeholders(int, int)}：
 * {@code <x> <y> <value> <id> <name>}。
 */
public final class ProtocolConfig {

    public static final String DEFAULT_TERMINATOR = "\n";
    public static final String FALLBACK_2D_TEMPLATE = "$J,<x>,<y>#";
    public static final String FALLBACK_1D_TEMPLATE = "$ARM,<id>,<value>#";

    public final String header;
    public final String footer;
    public final String terminator;
    public final int minValue;
    public final int maxValue;
    public final Map<String, String> templates;

    private ProtocolConfig(String header, String footer, String terminator, int minValue, int maxValue,
                           Map<String, String> templates) {
        this.header = header;
        this.footer = footer;
        this.terminator = terminator;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.templates = templates;
    }

    public static ProtocolConfig fromYaml(Map<String, Object> yaml, List<String> warnings) {
        String header = SimpleYaml.getString(yaml, "header", "$");
        String footer = SimpleYaml.getString(yaml, "footer", "#");
        String terminator = SimpleYaml.getString(yaml, "terminator", DEFAULT_TERMINATOR);
        int minValue = SimpleYaml.getInt(yaml, "minValue", -100);
        int maxValue = SimpleYaml.getInt(yaml, "maxValue", 100);
        if (maxValue <= minValue) {
            warnings.add("protocol.maxValue 必须大于 minValue（当前 " + minValue + "~" + maxValue + "），已按 -100~100 处理");
            minValue = -100;
            maxValue = 100;
        }

        Map<String, String> templates = new LinkedHashMap<>();
        Map<String, Object> declared = SimpleYaml.getMap(yaml, "templates");
        for (Map.Entry<String, Object> entry : declared.entrySet()) {
            if (entry.getValue() == null) {
                warnings.add("protocol.templates." + entry.getKey() + " 为空，已忽略");
                continue;
            }
            templates.put(entry.getKey(), String.valueOf(entry.getValue()));
        }
        if (!templates.containsKey("joystick2d")) {
            templates.put("joystick2d", FALLBACK_2D_TEMPLATE);
        }
        if (!templates.containsKey("joystick1d")) {
            templates.put("joystick1d", FALLBACK_1D_TEMPLATE);
        }
        return new ProtocolConfig(header, footer, terminator, minValue, maxValue, templates);
    }

    /**
     * 把 controls 段里写的 template 解析成真正的模板字符串。
     * 不含 {@code <} 的值视为 templates 里的键名；找不到就把原值当模板用。
     */
    public String resolveTemplate(String referenceOrLiteral) {
        if (referenceOrLiteral == null || referenceOrLiteral.trim().isEmpty()) {
            return defaultTemplate();
        }
        String value = referenceOrLiteral.trim();
        if (!value.contains("<")) {
            String template = templates.get(value);
            if (template != null) {
                return template;
            }
        }
        return value;
    }

    public String defaultTemplate() {
        String template = templates.get("joystick2d");
        return template == null ? FALLBACK_2D_TEMPLATE : template;
    }

    public String oneDimensionalTemplate() {
        String template = templates.get("joystick1d");
        return template == null ? FALLBACK_1D_TEMPLATE : template;
    }

    public int clamp(int value) {
        return Math.max(minValue, Math.min(maxValue, value));
    }
}

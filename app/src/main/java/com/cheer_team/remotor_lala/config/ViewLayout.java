package com.cheer_team.remotor_lala.config;

import java.util.List;
import java.util.Map;

/**
 * 一个控件的位置 / 大小描述，对应 config.yaml 里 layout.views 的一项。
 *
 * <p>所有字段都是“可选”的：为 null 表示不改动该属性，继续用 XML / dimens 里的值。
 */
public final class ViewLayout {

    public final String id;
    public final SizeSpec width;
    public final SizeSpec height;
    public final Float weight;
    public final String anchor;
    public final Float marginStartDp;
    public final Float marginTopDp;
    public final Float marginEndDp;
    public final Float marginBottomDp;

    private ViewLayout(String id, SizeSpec width, SizeSpec height, Float weight, String anchor,
                       Float marginStartDp, Float marginTopDp, Float marginEndDp, Float marginBottomDp) {
        this.id = id;
        this.width = width;
        this.height = height;
        this.weight = weight;
        this.anchor = anchor;
        this.marginStartDp = marginStartDp;
        this.marginTopDp = marginTopDp;
        this.marginEndDp = marginEndDp;
        this.marginBottomDp = marginBottomDp;
    }

    public static ViewLayout fromYaml(Map<String, Object> yaml, String path, List<String> warnings) {
        String id = SimpleYaml.getString(yaml, "id", null);
        if (id == null || id.trim().isEmpty()) {
            warnings.add(path + " 缺少 id，已忽略该布局配置");
            return null;
        }
        id = id.trim();

        SizeSpec width = size(yaml, "width", path, warnings);
        SizeSpec height = size(yaml, "height", path, warnings);

        Float weight = null;
        Object weightValue = SimpleYaml.get(yaml, "weight");
        if (weightValue instanceof Number) {
            weight = ((Number) weightValue).floatValue();
        } else if (weightValue != null) {
            try {
                weight = Float.parseFloat(String.valueOf(weightValue).trim());
            } catch (NumberFormatException e) {
                warnings.add(path + ".weight 不是数字: " + weightValue);
            }
        }

        String anchor = SimpleYaml.getString(yaml, "anchor", null);
        if (anchor != null && anchor.trim().isEmpty()) {
            anchor = null;
        }

        String all = SimpleYaml.getString(yaml, "margin", null);
        Float marginStart = dp(yaml, "marginStart", all, path, warnings);
        Float marginTop = dp(yaml, "marginTop", all, path, warnings);
        Float marginEnd = dp(yaml, "marginEnd", all, path, warnings);
        Float marginBottom = dp(yaml, "marginBottom", all, path, warnings);

        return new ViewLayout(id, width, height, weight, anchor, marginStart, marginTop, marginEnd, marginBottom);
    }

    private static SizeSpec size(Map<String, Object> yaml, String key, String path, List<String> warnings) {
        String text = SimpleYaml.getString(yaml, key, null);
        if (text == null) {
            return null;
        }
        SizeSpec spec = SizeSpec.parse(text);
        if (spec == null) {
            warnings.add(path + "." + key + " 无法识别: \"" + text + "\"（可用 220dp / 120px / 25% / match_parent / wrap_content）");
        }
        return spec;
    }

    private static Float dp(Map<String, Object> yaml, String key, String fallback, String path, List<String> warnings) {
        String text = SimpleYaml.getString(yaml, key, fallback);
        if (text == null) {
            return null;
        }
        String s = text.trim().toLowerCase();
        if (s.endsWith("dip")) {
            s = s.substring(0, s.length() - 3).trim();
        } else if (s.endsWith("dp")) {
            s = s.substring(0, s.length() - 2).trim();
        }
        try {
            return Float.parseFloat(s);
        } catch (NumberFormatException e) {
            warnings.add(path + "." + key + " 不是合法的 dp 值: \"" + text + "\"（例如 \"16dp\"）");
            return null;
        }
    }

    public boolean hasMarginStart() {
        return marginStartDp != null;
    }

    public boolean hasMarginTop() {
        return marginTopDp != null;
    }

    public boolean hasMarginEnd() {
        return marginEndDp != null;
    }

    public boolean hasMarginBottom() {
        return marginBottomDp != null;
    }

    public int marginStartPx(float density, float scale) {
        return marginStartDp == null ? 0 : Math.round(marginStartDp * density * scale);
    }

    public int marginTopPx(float density, float scale) {
        return marginTopDp == null ? 0 : Math.round(marginTopDp * density * scale);
    }

    public int marginEndPx(float density, float scale) {
        return marginEndDp == null ? 0 : Math.round(marginEndDp * density * scale);
    }

    public int marginBottomPx(float density, float scale) {
        return marginBottomDp == null ? 0 : Math.round(marginBottomDp * density * scale);
    }
}

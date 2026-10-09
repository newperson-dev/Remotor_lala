package com.cheer_team.remotor_lala.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * layout 段：全局尺寸缩放 + 各个控件的布局规格。
 */
public final class LayoutConfig {

    public final boolean apply;
    public final float scale;
    public final Map<String, ViewLayout> views;

    private LayoutConfig(boolean apply, float scale, Map<String, ViewLayout> views) {
        this.apply = apply;
        this.scale = scale;
        this.views = views;
    }

    public static LayoutConfig fromYaml(Map<String, Object> yaml, List<String> warnings) {
        boolean apply = SimpleYaml.getBoolean(yaml, "apply", true);
        float scale = SimpleYaml.getFloat(yaml, "scale", 1f);
        if (scale <= 0.1f || scale > 4f) {
            warnings.add("layout.scale 应在 0.1~4 之间，当前 " + scale + "，已按 1.0 处理");
            scale = 1f;
        }

        Map<String, ViewLayout> views = new LinkedHashMap<>();
        List<Object> list = SimpleYaml.getList(yaml, "views");
        for (int i = 0; i < list.size(); i++) {
            Map<String, Object> item = SimpleYaml.castMap(list.get(i));
            ViewLayout view = ViewLayout.fromYaml(item, "layout.views[" + i + "]", warnings);
            if (view == null) {
                continue;
            }
            if (views.containsKey(view.id)) {
                warnings.add("layout.views 里 id 重复: " + view.id + "（后一条生效）");
            }
            views.put(view.id, view);
        }
        return new LayoutConfig(apply, scale, views);
    }

    public ViewLayout forId(String id) {
        return id == null ? null : views.get(id);
    }
}

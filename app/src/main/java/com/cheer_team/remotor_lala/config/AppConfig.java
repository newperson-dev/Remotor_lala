package com.cheer_team.remotor_lala.config;

import android.content.Context;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置总入口：一次性把 assets/config.yaml 读成可用的模型对象。
 *
 * <p>用法（MainActivity）：
 * <pre>
 *   AppConfig config = AppConfig.load(this);
 *   FrameBuilder frames = new FrameBuilder(config.protocol);
 *   LayoutApplier layout = new LayoutApplier(config.layout, density, resolver);
 *   for (ControlConfig control : config.controls) { ... }
 * </pre>
 *
 * <p>配置有误不会崩溃：问题会收集到 {@link #warnings}，由界面显示出来。
 */
public final class AppConfig {

    public static final String ASSET_NAME = "config.yaml";

    public final ProtocolConfig protocol;
    public final HeartbeatConfig heartbeat;
    public final UiConfig ui;
    public final LayoutConfig layout;
    public final List<ControlConfig> controls;
    public final List<String> warnings;

    private AppConfig(ProtocolConfig protocol, HeartbeatConfig heartbeat, UiConfig ui, LayoutConfig layout,
                      List<ControlConfig> controls, List<String> warnings) {
        this.protocol = protocol;
        this.heartbeat = heartbeat;
        this.ui = ui;
        this.layout = layout;
        this.controls = controls;
        this.warnings = warnings;
    }

    /** 从 assets/config.yaml 读取；文件缺失或语法错误时退回内置默认值并记录警告。 */
    public static AppConfig load(Context context) {
        try (InputStream in = context.getAssets().open(ASSET_NAME)) {
            return load(in);
        } catch (IOException e) {
            AppConfig config = fromYaml(new LinkedHashMap<>());
            config.warnings.add("读取 assets/" + ASSET_NAME + " 失败: " + e.getMessage() + "（已使用内置默认值）");
            return config;
        } catch (SimpleYaml.YamlException e) {
            AppConfig config = fromYaml(new LinkedHashMap<>());
            config.warnings.add("assets/" + ASSET_NAME + " 解析失败: " + e.getMessage() + "（已使用内置默认值）");
            return config;
        }
    }

    public static AppConfig load(InputStream in) throws IOException {
        return fromYaml(SimpleYaml.parse(in));
    }

    public static AppConfig fromYaml(Map<String, Object> root) {
        List<String> warnings = new ArrayList<>();
        ProtocolConfig protocol = ProtocolConfig.fromYaml(SimpleYaml.getMap(root, "protocol"), warnings);
        HeartbeatConfig heartbeat = HeartbeatConfig.fromYaml(SimpleYaml.getMap(root, "heartbeat"), warnings);
        UiConfig ui = UiConfig.fromYaml(SimpleYaml.getMap(root, "ui"), warnings);
        LayoutConfig layout = LayoutConfig.fromYaml(SimpleYaml.getMap(root, "layout"), warnings);

        List<ControlConfig> controls = new ArrayList<>();
        List<Object> declared = SimpleYaml.getList(root, "controls");
        for (int i = 0; i < declared.size(); i++) {
            Map<String, Object> item = SimpleYaml.castMap(declared.get(i));
            ControlConfig control = ControlConfig.fromYaml(item, "controls[" + i + "]", warnings);
            if (control == null) {
                continue;
            }
            if (findControl(controls, control.id) != null) {
                warnings.add("controls 里 id 重复: " + control.id + "（后一条生效）");
                controls.remove(findControl(controls, control.id));
            }
            controls.add(control);
        }

        if (controls.isEmpty()) {
            ControlConfig fallback = ControlConfig.fallback2d();
            controls.add(fallback);
            warnings.add("controls 为空，已自动添加兜底二维摇杆 " + fallback.id);
        }

        return new AppConfig(protocol, heartbeat, ui, layout, controls, warnings);
    }

    public ControlConfig control(String id) {
        return findControl(controls, id);
    }

    private static ControlConfig findControl(List<ControlConfig> controls, String id) {
        for (ControlConfig control : controls) {
            if (control.id.equals(id)) {
                return control;
            }
        }
        return null;
    }
}

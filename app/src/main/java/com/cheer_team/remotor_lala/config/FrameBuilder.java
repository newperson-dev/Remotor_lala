package com.cheer_team.remotor_lala.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 一帧数据的拼装器：把模板里的 {@code <x> <y> <value> <id> <name>} 换成实际数值。
 *
 * <p>结束符（\n）由 {@code BluetoothSppManager} 统一追加，这里只产出 {@code $J,10,20#} 这样的负载。
 */
public final class FrameBuilder {

    private final ProtocolConfig protocol;

    public FrameBuilder(ProtocolConfig protocol) {
        this.protocol = protocol;
    }

    public ProtocolConfig protocol() {
        return protocol;
    }

    /**
     * @param templateReferenceOrLiteral 模板键名（如 "joystick2d"）或整条模板字面量
     * @param args                       占位符取值
     */
    public String build(String templateReferenceOrLiteral, Map<String, String> args) {
        String result = protocol.resolveTemplate(templateReferenceOrLiteral);
        if (args != null) {
            for (Map.Entry<String, String> entry : args.entrySet()) {
                String value = entry.getValue() == null ? "" : entry.getValue();
                result = result.replace("<" + entry.getKey() + ">", value);
            }
        }
        return result;
    }

    /** 用默认二维模板拼一帧（x、y 会自动按 protocol 配置裁剪）。 */
    public String build2d(int x, int y) {
        Map<String, String> args = new LinkedHashMap<>();
        args.put("x", String.valueOf(protocol.clamp(x)));
        args.put("y", String.valueOf(protocol.clamp(y)));
        args.put("value", "0");
        args.put("id", "");
        args.put("name", "");
        return build(protocol.defaultTemplate(), args);
    }

    /** 用默认一维模板拼一帧。 */
    public String build1d(String id, int value) {
        Map<String, String> args = new LinkedHashMap<>();
        args.put("x", "0");
        args.put("y", String.valueOf(protocol.clamp(value)));
        args.put("value", String.valueOf(protocol.clamp(value)));
        args.put("id", id == null ? "" : id);
        args.put("name", id == null ? "" : id);
        return build(protocol.oneDimensionalTemplate(), args);
    }
}

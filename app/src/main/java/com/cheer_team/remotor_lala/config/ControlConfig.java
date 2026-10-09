package com.cheer_team.remotor_lala.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * controls 段里的一项：一个摇杆控件 + 它对应的协议模板。
 *
 * <p>这个类是“协议扩展”的核心：新增一个摇杆 / 新指令，只要在 config.yaml 里加一段，
 * 不需要改 Java 代码，也不需要改 XML（XML 里没声明的 id 会自动创建）。
 */
public final class ControlConfig {

    public static final String TYPE_2D = "joystick2d";
    public static final String TYPE_1D = "joystick1d";

    public static final String AXIS_BOTH = "both";
    public static final String AXIS_HORIZONTAL = "horizontal";
    public static final String AXIS_VERTICAL = "vertical";

    private static final String COLOR_PATTERN = "#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})";
    private static final String DEFAULT_CONTAINER = "rightPanel";

    /** 控件 id，同时也是 XML 里 @+id/xxx 的名字。 */
    public final String id;
    /** false = 不绑定、不发送；XML 里若声明了同名控件会被隐藏（方便临时“去掉”一个摇杆）。 */
    public final boolean enabled;
    public final String type;
    public final String axis;
    /** 模板键名或模板字面量。 */
    public final String template;
    /** 填充模板里 {@code <id>} 的值，下位机用它区分关节。 */
    public final String commandId;
    public final int maxValue;
    public final float deadZone;
    public final boolean invertX;
    public final boolean invertY;
    public final boolean invert;
    public final boolean heartbeat;
    /** XML 中没有该 id 时，动态创建后放进哪个容器（容器 id 名）。 */
    public final String container;
    public final String label;
    public final String baseColor;
    public final String stickColor;

    private ControlConfig(String id, boolean enabled, String type, String axis, String template, String commandId,
                          int maxValue, float deadZone, boolean invertX, boolean invertY, boolean invert,
                          boolean heartbeat, String container, String label, String baseColor, String stickColor) {
        this.id = id;
        this.enabled = enabled;
        this.type = type;
        this.axis = axis;
        this.template = template;
        this.commandId = commandId;
        this.maxValue = maxValue;
        this.deadZone = deadZone;
        this.invertX = invertX;
        this.invertY = invertY;
        this.invert = invert;
        this.heartbeat = heartbeat;
        this.container = container;
        this.label = label;
        this.baseColor = baseColor;
        this.stickColor = stickColor;
    }

    public static ControlConfig fromYaml(Map<String, Object> yaml, String path, List<String> warnings) {
        String id = SimpleYaml.getString(yaml, "id", null);
        if (id == null || id.trim().isEmpty()) {
            warnings.add(path + " 缺少 id，已忽略该控件");
            return null;
        }
        id = id.trim();

        String type = SimpleYaml.getString(yaml, "type", TYPE_2D).trim().toLowerCase();
        if (!TYPE_2D.equals(type) && !TYPE_1D.equals(type)) {
            warnings.add(path + ".type 只能是 joystick2d / joystick1d，当前 \"" + type + "\"，已按 joystick2d 处理");
            type = TYPE_2D;
        }

        String axis = SimpleYaml.getString(yaml, "axis", TYPE_1D.equals(type) ? AXIS_VERTICAL : AXIS_BOTH).trim().toLowerCase();
        if (!AXIS_BOTH.equals(axis) && !AXIS_HORIZONTAL.equals(axis) && !AXIS_VERTICAL.equals(axis)) {
            warnings.add(path + ".axis 只能是 both / horizontal / vertical，当前 \"" + axis + "\"，已按默认值处理");
            axis = TYPE_1D.equals(type) ? AXIS_VERTICAL : AXIS_BOTH;
        }
        if (TYPE_1D.equals(type) && AXIS_BOTH.equals(axis)) {
            warnings.add(path + ".axis=both 对一维摇杆无意义，已按 vertical 处理");
            axis = AXIS_VERTICAL;
        }

        int maxValue = SimpleYaml.getInt(yaml, "maxValue", 100);
        if (maxValue <= 0) {
            warnings.add(path + ".maxValue 必须为正数，当前 " + maxValue + "，已按 100 处理");
            maxValue = 100;
        }

        float deadZone = SimpleYaml.getFloat(yaml, "deadZone", 0f);
        if (deadZone < 0f || deadZone > 0.9f) {
            warnings.add(path + ".deadZone 应在 0~0.9 之间，当前 " + deadZone + "，已按 0 处理");
            deadZone = 0f;
        }

        // template 缺省就用类型名（对应 protocol.templates 里的 joystick2d / joystick1d）
        String template = SimpleYaml.getString(yaml, "template", type).trim();
        String commandId = SimpleYaml.getString(yaml, "commandId", id).trim();
        String container = SimpleYaml.getString(yaml, "container", DEFAULT_CONTAINER).trim();
        String label = SimpleYaml.getString(yaml, "label", null);

        return new ControlConfig(
                id,
                SimpleYaml.getBoolean(yaml, "enabled", true),
                type, axis, template, commandId, maxValue, deadZone,
                SimpleYaml.getBoolean(yaml, "invertX", false),
                SimpleYaml.getBoolean(yaml, "invertY", false),
                SimpleYaml.getBoolean(yaml, "invert", false),
                SimpleYaml.getBoolean(yaml, "heartbeat", true),
                container.isEmpty() ? null : container,
                label,
                color(yaml, "baseColor", path, warnings),
                color(yaml, "stickColor", path, warnings));
    }

    private static String color(Map<String, Object> yaml, String key, String path, List<String> warnings) {
        String value = SimpleYaml.getString(yaml, key, null);
        if (value == null) {
            return null;
        }
        if (!value.trim().matches(COLOR_PATTERN)) {
            warnings.add(path + "." + key + " 不是合法颜色: \"" + value + "\"，已忽略");
            return null;
        }
        return value.trim();
    }

    /** assets 缺失且没有配置任何控件时的兜底二维摇杆。 */
    public static ControlConfig fallback2d() {
        return new ControlConfig("joystickView", true, TYPE_2D, AXIS_BOTH, "joystick2d", "joystickView", 100, 0.05f,
                false, false, false, true, DEFAULT_CONTAINER, null, null, null);
    }

    public boolean isOneDimensional() {
        return TYPE_1D.equals(type);
    }

    public boolean isVertical() {
        return AXIS_VERTICAL.equals(axis);
    }

    public boolean effectiveInvertX() {
        return invertX ^ (invert && !isVertical());
    }

    public boolean effectiveInvertY() {
        return invertY ^ (invert && isVertical());
    }

    /**
     * 生成模板占位符。所有可用占位符都会准备好，模板里没用到也无妨：
     * {@code <x> <y> <value> <id> <name>}。
     */
    public Map<String, String> placeholders(int x, int y) {
        int value = isOneDimensional() ? (isVertical() ? y : x) : 0;
        Map<String, String> args = new LinkedHashMap<>();
        args.put("x", String.valueOf(x));
        args.put("y", String.valueOf(y));
        args.put("value", String.valueOf(value));
        args.put("id", commandId == null ? id : commandId);
        args.put("name", id);
        return args;
    }
}

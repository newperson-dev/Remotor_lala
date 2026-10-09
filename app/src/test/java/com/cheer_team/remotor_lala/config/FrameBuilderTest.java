package com.cheer_team.remotor_lala.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

/** 协议模板 + 控件绑定（协议扩展的核心逻辑）。 */
public class FrameBuilderTest {

    private static Map<String, Object> mapOf(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return map;
    }

    private static Map<String, String> stringMap(String... keyValues) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    private static ControlConfig control(Map<String, Object> yaml, List<String> warnings) {
        ControlConfig control = ControlConfig.fromYaml(yaml, "controls[0]", warnings);
        assertTrue("控件配置应当解析成功", control != null);
        return control;
    }

    @Test
    public void usesBuiltInTemplatesWhenConfigHasNone() {
        List<String> warnings = new ArrayList<>();
        ProtocolConfig protocol = ProtocolConfig.fromYaml(new LinkedHashMap<>(), warnings);
        FrameBuilder builder = new FrameBuilder(protocol);

        assertEquals("$J,10,-20#", builder.build2d(10, -20));
        assertEquals("$ARM,lift,60#", builder.build1d("lift", 60));
        assertEquals(0, warnings.size());
    }

    @Test
    public void resolvesTemplateByNameAndByLiteral() {
        List<String> warnings = new ArrayList<>();
        ProtocolConfig protocol = ProtocolConfig.fromYaml(mapOf(
                "templates", mapOf("move", "$M,<x>,<y>#", "arm", "$ARM,<id>,<value>#")), warnings);
        FrameBuilder builder = new FrameBuilder(protocol);

        Map<String, String> args = stringMap("x", "1", "y", "2", "value", "3", "id", "grip");
        assertEquals("$M,1,2#", builder.build("move", args));
        assertEquals("$ARM,grip,3#", builder.build("arm", args));
        // 不含 < 时先当模板名找，找不到再把原值当模板
        assertEquals("$ARM,grip,3#", builder.build("$ARM,<id>,<value>#", args));
        assertEquals("$C,<value>#", builder.build("$C,<value>#", stringMap("id", "x")));
    }

    @Test
    public void clampsValuesToProtocolRange() {
        List<String> warnings = new ArrayList<>();
        ProtocolConfig protocol = ProtocolConfig.fromYaml(mapOf("minValue", -100, "maxValue", 100), warnings);
        FrameBuilder builder = new FrameBuilder(protocol);

        assertEquals("$J,100,100#", builder.build2d(500, 500));
        assertEquals("$J,-100,-100#", builder.build2d(-500, -500));
    }

    @Test
    public void oneDimensionalControlBindsValueAndCommandId() {
        List<String> warnings = new ArrayList<>();
        ControlConfig lift = control(mapOf(
                "id", "armLift",
                "type", "joystick1d",
                "axis", "vertical",
                "template", "joystick1d",
                "commandId", "lift"), warnings);

        assertTrue(lift.isOneDimensional());
        assertTrue(lift.isVertical());
        assertEquals(0, warnings.size());

        Map<String, String> down = lift.placeholders(0, -60);
        assertEquals("-60", down.get("value"));
        assertEquals("lift", down.get("id"));
        assertEquals("armLift", down.get("name"));

        ProtocolConfig protocol = ProtocolConfig.fromYaml(new LinkedHashMap<>(), warnings);
        FrameBuilder builder = new FrameBuilder(protocol);
        assertEquals("$ARM,lift,-60#", builder.build(lift.template, down));
        assertEquals("$ARM,lift,0#", builder.build(lift.template, lift.placeholders(0, 0)));
    }

    @Test
    public void twoDimensionalControlKeepsBothAxes() {
        List<String> warnings = new ArrayList<>();
        ControlConfig move = control(mapOf("id", "joystickView", "type", "joystick2d", "axis", "both"), warnings);

        assertFalse(move.isOneDimensional());
        Map<String, String> args = move.placeholders(-30, 70);
        assertEquals("-30", args.get("x"));
        assertEquals("70", args.get("y"));

        ProtocolConfig protocol = ProtocolConfig.fromYaml(new LinkedHashMap<>(), warnings);
        FrameBuilder builder = new FrameBuilder(protocol);
        assertEquals("$J,-30,70#", builder.build(move.template, args));
    }

    @Test
    public void controlIsEnabledByDefaultAndCanBeDisabled() {
        List<String> warnings = new ArrayList<>();

        ControlConfig on = control(mapOf("id", "armRotate", "type", "joystick1d"), warnings);
        assertTrue(on.enabled);

        ControlConfig off = control(mapOf("id", "armRotate", "type", "joystick1d", "enabled", false), warnings);
        assertFalse(off.enabled);
        assertEquals(0, warnings.size());
    }

    @Test
    public void invalidConfigurationProducesWarningsInsteadOfCrash() {
        List<String> warnings = new ArrayList<>();
        ControlConfig broken = ControlConfig.fromYaml(mapOf(
                "id", "weird",
                "type", "slider",
                "axis", "diagonal",
                "maxValue", -5,
                "deadZone", 5,
                "baseColor", "red"), "controls[3]", warnings);

        assertTrue("应当回退成二维摇杆", broken != null && !broken.isOneDimensional());
        assertEquals(100, broken.maxValue);
        assertEquals(0f, broken.deadZone, 0.0001f);
        assertEquals(5, warnings.size());

        ProtocolConfig protocol = ProtocolConfig.fromYaml(mapOf("maxValue", 0, "minValue", 0), warnings);
        assertEquals(100, protocol.maxValue);
        assertEquals(-100, protocol.minValue);
    }
}

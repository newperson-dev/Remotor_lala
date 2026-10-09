package com.cheer_team.remotor_lala.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.Map;

import org.junit.Test;

/** SimpleYaml 解析器的行为测试（含本项目配置文件用到的全部语法）。 */
public class SimpleYamlTest {

    @Test
    public void parsesNestedMappingAndScalarTypes() {
        String text = String.join("\n",
                "protocol:",
                "  header: \"$\"",
                "  terminator: \"\\n\"",
                "  minValue: -100",
                "  scale: 1.5",
                "  enabled: true",
                "  missing: null",
                "  templates:",
                "    joystick2d: \"$J,<x>,<y>#\"");

        Map<String, Object> root = SimpleYaml.parse(text);

        assertEquals("$", SimpleYaml.getString(root, "protocol.header", null));
        assertEquals("\n", SimpleYaml.getString(root, "protocol.terminator", null));
        assertEquals(-100, SimpleYaml.getInt(root, "protocol.minValue", 0));
        assertEquals(1.5f, SimpleYaml.getFloat(root, "protocol.scale", 0f), 0.0001f);
        assertTrue(SimpleYaml.getBoolean(root, "protocol.enabled", false));
        assertNull(SimpleYaml.get(root, "protocol.missing"));
        assertEquals("$J,<x>,<y>#", SimpleYaml.getString(root, "protocol.templates.joystick2d", null));
    }

    @Test
    public void stripsCommentsButKeepsHashInsideQuotes() {
        String text = String.join("\n",
                "# 整行注释",
                "key: value # 行尾注释",
                "other: \"$J,1,2#\"",
                "   ",
                "nested:",
                "  # 只有注释",
                "  a: 1");

        Map<String, Object> root = SimpleYaml.parse(text);

        assertEquals("value", root.get("key"));
        assertEquals("$J,1,2#", root.get("other"));
        assertEquals(1, SimpleYaml.getInt(root, "nested.a", 0));
    }

    @Test
    public void parsesSequenceOfMappings() {
        String text = String.join("\n",
                "controls:",
                "  - id: joystickView",
                "    type: joystick2d",
                "    axis: both",
                "  - id: armLift",
                "    type: joystick1d",
                "    axis: vertical");

        Map<String, Object> root = SimpleYaml.parse(text);
        List<Object> controls = SimpleYaml.getList(root, "controls");

        assertEquals(2, controls.size());
        Map<String, Object> first = SimpleYaml.castMap(controls.get(0));
        Map<String, Object> second = SimpleYaml.castMap(controls.get(1));
        assertEquals("joystickView", first.get("id"));
        assertEquals("joystick2d", first.get("type"));
        assertEquals("armLift", second.get("id"));
        assertEquals("vertical", second.get("axis"));
    }

    @Test
    public void parsesSequenceAlignedWithParentKey() {
        String text = String.join("\n",
                "views:",
                "- id: joystickView",
                "  width: 220dp",
                "  height: 220dp",
                "- id: armLift",
                "  width: 0dp");

        Map<String, Object> root = SimpleYaml.parse(text);
        List<Object> views = SimpleYaml.getList(root, "views");

        assertEquals(2, views.size());
        assertEquals("220dp", SimpleYaml.getString(SimpleYaml.castMap(views.get(0)), "width", null));
        assertEquals("0dp", SimpleYaml.getString(SimpleYaml.castMap(views.get(1)), "width", null));
    }

    @Test
    public void parsesPlainScalarsWithoutQuotes() {
        String text = "a: $ARM,<id>,<value>#\n";

        Map<String, Object> root = SimpleYaml.parse(text);

        assertEquals("$ARM,<id>,<value>#", root.get("a"));
    }

    @Test
    public void rejectsTabIndentation() {
        try {
            SimpleYaml.parse("a:\n\tb: 1\n");
            fail("Tab 缩进应当抛出 YamlException");
        } catch (SimpleYaml.YamlException expected) {
            assertTrue(expected.getMessage().contains("Tab"));
        }
    }
}

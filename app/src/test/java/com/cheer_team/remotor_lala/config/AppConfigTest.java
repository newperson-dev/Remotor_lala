package com.cheer_team.remotor_lala.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;

import org.junit.Test;

/**
 * 针对“真正随包发布的 assets/config.yaml”的校验：
 * 配置一旦写错（尺寸、颜色、类型、心跳周期……），这里就会失败。
 */
public class AppConfigTest {

    private static File shippedConfig() {
        File[] candidates = {
                new File("src/main/assets/config.yaml"),
                new File("app/src/main/assets/config.yaml")
        };
        for (File candidate : candidates) {
            if (candidate.isFile()) {
                return candidate;
            }
        }
        throw new IllegalStateException("找不到 assets/config.yaml，当前工作目录: "
                + new File(".").getAbsolutePath());
    }

    private static AppConfig loadShipped() throws IOException {
        try (InputStream in = new FileInputStream(shippedConfig())) {
            return AppConfig.load(in);
        }
    }

    @Test
    public void shippedConfigHasNoWarnings() throws IOException {
        AppConfig config = loadShipped();
        assertEquals("配置不应有警告: " + config.warnings, 0, config.warnings.size());
    }

    @Test
    public void shippedProtocolMatchesDownstreamContract() throws IOException {
        AppConfig config = loadShipped();

        assertEquals("\n", config.protocol.terminator);
        assertEquals(-100, config.protocol.minValue);
        assertEquals(100, config.protocol.maxValue);
        assertEquals("$J,<x>,<y>#", config.protocol.templates.get("joystick2d"));
        assertEquals("$ARM,<id>,<value>#", config.protocol.templates.get("joystick1d"));

        FrameBuilder frames = new FrameBuilder(config.protocol);
        ControlConfig move = config.control("joystickView");
        ControlConfig lift = config.control("armLift");
        ControlConfig grip = config.control("armGrip");
        assertNotNull(move);
        assertNotNull(lift);
        assertNotNull(grip);

        assertEquals("$J,100,-50#", frames.build(move.template, move.placeholders(100, -50)));
        assertEquals("$J,0,0#", frames.build(move.template, move.placeholders(0, 0)));
        assertEquals("$ARM,lift,80#", frames.build(lift.template, lift.placeholders(0, 80)));
        assertEquals("$ARM,grip,-30#", frames.build(grip.template, grip.placeholders(0, -30)));
    }

    @Test
    public void shippedControlsAreWiredCorrectly() throws IOException {
        AppConfig config = loadShipped();

        assertEquals(3, config.controls.size());
        assertFalse(config.control("joystickView").isOneDimensional());
        assertTrue(config.control("armLift").isOneDimensional());
        assertTrue(config.control("armLift").isVertical());
        assertTrue(config.control("armGrip").isOneDimensional());
        assertTrue(config.control("joystickView").heartbeat);
        assertEquals("lift", config.control("armLift").commandId);
        assertEquals("grip", config.control("armGrip").commandId);
        assertEquals("平移", config.control("joystickView").label);
    }

    @Test
    public void shippedLayoutCoversEveryControl() throws IOException {
        AppConfig config = loadShipped();

        assertTrue(config.layout.apply);
        assertEquals(1f, config.layout.scale, 0.0001f);

        ViewLayout joystick = config.layout.forId("joystickView");
        assertNotNull(joystick);
        assertEquals(SizeSpec.Unit.DP, joystick.width.unit);
        assertEquals(220f, joystick.width.value, 0.0001f);
        assertEquals("bottom_start", joystick.anchor);
        assertEquals(32f, joystick.marginStartDp.floatValue(), 0.0001f);

        ViewLayout panel = config.layout.forId("rightPanel");
        assertNotNull(panel);
        assertEquals(SizeSpec.Unit.PERCENT, panel.width.unit);
        assertEquals(0.25f, panel.width.value, 0.0001f);

        ViewLayout lift = config.layout.forId("armLift");
        assertNotNull(lift);
        assertEquals(1f, lift.weight.floatValue(), 0.0001f);
        assertTrue(lift.height.isMatchParent());

        // 每个控件都应该能在 layout.views 里找到（否则动态创建时只能拿到默认尺寸）
        for (ControlConfig control : config.controls) {
            assertNotNull("layout.views 缺少 " + control.id, config.layout.forId(control.id));
        }
    }

    @Test
    public void shippedHeartbeatAndUiAreUsable() throws IOException {
        AppConfig config = loadShipped();

        assertTrue(config.heartbeat.enabled);
        assertEquals(100L, config.heartbeat.intervalMs);
        assertTrue(config.heartbeat.sendWhenIdle);

        assertTrue(config.ui.showLastSentFrame);
        assertEquals(300, config.ui.receivedMaxLines);
        assertEquals("#121212", config.ui.colorBackground);
        assertEquals("#00FF00", config.ui.colorTextReceived);
    }

    @Test
    public void fullscreenHidesSystemBarsByDefault() throws IOException {
        AppConfig shipped = loadShipped();
        assertTrue(shipped.ui.fullscreen.hideSystemBars);
        assertTrue(shipped.ui.fullscreen.respectCutout);

        AppConfig defaults = AppConfig.fromYaml(new LinkedHashMap<>());
        assertTrue(defaults.ui.fullscreen.hideSystemBars);
        assertTrue(defaults.ui.fullscreen.respectCutout);
    }

    @Test
    public void missingConfigStillGivesUsableDefaults() {
        AppConfig config = AppConfig.fromYaml(new LinkedHashMap<>());

        assertEquals("$J,<x>,<y>#", config.protocol.defaultTemplate());
        assertEquals("$ARM,<id>,<value>#", config.protocol.oneDimensionalTemplate());
        assertEquals("\n", config.protocol.terminator);
        assertFalse(config.warnings.isEmpty());
        assertEquals(1, config.controls.size());
        assertEquals("joystickView", config.controls.get(0).id);
    }
}

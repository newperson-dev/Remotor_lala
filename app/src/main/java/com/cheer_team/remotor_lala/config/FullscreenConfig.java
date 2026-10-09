package com.cheer_team.remotor_lala.config;

import java.util.List;
import java.util.Map;

/**
 * ui.fullscreen 段：像横屏游戏那样把系统状态栏（时间/电量）和导航栏藏起来，
 * 从屏幕边缘下滑可以临时唤出，片刻后自动隐藏。
 */
public final class FullscreenConfig {

    /** 隐藏状态栏与导航栏。 */
    public final boolean hideSystemBars;
    /** 内容避开挖孔/刘海区域（挖孔屏建议 true，避免时间/状态文字被摄像头挡住）。 */
    public final boolean respectCutout;

    private FullscreenConfig(boolean hideSystemBars, boolean respectCutout) {
        this.hideSystemBars = hideSystemBars;
        this.respectCutout = respectCutout;
    }

    public static FullscreenConfig fromYaml(Map<String, Object> yaml, List<String> warnings) {
        return new FullscreenConfig(
                SimpleYaml.getBoolean(yaml, "hideSystemBars", true),
                SimpleYaml.getBoolean(yaml, "respectCutout", true));
    }
}

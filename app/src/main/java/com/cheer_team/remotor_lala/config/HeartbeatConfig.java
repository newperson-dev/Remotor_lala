package com.cheer_team.remotor_lala.config;

import java.util.List;
import java.util.Map;

/**
 * heartbeat 段：定时重复下发当前状态帧，防止下位机超时进入失控保护。
 */
public final class HeartbeatConfig {

    public static final long MIN_INTERVAL_MS = 10L;
    public static final long MAX_INTERVAL_MS = 5000L;

    public final boolean enabled;
    public final long intervalMs;
    public final boolean sendWhenIdle;

    private HeartbeatConfig(boolean enabled, long intervalMs, boolean sendWhenIdle) {
        this.enabled = enabled;
        this.intervalMs = intervalMs;
        this.sendWhenIdle = sendWhenIdle;
    }

    public static HeartbeatConfig fromYaml(Map<String, Object> yaml, List<String> warnings) {
        boolean enabled = SimpleYaml.getBoolean(yaml, "enabled", true);
        long interval = SimpleYaml.getInt(yaml, "intervalMs", 100);
        if (interval < MIN_INTERVAL_MS || interval > MAX_INTERVAL_MS) {
            long fixed = Math.max(MIN_INTERVAL_MS, Math.min(MAX_INTERVAL_MS, interval));
            warnings.add("heartbeat.intervalMs=" + interval + " 超出 " + MIN_INTERVAL_MS + "~" + MAX_INTERVAL_MS
                    + "ms，已按 " + fixed + "ms 处理");
            interval = fixed;
        }
        boolean sendWhenIdle = SimpleYaml.getBoolean(yaml, "sendWhenIdle", true);
        return new HeartbeatConfig(enabled, interval, sendWhenIdle);
    }
}

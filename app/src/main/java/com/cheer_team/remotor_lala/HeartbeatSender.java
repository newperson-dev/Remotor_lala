package com.cheer_team.remotor_lala;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 定时心跳：按固定周期回调，用来持续下发当前状态帧，防止下位机超时进入失控保护。
 *
 * <p>周期与开关来自 config.yaml 的 heartbeat 段；界面进入后台（onPause）时应调用 {@link #stop()}。
 */
public class HeartbeatSender {

    public interface Tick {
        void onTick();
    }

    private ScheduledExecutorService executor;
    private ScheduledFuture<?> future;
    private volatile Tick tick;
    private long intervalMs = 100L;

    /** 启动（重复调用会先停掉旧的）。周期最小 10ms。 */
    public synchronized void start(long intervalMs, Tick tick) {
        this.intervalMs = Math.max(10L, intervalMs);
        this.tick = tick;
        stopInternal();
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "remotor-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
        future = executor.scheduleWithFixedDelay(() -> {
            Tick current = this.tick;
            if (current == null) {
                return;
            }
            try {
                current.onTick();
            } catch (RuntimeException ignored) {
                // 心跳线程不能因为一次异常就整体停掉
            }
        }, this.intervalMs, this.intervalMs, TimeUnit.MILLISECONDS);
    }

    public synchronized void stop() {
        stopInternal();
    }

    private void stopInternal() {
        if (future != null) {
            future.cancel(false);
            future = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    public synchronized boolean isRunning() {
        return future != null && !future.isCancelled();
    }

    public synchronized long getIntervalMs() {
        return intervalMs;
    }
}

package com.cheer_team.remotor_lala;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 经典蓝牙 SPP 连接管理（JDY-31 透传）。
 *
 * <p>发送与接收都在独立线程完成，回调用 Handler 切回主线程。
 * 结束符由 {@link #setTerminator(String)} 统一追加（默认 "\n"），
 * 因此调用方只需要传 {@code $J,10,20#} 这样的负载。
 */
public class BluetoothSppManager {

    /** 经典蓝牙 SPP 的固定 UUID。 */
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    public interface Listener {
        void onConnected(String deviceName);

        void onDisconnected();

        void onDataReceived(String data);

        void onError(String message);
    }

    private final BluetoothAdapter bluetoothAdapter;
    private final Listener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService sendExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "remotor-spp-send");
        thread.setDaemon(true);
        return thread;
    });

    private BluetoothSocket socket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private Thread readThread;
    private volatile boolean isConnected = false;
    private String terminator = "\n";

    public BluetoothSppManager(BluetoothAdapter adapter, Listener listener) {
        this.bluetoothAdapter = adapter;
        this.listener = listener;
    }

    /** 设置帧结束符（来自 config.yaml 的 protocol.terminator），空字符串表示不追加。 */
    public void setTerminator(String terminator) {
        this.terminator = terminator == null ? "" : terminator;
    }

    @SuppressLint("MissingPermission")
    public void connect(BluetoothDevice device) {
        new Thread(() -> {
            try {
                // 取消发现过程，否则连接可能极慢或失败
                if (bluetoothAdapter != null && bluetoothAdapter.isDiscovering()) {
                    bluetoothAdapter.cancelDiscovery();
                }
                socket = device.createRfcommSocketToServiceRecord(MY_UUID);
                socket.connect(); // 阻塞操作
                inputStream = socket.getInputStream();
                outputStream = socket.getOutputStream();
                isConnected = true;

                post(() -> listener.onConnected(device.getName()));
                startReadLoop();

            } catch (IOException e) {
                isConnected = false;
                closeQuietly();
                post(() -> listener.onError("连接失败: " + e.getMessage()));
            }
        }, "remotor-spp-connect").start();
    }

    /**
     * 发送一帧负载（自动补结束符）。发送排队在单线程里，避免多线程写同一个流。
     *
     * @return true 表示已排队发送；未连接时返回 false
     */
    public boolean sendData(String data) {
        if (data == null || !isConnected || outputStream == null) {
            return false;
        }
        String frame = data.endsWith(terminator) ? data : data + terminator;
        sendExecutor.execute(() -> {
            OutputStream out = outputStream;
            if (!isConnected || out == null) {
                return;
            }
            try {
                out.write(frame.getBytes(StandardCharsets.UTF_8));
                out.flush();
            } catch (IOException e) {
                isConnected = false;
                post(() -> listener.onError("发送失败: " + e.getMessage()));
            }
        });
        return true;
    }

    /** 直接发送原始字节（不追加结束符）。 */
    public boolean sendRaw(byte[] raw) {
        if (raw == null || !isConnected || outputStream == null) {
            return false;
        }
        sendExecutor.execute(() -> {
            OutputStream out = outputStream;
            if (!isConnected || out == null) {
                return;
            }
            try {
                out.write(raw);
                out.flush();
            } catch (IOException e) {
                isConnected = false;
                post(() -> listener.onError("发送失败: " + e.getMessage()));
            }
        });
        return true;
    }

    private void startReadLoop() {
        readThread = new Thread(() -> {
            byte[] buffer = new byte[1024];
            while (isConnected) {
                try {
                    int bytes = inputStream.read(buffer); // 阻塞读
                    if (bytes > 0) {
                        String received = new String(buffer, 0, bytes, StandardCharsets.UTF_8);
                        post(() -> listener.onDataReceived(received));
                    } else if (bytes < 0) {
                        break;
                    }
                } catch (IOException e) {
                    if (isConnected) {
                        isConnected = false;
                        post(listener::onDisconnected);
                    }
                    break;
                }
            }
        }, "remotor-spp-read");
        readThread.start();
    }

    public void disconnect() {
        boolean wasConnected = isConnected;
        isConnected = false;
        closeQuietly();
        if (wasConnected) {
            post(listener::onDisconnected);
        }
    }

    private void closeQuietly() {
        try {
            if (inputStream != null) {
                inputStream.close();
            }
        } catch (IOException ignored) {
        }
        try {
            if (outputStream != null) {
                outputStream.close();
            }
        } catch (IOException ignored) {
        }
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
        inputStream = null;
        outputStream = null;
        socket = null;
    }

    public boolean isConnected() {
        return isConnected;
    }

    private void post(Runnable runnable) {
        if (listener != null) {
            mainHandler.post(runnable);
        }
    }
}

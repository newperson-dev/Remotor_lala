package com.cheer_team.remotor_lala;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.cheer_team.remotor_lala.config.AppConfig;
import com.cheer_team.remotor_lala.config.ControlConfig;
import com.cheer_team.remotor_lala.config.FrameBuilder;
import com.cheer_team.remotor_lala.config.LayoutApplier;
import com.cheer_team.remotor_lala.config.ViewLayout;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 主界面：完全由 assets/config.yaml 驱动。
 *
 * <p>界面上的每个摇杆都来自 config.yaml 的 controls 段：
 * <ul>
 *   <li>XML 里已经声明了同名 id → 直接绑定，并按 layout.views 覆盖大小 / 位置；</li>
 *   <li>XML 里没有这个 id → 自动创建控件并放进 container 指定的容器。</li>
 * </ul>
 * 数据帧的格式来自 protocol.templates，心跳来自 heartbeat 段，因此扩展协议、
 * 改控件大小都不需要改本文件。
 */
public class MainActivity extends AppCompatActivity implements BluetoothSppManager.Listener {

    /** 一个摇杆的运行态：配置 + 控件 + 当前值（心跳会重复发送这些值）。 */
    private static final class ControlRuntime {
        final ControlConfig config;
        final JoystickView view;
        volatile int x = 0;
        volatile int y = 0;

        ControlRuntime(ControlConfig config, JoystickView view) {
            this.config = config;
            this.view = view;
        }
    }

    private AppConfig config;
    private FrameBuilder frames;
    private LayoutApplier layoutApplier;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSppManager bluetoothManager;
    private HeartbeatSender heartbeat;

    private TextView tvStatus;
    private TextView tvReceived;
    private ScrollView receivedScroll;

    private final Map<String, ControlRuntime> controls = new LinkedHashMap<>();
    private final Map<String, View> dynamicViews = new LinkedHashMap<>();
    private final Deque<String> receivedLines = new ArrayDeque<>();

    private volatile boolean stateDirty = true;
    private volatile String lastSentFrame = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        config = AppConfig.load(this);
        frames = new FrameBuilder(config.protocol);

        setContentView(R.layout.activity_main);

        // 保持屏幕常亮，防止比赛中熄屏
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // 隐藏系统状态栏/导航栏（配置见 config.yaml 的 ui.fullscreen）
        applyFullscreen();

        bindViews();
        applyTheme();

        layoutApplier = new LayoutApplier(config.layout, getResources().getDisplayMetrics().density,
                this::findViewByName);
        layoutApplier.applyAll();

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        bluetoothManager = new BluetoothSppManager(bluetoothAdapter, this);
        bluetoothManager.setTerminator(config.protocol.terminator);

        heartbeat = new HeartbeatSender();

        buildControls();

        Button btnConnect = findViewById(R.id.btnConnect);
        btnConnect.setOnClickListener(v -> checkPermissionsAndConnect());
        btnConnect.setOnLongClickListener(v -> {
            if (bluetoothManager.isConnected()) {
                bluetoothManager.disconnect();
                Toast.makeText(this, "已断开连接", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "长按可断开当前连接", Toast.LENGTH_SHORT).show();
            }
            return true;
        });

        // 百分比尺寸要等父容器测量完成才能换算成像素，这里再应用一次
        View root = findViewById(R.id.rootLayout);
        if (root != null) {
            root.post(() -> layoutApplier.applyAll());
        }

        showConfigWarnings();
    }

    // ================= 初始化 =================

    /**
     * 全屏（沉浸式）：像横屏游戏一样藏掉系统状态栏与导航栏。
     *
     * <p>用户从边缘下滑可以把状态栏临时唤出来，片刻后自动隐藏（BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE）。
     * 具体是否隐藏、是否避开挖孔，由 config.yaml 的 ui.fullscreen 决定。
     */
    private void applyFullscreen() {
        Window window = getWindow();
        // 内容延伸到系统栏区域（Android 15+ 强制 edge-to-edge，这里显式声明更稳妥）
        WindowCompat.setDecorFitsSystemWindows(window, false);

        // 允许绘制到挖孔区域，否则全屏后左右两侧会留下黑边
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(attributes);
        }

        hideSystemBarsIfNeeded();

        // 挖孔屏：把挖孔占用的区域留出来，避免顶部状态文字被摄像头挡住
        View root = findViewById(R.id.rootLayout);
        if (root != null && config.ui.fullscreen.respectCutout) {
            ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
                Insets cutout = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout());
                view.setPadding(cutout.left, cutout.top, cutout.right, cutout.bottom);
                return windowInsets;
            });
            ViewCompat.requestApplyInsets(root);
        }
    }

    private void hideSystemBarsIfNeeded() {
        if (!config.ui.fullscreen.hideSystemBars) {
            return;
        }
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        controller.hide(WindowInsetsCompat.Type.systemBars());
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // 部分机型（如 OriginOS / vivo）切回前台后会自己把状态栏放回来，这里再收一次
        if (hasFocus && config != null) {
            hideSystemBarsIfNeeded();
        }
    }

    private void bindViews() {
        tvStatus = findViewById(R.id.tvStatus);
        tvReceived = findViewById(R.id.tvReceived);
        receivedScroll = findViewById(R.id.receivedScroll);
    }

    private void applyTheme() {
        View root = findViewById(R.id.rootLayout);
        if (root != null) {
            root.setBackgroundColor(color(config.ui.colorBackground, 0xFF121212));
        }
        if (receivedScroll != null) {
            receivedScroll.setBackgroundColor(color(config.ui.colorPanel, 0xFF222222));
        }
        if (tvReceived != null) {
            tvReceived.setTextColor(color(config.ui.colorTextReceived, 0xFF00FF00));
            tvReceived.setTextSize(config.ui.receivedTextSizeSp);
        }
        if (tvStatus != null) {
            tvStatus.setBackgroundColor(color(config.ui.colorStatusBar, 0xFF333333));
            tvStatus.setTextColor(color(config.ui.colorTextPrimary, 0xFFFFFFFF));
            tvStatus.setTextSize(config.ui.statusTextSizeSp);
        }
        Button btnConnect = findViewById(R.id.btnConnect);
        if (btnConnect != null) {
            btnConnect.setTextColor(color(config.ui.colorTextPrimary, 0xFFFFFFFF));
        }
    }

    /** 按配置生成 / 绑定所有摇杆控件。 */
    private void buildControls() {
        for (ControlConfig control : config.controls) {
            if (!control.enabled) {
                // enabled: false —— 不绑定、不发送；XML 里若声明过同名控件就隐藏掉
                View disabled = findViewByName(control.id);
                if (disabled != null) {
                    disabled.setVisibility(View.GONE);
                }
                continue;
            }
            JoystickView view = resolveOrCreateView(control);
            if (view == null) {
                continue;
            }
            styleControl(control, view);

            ControlRuntime runtime = new ControlRuntime(control, view);
            controls.put(control.id, runtime);
            view.setJoystickListener(new JoystickView.JoystickListener() {
                @Override
                public void onMove(int x, int y) {
                    runtime.x = x;
                    runtime.y = y;
                    stateDirty = true;
                    String payload = sendControl(runtime);
                    if (payload != null) {
                        updateStatus("已发送: " + payload);
                    } else {
                        updateStatus("未连接 | " + control.id + " = " + x + ", " + y);
                    }
                }

                @Override
                public void onRelease() {
                    runtime.x = 0;
                    runtime.y = 0;
                    stateDirty = true;
                    sendControl(runtime);
                }
            });
        }
    }

    /** 先找 XML 里声明的控件；没有就按配置动态创建并加入 container。 */
    private JoystickView resolveOrCreateView(ControlConfig control) {
        View existing = findViewByName(control.id);
        if (existing instanceof JoystickView) {
            return (JoystickView) existing;
        }
        if (existing != null) {
            warn("控件 " + control.id + " 已存在但不是 JoystickView，已跳过");
            return null;
        }

        if (control.container == null) {
            warn("控件 " + control.id + " 不在 XML 中，且未配置 container，已跳过");
            return null;
        }
        View containerView = findViewByName(control.container);
        if (!(containerView instanceof ViewGroup)) {
            warn("控件 " + control.id + " 的 container(" + control.container + ") 不存在，已跳过");
            return null;
        }

        ViewGroup parent = (ViewGroup) containerView;
        JoystickView created = new JoystickView(this);
        created.setId(View.generateViewId());
        parent.addView(created, defaultLayoutParams(parent));
        dynamicViews.put(control.id, created);

        if (!layoutApplier.applyTo(created, control.id)) {
            applyFallbackSize(created);
        }
        if (parent instanceof ConstraintLayout) {
            // 约束布局里没有约束的控件会堆在左上角，这里兜底居中并给出提醒
            ViewLayout spec = config.layout.forId(control.id);
            if (spec == null || spec.anchor == null) {
                layoutApplier.applyAnchor(created, "center");
                warn("控件 " + control.id + " 动态创建在约束布局里，但 layout.views 没写 anchor，已默认居中；建议补上 anchor");
            }
        }
        return created;
    }

    private void styleControl(ControlConfig control, JoystickView view) {
        view.setAxisMode(axisModeOf(control));
        view.setMaxValue(control.maxValue);
        view.setDeadZone(control.deadZone);
        view.setInvert(control.effectiveInvertX(), control.effectiveInvertY());
        if (control.baseColor != null || control.stickColor != null) {
            view.setColors(color(control.baseColor, 0xFF333333), color(control.stickColor, 0xFF00FF00));
        }
        if (control.label != null && !control.label.isEmpty()) {
            view.setLabel(control.label);
        }
    }

    private static int axisModeOf(ControlConfig control) {
        if (!control.isOneDimensional()) {
            return "horizontal".equalsIgnoreCase(control.axis) ? JoystickView.AXIS_HORIZONTAL : JoystickView.AXIS_BOTH;
        }
        return "horizontal".equalsIgnoreCase(control.axis)
                ? JoystickView.AXIS_HORIZONTAL
                : JoystickView.AXIS_VERTICAL;
    }

    private ViewGroup.LayoutParams defaultLayoutParams(ViewGroup parent) {
        if (parent instanceof LinearLayout) {
            return new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        return new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    /** 动态创建的控件若没有 layout.views 配置，给一个能用的默认尺寸。 */
    private void applyFallbackSize(View view) {
        if (!(view.getLayoutParams() instanceof ViewGroup.MarginLayoutParams)) {
            return;
        }
        float density = getResources().getDisplayMetrics().density;
        int size = Math.round(160 * density);
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        params.width = size;
        params.height = size;
        params.topMargin = Math.round(8 * density);
        view.setLayoutParams(params);
    }

    // ================= 数据发送 =================

    /** 发送某个控件的当前状态；未连接返回 null。 */
    private String sendControl(ControlRuntime runtime) {
        if (bluetoothManager == null || !bluetoothManager.isConnected()) {
            return null;
        }
        int x = config.protocol.clamp(runtime.x);
        int y = config.protocol.clamp(runtime.y);
        String payload = frames.build(runtime.config.template, runtime.config.placeholders(x, y));
        if (bluetoothManager.sendData(payload)) {
            lastSentFrame = payload;
            return payload;
        }
        return null;
    }

    /** 心跳：把参与心跳的控件当前状态全部重发一遍。 */
    private void sendAllControls() {
        List<String> sent = new ArrayList<>();
        for (ControlRuntime runtime : controls.values()) {
            if (!runtime.config.heartbeat) {
                continue;
            }
            String payload = sendControl(runtime);
            if (payload != null) {
                sent.add(payload);
            }
        }
        if (!sent.isEmpty() && config.ui.showLastSentFrame) {
            updateStatus(sent.size() == 1
                    ? "心跳 " + config.heartbeat.intervalMs + "ms | " + sent.get(0)
                    : "心跳 " + config.heartbeat.intervalMs + "ms | " + TextUtils.join("  ", sent));
        }
    }

    private void onHeartbeatTick() {
        if (!config.heartbeat.enabled || bluetoothManager == null || !bluetoothManager.isConnected()) {
            return;
        }
        if (!config.heartbeat.sendWhenIdle && !stateDirty) {
            return;
        }
        sendAllControls();
        stateDirty = false;
    }

    private void updateStatus(String text) {
        if (!config.ui.showLastSentFrame) {
            return;
        }
        runOnUiThread(() -> {
            if (tvStatus != null) {
                tvStatus.setText(text);
            }
        });
    }

    private int color(String text, int fallback) {
        if (text == null) {
            return fallback;
        }
        try {
            return Color.parseColor(text);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    // ================= 生命周期 =================

    @Override
    protected void onResume() {
        super.onResume();
        if (heartbeat != null && config.heartbeat.enabled) {
            heartbeat.start(config.heartbeat.intervalMs, this::onHeartbeatTick);
        }
    }

    @Override
    protected void onPause() {
        if (heartbeat != null) {
            heartbeat.stop();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (heartbeat != null) {
            heartbeat.stop();
        }
        if (bluetoothManager != null) {
            bluetoothManager.disconnect();
        }
        super.onDestroy();
    }

    // ================= 权限与连接 =================

    private void checkPermissionsAndConnect() {
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "此设备不支持蓝牙", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "请先打开手机蓝牙", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> permissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_SCAN);
            }
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        if (!permissions.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toArray(new String[0]), 100);
        } else {
            showDeviceListDialog();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != 100) {
            return;
        }
        boolean allGranted = true;
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
            }
        }
        if (allGranted) {
            showDeviceListDialog();
        } else {
            Toast.makeText(this, "权限被拒绝，无法使用蓝牙", Toast.LENGTH_SHORT).show();
        }
    }

    /** 显示已配对设备列表（JDY-31 通常需要先在系统设置里配对）。 */
    @SuppressLint("MissingPermission")
    private void showDeviceListDialog() {
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        if (pairedDevices == null || pairedDevices.isEmpty()) {
            Toast.makeText(this, "没有已配对的设备，请先去系统设置配对 JDY-31", Toast.LENGTH_LONG).show();
            return;
        }

        List<String> deviceNames = new ArrayList<>();
        List<BluetoothDevice> deviceList = new ArrayList<>();
        for (BluetoothDevice device : pairedDevices) {
            deviceNames.add(device.getName() + "\n" + device.getAddress());
            deviceList.add(device);
        }

        new AlertDialog.Builder(this)
                .setTitle("选择已配对的设备")
                .setItems(deviceNames.toArray(new String[0]), (dialog, which) -> {
                    BluetoothDevice selected = deviceList.get(which);
                    updateStatus("正在连接: " + selected.getName());
                    bluetoothManager.connect(selected);
                })
                .show();
    }

    // ================= 蓝牙回调（已切回主线程） =================

    @Override
    public void onConnected(String deviceName) {
        String suffix = config.heartbeat.enabled ? " | 心跳 " + config.heartbeat.intervalMs + "ms" : "";
        runOnUiThread(() -> {
            if (tvStatus != null) {
                tvStatus.setText("已连接: " + deviceName + suffix);
            }
        });
        stateDirty = true;
        sendAllControls(); // 连上立刻同步一次当前状态
    }

    @Override
    public void onDisconnected() {
        runOnUiThread(() -> {
            if (tvStatus != null) {
                tvStatus.setText("状态: 已断开");
            }
        });
    }

    @Override
    public void onDataReceived(String data) {
        runOnUiThread(() -> {
            for (String line : data.split("\r?\n")) {
                if (!line.isEmpty()) {
                    receivedLines.addLast(line);
                }
            }
            while (receivedLines.size() > config.ui.receivedMaxLines) {
                receivedLines.removeFirst();
            }
            if (tvReceived != null) {
                tvReceived.setText(TextUtils.join("\n", receivedLines));
            }
            if (receivedScroll != null) {
                receivedScroll.post(() -> receivedScroll.fullScroll(View.FOCUS_DOWN));
            }
        });
    }

    @Override
    public void onError(String message) {
        runOnUiThread(() -> {
            if (tvStatus != null) {
                tvStatus.setText("错误: " + message);
            }
            Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
        });
    }

    // ================= 工具 =================

    /** 先查运行时动态创建的控件，再按 id 名字查 XML 里的控件。 */
    @SuppressWarnings("deprecation")
    private View findViewByName(String name) {
        if (name == null) {
            return null;
        }
        View dynamic = dynamicViews.get(name);
        if (dynamic != null) {
            return dynamic;
        }
        int id = getResources().getIdentifier(name, "id", getPackageName());
        return id == 0 ? null : findViewById(id);
    }

    private void showConfigWarnings() {
        if (config.warnings.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder("【配置提醒】\n");
        for (String warning : config.warnings) {
            sb.append("· ").append(warning).append('\n');
        }
        receivedLines.addLast(sb.toString().trim());
        if (tvReceived != null) {
            tvReceived.setText(TextUtils.join("\n", receivedLines));
        }
    }

    private void warn(String message) {
        config.warnings.add(message);
    }
}

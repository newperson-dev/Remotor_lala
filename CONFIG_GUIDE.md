# Remotor_lala 配置与复用指南

本文回答三件事：

1. **待办事项做完了什么**（右侧一维摇杆、协议扩展、心跳机制、机械臂映射）
2. **怎么改协议 / 改控件位置大小**，而且只改一个地方
3. **怎么在 Java 与 XML 里复用**这些配置（新增摇杆、新增指令）

---

## 一、总览：一份 yaml 管协议 + 界面

```
app/src/main/
├── assets/config.yaml              ← 【唯一改动入口】协议 + 布局 + 控件 + 心跳 + 配色
├── res/values/dimens.xml           ← 纯 XML 路线的尺寸变量（yaml 里没写的才会用到）
├── res/values/attrs.xml            ← JoystickView 的 XML 属性（app:jv*）
├── res/layout/activity_main.xml    ← 只描述"结构/约束"，尺寸尽量引用 @dimen
└── java/com/cheer_team/remotor_lala/
    ├── MainActivity.java           ← 读配置、绑控件、发数据（不含任何硬编码协议）
    ├── JoystickView.java           ← 一个控件同时支持 2D / 1D，属性可 XML 配置
    ├── HeartbeatSender.java        ← 心跳定时器
    ├── BluetoothSppManager.java    ← SPP 收发（结束符来自配置）
    └── config/                     ← 配置系统（无第三方依赖）
        ├── SimpleYaml.java         ← 极简 YAML 解析器（离线可用）
        ├── AppConfig.java          ← 总入口 AppConfig.load(this)
        ├── ProtocolConfig.java     ← 帧头/帧尾/结束符/模板
        ├── ControlConfig.java      ← 一个摇杆 = 类型 + 轴向 + 模板 + 量程 + 颜色
        ├── LayoutConfig.java       ← layout 段（scale + views）
        ├── ViewLayout.java         ← 单个控件的宽高/边距/锚点/weight
        ├── SizeSpec.java           ← 尺寸写法解析（dp/px/%/match_parent）
        ├── LayoutApplier.java      ← 把布局规格真正作用到控件上
        ├── FrameBuilder.java       ← 把 <x> <y> <value> <id> 填进模板
        ├── HeartbeatConfig.java
        └── UiConfig.java
```

**运行时流程**：`AppConfig.load()` → 解析 yaml → `LayoutApplier` 按 id 覆盖宽高/边距/锚点
→ 按 `controls` 段绑定（或动态创建）JoystickView → 摇杆回调经 `FrameBuilder` 拼帧
→ `BluetoothSppManager.sendData()` 自动补 `\n` 发出；同时 `HeartbeatSender` 每 100ms 重发当前状态。

配置写错不会崩溃：所有问题会记进 `AppConfig.warnings`，并显示在 App 的回传数据区顶部（`【配置提醒】`）。

---

## 二、待办事项完成情况

| 待办 | 状态 | 说明 |
| --- | --- | --- |
| 右侧一维摇杆 | ✅ | `JoystickView` 增加 `AXIS_VERTICAL / AXIS_HORIZONTAL`（长条轨道+滑块），右侧面板放了 `armLift`(升降) 与 `armGrip`(开合) 两个一维摇杆 |
| 通信协议扩展 | ✅ | 模板化：`protocol.templates` 里随意增删，`$J,<x>,<y>#` / `$ARM,<id>,<value>#` 只是默认值 |
| 心跳机制 | ✅ | `heartbeat: {enabled, intervalMs, sendWhenIdle}`，默认 100ms，可在 yaml 关闭 |
| 机械臂控制逻辑 | ✅(已按速度型实现) | 一维摇杆输出 `-100..100` 的速度增量，松开回中并立即发一帧 `value=0`；下位机把 `value` 当作"速度"积分成位置即可 |

发出去的原始字节（每帧以 `\n` 结尾）：

```
$J,<x>,<y>#\n              二维摇杆：x、y 均为 -100..100
$ARM,lift,<value>#\n       机械臂升降：value 为 -100..100（+ 上升 / - 下降）
$ARM,grip,<value>#\n       夹爪开合：value 为 -100..100（+ 张开 / - 闭合）
```

下位机（Arduino 示例）：

```cpp
if (Serial.available()) {
  String line = Serial.readStringUntil('\n');
  line.trim();
  if (!line.startsWith("$") || !line.endsWith("#")) return;
  String body = line.substring(1, line.length() - 1);   // "ARM,lift,80"
  // 按逗号切分后分发即可
}
```

---

## 三、改协议（不动 Java / XML）

`config.yaml` 里 `protocol.templates` 加一行，然后让某个控件引用它：

```yaml
protocol:
  terminator: "\n"          # 不想加结束符就写 ""
  templates:
    joystick2d: "$J,<x>,<y>#"
    joystick1d: "$ARM,<id>,<value>#"
    panTilt: "$PT,<x>,<y>#"          # ← 新增指令

controls:
  - id: joystickView
    template: joystick2d
  - id: armLift
    template: joystick1d
    commandId: "lift"
```

可用占位符（模板里没用到也无所谓）：`<x>` `<y>` `<value>` `<id>` `<name>`。
模板也可以直接写成完整字符串，例如 `template: "$S,<value>#"`（不含 `<` 时才会先当键名查表）。

数值范围由 `protocol.minValue / maxValue`（默认 -100~100）统一裁剪；每个控件还能用
`maxValue` 单独设输出上限（比如把某个开关量限制在 -50~50，配置里写 `maxValue: 50`）。

---

## 四、改控件位置与大小（三条路线，任选）

### 路线 A：改 `config.yaml`（推荐，不用重新编译资源）

```yaml
layout:
  apply: true        # false = 完全交给 XML/dimens
  scale: 1.0         # 全局缩放：1.2 就是所有 dp 尺寸放大 20%
  views:
    - id: joystickView
      width: "220dp"
      height: "220dp"
      marginStart: "32dp"
      marginBottom: "32dp"
      anchor: "bottom_start"
    - id: rightPanel
      width: "25%"            # 百分比
      height: "0dp"           # 约束布局里 = 撑满剩余空间
    - id: armLift
      width: "0dp"
      height: "match_parent"
      weight: 1               # LinearLayout 权重
```

支持的写法：`"220dp"`、`"220"`(默认 dp)、`"120px"`、`"25%"`、`"0dp"`、`match_parent`、`wrap_content`。
`anchor` 可写：`top_start / top_center / top_end / center / center_start / center_end / bottom_start / bottom_center / bottom_end`
（写了 anchor 就按它重新吸附到父容器；不写则保留 XML 里的约束）。

### 路线 B：改 `res/values/dimens.xml`（纯 XML 路线，适合大改版式）

```xml
<dimen name="joystick2d_size">220dp</dimen>
<dimen name="joystick2d_margin">32dp</dimen>
<dimen name="joystick1d_margin">8dp</dimen>
```

XML 里全部用 `@dimen/...` 引用。注意：`layout.apply: true` 时 yaml 里的同名项会覆盖 dimens，
想让 dimens 生效就把 yaml 里对应的 `views` 项删掉，或把 `layout.apply` 设为 `false`。

### 路线 C：Java 里直接设（复用控件做别的界面）

```java
JoystickView view = new JoystickView(context);
view.setAxisMode(JoystickView.AXIS_VERTICAL);   // 2D / 横向 1D / 纵向 1D
view.setMaxValue(100);
view.setDeadZone(0.08f);
view.setInvert(false, false);
view.setColors(0xFF2A2A2A, 0xFF00C8FF);
view.setLabel("升降");
view.setJoystickListener(new JoystickView.JoystickListener() {
    @Override public void onMove(int x, int y) { /* y 即一维数值 */ }
    @Override public void onRelease() { /* 回到 0 */ }
});
```

XML 里同样可以直接配（等价写法）：

```xml
<com.cheer_team.remotor_lala.JoystickView
    android:id="@+id/armLift"
    android:layout_width="0dp"
    android:layout_height="match_parent"
    android:layout_weight="1"
    app:jvAxis="vertical"
    app:jvMaxValue="100"
    app:jvDeadZone="0.08"
    app:jvLabel="升降"
    app:jvStickColor="#00C8FF" />
```

---

## 五、新增一个摇杆（示例：加第三个一维摇杆控制云台旋转）

只改 `config.yaml`（XML 不用动，控件会自动创建进右侧面板）：

```yaml
controls:
  - id: armRotate            # 新增
    type: joystick1d
    axis: horizontal         # 横向轨道
    template: joystick1d
    commandId: "rotate"
    maxValue: 80
    deadZone: 0.1
    invert: true
    label: "旋转"
    stickColor: "#FF5252"
    heartbeat: true
    container: rightPanel    # XML 里没有这个 id 时放进这个容器

layout:
  views:
    - id: armRotate
      width: "0dp"
      height: "match_parent"
      weight: 1
      margin: "8dp"
```

保存后重新运行即可。想改回 XML 声明方式也行：在 `rightPanel` 里加一个
`@+id/armRotate` 的 `JoystickView`，配置会自动绑定到它，不再动态创建。

### So Easy：复制 / 添加 / 去除摇杆

摇杆本身就是 `controls` 里的一段配置，**复制一段就是加一个摇杆，删掉一段就是减一个摇杆**。

**① 添加（复制现有摇杆）** —— 最省事的做法是把 `armGrip` 整段复制，改 4 个地方：

| 要改的 | 为什么 |
| --- | --- |
| `id` | 必须全局唯一，不能和别的摇杆重名（重名会在【配置提醒】里报警告） |
| `commandId` | 下位机靠它区分关节，例如改成 `"rotate"` |
| `label` / `stickColor` | 显示用，便于现场区分 |
| `layout.views` 里加一条同名 `id` | 不给尺寸的话动态创建的控件只有 160dp 默认大小 |

**② 去除（分两种情况）**

* 摇杆只写在 `config.yaml` 里 → 直接删掉那段 `controls` 配置即可；
* 摇杆也写在 `activity_main.xml` 里（例如 `armLift` / `armGrip`）→ 删掉 XML 里那个
  `<com.cheer_team.remotor_lala.JoystickView .../>` 块即可；如果只是**临时**不想要它，
  保留 XML、在 `controls` 里写一行 `enabled: false` 更安全（不绑定、不发送、自动隐藏，改回来只删一行）。

```yaml
  - id: armGrip
    enabled: false           # ← 临时禁用这个摇杆
    ...
```

**③ 加一个二维摇杆到主界面**（不是放进右侧面板，而是直接摆在根布局上）

```yaml
controls:
  - id: joystickView2
    type: joystick2d
    axis: both
    template: joystick2d
    maxValue: 100
    container: rootLayout     # 放进根 ConstraintLayout
    label: "云台"

layout:
  views:
    - id: joystickView2
      width: "180dp"
      height: "180dp"
      marginEnd: "32dp"
      marginBottom: "32dp"
      anchor: "bottom_end"    # 约束布局里必须写 anchor，否则控件没有约束
```

> 动态创建到 `ConstraintLayout` 的控件**必须**在 `layout.views` 里写 `anchor`；
> 忘了写不会崩，App 会把它默认居中并在【配置提醒】里提示你补上。
> 放进 `LinearLayout`（如 `rightPanel`）则用 `weight` 控制宽度，不需要 anchor。

---

## 六、心跳与安全

```yaml
heartbeat:
  enabled: true
  intervalMs: 100      # 10~5000ms
  sendWhenIdle: true   # false = 只在状态变化后的一帧发送
```

* 每个控件可以单独用 `heartbeat: false` 退出心跳（例如只需要边沿触发的开关）。
* 界面进入后台会 `stop()` 心跳，回到前台自动恢复；退出时心跳和蓝牙连接一起释放。
* 心跳发送的是**当前所有摇杆状态**，所以下位机只要在几百毫秒内收到任意一帧就不会超时。

---

## 七、界面外观

```yaml
ui:
  showLastSentFrame: true    # 顶部显示最近发出的帧
  receivedMaxLines: 300      # 回传区最多保留行数
  statusTextSizeSp: 14
  receivedTextSizeSp: 14
  fullscreen:
    hideSystemBars: true     # 隐藏系统状态栏(时间/电量)与导航栏
    respectCutout: true      # 内容避开挖孔/刘海
  colors:
    background: "#121212"
    panel: "#222222"
    statusBar: "#333333"
    textPrimary: "#FFFFFF"
    textReceived: "#00FF00"
```

摇杆本体的配色写在 `controls` 段里（`baseColor` / `stickColor`），优先级高于 XML 里的 `app:jvBaseColor`。

### 全屏：隐藏系统状态栏（时间 / 电量 / 导航栏）

`ui.fullscreen.hideSystemBars: true` 就是横屏游戏那种沉浸式全屏：状态栏与导航栏都不占位置，
**从屏幕边缘下滑可以临时唤出**，手指离开后片刻自动收起（`BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`）。

实现要点（都在 `MainActivity.applyFullscreen()` 里，共 4 步）：

1. `WindowCompat.setDecorFitsSystemWindows(window, false)` —— 内容延伸到系统栏区域；
   你的工程 `targetSdk = 37`，Android 15+ 本来就强制 edge-to-edge，显式声明更稳妥；
2. `layoutInDisplayCutoutMode = SHORT_EDGES` —— 允许画到挖孔区域，否则全屏后左右会留黑边；
3. `WindowInsetsControllerCompat.hide(systemBars())` —— 真正把状态栏/导航栏藏起来；
4. `ViewCompat.setOnApplyWindowInsetsListener(...)` —— 只把**挖孔**占用的区域留成 padding
   （只取 `displayCutout()`，不取 `systemBars()`，这样下滑唤出状态栏时界面不会跳动）。

`onWindowFocusChanged(true)` 时会再隐藏一次：vivo / OriginOS 等 ROM 切回前台后偶尔会把状态栏放回来，
这一步就是针对它的（iQOO + OriginOS 6 属于这一类）。

**如果仍然显示状态栏**，是系统层面的“应用全屏显示”开关在管：

* 设置 → 显示与亮度 → （应用）全屏显示 / 隐藏刘海 → 把 `Remotor_lala` 设为全屏显示；
* 部分 OriginOS 版本在「设置 → 显示与亮度 → 屏幕顶部显示」里，选择“隐藏顶部区域”。
* 想自己确认是否生效，把 `hideSystemBars` 改成 `false` 对比一下即可。
* 挖孔挡住顶部状态文字时保持 `respectCutout: true`；想要极致满屏（内容铺到挖孔下面）改成 `false`。


---

## 八、验证与排错

* 单元测试会校验**真正随包发布的** `assets/config.yaml`：

  ```
  gradlew :app:testDebugUnitTest
  ```

  测试包含：YAML 解析、尺寸解析、协议模板拼装，以及 `AppConfigTest`（配置有警告就失败）。
* App 里显示的 `【配置提醒】` 就是配置问题清单，常见原因：
  * 尺寸写法不认识（`220dpp`）→ 只支持 dp / px / % / match_parent / wrap_content
  * 颜色不是 `#RRGGBB` / `#AARRGGBB`
  * `type` 不是 `joystick2d` / `joystick1d`，`axis` 不是 both / horizontal / vertical
  * yaml 用了 Tab 缩进，或同层没对齐
  * `controls` 里的 id 在 XML 和 `layout.views` 里都找不到（会用 160dp 的默认尺寸）

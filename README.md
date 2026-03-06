# 悬浮马赛克 - 安卓平板隐私遮挡应用

轻量化、纯本地运行的安卓平板悬浮窗工具，在屏幕顶层创建可自定义的马赛克遮挡区域，用于临时遮挡屏幕局部内容，保护隐私或隐藏敏感信息。

## 一、技术方案

### 技术栈
| 技术 | 选择 | 理由 |
|------|------|------|
| 语言 | Kotlin | 官方推荐，简洁安全，与 Android SDK 深度集成 |
| 架构 | 原生 Android SDK | 无 Flutter/RN，安装包≤10MB，内存占用低 |
| 悬浮窗 | WindowManager + TYPE_APPLICATION_OVERLAY | 系统标准 API，兼容 Android 8.0+ |
| 马赛克渲染 | Canvas 绘制像素块 | 纯本地、无 MediaProjection，满足隐私要求 |
| 配置存储 | SharedPreferences | 轻量本地存储，无网络、无上传 |

### 核心实现思路

1. **悬浮窗管理**：`FloatService` 通过 `WindowManager.addView()` 添加三个 overlay 窗口：
   - 悬浮控制球（可拖动、贴边缩小）
   - 马赛克遮罩层（全屏透明，仅马赛克区域绘制）
   - 操作面板（点击悬浮球展开/收起）

2. **马赛克效果**：在指定矩形内绘制交替深浅的像素块，粒度 0/1/2 对应块大小 8/16/32 像素。不捕获屏幕内容，仅遮挡。

3. **手势交互**：
   - 单指长按（300ms）马赛克区域 → 拖动
   - 双指捏合/张开 → 缩放（50dp～全屏）
   - 拖动/缩放时显示半透明边界框，操作结束自动隐藏

4. **屏幕旋转适配**：`MosaicOverlayView.onSizeChanged()` 监听尺寸变化，按相对比例调整马赛克区域，保持位置和大小比例不变。

## 二、编译与运行

### 方式一：Android Studio（推荐）

1. 用 Android Studio 打开 `FloatingMosaic` 目录
2. 等待 Gradle 同步完成
3. 连接安卓平板（开启 USB 调试）或启动模拟器
4. 点击 Run 或 `Shift+F10` 运行

### 方式二：命令行

```bash
# 需先安装 Gradle 或使用 Android Studio 自带的
cd FloatingMosaic
./gradlew assembleDebug   # 生成 app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug    # 安装到已连接设备
```

### 方式三：GitHub 云端构建（无需安装 Android Studio）

**适合不想安装任何开发环境的用户**，在云端自动构建 APK：

1. 将项目推送到 GitHub（新建仓库后 `git push`）
2. 打开仓库 → **Actions** 标签页
3. 首次推送会自动触发构建；或点击左侧「构建 APK」→ 右侧「Run workflow」手动触发
4. 构建完成后，在对应运行记录中点击 **FloatingMosaic-APK** 下载 `app-debug.apk`
5. 将 APK 传到手机/平板安装即可

### 平板端权限开启

1. 首次启动会检测悬浮窗权限
2. 点击「去设置」跳转系统设置
3. 找到「显示在其他应用上层」或「悬浮窗」开关，开启
4. 返回应用，点击「开启悬浮窗」

**常见厂商路径**：
- 华为：设置 → 应用 → 应用管理 → 悬浮马赛克 → 权限 → 显示在其他应用上层
- 小米：设置 → 应用设置 → 应用管理 → 悬浮马赛克 → 权限管理 → 显示悬浮窗
- 三星：设置 → 应用 → 悬浮马赛克 → 权限 → 显示在其他应用上层
- OPPO/vivo：设置 → 应用管理 → 悬浮马赛克 → 权限 → 悬浮窗

## 三、测试注意事项

- 在真机平板上测试悬浮窗、马赛克、手势
- 测试横竖屏切换、分屏模式下显示是否正常
- 长时间运行（4 小时以上）观察是否崩溃或自动退出
- 检查安装包体积（`app-release.apk`）是否≤10MB
- 使用「开发者选项 → 运行中的服务」查看内存占用是否≤50MB

## 四、项目结构

```
FloatingMosaic/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/floatingmosaic/
│   │   │   ├── MainActivity.kt      # 主界面、权限引导
│   │   │   ├── FloatService.kt      # 悬浮窗服务
│   │   │   ├── MosaicOverlayView.kt # 马赛克视图与手势
│   │   │   ├── PrefsManager.kt      # 配置持久化
│   │   │   └── MosaicApplication.kt # Application 入口
│   │   ├── res/
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

## 五、常见问题与解决方案

### 1. 悬浮窗被系统杀死、自动消失

**原因**：后台服务在部分厂商 ROM 上会被省电策略或清理工具结束。

**解决**：
- 在「设置 → 电池 → 应用耗电管理」中将本应用设为「不限制」或「允许后台运行」
- 在「设置 → 应用 → 悬浮马赛克」中关闭「省电策略」或加入白名单
- 华为/小米等：将应用加入「受保护应用」或「自启动管理」白名单

### 2. 马赛克渲染卡顿

**原因**：马赛克区域过大或粒度过细，导致绘制量增加。

**解决**：
- 将粒度调为「粗」（块更大，绘制更少）
- 适当缩小马赛克区域
- 代码中已使用 `Paint.ANTI_ALIAS_FLAG` 和 `invalidate()` 局部刷新，一般可满足≤100ms 延迟

### 3. 屏幕旋转后马赛克偏移/变形

**原因**：`onSizeChanged` 未正确触发或比例计算有误。

**解决**：已在 `MosaicOverlayView.onSizeChanged()` 中按 `scaleX/scaleY` 比例调整 `mosaicRect`，并做边界裁剪。若仍有问题，可检查 `configChanges` 是否包含 `orientation|screenSize`。

### 4. 触摸穿透异常（马赛克区域外无法操作底层应用）

**原因**：部分 ROM 对 overlay 窗口的触摸传递实现不一致。

**解决**：`MosaicOverlayView` 在触摸马赛克区域外时返回 `false`，理论上可穿透。若仍不穿透，可尝试在 `createOverlayParams` 中调整 `FLAG_NOT_TOUCH_MODAL` 或使用 `FLAG_NOT_FOCUSABLE` 组合。

### 5. 安装失败或启动闪退

**原因**：权限未开启、minSdk 不满足、或系统限制。

**解决**：
- 确认设备为 Android 8.0 及以上
- 安装前先授予悬浮窗权限（部分 ROM 要求）
- 查看 `adb logcat` 获取崩溃堆栈，针对性修复

### 6. 无法打开设置页

**原因**：部分厂商定制了权限设置入口。

**解决**：应用会提示「请手动在应用管理中开启悬浮窗权限」，用户需自行在系统设置中搜索「悬浮窗」或「显示在其他应用上层」进行开启。

## 六、权限说明

本应用**仅申请** `SYSTEM_ALERT_WINDOW`（悬浮窗）权限，用于在屏幕顶层显示马赛克遮罩。

**不申请**：摄像头、麦克风、存储、定位、通讯录、剪贴板、自启动等。

**数据处理**：所有马赛克渲染、配置存储均在本地完成，不上传任何数据，不收集用户行为或屏幕内容。

## 七、版本信息

- 版本：1.0.0
- minSdk：26（Android 8.0）
- targetSdk：34
- 安装包目标：≤10MB
- 运行内存目标：≤50MB

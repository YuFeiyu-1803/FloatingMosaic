# 悬浮马赛克 - 技术实现文档

## 一、技术选型与理由

### 1. 开发框架
- **Kotlin + Android SDK 原生**：无 Flutter/React Native，保证安装包≤10MB、内存≤50MB
- **minSdk 26（Android 8.0）**：满足需求，TYPE_APPLICATION_OVERLAY 自 API 26 起可用
- **targetSdk 34**：适配最新系统行为

### 2. 马赛克实现方案
- **方案对比**：
  - MediaProjection 截屏 + 马赛克：需额外权限，不符合「仅悬浮窗权限」要求
  - 纯 Canvas 绘制像素块：无权限、纯本地、延迟低
- **选择**：在指定矩形内绘制交替深浅的方块，模拟马赛克遮挡效果，不读取屏幕内容

### 3. 悬浮窗实现
- **TYPE_APPLICATION_OVERLAY**：可在任意应用之上显示
- **FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL**：不抢占焦点，触摸可穿透到非马赛克区域
- **FLAG_LAYOUT_NO_LIMITS**：马赛克区域可拖出屏幕边缘（按需求「无边界限制」）

## 二、核心模块实现

### 1. FloatService（悬浮窗服务）
- 创建三个 overlay 窗口：马赛克层、悬浮球、操作面板
- 添加顺序决定 z-order：马赛克在下，悬浮球和面板在上
- 悬浮球贴边时缩小为 1/3 尺寸
- 点击悬浮球展开/收起操作面板

### 2. MosaicOverlayView（马赛克视图）
- 全屏透明 View，仅在 `mosaicRect` 内绘制马赛克
- 粒度 0/1/2 → 块大小 8/16/32 像素
- 单指长按 300ms 后进入拖动
- 双指捏合进入缩放，限制 50dp～全屏
- 触摸在马赛克区域外返回 false，尝试穿透到底层应用

### 3. PrefsManager（配置持久化）
- SharedPreferences 存储：mosaicRect、granularity、visible、floatBall 位置
- 应用重启后自动恢复

### 4. 屏幕旋转适配
- `MosaicOverlayView.onSizeChanged()` 监听尺寸变化
- 按 `scaleX = newW/oldW`、`scaleY = newH/oldH` 等比缩放 mosaicRect
- 保持相对位置和大小比例不变

## 三、平板适配策略

1. **尺寸**：使用 `dp` 和 `resources.displayMetrics` 适配不同分辨率
2. **横竖屏**：`AndroidManifest` 中 `configChanges="orientation|screenSize|screenLayout"`
3. **分屏**：overlay 窗口随系统布局变化，`onSizeChanged` 自动处理

## 四、性能优化

1. **马赛克渲染**：仅 `invalidate()` 马赛克区域（当前为整 View，可后续优化为 `invalidate(mosaicRect)`）
2. **内存**：无大图、无 MediaProjection，常驻内存主要为 View 和配置
3. **CPU**：Canvas 绘制轻量，块大小可调粗以降低绘制量

## 五、稳定性保障

1. **异常处理**：`openOverlaySettings()` 用 try-catch 捕获无法打开设置的情况
2. **服务保活**：`START_STICKY`，被杀死时系统尝试重启
3. **权限检测**：`onResume` 刷新权限状态，避免从设置返回后状态不同步

## 六、构建与产物

- **Debug**：`./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
- **Release**：`./gradlew assembleRelease` → 需配置签名，输出 `app-release.apk`
- **ProGuard**：Release 已开启 minify 与 shrinkResources，规则见 `proguard-rules.pro`

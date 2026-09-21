# 阵容盒

金铲铲之战的**本地阵容码管理 + 悬浮球快捷复制**工具。给自己用的，不联网、不上传、不碰游戏进程。

## 它能干什么

1. 把网上抄来的阵容码存进本地库，起个自己认得的名，打上赛季/标签/备注
2. 进游戏后，屏幕上飘着一个悬浮球（默认是那张插图，配金色描边）
3. 点球 → 弹出阵容列表 → 点任意一条 → 阵容码进剪贴板
4. 回游戏「阵容 → 导入阵容码」粘贴

支持从浏览器**分享**文本直接存，也会在打开 App 时**自动识别剪贴板**里的阵容码并提示保存。

## 编译

前置：JDK 17、Android SDK（platform 35 + build-tools 35.0.0）。

```bash
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools

./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

装到手机：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 首次使用要开的权限

App 里的「设置与权限」页会引导，按顺序来：

| 权限 | 为什么必须 |
|---|---|
| 悬浮窗权限 | 没有它悬浮球根本显示不出来 |
| 通知权限（Android 13+） | 悬浮球靠前台服务常驻通知保命 |
| 电池优化白名单 | 不加的话后台久了会被系统清掉 |
| 自启动 + **后台弹出界面** | 国产 ROM 藏在应用详情里的额外开关，见下 |

### 关于「后台弹出界面」

点复制时，App 会短暂启动一个**透明的 Activity** 来写剪贴板。原因是 Android 10 起剪贴板写入跟「当前有焦点的应用」绑定，而悬浮面板是 Service 窗口，不算有焦点，部分 ROM 会静默写入失败（表现为点了复制、粘贴出来是空的）。

MIUI / 华为 / ColorOS / OriginOS 会拦截这种后台启动，需要手动在**应用详情**里打开「后台弹出界面」（小米叫这个名字）之类的开关。不开的话 App 会自动降级成 Service 直接写剪贴板，多数情况下也能用，但不如走 Activity 稳。

## 代码结构

```
app/src/main/java/com/wangye/tftbox/
├── TftApp.kt              Application，持有数据库 / Repository
├── MainActivity.kt        Compose 宿主，处理分享 Intent 和剪贴板检测
├── data/                   Room：Lineup 实体 / DAO / Repository
├── ui/                     Compose 界面：列表、编辑、设置与权限
└── overlay/                悬浮球、浮窗、复制，核心都在这里
    ├── FloatingService.kt     前台服务，挂球和面板
    ├── BallView.kt            可拖动吸边的悬浮球（画「阵」字）
    ├── PanelView.kt           浮窗面板：搜索 + 列表
    ├── LineupAdapter.kt       列表行（经典 View 手写）
    ├── ClipboardActivity.kt   透明 Activity，专门负责写剪贴板
    └── BootReceiver.kt        开机自启
```

### 几个设计取舍

- **浮窗不用 Compose**：浮窗是 Service 的窗口，没有 Activity 生命周期，跑 Compose 要额外接 `ViewTreeLifecycleOwner` / `SavedStateRegistry`，不值得。列表结构简单，经典 View 手写更直接。
- **阵容码不做解析**：没人公开过它的编码格式，猜出来的字段不可靠。名称/标签/赛季全部由用户自己标注。如果以后拿到足够样本能解出英雄和羁绊，再考虑加自动填充。
- **前台服务类型用 specialUse**：悬浮窗不属于任何标准前台服务类型（camera / location / mediaPlayback …），Android 14 起必须显式声明，`specialUse` 是官方给这类用途的口子。

## 已知限制

- 部分游戏（尤其腾讯系）可能弹「检测到悬浮窗」提示，或手机的「游戏空间 / 触控优化」会吞掉浮窗的触摸。真机遇到再说。
- 悬浮球位置、开关状态存在 SharedPreferences；阵容数据在 Room，卸载会一起没。暂时没做导出备份。

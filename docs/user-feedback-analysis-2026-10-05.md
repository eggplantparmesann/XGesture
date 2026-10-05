# 用户反馈分析（2026-10-05）

> 本文只做分析，不含任何代码改动。结论标注 `相对路径:行号` 证据，并区分「已证实」与「推测／平台事实」。
> 仓库版本：1.33.0（versionCode 68），主分支 main。

## 0. 结论速览

| # | 反馈 | 性质 | 现状是否成立 | 可行性 | 建议优先级 |
| :- | :- | :- | :- | :- | :- |
| 1 | 手势动作增加「执行 JavaScript」 | 新功能，**需求语义有歧义**；无现成引擎 | 无 JS 执行动作，仅 Shell | 中（MVP 10~14 文件；全量 30+） | 需先澄清需求 |
| 2 | 识别文字后的选词面板类似 FV、挡屏幕中间 | 体验优化（**可能是另一个面板**） | 部分成立 | 高 | 中高 |
| 3 | 悬浮球增加「两边都显示为线」模式 | 新功能 + 现有模式缺陷（含 1 个疑似真 bug） | **成立** | 中（交互需先定义） | 中高 |
| 4 | 悬浮球增加「向下再向内」混合手势 | 新功能，**边缘手势已有成熟实现可复用** | 悬浮球侧不支持 | 中高 | 中 |
| 5 | 锁屏收到消息，解锁后提醒悬浮球还在/停留几秒 | 体验缺陷 | **不成立**（熄屏即整窗清除） | 高 | 高 |

---

## 1. 手势选项里增加「执行 JavaScript」

### 反馈理解与歧义
用户原话只说「手势选项里面加个执行 JavaScript 的功能选项，目前只有执行 shell 命令的」。至少有三种互不相同的诉求，实现代价差一个数量级：

- **理解① JS 作为「轻量脚本宿主」**：手势触发时在本应用进程里跑一段用户写的 JS，通过桥接 API 调用本应用已有能力（返回/Home/发通知/跑 shell/读剪贴板……），等价于「Shell 动作的 JS 版本 + 条件/循环编排」。
- **理解② JS 注入当前前台界面**：对当前正在浏览的网页执行 JS（改页面、自动点击）。**普通应用做不到**——无法向其它应用的 WebView 注入脚本，除非 LSPosed hook 目标应用并拿到 WebView 实例（工程量与风险极高，且要按 App/WebView 版本适配）；本仓自建 WebView 也拿不到别人的页面上下文（见下文「关键限制」）。
- **理解③ JS 交给外部执行**：把脚本写文件后通过 Shizuku/Root 交给外部解释器。Android 上没有系统自带的 JS 命令行解释器，实际仍要自带引擎，等于绕回理解①。

> 建议先确认是哪一种；若是理解②，需要明确告知「不可行或需 LSPosed 深度适配」。下面「方案 A–D」是**实现选项**，与这里的三种理解不是同一套编号。

### 现状（已证实）
- 动作体系是「单一枚举 + 字符串载荷」：`core/gesture/src/main/java/com/slideindex/app/gesture/GestureAction.kt:24`（`GestureActionType`，当前最大 id 90，**89 与 91 均空闲，建议新动作取 91 以示追加**），`ExecuteShellCommand` 定义在同文件 `:398-403`，反序列化 `:785`。
- Shell 动作链路：`app/src/main/java/com/slideindex/app/gesture/ActionExecutor.kt:146` → `:458-471`（子线程执行）→ `app/src/main/java/com/slideindex/app/util/ShellCommandRunner.kt:18-39`（Shizuku/Root 通道 + 命令历史）。命令支持占位符（`{package}` `{timestamp}` `{datetime}`，`core/common/src/main/java/com/slideindex/app/shell/ShellCommandTemplate.kt:15-21`）——这是「新动作能拿到什么上下文」的现成参照。
- 配置对话框与集中式辅助函数：`app/src/main/java/com/slideindex/app/ui/GestureExecuteShellCommandConfigDialog.kt:179-208`。
- **没有 JS 引擎**：全仓 grep `javascript / rhino / quickjs / duktape / j2v8 / ScriptEngine` 只有 2 处命中，且都是**安全拦截**（`app/src/main/java/com/slideindex/app/overlay/pickresult/PickResultUrl.kt:51` 把 `javascript`、`data` 列为 blockedSchemes）；`evaluateJavascript` **0 命中**；依赖里没有任何 JS 引擎。
- **WebView 基础设施存在但依赖是「挂着没用」**：`androidx.webkit 1.17.0` 已声明（`gradle/libs.versions.toml:42,121`、`app/build.gradle.kts:460`），但源码里 `WebViewCompat`/`WebViewFeature` **0 命中**（安卓 `android.webkit.WebView` 属系统组件，无需该依赖）；实战先例在 `app/src/main/java/com/slideindex/app/overlay/FloatBallImageSearchPanel.kt:1148`（`WebView(context)`、`:1159` `javaScriptEnabled = true`、`:244-247` 强制主线程、`:1130-1140` 渲染进程崩溃兜底、`:257` 用 overlay host context）。→「隐藏 WebView 当 JS 引擎」在本仓是**已被验证可行、零新依赖**的路径（含「无 Activity 的 Service 里用 WebView」这一关键前提）。
- 分类与选择器：`app/src/main/java/com/slideindex/app/ui/picker/GestureActionCatalog.kt:180-181`（Shell 两个动作归 `GestureActionCategory.Pointer`）；文案/图标需同步：`app/src/main/java/com/slideindex/app/ui/gesturepicker/GestureActionPickerSearch.kt:276-277,423-424`、`app/src/main/java/com/slideindex/app/ui/GestureActionIcons.kt:33`、`app/src/main/java/com/slideindex/app/ui/GestureActionOutlinedIcons.kt:37-38,127-128`、多语言 `app/src/main/res/values*/strings.xml`。

### 主要成本在「参数化动作的配置入口散落各处」（已证实）
带参数的动作（目前只有 Shell）在每个手势配置入口都有一处特判（跳转编辑子页 / 显示命令预览）：

- `app/src/main/java/com/slideindex/app/ui/FloatBallGestureSettingsScreen.kt:180-182`
- `app/src/main/java/com/slideindex/app/ui/SideGestureSlotConfigScreen.kt:381,419`
- `app/src/main/java/com/slideindex/app/ui/FloatingPointerRadialMenuSettingsScreen.kt:190-197,426-429`
- `app/src/main/java/com/slideindex/app/ui/FingertipRingSettingsScreen.kt:125-133`
- `app/src/main/java/com/slideindex/app/ui/FloatingPointerEdgeSideSettingsScreen.kt:133-139`
- `app/src/main/java/com/slideindex/app/ui/gesturepicker/GestureActionPickerLazyItems.kt:135-137`
- `app/src/main/java/com/slideindex/app/ui/quicklauncher/QuickLauncherEditorAddPickerLazyItems.kt:113`
- `app/src/main/java/com/slideindex/app/ui/quicklauncher/QuickLauncherAddOverlaySections.kt:92,111,123,181,435,840`
- `app/src/main/java/com/slideindex/app/ui/appswitcher/AppSwitcherSlotConfigSheet.kt:133,250`

**结论**：识别+执行本身几十行，但要把「动作参数配置」推广到上表 ~9 个入口。建议先把 `gestureActionNeedsShellCommandConfig` 泛化为「动作参数配置 spec」，让 Shell 与 JS 共用一套入口，避免第二次复制。

### 执行侧（方案 A：隐藏 WebView）的技术要点
- 引擎宿主：隐藏 `WebView`（已有先例）或引入 QuickJS/Rhino 依赖（增体积，Lite/Full 分包需评估）。
- 线程/生命周期：无 Activity 环境下可用 application context 创建 WebView，但必须主线程创建并显式 `destroy()`；WebView 只能存在于单进程（本仓已是多进程架构，见 `docs/multiprocess_refactor_plan.md`），内存与崩溃面需要评估。
- 能力边界：能做什么完全取决于我们桥接什么（`@JavascriptInterface`）；**JS 默认做不到 shell 能做的事**，反之 shell 能做的用 JS 包装只是语法糖 + 流程编排。
- 安全：Shell 动作已属高危；若 JS 再暴露 shell/网络桥，风险等同，需与现有提示/审计一致（`app/src/main/java/com/slideindex/app/gesture/GestureActionPermissionAuditor.kt` 目前未特判 Shell 动作）。

### 方案对比（细化）

| 方案 | 新增依赖 | 权限 | JS 能拿到什么 | 体积 | 备注 |
| :- | :- | :- | :- | :- | :- |
| **A 隐藏 WebView + `evaluateJavascript`** | 0（系统组件） | 0 | 只有该 WebView 自己的 DOM；要点击/读通知**必须自己桥接** | ≈0 | 本仓已有 Service 态 WebView 先例，风险最低 |
| **B Rhino / QuickJS / Duktape** | 必须新增 | 0 | 无 DOM，只有 ECMAScript 内置 + 自注册宿主函数 | +0.3~1.5 MB | 能力上限不高于 A，还要处理 R8/反射/单线程约束 |
| **C Shizuku/Root 交给外部解释器** | 0 | Shizuku/Root | 最弱：Android shell **没有** JS 解释器，`am` 也回传不了结果 | 0 | 只有设备另装 node/Termux 才可能，不推荐 |
| **D A + 白名单桥接**（推荐形态） | 0 | 视桥接而定 | 固定宿主函数（点击/滚动/粘贴/取前台包名/发通知…），能力=桥接设计 | ≈0 | **唯一能在「手势触发 + 当前前台应用」语境下真正做事的形态** |

**关键限制（已证实）**：本项目的 WebView 全是自建 overlay（`FloatBallImageSearchPanel.kt:1148` 是唯一实例），拿不到系统浏览器/其它 App 的页面上下文 → **「对当前正在浏览的网页执行 JS」在当前架构下做不到**（除非 Xposed/无障碍越界手段）。这直接决定第 1 条反馈只能按 A/D 理解才有意义。

### 编译器会强制你补齐的 5 处（已证实，改漏即编不过）
1. `core/gesture/src/main/java/com/slideindex/app/gesture/GestureAction.kt:118` 附近（枚举追加，**id 91 未被占用**）与 `:785` 的 `from(type,payload)` 分支；
2. `app/src/main/java/com/slideindex/app/gesture/ActionExecutor.kt:146` 附近（`:425` 的 `when` 无 `else`）；
3. `app/src/main/java/com/slideindex/app/gesture/GestureSessionActionDispatch.kt:445` 附近（`:459-463` 无 `else`）；
4. `app/src/main/java/com/slideindex/app/ui/gesturepicker/GestureActionPickerSearch.kt:216-307`（文案 `when` 无 `else`）；
5. `app/src/main/java/com/slideindex/app/ui/GestureActionOutlinedIcons.kt:38` 与 `:128`（两处无 `else`）。

### 白名单与入口（已证实，结论是「几乎不用改」）
- 动作体系是统一的：`ActionExecutor.execute`（`ActionExecutor.kt:130`）被边缘手势、悬浮球、边角轮盘、指尖环、全息/蜂窝启动器、摇杆、音量面板、摇一摇共用（宿主清单见 `overlay/EdgeGestureOverlayView.kt:74`、`overlay/FloatBallOverlay.kt:1797,2014`、`overlay/corner/CornerGestureOverlayView.kt:80`、`overlay/fingertip/FingertipRingOverlayWindow.kt:226`、`overlay/holographic/HolographicLauncherOverlayWindow.kt:81`、`overlay/FloatingPointerWindowLifecycle.kt:230`、`overlay/volumepanel/VolumePanelContent.kt:117`、`shake/AppShakePorts.kt:77`）→ **加一个分支，所有入口自动获得执行能力**。
- Slot 白名单是**黑名单实现**：`core/gesture/src/main/java/com/slideindex/app/gesture/GestureActionSlotPicker.kt:32-36` 只排除少数动作，`ExecuteShellCommand` 不在其中 → 新增 JS 动作**默认出现在全部 6 个 `SlotPickerKind`**（`:4-17`），**无需向任何白名单添加**；有测试守门 `core/gesture/src/test/java/com/slideindex/app/gesture/GestureActionSlotPickerTest.kt:72-80`。只有当 JS 只能在主线程/有 Activity 时运行，才需要在 `:32-36` 加**一条**排除。
- **消息手势不共用体系**：`app/src/main/java/com/slideindex/app/ui/MessageGestureActionPickerScreen.kt:9-22` 是硬编码的 `MessageAction` 列表、执行走 `feature/message/.../MessageReminderOrchestrator.kt:208,233` → 若也要给消息手势加 JS，是**另一份独立工作量**。
- 顺带发现既有缺口：`GestureActionPickerSearch.kt:676-795` 的 `requestPermissionForAdjustAction` **没有 `ExecuteShellCommand` 分支**（掉进 `:793 else -> Unit`）→ 从选择器点「配置」不会引导授权。

### 工作量分级与实施顺序
- **MVP（阶段 0-2，10~14 个文件）**：`GestureAction` 数据层 → `ActionExecutor` + 新建 JS 运行时装包（建议 `app/src/main/java/com/slideindex/app/script/JavaScriptRuntime.kt`）→ 选择器条目/文案/图标（可先复用现有 Shell 编辑页改标题编辑脚本）。
- **完整体验版（30+ 个文件）**：12 条配置页路由（`app/src/main/java/com/slideindex/app/ui/navigation/AppNavKey.kt:69,86,145,184,227,257,292,331,360,416,484,502`）+ 13 个宿主文件（`HomeNavEntries`/`FloatBallNavEntries`/`FloatingPointerNavEntries`/`CornerGestureSlotNavEntries`/`ShakeNavEntries`/`QuickLauncherNavEntries`/`HoneycombLauncherNavEntries`/`CornerGestureSlotEditorHost`/`QuickLauncherAddOverlaySections`/`QuickLauncherAddPickerScreen`/`HoneycombLauncherAddPickerScreen`/`QuickLauncherEditorAddPickerLazyItems`/`ExpandPanelSlotPicker`）——**同一模式复制 12 遍，是总工作量的大头**。
- **payload 风险（推测）**：动作载荷格式是 `<typeId>\u001C<body>`（`core/gesture/src/main/java/com/slideindex/app/launcher/QuickLauncherItem.kt:129,152-183`），JS 正文若包含 `\u001C` 会被截断/误解析 → 建议 base64 或转义后存储。
- **结果反馈缺口**：手势路径现在**完全丢弃执行结果**（`ActionExecutor.kt:458-471`），仅写历史（`app/src/main/java/com/slideindex/app/shell/ShellOutputHistoryRecorder.kt:9-30`）；若 JS 要「做点事并让用户看见」，需要同时补一套结果/错误提示链路（可参考面板路径 `app/src/main/java/com/slideindex/app/overlay/ShellCommandPanelController.kt:198-215`）。

> 建议：先做 MVP（隐藏 WebView + 桥接白名单），**不要**一上来铺 12 条路由；MVP 验证后再批量复制。

### 待确认
1. JS 跑在哪（理解①/②/③）？是否接受「JS 只能调我们暴露的 API」？
2. 是否需要结果/错误可见（会额外牵出提示 UI）？
3. 是否需要覆盖消息手势（另一套体系）？

---

## 2. 识别文字后的「选词/取词面板」类似 FV，挡在屏幕中间

### 现状（已证实）
- 面板窗口本身不参与定位：全屏 `MATCH_PARENT` + `TOP|START`（`app/src/main/java/com/slideindex/app/overlay/OverlayPanelLayoutParams.kt:27-42,67-74`），真正的锚定在 Compose：**贴底居中**
  `Box(fillMaxSize, contentAlignment = Alignment.BottomCenter)` → `Modifier.overlayBottomPanelWidth()`（`app/src/main/java/com/slideindex/app/overlay/pickresult/FloatBallPickResultContent.kt:444-456`），自下而上滑入（`PickResultPanelLayout.kt:687-691`）、卡片下圆角为 0（`PickResultPanelChrome.kt:265-270`）。
- 尺寸：竖屏**铺满宽度**、高度上限 **0.85 屏高**；横屏宽度 `min(560dp, 72%屏宽)`、高度上限 **0.92 屏高**（`app/src/main/java/com/slideindex/app/overlay/OverlayBottomPanelMetrics.kt:13-16,37-61`）；面板高 = 上限 − IME 高（`FloatBallPickResultContent.kt:160,365-367`），实际高度 `wrapContentHeight().heightIn(min=190dp, max=panelContentHeight)`（`PickResultPanelLayout.kt:1231-1246`）。
- 两种样式（`feature/settings/src/main/java/com/slideindex/app/settings/PickResultPanelStyle.kt:4-17`，默认 `TAB_PAGED` `AppSettingsSlices.kt:264`）：
  - `TAB_PAGED`：46dp 药丸 Tab 头 + `HorizontalPager`（文本页/图片页）`PickResultPanelLayout.kt:803-806,1291-1374`；
  - `INTEGRATED_BOTTOM_BAR`：图片区→分隔线→文本区→搜索网格同屏堆叠（`:1375-1398`）。
  - 横屏「有图有文」时强制双列（0.58/0.42），分页样式失效（`:804,102-103`；`FloatBallPickResultContent.kt:155-159`）。
- **面板不可移动、不可改宽高**，布局行为设置页 11 项里没有任何位置/尺寸项（`app/src/main/java/com/slideindex/app/ui/FloatBallPickPanelLayoutBehaviorSettingsScreen.kt:132-354`，逐项：样式、字号 15sp、复制按钮位置 LEFT、图片工具栏位置 LEFT、文本优先 false、搜索网格默认态、点词默认态、自动全选 false、复制后关闭 false、触感 true、进出场动画 64/64ms）；面板内的纵向拖动**已被折叠机制占满**（`PickResultPanelLayout.kt:115-182,398-407,471-521,1009-1049`）。
- **关键发现：取词点 anchor 传了但被丢弃**——`showResult(context, anchorX, anchorY, ...)` 除转发外从未读取（`app/src/main/java/com/slideindex/app/overlay/FloatBallPickResultPanel.kt:315-327`），而所有调用方都认真算过 anchor（`FloatBallOverlay.kt:2484-2503,2514-2530`、`RegionalPickOverlay.kt:434-474`、`FloatingPointerHoverSelectController.kt:198-239`、`SlideIndexAccessibilityService.kt:761-779`）。→ **「按取词点定位面板」的入参已就位，调用点零改动**。
- FV 相关注释只覆盖分词算法与取词点偏移（`pickresult/PickResultWordTokenizer.kt:8`、`FloatBallPickAnchor.kt:8`），**没有任何 FV 式面板位置策略的移植痕迹**（现役实现是「FV 式分词 + 非 FV 式的全宽贴底卡片」）。

### 判断：用户说的「挡屏幕中间」可能有三个不同来源
1. **取词面板本身**：不是居中对话框，但上限 0.85 屏高；`INTEGRATED_BOTTOM_BAR` 下且 `floatBallPickTextFirstPanel=false`（默认）时**截图区默认展开**（`FloatBallPickResultContent.kt:318-320,134-138`），图片区上限 = 屏宽（`PickResultImageSearchBar.kt:107-114`），叠加文本与操作栏必然撞上限 → 面板顶边到屏幕约 15% 处，视觉上就是「盖住中部」。
2. **搜索网格展开 / 滑到图片页 / 键盘弹出**：都会把面板抬高（`PickResultTextSearchGrid.kt:96-111`、`PickResultPanelLayout.kt:818-823,1177-1181`）；编辑态高度 = `panelContentHeight + overlayImeBottom`（`:956-959`）→ 键盘上方整段被占。
3. **真正居中的是另一个面板**：以图搜图 `FloatBallImageSearchPanel` 是 `Gravity.CENTER` + 全屏黑色遮罩 + ≤520dp/90% 屏宽卡片（`FloatBallImageSearchPanel.kt:400-413,587-605`；`ImageSearchPanelMetrics.kt:8-19`），从取词面板点「以图搜图」即打开它（`FloatBallPickResultPanel.kt:1258-1260`）。另有一个 `FloatBallTranslatePanel`（`contentAlignment=Center`，`FloatBallTranslatePanel.kt:422-443`）**疑似已废弃**（`showLoading/showResult/showError` 在仓库内无外部调用者）。

> 因此需要先和用户确认「挡中间」看到的是哪一个：取词面板、展开后的搜索/图片区，还是居中的以图搜图卡片。

### 可选改进（代价从低到高）
1. **降低/可调面板高度上限**（把竖屏 0.85 做成设置项；改动集中在 `OverlayBottomPanelMetrics.kt`）。
2. **消费 anchor 让面板靠近取词点 / 球侧**（入参已存在，仅需把它存进 Compose 状态并按球侧选择 `BottomStart`/`BottomEnd`；注意 `FloatBallLayout.resolvedActiveSide` 可复用）。风险：横屏整宽假设（`FloatBallPickResultContent.kt:162-184`）、IME 避让假设「贴底 + bottom padding」（`PickResultPanelLayout.kt:1234`）、入场方向与 0 下圆角。
3. **面板整体可拖拽/上滑收起**：纵向手势预算已被折叠占用、横向与 `HorizontalPager` 冲突（`PickResultPanelLayout.kt:1304-1320`），需先划清手势分区，成本最高。
4. 若「类似 FV」指**从球附近弹出的小面板**（而非全宽贴底卡片），那是新样式而不是参数调整。

### 顺带发现（可选修）
- 默认值双份漂移：`floatBallPickTextModeDefault` 在 `OverlaySettings.kt:114` 是 `ALWAYS_ON`、在 `AppSettingsSlices.kt:270` 是 `REMEMBER_LAST`（实际读取走 `AppSettings.floatBall`）；新增字段时两处都要改。

### 待确认
- 「类似 FV」指位置、尺寸还是呼出方式？可接受的最大高度（例如 60%）？

---

## 3. 悬浮球增加「两边都显示为线」的模式

### 现状（已证实）
- 「左右两边同时显示」= `FloatBallPositionMode.BOTH_EDGES`（`feature/settings/src/main/java/com/slideindex/app/settings/FloatBallPositionMode.kt:3-19`；`selectable` 只暴露 LEFT/RIGHT/BOTH_EDGES，`:11`；`CUSTOM` 隐藏，`fromStorageKey` 把 null/CUSTOM 回落 RIGHT，`:13-17`）。中文文案「左右两边同时显示」「边缘线」「线高度」「触发区宽度」（`app/src/main/res/values-zh/strings.xml:1420-1428`）。
- 该模式**不是两侧都显示球**，而是「一侧球 + 对侧一条边缘线」：
  - 球侧 = `floatBallActiveSide`（`app/src/main/java/com/slideindex/app/overlay/FloatBallLayout.kt:32-40`）；线只在 BOTH_EDGES 显示（`:53-54`）且恒在球的对面（调用方传 `opposite(activeSide)`：`FloatBallOverlay.kt:1634-1642`、`FloatBallSceneState.kt:123-137`、`FloatBallChrome.kt:81-90`）；
  - 线矩形 `FloatBallLayout.kt:128-145`；球停靠横向 `dockedBallLeftPx :170-178`（按 `visibleFraction` 可半出屏）。
  - 默认参数：球 48dp（`AppSettingsSlices.kt:210`）、`visibleFraction = 1f`（`:215`）、位置 Y 0.55（`:219`）、线高 0.08（`:240`）、线宽 0.04（`:242`）、线透明度 0.9（`:243`）；范围：可见比例 0.5–1、线高 0.04–0.4、线宽 0.01–0.50。
- 设置 UI：位置下拉与线参数只在 BOTH_EDGES 出现（`app/src/main/java/com/slideindex/app/ui/FloatBallAppearanceSettingsScreen.kt:78-81,168-178,226-304`）。

### 「两边触发范围不一致」的精确来源（已证实，核心）
- 球与线是**两个独立的 WindowManager 触摸窗**（另有 display 窗）：`FloatBallOverlay.kt:1275,1283,1292`（后两者共用 `buildTouchLayoutParams` `:1742-1759`）。**全仓没有 `setTouchableRegion`/`Region` 机制**，命中靠「窗口尺寸 == 命中矩形」+ 视图内复核，未命中 DOWN 返回 false 放行（`FloatBallTouchHostLayout.kt:212-235`、`FloatBallStripHost.kt:165-197`）；拖出期间临时扩成 MATCH_PARENT（`:1493-1511`）再收回（`:1513-1543`）。
- 球侧命中 = 球的**外接正方形**（边长 36–72dp，默认 48dp；`FloatBallLayout.kt:59-60`、`FloatBallSceneState.kt:102-121`），圆外四角也算；`visibleFraction<1` 时半个矩形在屏外不可触。
- 线侧命中 = `max(屏宽×线宽分数, 24dp) × max(屏高×线高分数, 48dp)`（`FloatBallLayout.kt:64-74`）→ 1080×2400 默认值下约 **24dp × 70dp**；而**线的视觉只有 4dp 宽**（`FloatBallChrome.kt:156`），即「看得见的细、摸得到的宽」。
- 对比结论：球侧「所见即所得」，线侧「命中区远大于视觉」。两侧形状/面积/可拖动性都不同，用户说「两边模式不好用」是准确的。

### 顺带发现的疑似真 bug（与本次反馈相关）
`app/src/main/java/com/slideindex/app/gesture/KeyboardTriggerBoundsAdjuster.kt:68-74` 的 NARROW 只从 `right` 收窄（`copy.right = copy.left + newWidth`）：**贴右边缘的线会被整体推离屏幕右边缘** `width×(1-scale)`，贴左边无此问题；球侧只调纵向（`FloatBallLayout.kt:228-255`）。在双线模式下这个问题会更显眼，建议单独修。

### 关键设计问题（必须先定义）
两侧都是线时，**球去哪了、怎么呼出球/取词/面板？** 机制上「线窗 DOWN 已进入手势、单击/双击/长按不进取词」（`FloatBallGestureDetector.kt:21-24`）、回调带 `fromLineStrip=true`（`FloatBallOverlay.kt:1245-1249`）、单击延迟派发（`:118-123`）——所以「用线的手势打开面板」这条路已经通，但产品上要定默认绑定与引导文案。

三个可选定义：
- **方案 A「双线 + 拖出球」**：两侧常驻线，按线拖动时球从该侧浮现并跟手，松手收回线（把今天「从线拖出 → 球换边」变成两侧对称）。
- **方案 B「双线 + 线即手势面」**：线自身承接各方向手势与取词，球只在拖动中出现。
- **方案 C「保留球，让球侧命中区也可拉长/放大」**：不新增模式，只解决「触发不一致」；但外观不与用户诉求（显示为线）一致。

### 改动面与风险（方案 A）
- **不能用一个窗口覆盖两条线**：两线不连续，需引入本仓完全不存在的 `Region`/`setTouchableRegion`；正确做法是**新增第二个 `FloatBallStripHost` 窗口**，窗口数 3→4。第二个窗必须对称补齐：`dismiss`（`FloatBallOverlay.kt:774-805`）、截图摘挂/回贴（`:1010-1050`）、idle chrome（`:1363-1406`）、enable/passthrough（`:1440-1476,1558-1569`）、fullscreen 扩收（`:1488-1543`）、空闲几何（`:1610-1651`）、`updateSettings`（`:766`）、`areChromeWindowsAttached`（`:2203-2209`）、`bringOverlayToFront`（`:2211-2240`）与 z-order——任一处漏对称 = 幽灵窗或线消失。
- 数据层：`FloatBallPositionMode.kt:3-19`（新值 + `selectable` + `fromStorageKey`）、`AppSettingsSlices.kt:236-243`、`OverlaySettings.kt:97-101`、`SettingsPreferenceKeys.kt:438-441`、`SettingsSnapshotReader.kt:432-439`、`SettingsSliceBridges.kt:189-193`、`AppSettings.kt:273-284`、`OverlaySettingsMutator.kt:900-910`（`when(mode)` 需穷尽）。
- 布局：`FloatBallLayout.kt:32-54,128-145,45-46,194-210`（键盘避让可复用）。
- 面板/准星侧别：现在是「对面那条」（`FloatBallLayout.kt:45-46`），双线时须改为「按下的那条」；`FloatBallOverlay.kt:2009-2013`、`FloatBallPickAnchor.kt:147-162`。
- LSPosed 接管：契约可复用（`ModuleHookBridgeContract.kt:129-138` 的 `TARGET_FLOAT_BALL=16`/`TARGET_FLOAT_LINE=17`），**模块侧代码就在本仓**（`app/src/main/java/com/slideindex/app/xposed/**` 的 `TakeoverGeometry.kt:34-36,67-71`、`SystemGestureTakeoverController.kt:124-130`、`TakeoverSessionPolicy.kt:110-122`：会话只记 `sideId`，两条线共用 target 不会中途断流）。但 **app 侧复核必须改成「任一条线命中」**（`FloatBallOverlay.kt:1686-1695` 现在只算 `opposite(activeSide)` 那一条），否则被模块吞掉的那条会复核失败 → 模块放行（表现为该侧不接管，不会形成死区）。
- 测试：`FloatBallLayoutTest.kt:82-105`、`TakeoverExtraRectsTest.kt:82-98`、`TakeoverExtraRectsFingerprintTest.kt:41-96`、`FloatBallLineDragSwapTest.kt:16`。
- 无半成品可复用：生产代码没有任何该模式实现或 TODO（只有 `SlideIndexAccessibilityService.kt:153`、`TakeoverGeometry.kt:34`、`ModuleHookConfigWriter.kt:63` 的**过期注释**仍写「悬浮球线条待下一步」）。
- 附带：`FloatBallLayout.kt:147-149` 的 `activeSideAfterLineDragSwap` **生产代码从未调用**（仅测试引用），实际走 `FloatBallSide.opposite`；`floatBallVisibleFraction` 只作用于球（`:96,170-178`），双线模式下该滑条应隐藏或改义。

### 待确认
- 两侧都是线时球的呼出方式；面板从哪侧弹；两侧线条参数是否共享；`floatBallActiveSide` 是否还有意义。

---

## 4. 悬浮球增加「向下再向内」的混合手势

### 现状（已证实）
- 悬浮球手势 12 种、id 0–11：`core/gesture/src/main/java/com/slideindex/app/floatball/FloatBallGestureSettings.kt:9-22`；默认动作表 `:74-87`（三个 RETURN 默认全是 `None`，即默认不生效）。
- 识别器是**单轴锁定**模型（`app/src/main/java/com/slideindex/app/overlay/FloatBallGestureDetector.kt`）：
  - 首次判定方向即锁轴：`:465-475`（`absDy > absDx×1.4` → UP/DOWN；`absDx > absDy×1.4` → SIDE；斜向不锁定），正向符号 `:395-400`（UP=-1、DOWN=+1、SIDE=sign(dx)）；
  - 锁轴后**另一轴位移被彻底丢弃**：`:477-487`（锁定 DOWN 时返回 `0f to totalDy`）；
  - 短/长只是同轴两档距离：`:561-588`，阈值 = `百分比(50..500) × 40dp / 100`（`:39-40,611-612`）；
  - 时间窗 `PICK_GESTURE_LOCK_MS = 800ms`：拖出后超时即锁定为「取词」并取消手势（`:35,332-337`）；
  - 折返 = 沿轴正向达标后**反向回拉 ≥28dp**（`:42,522-545`，含「回到原点停顿 ≥320ms 视为撤销」、容忍末尾微回弹 0.55，`:434-439`）；折返必须配了有效动作才生效（`:523-527`）。
  - 字段清单 `:59-86` **没有拐点坐标/第二段方向记录**。
- 「向内」的语义已有定义，但**不在悬浮球识别器里**：`core/gesture/src/main/java/com/slideindex/app/gesture/SwipePathGeometry.kt:11-16` `inwardDelta(dx,dy,side)`（LEFT→+dx、RIGHT→−dx、TOP→+dy、BOTTOM→−dy）；球侧只在图标层按停靠侧判朝向（`app/src/main/java/com/slideindex/app/ui/GestureTriggerIcons.kt:202-214`）。识别器 `bind()` 入参**不含侧别**（`:129-164`），上游 `FloatBallTouchHostLayout.kt:181-197` 也不传（`activeSideProvider` 只用于命中测试 `:216-219`），`classifySwipe()` 只看 `absDx`（`:579-585`）→ 左右对称、无「向内」概念。**「下滑再向内」今天会被判成 `SWIPE_DOWN_SHORT/LONG`**（投影只看 `totalDy`，`:483`）。

### 最有价值的发现：边缘触钮的同类组合手势已成熟，几何可直接复用（已证实）
- `core/gesture/src/main/java/com/slideindex/app/gesture/SwipePathGeometry.kt:231-258` `resolveAlongToInwardTrigger()`——注释即「先上滑再向内」「**先下滑再向内**」；次段必须以内滑为主（沿边分量 ≤ 内滑分量 × 0.8，`:248-250,293`），达标返回 `SHORT/LONG_SWIPE_DOWN_IN`。
- 类型：`core/gesture/src/main/java/com/slideindex/app/gesture/GestureTriggerType.kt:29-33`（id 26/28）、`:57-58` `isCompoundSwipe`；**图标零成本**：球侧手势可直接映射到既有的 `SHORT/LONG_SWIPE_DOWN_IN` 图标与旋转表（`GestureTriggerIcons.kt:59-74,101,114,186-200`）。
- 状态机与防误触：`core/gesture/src/main/java/com/slideindex/app/gesture/SwipePathRecognizer.kt:279-293`（首段方向 + 拐点锚点）、`:452-486`（第二段判定）、`:162-167`（首段走够长距即解除组合，防止长滑被第二段抢走）、`:965,967`（`TURN_SLOP_DP=32f`、`RETURN_SLOP_DP=28f`，与球侧 28dp 折返门槛一致）；判定顺序参考 `:758-788`（折返 → 组合第二段 → 悬停 → 基础）。
- 变更记录佐证：`CHANGELOG.md:138` 已写明该组合手势的防误触目标。

### 判断
反馈**可行且性价比高**：几何、容差、图标、防误触经验都能从边缘手势体系搬。悬浮球侧必须补三处结构性能力：
1. **注入停靠侧/向内符号**（否则无法区分向内与向外）；
2. **记录首段方向与拐点锚点**（现有 `lockedSwipeAxis + peakForwardProgressPx` 是雏形，缺拐点）；
3. **在轴投影之外判定第二段正交方向**（今天的 `projectedDisplacement` 会把向内分量抹掉）。
并必须把判定插在 `FloatBallGestureDetector.kt:259`（折返）与 `:263`（基础滑动）之间，否则永远抢不到。

### 主要风险
1. **与 `SWIPE_DOWN_SHORT/LONG` 直接竞争**：必须先判定组合手势，且需要「首段过长即不升级」的守卫。
2. **与 `SWIPE_DOWN_RETURN` 互斥**：两者都在第二段动手指时命中（`:536` vs 组合判定），需明确优先级（边缘侧是折返优先）；球侧只能二选一。
3. **800ms 取词窗口是最大误触源**：两段式天然更慢，超时即被判成取词（`:35,249,258`）——很可能需要为该手势放宽窗口或允许第二段起始时续期。
4. **持久化追加式安全但默认动作为空**：新 id 12 对旧版本安全（`FloatBallGestureSettings.kt:28` `fromId` 未知返回 null；codec `:54-63` 静默丢弃）；但读取是 `decoded.ifEmpty { defaultActions() }`（`SettingsSnapshotReader.kt:750-755`）、写入是全量 `encodeAll`（`OverlaySettingsMutator.kt:929-934`）→ **已保存过任意手势的老用户，升级后新手势必为「无动作」**，需一次性迁移或产品接受。
5. **必须同步的清单**：`settingsDisplayOrder()`（`FloatBallGestureSettings.kt:31-44`，消费点 `FloatBallGestureSettingsScreen.kt:167`）、`defaultActions()`（`:74-87`）、两处穷尽 `when`（`FloatBallGestureSettingsScreen.kt:274-300` 标签、`GestureTriggerIcons.kt:186-200` 图标——会编译报错，正好防漏）、4 套 locale（`values/strings.xml:855-863`、`values-zh:1723-1731`、`values-ja:1680-1688`、`values-ar:1664-1672`）；导航用 `fromId` + fallback（`FloatBallNavEntries.kt:386` 等 6 处），id 12 天然可用。
6. **提示窗只显示动作图标、不显示手势方向**（`FloatBallOverlay.kt:1766-1792` + `FloatBallGestureHintWindow.kt:142`）→ 不加也能跑；若想让用户「看得见两段手势」，需新开方向级提示能力（现状没有，`GestureAnimationTriggerDirection.kt:24-32` 只服务边缘触钮）。
7. 顺带：`SettingsPreferenceKeys.kt:444` `FLOAT_BALL_GESTURE_TRIGGER_MODES` 全仓无引用（疑似遗留键），不影响本次改动。

### 待确认
- 是否需要短/长两档？与 `SWIPE_DOWN_RETURN` 谁优先？是否接受「第二段必须以内向为主，斜滑不升级」的与边缘一致容差？

---

## 5. 锁屏收到消息后，通知提醒悬浮球能否在解锁后仍停留（或停留几秒）

### 现状（已证实）
- 四种形态各自独立窗口：悬浮球 `FloatIcon`、侧边气泡 `SideBubble`、贴边列表 `CNotice`、弹幕 `Danmaku`（`core/notification/src/main/java/com/slideindex/app/message/MessageSettings.kt:9-12`；分发 `app/src/main/java/com/slideindex/app/message/AppMessageOverlayPort.kt:185-231`）。用户在中文界面看到的「悬浮球」= `FloatIcon`。
- **展示路径没有任何锁屏判断**：`feature/message/src/main/java/com/slideindex/app/message/MessageNotificationFilter.kt:16-53,96-134` 与四个窗口的 `show()` 都不看 keyguard；锁屏只被用于记录待打开消息（`MessageReminderOrchestrator.kt:107-109`，判定 `app/src/main/java/com/slideindex/app/util/LockScreenState.kt:12-21`）。锁屏抑制开关只作用于触钮/悬浮球本体（`app/src/main/java/com/slideindex/app/util/OverlaySuppression.kt:24,54-63`）。
- **真正的杀手是「熄屏/检测到锁定」的硬边界清除**：
  - `app/src/main/java/com/slideindex/app/service/SlideIndexAccessibilityWatchdog.kt:28-36`（`ACTION_SCREEN_OFF` → 置锁屏标志 + `GlobalOverlayDismissHelper.dismissAllPanels()`）、`:50-58`（检测到锁屏同样清屏）；
  - `app/src/main/java/com/slideindex/app/overlay/GlobalOverlayDismissHelper.kt:24-64`：`:39` 侧边气泡全删、`:51` **悬浮球全删**、`:58` 弹幕 detach；而贴边列表只 `:52 closePanel()`（仅关详情面板）→ **只有 CNotice 幸存**；
  - 双重保底：`FloatIconOverlayWindow.kt:71,246`、`SideBubbleOverlayWindow.kt:79,265` 各自注册 `ScreenOffDismissReceiver`（`app/src/main/java/com/slideindex/app/overlay/ScreenOffDismissReceiver.kt:22-45`），`ACTION_SCREEN_OFF` 即 `dismiss()`；CNotice 未注册该 receiver。
  - `SlideIndexAccessibilityWatchdog.kt:32-34` 注释说明这是**有意的硬边界**（避免丢 UP 后整屏被浮层吃掉）。
- 「悬浮球」的时长语义：默认 **5 秒**、可设 0–30s（0 = 不自动关闭），计时从 `show()` 当刻起算（`MessageSettings.kt:30-31`、`FloatIconOverlayWindow.kt:99-117,249-256`、UI 滑块 `app/src/main/java/com/slideindex/app/ui/messagestyle/MessageStyleCardPreview.kt:41-68`）；侧边气泡同为 5s 可调（`MessageSettings.kt:33`、`SideBubbleOverlayWindow.kt:288-295`）；弹幕 3.5/5.5/8s 划过即走（`core/notification/src/main/java/com/slideindex/app/message/MessageOverlayPlacement.kt:68-72`）；新消息横幅固定 3s（`CNoticeOverlayWindow.kt:255,1280-1286`）。
- 贴边列表 `CNotice` 是事实上的「常驻」形态：`scheduleAutoDismiss` 是**空实现**（`CNoticeOverlayWindow.kt:1311-1319` 只 removeCallbacks，从不 postDelayed）、`CNoticeListUiState.kt:233-239` 固化「不因通知移除而删球」；其「自动关闭时长」设置项是**死键**（`MessageSettingsMutator.kt:107-110` 写入通路存在，但 UI 从未提供滑块：`app/src/main/java/com/slideindex/app/ui/MessageStyleDetailSettingsScreen.kt:122-135`）。
- 解锁通道已存在但**当前只服务「解锁后进入最后一条消息」**：`app/src/main/java/com/slideindex/app/service/MediaNotificationListener.kt:42-48`（`ACTION_USER_PRESENT` → `onUserPresent`）与 `SlideIndexAccessibilityWatchdog.kt:41-45`（只把锁屏标志置 false + 刷新抑制，**不清也不恢复提醒**）；`MessageReminderOrchestrator.kt:153-182` 延迟 900ms，按 `openLastMessageAlwaysPackages` 决定直开或弹询问卡（策略 `feature/message/src/main/java/com/slideindex/app/message/LastMessageUnlockPolicy.kt:4-7`，开关 `MessageSettings.kt:69-74`，询问卡自动消失 1–30s：`AppMessageOverlayPort.kt:380-392`）。它**不要求**锁屏期间提醒存在（`pendingUnlockMessage` 在 `onNotificationPosted` 内独立记录）。

### 判断（本批反馈里最值得先做的一条）
用户诉求在当前实现下**不成立**，是三个行为叠加：
1. 锁屏期间提醒照常创建、**倒计时照走**（默认 5 秒，用户看不到就过期）；
2. 一旦熄屏或检测到锁定，**悬浮球/侧边气泡被整窗删除**；
3. 解锁后没有任何「补显」路径（`onUserPresent` 只负责打开 App，`ACTION_USER_PRESENT` 只刷新抑制状态）。

另有一条**平台事实**（非本仓证据，建议真机确认）：普通 `TYPE_APPLICATION_OVERLAY` 悬浮窗位于 Keyguard 之下，**锁屏界面上通常看不到这些提醒**；另外 `FloatBallOverlay.kt:686` 的注释也提示熄屏/锁屏后系统可能摘掉无障碍浮窗而本地引用仍在。→ 能可靠达成的是「**解锁后补显提醒并停留 N 秒**」，而不是「锁屏时就能看到」。

### 可落地方案
1. **解锁后补显（推荐）**：把锁屏期间被清除的提醒计划缓存为小队列（现有 `pendingUnlockMessage` 只存最后一条 `NotificationData`，需扩展），在 `ACTION_USER_PRESENT` 时经 `overlayPort` **重放**并**重新起算** `floatIconAutoDismissSeconds`。
   - 新增设置项（如「解锁后继续显示提醒 / 停留 N 秒」），默认关闭；
   - 只对 `FloatIcon` 生效还是同时 `SideBubble`/`CNotice`，需产品决策；
   - 与「解锁后进入最后一条消息」共存时注意先后（可复用 900ms 延迟）。
   - **必须走显式重放入口**：再走一次 `onNotificationPosted` 会被 15 秒去重（`MessageNotificationFilter.kt:9,55-64`）与 `isAlreadyDisplayed`（`MessageReminderOrchestrator.kt:276-284`）吞掉。
2. **修正计时基准**（最小改动、收益明确）：熄屏/锁屏时**冻结**自动关闭计时，解锁后恢复——现成的 `pauseAutoDismiss`/`resumeAutoDismiss`（`AppMessageOverlayPort.kt:126-140`、`FloatIconOverlayWindow.kt:258-282`）直接可用，无需新计时器。
3. **不因熄屏清空**：把 `GlobalOverlayDismissHelper` 中悬浮球/侧边气泡的「全删」改为「隐藏/保留」，解锁后恢复。注意该 helper 同时收掉搜索面板/小窗/音量面板等（`:36-63`），**不能整段删除调用**；`FloatIconOverlayWindow.kt:71` 的 `ScreenOffDismissReceiver` 也要一并改为暂停语义。
4. 用户提到「悬浮几秒钟」本身**已有等价能力**（悬浮球 5s、可调 0 常驻；贴边列表更是天然常驻），真正缺的是「解锁后还能看到」。→ 优先做 1+2，必要时补 3。

### 风险
- 熄屏清屏是有意设计（`SlideIndexAccessibilityWatchdog.kt:32-34`），绕过会带来残留窗口与手势丢失风险，需保留「解锁后兜底重建」。
- 通知被清除后的 token 同步：`MessageReminderOrchestrator.kt:55-57`（清 pending）、`:77`（按 key 清 entry）需要在补显前复核，避免补显已被删除的通知。
- 窗口生命周期：锁屏期间消息窗可能被系统隐藏或 view 失效（`app/src/main/java/com/slideindex/app/overlay/FloatBallOverlay.kt:686` 注释为同类现象），解锁后应做 `isAttachedToWindow`/重新 `addView` 兜底，而不是只改计时。
- 与解锁询问卡同屏互斥（`AppMessageOverlayPort.kt:253-379`）。
- 参考实现：预览路径已经在用「暂停自动消失」（`app/src/main/java/com/slideindex/app/message/MessageReminderPreviewController.kt:117`）。

### 待确认
- 「解锁后仍悬浮」是**常驻到用户处理**还是**补显 N 秒**（用户原话两者都提了）？
- 只对通知提醒悬浮球生效，还是四种形态统一？
- 「锁屏时显示 + 解锁后继续」还是「锁屏不显示、解锁补显」？

---

## 6. 交叉观察与建议排序

1. **第 5 条最值得先做**：痛感明确，代码路径已存在（解锁广播 + 计时暂停 + 提醒缓存），主要改动集中在 `MessageReminderOrchestrator` / `FloatIconOverlayWindow` / `GlobalOverlayDismissHelper`。
2. **第 3 与第 4 条可以同批做**：都在悬浮球布局/手势层，第 4 条可直接复用边缘手势的 `SwipePathGeometry.resolveAlongToInwardTrigger`；但第 3 条必须先定义「两侧都是线时球怎么呼出」，且要接受窗口数 3→4 的维护成本。
3. **第 2 条先做小步**：先「高度上限可调」与「消费 anchor / 按球侧锚定」（入参已存在，调用点零改动），不要一上来做整面板拖拽（纵向手势已被折叠占用、横向与 Pager 冲突）。
4. **第 1 条先澄清需求**：本仓 WebView 拿不到别的 App/浏览器的页面上下文，「注入网页 JS」做不到；能做的只有「脚本宿主 + 白名单桥接」。若确认要做，建议**先做 MVP（隐藏 WebView + 桥接）**，同时把散落在 9 个入口的 Shell 配置特判抽成统一的「动作参数配置」机制，再决定是否铺 12 条路由。
5. **附带可修的既有问题**：键盘 NARROW 只向右收窄导致贴右边缘的线离屏（`KeyboardTriggerBoundsAdjuster.kt:68-74`）；贴边列表 CNotice 的「自动关闭时长」死键（`MessageStyleDetailSettingsScreen.kt:122-135`）；`activeSideAfterLineDragSwap` 生产未调用（`FloatBallLayout.kt:147-149`）；`floatBallPickTextModeDefault` 双份默认值漂移。

## 附：动手前需确认的清单
- [ ] JS 动作：执行上下文（本应用脚本宿主 / 注入前台网页 / 外部解释器）与可用 API 边界。
- [ ] 取词面板：「挡屏幕中间」具体是取词面板、展开的搜索/图片区，还是居中的以图搜图卡片；「类似 FV」指位置、尺寸还是呼出方式；可接受的最大高度上限。
- [ ] 双线模式：球的呼出方式；面板从哪侧弹；两侧参数是否共享；`floatBallActiveSide`/`floatBallVisibleFraction` 的去留。
- [ ] 混合手势：是否分短/长档；与 `SWIPE_DOWN_RETURN` 的优先级；800ms 取词窗口是否例外处理。
- [ ] 锁屏提醒：补显时长与是否常驻；生效的提醒形态范围；是否接受改动熄屏清屏硬边界。

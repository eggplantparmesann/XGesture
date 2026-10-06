# 冰箱「暂停」功能需求分析

> 本文只做分析，不含任何代码改动。结论标注 `相对路径:行号` 证据，并区分「已核实（源码/文档证据）」「未核实（待真机）」「推测」。
> 仓库版本：1.35.0（versionCode 70）；主分支 main；分析时工作区干净（`git status --short` 无输出）。

## 0. 结论速览

| 项 | 结论 |
| :- | :- |
| 需求语义 | 按最合理解读：「暂停」= Android **应用挂起（Suspend）**，与现有「冻结」（停用/Disable）并列的第三种状态。桌面图标变灰但仍保留，**正是挂起态的系统行为，不需要我们改桌面**（AOSP Launcher3 已核实：全灰 + 亮度 0.5、图标保留，见 §3.1） |
| 需求是否有歧义 | **有**，见 §1。四种解读里只有①需要挂起 API；②是纯 UI 改动，③④不满足「变灰」 |
| 现状基础 | 好：Shizuku/Root 双通道、`IPackageManager` 反射绑定、通用 shell 通道、`QUERY_ALL_PACKAGES` 都已具备（§2.3） |
| 主要成本 | 状态模型从 `Boolean frozen` 扩成三态，波及 **11 个文件 + 13 处调用点**，另有 3 语言（zh/ja/ar）+ 英文共 4 份文案 |
| 主要风险 | ① **挂起按「发起方包名 + 用户」分别记账**，特权模式切换或路径混用会留下清不掉的挂起 → 桌面图标永久变灰；② **Android 16/17 的 OEM ROM 有活跃 bug**（One UI 8.5 / HyperOS 3）：被挂起过的应用可能再也无法取消暂停，只能卸载重装（Hail #412/#398）；③ 挂起**不阻止后台运行**（框架里没有 force-stop），想省电必须自己补；④ 灰化表现已核实于 AOSP Launcher3，第三方/OEM 桌面未核实，需真机基线 |
| 建议优先级 | 中高。若只做 MVP（长按菜单 + 网格灰化 + 批量暂停/取消暂停 + 点击自动取消暂停后启动），是**低风险增量**；若要重做 tab / 统计 / 搜索面板，成本翻倍 |
| 待确认 | 10 条，见 §7；其中第 1、2 条决定整个实现方向，第 4～6 条是动工前必须定的实现决策 |

---

## 1. 需求理解与歧义

原话：**「冰箱加入暂停功能（应用暂停后 图标变灰色不会被移除）」**。

先说清一个容易误判的点：**「图标变灰」这件事，冰箱内部其实已经做到了**。冰箱网格里已冻结的应用图标就是灰度 + 灰字（`app/src/main/java/com/slideindex/app/freezer/FreezerGridUi.kt:493-497` 的 `ColorMatrix().setToSaturation(0f)`、`:527-531` 的 `alpha = 0.38f`），搜索面板同理（`app/src/main/java/com/slideindex/app/overlay/searchpanel/SearchPanelResultCards.kt:406,415-419`）。所以新需求真正新增的信息量在**「不会被移除」**——即：不只是应用内部灰给你看，而是**桌面上那张图标还在、只是变成灰色、点不动**。

四种解读：

- **解读①（本文按此展开，推荐）新增「暂停」= 系统挂起**。用 Android 的 app suspension（`setPackagesSuspended` / `pm suspend`）让应用处于 suspended 状态：包仍然安装且仍是 enabled，所以**桌面图标不被移除**，但系统让桌面把它画成灰色（disabled 外观）、点击时弹出系统对话框、无法进入。这是「冻结」的轻量替代：冻结 = 停用 → 图标从桌面消失、彻底不能运行；暂停 = 挂起 → 图标留在桌面但变灰、点不动。
- **解读② 纯应用内 UI**：只是在冰箱列表里给某类应用画灰、且不把它从列表移除。**与现状不符**：冰箱列表里的成员本来就不会因为冻结而被移除（`freezerAppPackages` 只有用户手动「从列表移除」才减少，见 `FreezerListOperations.kt:12-27`），且冻结成员已经是灰的。若真是这个意思，几乎不用改代码，说明解读成立性低。
- **解读③ 暂停 = 强制停止（force-stop）**：只能杀掉进程，图标不会变灰（`TaskManagerUtil.kt:530` 已有能力）。不满足需求描述。
- **解读④ 暂停冰箱自身**（暂停自动重冻/后台轮询）：与「应用暂停后」的措辞不符。

> 解读①还有一个佐证：本项目的冰箱明确对齐「雹 / Hail」（`README_zh.md:425`、`THIRD_PARTY_NOTICES.md:156`），而 Hail 的能力描述是「Disable / Hide / Suspend / Uninstall Android apps without root」——**Suspend 是与 Disable 并列的独立能力**，本仓库目前只移植了 Disable 一侧（全仓 grep `setPackagesSuspended|FLAG_SUSPENDED|pm suspend` **0 命中**）。Hail 里 Suspend 的完整实现（含跨版本反射级联与桌面灰化表现）已核实并整理在 §3.1，可直接当移植蓝本。

---

## 2. 现状（已证实）

### 2.1 数据模型：冰箱只有「集合」，没有「每应用模式」

- 成员列表就是一组包名：`feature/settings/src/main/java/com/slideindex/app/settings/AppSettingsSlices.kt:94-96`（`freezerAppPackages` / `freezerBootstrapExcludedPackages` / `freezerShowInLauncher`）。
- 持久化键：`feature/settings/src/main/java/com/slideindex/app/settings/SettingsPreferenceKeys.kt:334-336`。
- 读写链路：`SettingsSnapshotReader.kt:236-240` → `AppSettings.kt:190-192`（薄代理）→ 写入 `EdgeSettingsMutator.kt:477-503`（`addFreezerApp`/`removeFreezerApp`/`setFreezerShowInLauncher`）→ 对外 `SettingsRepository.kt:264-265`。
- **冻结状态不是本地记录的**，而是每次从系统反推：`FreezerPrivilegedOps.kt:21-23` 用 `getApplicationInfo(pkg, MATCH_UNINSTALLED_PACKAGES or MATCH_DISABLED_COMPONENTS).enabled.not()` 判定。→ 好消息：加「暂停」也应当用系统标志做同一件事，天然不会状态漂移。

### 2.2 执行层：双通道已经写好，只差新动词

- 入口：`FreezerOperations.kt:23-24`（`isFrozen`）、`:26-54`（`setFrozen`，含权限校验与失败文案）、`:56-71`（`launchAndUnfreeze`）、`:106-149`（`refreezeAll`/`freezeAll`/`unfreezeAll`）。
- 真正的特权实现：`FreezerPrivilegedOps.kt:25-83`。
  - 冻结时先杀进程（`:34-40`：`TaskManagerUtil.forceStopPackage` + `am force-stop --user`）。
  - 路径 A：Shizuku 直连 `IPackageManager` 反射调 `setApplicationEnabledSetting`（`:122-160`，绑 binder 靠 `HiddenFrameworkAccess.bindPackageManager(ShizukuBinderWrapper(SystemServiceHelper.getSystemService("package")))`，见 `app/src/main/java/com/slideindex/app/search/ral/HiddenFrameworkAccess.kt:73-78`）。
  - 路径 B：shell 兜底 `pm disable/enable [--user U]`（`:162-191`），走 `TaskManagerUtil.runShellCommandLine`（`TaskManagerUtil.kt:700-729`）。
  - 结果校验：轮询 `waitForState`（`:193-199`，8×60ms）——**加暂停后需要同构的 `waitForSuspended`**。
- 权限门面：`PrivilegeGateway.kt:9-27`（Shizuku / Root 二选一）；`AndroidManifest.xml:49` 已声明 `QUERY_ALL_PACKAGES`，所以我们能直接查任意包的状态。

### 2.3 调用点清单（状态由二态变三态后的完整波及面）

| 文件 | 现状用法 | 需要改动 |
| :- | :- | :- |
| `app/.../freezer/FreezerGridUi.kt` | `:162,194` 取 `frozen`；`:221` 切换冻结；`:493-497,527-531` 灰度 | 灰化区分暂停/冻结、长按菜单加「暂停/取消暂停」、点击行为 |
| `app/.../freezer/FreezerPanelContent.kt` | `:138-155` 溢出菜单（导入、全部解冻）、`:231-243` FAB 冻结全部 | 批量暂停 / 取消暂停全部 |
| `app/.../freezer/FreezerListOperations.kt` | `:12-27` 冻结时禁止移出列表；`:29-43` 解冻并移除；`:45-55` 导入 | 守卫扩展到暂停态；「解冻并移除」→「恢复并移除」 |
| `app/.../freezer/FreezerBootstrap.kt` | `:13-24` 只扫描 `enabled == false` 的启动器应用 | 需要同时扫 `FLAG_SUSPENDED`，否则导入漏掉暂停应用 |
| `app/.../freezer/FreezerOperations.kt` | 见 §2.2 | 全部动词 |
| `app/.../freezer/FreezerAppLaunchTrampolineActivity.kt` | `:32-37` 调 `launchAndUnfreeze` | 只要 `launchAndUnfreeze` 扩展即可自动覆盖 |
| `app/.../ui/FreezerAppsPickerScreen.kt` | `:109-128` 三个 tab 计数（全部/已冻结/使用中）、`:271-311` 行状态后缀与 `enabled` 守卫 | 计数与文案要容纳第三种状态 |
| `app/.../overlay/searchpanel/SearchPanelAppQuickActions.kt` | `:117` 取冻结态 | 暂停态显示 |
| `app/.../overlay/searchpanel/SearchPanelResultCards.kt` | `:388,406,415-419,458` 图标/文字灰化 | 同上 |
| `app/.../overlay/searchpanel/SearchPanelScreen.kt` | `:860-895` 启动前解冻 / 冻结切换 | 启动前取消暂停 |
| `app/.../gesture/ActionExecutor.kt` | `:420-425`「重新冻结」手势 → `refreezeAll` | 语义待定（§7 第 7 条） |

### 2.4 两个容易踩的既有事实

1. **`FreezerTab` 是死管道**：`FreezerTab.kt:3-7` 定义了 `ALL/FROZEN/ACTIVE`，`ActionExecutor.kt:415-419` 与 `MainActivity.kt:216` 会 `setPendingInitialTab(FROZEN)`，但全仓 grep 只有 set、**没有任何 consume**（`FreezerLaunchState.kt:8-20` 的 `consumePendingInitialTab` 无调用者）。也就是说：**「手势打开默认进入已冻结列表」这个 CHANGELOG 里写过的行为，现在实际不生效**，panel 也没有 tab UI。想靠「加个 tab」实现暂停态展示，等于从零做 tab。
2. **`isFrozen` 在 Compose 组合期同步调用**（网格 item、搜索结果卡片）。挂起判定若再单独查一次 `getApplicationInfo`，等于把主线程 binder 查询翻倍；建议一次性返回「启用状态 + 挂起标志」的单查询函数。

---

## 3. 技术方案

### 3.1 平台能力与参考实现（已核实）

**雹（Hail）已经实现了同一功能**，可直接当移植蓝本（本项目冰箱本就对齐 Hail）：

- Shizuku 路径：`utils/HShizuku.kt:106-190` 的 `setAppSuspended(packageName, suspended)`，按 SDK 做**参数个数级联**：
  - A12 / A13 / A14.0 / A14-QPR1（7 参）：`setPackagesSuspendedAsUser(String[], boolean, PersistableBundle, PersistableBundle, SuspendDialogInfo, String callingPackage, int userId)`
  - A14-QPR2 / A15 / A16（9 参）：末尾追加 `int flags, String suspendingPackage, int suspendingUserId, int targetUserId`（普通挂起 `flags = 0`）
  - 本项目 minSdk 31、targetSdk 37，正好横跨这两代签名；2024-03 的 7→9 参数变更就是 Hail 当时翻车的原因（Hail #202）→ **必须按 SDK 级联 + `NoSuchMethodException` 兜底**。
- Root 路径：`HShell.kt:33-34` → `pm suspend/unsuspend --user <uid> <pkg>`。
- 状态判定：`HPackages.kt:57-64` → `flags and ApplicationInfo.FLAG_SUSPENDED == FLAG_SUSPENDED`（公开常量）。挂起包的 `enabled` 仍为 true，**所以不能复用现有 `enabled` 判定**；`getApplicationInfo` 一次调用可同时拿 `enabled` 与 `flags`。Hail 特意注释：公开的 `PackageManager.isPackageSuspended()` 对已卸载包会抛 `NameNotFoundException`，不要用。
- Hail 在挂起前**自己 force-stop**，并在 Android 9+ 额外把 appop `android:run_any_in_background` 设为 `MODE_IGNORED`（`HShizuku.kt:108-109,192-208`）——原因见下面的「后台」一行。
- 系统对话框：`SuspendDialogInfo.Builder().setNeutralButtonAction(1 /*BUTTON_ACTION_UNSUSPEND*/).build()`（`HShizuku.kt:184-190`），**故意不设标题与正文**，让系统用默认文案（`app_suspended_default_message`，格式化参数是「被挂起应用名 + 发起方应用名」）。这个按钮让用户能自己取消暂停（仅限该发起方的挂起），然后自动继续原来那次启动。

**AOSP 侧已核实的关键语义**：

| 事实 | 结论 | 出处 |
| :- | :- | :- |
| 权限 | `SUSPEND_APPS` 是 `signature\|role`；`com.android.shell` 持有它 → **Shizuku（uid 2000）可用**；uid 0 被提前放行 | AOSP `core/res/AndroidManifest.xml:2328`、`packages/Shell/AndroidManifest.xml:324`、`PackageManagerService`（13/15 版本） |
| 发起方包名校验 | 非 root 时 PMS 要求「声称的 suspendingPackage 的 uid == 调用 uid」（shell 有专门分支）→ **Shizuku 模式必须传 `com.android.shell`**，Root 模式可传本应用包名 | 同上 |
| 是否强停 | **框架不做 force-stop**，只写状态 + 发广播 | `SuspendPackageHelper.java:196-225` |
| 能否启动 | 启动被拦截并替换成系统对话框；最近任务被移除；桌面小组件被 mask | `ActivityStartInterceptor.java:327-357`、`RecentTasks.java:655-665`、`AppWidgetServiceImpl.java:487-495` |
| 后台 | 广播 / 服务 / 定时任务**没有被阻止**，与 Hail README 的明确警告一致（“Suspend only prevents the user from interacting with the app, it does NOT prevent the app from running in the background”） | `BroadcastQueueModernImpl` / `ActiveServices` 零命中 |
| 持久化 | 是，写进 `packages.xml`，重启仍在；**按 user + 发起方包名分别记账** | `Settings.java:2210,2245-2256` |
| 拒绝条件 | 不能挂起：自己、有活跃设备管理员的包、**当前桌面**、默认拨号/安装器/校验器/权限控制器、受保护包、默认豁免的系统包；被拒时**不报错**，只体现在失败数组 / `new suspended state: false` → **必须回读 `FLAG_SUSPENDED` 校验** | `SuspendPackageHelper.java:479-570` |
| 对话框与用户自救 | `dialogInfo.neutralButtonAction = UNSUSPEND` → 用户可自行取消暂停；`dialogInfo = null`（如 `pm suspend` 不带 `--dialogMessage`）→ 中性按钮变成「更多详情」（需发起方提供受 `SEND_SHOW_SUSPENDED_APP_DETAILS` 保护的 Activity，通常不存在）→ 用户**无法**自救 | `SuspendedAppActivity.java:132-340` |
| 桌面表现 | **AOSP Launcher3 已核实**：`FLAG_SUSPENDED` → `ItemInfoWithIcon.FLAG_DISABLED_SUSPENDED` → `FastBitmapDrawable` 全灰（`setSaturation(0)`）+ 亮度 0.5，**图标保留**；点击照常走启动，由框架弹对话框解释 | `PackageManagerHelper.java:137`、`LoaderTask.java:633-651`、`FastBitmapDrawable.java:310-330`、`ItemClickHandler.java:210-241` |
| OEM 桌面 | 未核实，只能真机测（OEM 那边已核实的只有 suspend 的别的故障，见 §4） | — |

**执行路径（建议双路径，与现有冻结同构）**：

- **路径 A（对齐 Hail，反射）**：绑定 `IPackageManager` 后按 SDK 级联调 `setPackagesSuspendedAsUser`；Shizuku 模式 `suspendingPackage = "com.android.shell"`，Root 模式传本应用包名。用与 `waitForState`（`FreezerPrivilegedOps.kt:193-199`）同构的 `waitForSuspended` 轮询回读 `FLAG_SUSPENDED` 确认成功。
- **路径 B（shell 兜底）**：`pm suspend|unsuspend [--user U] [--dialogMessage M] <pkg>`，接在 `setViaShell`（`FreezerPrivilegedOps.kt:162-191`）旁边，复用 `TaskManagerUtil.runShellCommandLine`。注意 `pm` 的 callingPackage 是 `com.android.shell`（root 下是字面量 `"root"`），**与路径 A 的 Root 键不同**；成功时输出 `Package <name> new suspended state: <bool>` 可解析。

**三个必须现在定下来的实现决策**：

1. **发起方键（suspendingPackage）怎么选**——挂起按「发起方包名 + user」记账，键不一致就清不掉（Hail README：必须用同一个工作模式解冻）。两种取向：
   - **统一用 `com.android.shell`**：Shizuku 与 Root 共用一个键，互操作最好；代价是**卸载本应用时系统不会清理这些挂起**（`DeletePackageHelper.unsuspendForSuspendingPackage` 只清理「发起方 == 被卸载包」的挂起），用户会留下永久灰图标。
   - **按 Hail 分键**（Shizuku→shell、Root→本应用包名）：Root 模式的挂起在卸载时自动清理；代价是切换特权模式会残留另一种键 → 建议「取消暂停」在 Root 模式下**两个键各调一次**，并在切换特权模式 / 卸载前提示先「全部取消暂停」。
   - 我倾向后者 + 双键清理：它把「卸载后残留灰图标」这个最恶心的场景处理掉了。
2. **要不要给用户自救按钮**——给了（反射路径 + `BUTTON_ACTION_UNSUSPEND`）用户能自己取消暂停，但要接受应用内显示与系统态度可能不同步（用系统标志做显示真值 + `ON_RESUME` 刷新即可，`FreezerPanelContent.kt:97-105` 已有这个机制）；不给（`pm suspend` 不带 message）用户被卡住时只能回来找本应用，风险更大。注意：**想自定义对话框文案又想保留自救按钮，只能走反射路径**（`pm suspend --dialogMessage` 只设正文，中性按钮仍是「更多详情」）。
3. **要不要补 force-stop / appop**——挂起**不阻止后台运行**。若「暂停」的卖点是省电，就要照 Hail 补 `force-stop`（现有冻结路径 `FreezerPrivilegedOps.kt:34-40` 已有）+ `appops set <pkg> RUN_ANY_IN_BACKGROUND ignore`；若卖点只是「图标留着但点不动」，就不补，但必须在 UI 里写清语义，别让用户以为等价于冻结。

**与冻结的关系（设计决策，结论是必须互斥）**：disable 会让桌面图标直接消失，挂起态在 disable 面前是「看不见」的（挂起本身**不要求** enabled，顺序得我们自己保证）。所以：冻结前先取消暂停，暂停前确保 `enabled`；「取消暂停」不要顺手把 disable 的包变成 enabled（两件事不要耦合）。

### 3.2 状态模型：两种落法

- **方案 A（纯派生，最小改动）**：不加任何本地字段，`ACTIVE / FROZEN / PAUSED` 全部从系统读。
  - 改动小：不碰 settings 的 5 个文件与迁移；风险低。
  - 缺点：没有「这个应用应该被暂停」的意图记忆 →「重新冻结」手势与「冻结全部」只能按状态处理（已暂停的保持暂停、启用中的默认冻结）。
- **方案 B（推荐，轻量版）**：新增 `freezerPausedPackages: Set<String>`（或每应用 mode map），批量/重冻按目标模式回放。
  - 需要动：`SettingsPreferenceKeys`、`SettingsSnapshotReader`、`AppSettingsSlices`、`AppSettings`、`EdgeSettingsMutator`、`SettingsRepository`，并补 `SettingsMutatorsTest`（参照 `feature/settings/src/test/java/com/slideindex/app/settings/SettingsMutatorsTest.kt:249-265` 的既有写法）。
  - 仍必须以系统标志为「显示真值」（因为用户可能在系统对话框里自行取消暂停、或卸载重装），本地集合只表示「意图」。
- 无论 A/B，建议把 `Boolean frozen` 收敛成单一入口：`FreezerOperations.stateOf(context, pkg): FreezerAppState`（一次 `getApplicationInfo` 出 enable + suspended），避免三态布尔在 11 个文件里继续扩散。

### 3.3 交互设计（MVP 建议范围）

1. 网格长按菜单（`FreezerGridUi.kt:281-287` 的 `FreezerAppActionCallbacks`）新增一项：**暂停 / 取消暂停**（与现有「冻结 / 解冻」并列）。
2. 网格图标：暂停态灰化 + **角标/小图标区分**（雪花=已冻结，暂停=已暂停）。纯灰会让两种状态不可分辨。
3. 点击暂停中的应用：沿用现有「点击即解冻后启动」的体验 → `launchAndUnfreeze`（`:56-71`）扩展为「先恢复（解冻或取消暂停）再启动」。这一处改完，冰箱网格、搜索面板、Pin 快捷方式 trampoline 全部自动生效。
4. 批量：FAB 保持「冻结全部」；溢出菜单新增「全部暂停」「全部取消暂停」；「全部解冻」是否连带取消暂停需明确（建议分开，避免误操作范围过大）。「全部取消暂停」要按 §3.1 决策 1 处理双键残留。
5. 守卫：`freezer_remove_while_frozen`（`FreezerListOperations.kt:18-22`）扩展到暂停态；picker 行的 `enabled = !inList || !frozen`（`FreezerAppsPickerScreen.kt:292`）同步。
6. 导入：`FreezerBootstrap.scanDisabledLauncherPackages`（`:13-24`）改为「已停用 **或** 已挂起」的启动器应用，并更新 `app/src/test/java/com/slideindex/app/freezer/FreezerBootstrapTest.kt`。
7. 安全护栏：AOSP 已自带一层保护（当前桌面、默认拨号、安装器/校验器、权限控制器、有活跃设备管理员的包、系统豁免包会被**静默拒绝**），但**不包含**输入法、SystemUI 这类「暂停了就回不来」的包，也不排除本应用自身 → picker 有「显示系统应用」开关（`FreezerAppsPickerScreen.kt:195-202`），用户能选到它们，所以仍要加黑名单；并且失败判定不能靠错误码，要回读 `FLAG_SUSPENDED`（拒绝时对外表现为「调用成功但状态没变」）。

### 3.4 文案（4 份语言）

新增 key（英文 `values/` + `values-zh/` + `values-ja/` + `values-ar/`，每组同时要补 `plurals`）：

`freezer_action_pause`、`freezer_action_unpause`、`freezer_status_paused`、`freezer_pause_failed`、`freezer_unpause_failed`、`freezer_pause_all`、`freezer_unpause_all`、`freezer_remove_while_paused`、`freezer_pause_semantics_hint`（说明「暂停不等于冻结」）、`freezer_pause_dialog_message`（系统对话框文案，若自定义）、`plurals: freezer_pause_all_done` / `freezer_unpause_all_done`。

日文有 `scripts/ja_batches/*.json` 的流水线痕迹，阿拉伯文与日文当前是「人工/机器填的」状态，新增文案需要按既有流程补。

### 3.5 改动清单与规模（方案 B + MVP）

| 文件 | 改动 | 估行数 |
| :- | :- | :- |
| `app/.../freezer/FreezerPrivilegedOps.kt` | `isAppSuspended` / `setAppSuspended`（7/9 参数级联反射 + shell 兜底 + 轮询回读 + 双键清理） | +110 |
| `app/.../freezer/FreezerOperations.kt` | `FreezerAppState`、`stateOf`、`setPaused`、`launchAndRestore`、批量 | +60 |
| `app/.../freezer/FreezerGridUi.kt` | 菜单项、灰化 + 角标、点击 | +35 |
| `app/.../freezer/FreezerPanelContent.kt` | 溢出菜单批量子项 | +25 |
| `app/.../freezer/FreezerListOperations.kt` | 守卫与「恢复并移除」 | +15 |
| `app/.../freezer/FreezerBootstrap.kt` | 扫描含挂起 | +10 |
| `app/.../ui/FreezerAppsPickerScreen.kt` | 计数/后缀/守卫 | +20 |
| `app/.../overlay/searchpanel/*.kt`（3 个） | 状态显示与启动前恢复 | +20 |
| `app/.../gesture/ActionExecutor.kt` | 重冻语义（若采纳） | +5 |
| settings 5 文件（方案 B） | 新键、读写、mutator | +30 |
| `values*/strings.xml` ×4 + `plurals` ×4 | 文案 | 小型 |
| 测试（`FreezerBootstrapTest`、新状态映射测试、`SettingsMutatorsTest`） | | +60 |
| `CHANGELOG.md`（项目惯例） | | 小型 |

**结论：MVP 方案 B 约 1.5～2 人日；方案 A 且不动 picker/搜索面板约 0.5～0.8 人日**；真机适配（OEM 桌面表现、Android 16 的「取消不掉」场景、双键残留）另计，这部分不确定性主要落在设备验证而不是编码。无新增依赖、无新增 Manifest 权限（`SUSPEND_APPS` 是 shell 侧权限，我们不需要声明）。

---

## 4. 风险与未知

1. **挂起键残留 → 永久灰图标（设计层面最高风险，已核实机制）**：挂起按「发起方包名 + user」记账，用 A 键挂起就得用 A 键取消。切换特权模式（Shizuku ↔ Root）、混用反射与 `pm` 路径、或在 Root 模式用本应用包名挂起后卸载本应用，都会留下清不掉的挂起。缓解：固定键策略 + Root 模式双键清理 + 卸载/切模式前提示「全部取消暂停」（§3.1 决策 1）。
2. **Android 16/17 的 OEM ROM 有活跃 bug（已核实，Hail #412 / #398）**：One UI 8.5、HyperOS 3 上「曾被挂起过」的应用可能永久无法取消暂停（`pm suspend` 输出 `new suspended state: false`，`dumpsys` 显示 `suspended=false hidden=false enabled=1` 但改不回可用状态），目前只能卸载重装清除。targetSdk 37 意味着**我们一定会跑到这个平台代际上**。缓解：取消暂停后必须回读标志校验并给出明确失败提示；必要时提供「改用冻结」的兜底与卸载重装指引；在 CHANGELOG/UI 里预警。
3. **挂起 ≠ 停用（已核实）**：框架不 force-stop，广播/服务/定时任务不受影响（仅启动被拦、最近任务被清、小组件被 mask）。若不做补充，用户会以为「暂停 = 省电」→ 要么补 `force-stop` + appop（Hail 的做法），要么在 UI 文案里写明语义差异。
4. **反射签名跨代漂移（已核实）**：12/13/14.0/14-QPR1 是 7 参，14-QPR2/15/16 是 9 参，Hail 曾在 2024-03 因此全线失败（#202）。必须按 SDK 级联 + `NoSuchMethodException` 降级到 shell，且失败要能透出可诊断信息。
5. **灰化表现在 OEM 桌面未核实**：AOSP Launcher3 已核实为「灰化 + 保留 + 点击弹对话框」，但 MIUI/HyperOS/Flyme/One UI 及第三方桌面（Nova 等）可能隐藏或不灰化。若目标机型表现为「也消失了」，功能卖点即不成立 → **真机基线先行**（§5 第 1 步，约 10 分钟）。
6. **静默拒绝**：受保护目标（当前桌面、默认拨号、安装器/校验器、权限控制器、有活跃设备管理员的包、系统豁免包）被拒时**不返回错误**，只表现为状态没变；黑名单还得自己补输入法/SystemUI/本应用自身（§3.3 第 7 条）。
7. **状态漂移（若给自救按钮）**：用户可能在系统对话框里自行取消暂停，之后的启动由框架自动继续。应用内必须用系统标志做显示真值并靠 `ON_RESUME` 刷新，不能只信本地记录（`FreezerPanelContent.kt:97-105` 已有该刷新点）。
8. **持久化与卸载语义**：挂起写进 `packages.xml`，重启后仍在（这点与冻结一致，重冻/重放逻辑要按「系统状态」判断）；应用本身被卸载重装后挂起标记消失，本地意图集合需要容错。
9. **多用户**：需沿用现有 `--user <current>` 解析（`FreezerPrivilegedOps.kt:90-101`）；挂起是按 user 记账的。
10. **文案噪音**：暂停与冻结并存，用户容易困惑「到底用哪个」；建议在冰箱设置页加一行说明，或在暂停确认气泡里提示语义（并在文案里避免把暂停说成「省电」）。

---

## 5. 验证方案

1. **先做零成本基线（决定要不要继续）**：真机上 `adb shell pm suspend --user 0 <某已装应用>` → 观察：桌面图标是否变灰且仍在、应用抽屉是否仍在列、点击是否弹系统对话框、最近任务是否被清、通知/后台进程是否还在跑、**重启后是否仍挂起**；状态用 `adb shell dumpsys package <pkg> | Select-String "suspend"` 核对（应能看到 `suspended=true` 与 `suspending-package`）。再 `pm unsuspend --user 0 <pkg>` 复原。
   - 至少覆盖用户主力机型的桌面（SystemLauncher / MIUI 或 HyperOS / Flyme）。`pm suspend --dialogMessage` 只能验证「文案」，**验证不了自救按钮**（那条只出现在反射 + `BUTTON_ACTION_UNSUSPEND` 路径上），所以按钮要在第 2 步用应用内路径测。
   - 若手上有 Android 16 的 One UI / HyperOS 设备，额外做一次「挂起 → 隔一段时间取消暂停」的往返，专门撞 #412 那个坑。
2. **应用内路径**：冰箱长按 → 暂停；`dumpsys` 核对 `suspended=true` 与 `suspending-package` 的取值（应等于 §3.1 决策 1 选定的键）；取消暂停后核对复原。Shizuku 与 Root 两种特权模式**各测一遍**，并交叉测一次「Shizuku 挂起 → 切 Root 取消暂停」（验证双键清理是否真的有效；这是最容易留残留的路径）。
3. **状态显示**：冰箱网格（灰 + 角标）、搜索面板图标/文字、picker tab 计数（全部/已冻结/使用中/已暂停）。
4. **交互回归**：暂停态点击 → 自动取消暂停并启动；暂停 + 冻结叠加时的行为（应先恢复再改状态）；暂停态「从列表移除」有提示；批量暂停/取消暂停；「重新冻结」手势；Pin 快捷方式 trampoline 启动暂停应用；系统对话框自救按钮（若采纳）点完能正常启动且应用内状态跟着刷新。
5. **边界**：系统应用 / 无启动图标应用 / 已卸载包 / 自身与桌面（应被拦截）；受保护包被静默拒绝时是否给出正确提示（不能报「成功」）；Shizuku 未授权、Root 缺失、ROM 没有 `pm suspend` 子命令时的报错文案。
6. **残留专项**：挂起若干应用后（a）在 Root 模式下卸载本应用，看图标是否自动恢复；（b）在 Shizuku 模式下卸载，看是否留下永久灰图标 → 据此确认 §3.1 决策 1 的取舍是否可接受，并在卸载/切换特权模式前加提示。
7. **自动化**：`FreezerBootstrap` 扫描单测（含 `FLAG_SUSPENDED`）、状态映射纯函数单测、settings mutator 单测（方案 B）。特权调用本身在 CI 无法覆盖，只能真机手测（与现有冻结路径同一限制）。

---

## 6. 建议实施顺序

0. **先定 §3.1 的三个决策**（发起方键策略 / 是否给用户自救按钮 / 是否补 force-stop + appop）——它们直接决定接口形状与失败语义，事后改代价大。
1. 真机基线验证（§5.1）——**这一步不通过就不做**。
2. 特权层：`isAppSuspended` + `setAppSuspended`；**先只做 shell 路径 B 把链路跑通**，再补反射路径 A（含 7/9 参数级联与回读校验），最后补双键清理。
3. 状态收敛：`FreezerAppState` + `stateOf`，把 11 个调用点的布尔换成三态（编译器会强制补齐分支，这是防止漏改的主要保障）。
4. 冰箱内 UI：长按菜单 + 灰化/角标 + 点击自动恢复。
5. 批量与守卫、导入扫描、黑名单、文案四语言。
6. 可选增量：picker tab、搜索面板入口、重冻手势语义。
7. 残留与危险路径收尾：特权模式切换 / 卸载前的提示，`CHANGELOG.md` 与 release notes（项目惯例）。

---

## 7. 待确认

1. **「暂停」是不是就是系统挂起**（桌面图标变灰但保留、应用不卸载不移除、点图标弹系统对话框）？如果你要说的是别的行为（比如只是冰箱列表里变灰），请纠正——这决定整个方案。
2. **暂停与冻结的关系**：二者互斥二选一（我推荐：冻结=彻底停用、图标消失；暂停=轻量、图标保留），还是允许同一应用同时处于两种状态？
3. **暂停后从冰箱点击图标**是否自动取消暂停再启动（沿用现有冻结「点击即解冻后启动」的习惯）？
4. **发起方键策略（§3.1 决策 1）**：选「统一 `com.android.shell`」（模式间互操作好、但卸载本应用会留下永久灰图标）还是「Hail 式分键 + Root 双键清理」（卸载干净、但切模式需提示）？
5. **要不要给用户自救按钮（§3.1 决策 2）**：给了用户能从系统对话框自己取消暂停（但会与应用内显示短暂不同步）；不给则用户被卡住时只能回来找本应用。另注意：**要自定义对话框文案 + 保留自救按钮，就只能走反射路径**。
6. **要不要补 force-stop + `RUN_ANY_IN_BACKGROUND=ignore`（§3.1 决策 3）**：挂起不阻止后台运行，不补的话这个功能只是「图标留着但点不动」，补了才接近省电诉求（Hail 就是这么做的）。
7. **「重新冻结」手势**遇到已暂停成员应：①保持暂停 ②改成冻结 ③连启用中的成员也一起暂停？→ 决定要不要引入本地「目标模式」（§3.2 方案 A/B）。
8. 是否需要**「已暂停」分区/标签**？（注意 `FreezerTab` 目前是死管道，panel 并无 tab UI，等于从零做）
9. 搜索面板 / 桌面快捷方式长按菜单是否也要加「暂停」入口？
10. 是否需要**危险包黑名单**（本应用自身、当前桌面、SystemUI、输入法等）？AOSP 只挡住了其中一部分，而且挡的方式是静默拒绝。

---

## 8. 实现落地（本次已按 §3 方案实现）

方案与 §3 一致，落地时确定的细节：

- **特权层**（`app/.../freezer/FreezerPrivilegedOps.kt`）：`setAppSuspended` 先走 `IPackageManager.setPackagesSuspendedAsUser` 反射（按参数个数 7 / 9 级联），失败再走 `pm suspend|unsuspend --user <u> [--dialogMessage …]`；两条路径都以**回读 `FLAG_SUSPENDED`** 判定成败（PMS 对受保护包的拒绝是静默的）。`SuspendDialogInfo` 走反射构造，带自定义正文 + `BUTTON_ACTION_UNSUSPEND`（本应用启动时已由 `HiddenApiBootstrap` 做整表 hidden API 豁免），让用户能在系统对话框里自救。
- **发起方键**：经 Shizuku 且能拿到 root 时用本应用包名（卸载本应用时系统会一并清理该挂起），否则用 `com.android.shell`；**取消暂停时把可用到的键依次清一遍**。已知窄边界：若挂起是用 Shizuku 键建立的，而之后 Shizuku 被卸载/撤销授权、只剩 Root 模式，则 `pm unsuspend` 无法清掉该键的挂起（失败会以 toast 暴露）。
- **状态模型**（`FreezerAppState` + `FreezerOperations.stateOf`）：一次 `getApplicationInfo` 出 enable + suspended，冻结优先于暂停；冻结前先取消暂停，暂停前先解冻。
- **UI**：网格暂停态灰化 + ⏸ 角标；长按菜单新增「暂停/取消暂停」，尾项按状态给出「解冻并移除 / 取消暂停并移除 / 从列表移除」；面板「⋮」新增「全部暂停」「全部取消暂停」；管理页新增「已暂停 (n)」筛选、状态后缀与一句语义说明；搜索面板快捷条第 5 项「暂停/取消暂停」，暂停态一并灰化，点击启动前自动恢复。
- **行为**：点击冰箱内已暂停应用 = 取消暂停后启动（`launchAndRestore`，覆盖网格 / 搜索面板 / Pin 快捷方式 trampoline）；「重新冻结」只处理使用中的成员，已暂停的保持暂停；「全部解冻」不碰已暂停的。
- **安全**：暂停与冻结都拦截本应用自身、`android`、SystemUI、当前桌面。
- **文案**：en / zh / ja / ar 四份（13 条字符串 + 2 个 plurals），并更新了导入项文案；新增 `FreezerAppStateTest`（4 例）与 `FreezerBootstrapTest` 的挂起用例（编译通过、单测全绿）。
- **仍需真机验证**（§5）：OEM 桌面是否同样「灰化但保留」、Android 16 的「取消不掉」场景、跨特权模式的挂起残留、系统对话框自救按钮在实际 ROM 上的表现。

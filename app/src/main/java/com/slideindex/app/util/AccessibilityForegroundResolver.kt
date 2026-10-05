package com.slideindex.app.util

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager
import com.slideindex.app.perf.PerfProbe
import com.slideindex.app.service.OverlayService
import com.slideindex.app.service.SlideIndexAccessibilityService

/**
 * 从无障碍窗口树解析当前前台宿主包名；比单条 WINDOW_STATE_CHANGED 更可靠（游戏/全屏视频）。
 *
 * ### 为什么必须缓存
 *
 * 真机实测（PerfProbe，51 秒交互）：本函数被调用 **1349 次（26.5 次/秒）**，
 * 累计 **13415.7ms ≈ 整段交互时间的 26%**，平均每次 **9.9ms**（正好一帧），
 * 最大 **820ms**，其中 **552 次超过一帧预算**。
 *
 * 原因是 `resolveHostPackageInner` 每次都要做一串跨进程调用：
 * `rootInActiveWindow` → `service.windows` 遍历两遍 → 每个窗口 `window.root` →
 * `imm.enabledInputMethodList`。而**前台包名一秒最多变几次**，
 * 绝大多数调用拿到的是同一个值 —— 纯重复劳动。
 *
 * 因此这里加**短 TTL 缓存**，并在无障碍窗口事件到来时主动失效（见
 * [SlideIndexAccessibilityService.onAccessibilityEvent]）。
 */
object AccessibilityForegroundResolver {

    /**
     * 缓存兜底有效期。
     *
     * **主要失效来源是事件，不是 TTL**：真实前台切换会伴随 `TYPE_WINDOW_STATE_CHANGED`，
     * 该事件里会主动调用 [invalidate]。实测把 TTL 从 150ms 放到 5000ms，
     * 冷查询次数几乎不变（3.4 → 2.9 次/秒）—— 说明绝大多数缓存失效都由事件触发，
     * TTL 到期大多被事件抢先。
     *
     * 因此 TTL 的作用是**兜底**：少数不发 `TYPE_WINDOW_STATE_CHANGED` 的场景
     * （游戏、部分全屏视频）下，保证最多 [CACHE_TTL_MS] 后自行纠正。
     * 取 1000ms 是在"兜底及时性"和"极端情况下少几次冷查询"之间的折中；
     * 注意 150ms 与 5000ms 的性能差异尚未在同会话 A/B 中量出，不要据此认定 TTL 可换性能。
     */
    private const val CACHE_TTL_MS = 1000L

    @Volatile
    private var cacheValid = false

    @Volatile
    private var cachedAtMs = 0L

    @Volatile
    private var cachedPackage: String? = null

    /**
     * 输入法包名集合：变化极少，而 `imm.enabledInputMethodList` 是跨进程查询。
     * 缓存到下次显式失效（IME 变化会带来窗口事件）。
     */
    @Volatile
    private var imePackagesCache: Set<String>? = null

    /** 无障碍窗口事件到来时调用，丢弃前台包名与 IME 包名缓存。 */
    fun invalidate() {
        cacheValid = false
        imePackagesCache = null
    }

    fun resolve(context: Context): String? {
        val service = context as? AccessibilityService
            ?: SlideIndexAccessibilityService.accessibilityInstance()
        // 主进程没有无障碍实例，改读 overlay 进程广播过来的镜像。
        return service?.let(::resolveHostPackage)
            ?: com.slideindex.app.overlay.OverlayStatePort.foregroundPackage()
    }

    fun resolveHostPackage(service: AccessibilityService): String? {
        val now = SystemClock.uptimeMillis()
        if (cacheValid && now - cachedAtMs < CACHE_TTL_MS) {
            return cachedPackage
        }
        val selfPackage = service.packageName
        val resolved = PerfProbe.probe("A11Y.resolveHostPackage.cold") {
            resolveHostPackageInner(service, selfPackage)
        }
        cachedPackage = resolved
        cachedAtMs = now
        cacheValid = true
        return resolved
    }

    private fun resolveHostPackageInner(service: AccessibilityService, selfPackage: String): String? {
        resolveFromActiveRoot(service, selfPackage)?.let { return it }
        resolveFromApplicationWindows(service, selfPackage, requireFocused = true)?.let { return it }
        resolveFromApplicationWindows(service, selfPackage, requireFocused = false)?.let { return it }
        val tracked = OverlayService.foregroundPackage
            ?: SlideIndexAccessibilityService.currentForegroundPackage()
        return tracked?.takeIf { isUsableHostPackage(service, it, selfPackage) }
    }

    private fun resolveFromActiveRoot(service: AccessibilityService, selfPackage: String): String? {
        val root = service.rootInActiveWindow ?: return null
        return try {
            root.packageName?.toString()
                ?.takeIf { isUsableHostPackage(service, it, selfPackage) }
        } finally {
            recycleNode(root)
        }
    }

    private fun resolveFromApplicationWindows(
        service: AccessibilityService,
        selfPackage: String,
        requireFocused: Boolean,
    ): String? {
        for (window in service.windows) {
            if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
            if (requireFocused && !window.isFocused && !window.isActive) continue
            val root = window.root ?: continue
            val packageName = try {
                root.packageName?.toString()
                    ?.takeIf { isUsableHostPackage(service, it, selfPackage) }
            } finally {
                recycleNode(root)
            }
            if (packageName != null) return packageName
        }
        return null
    }

    private fun isUsableHostPackage(
        service: AccessibilityService,
        packageName: String,
        selfPackage: String,
    ): Boolean {
        if (packageName.isBlank() || packageName == selfPackage) return false
        return !isInputMethodPackage(service, packageName)
    }

    private fun isInputMethodPackage(service: AccessibilityService, packageName: String): Boolean {
        if (packageName.contains("inputmethod", ignoreCase = true)) return true
        return imePackages(service).contains(packageName)
    }

    /** IME 包名集合，带缓存；查询失败时不缓存，下次重建。 */
    private fun imePackages(service: AccessibilityService): Set<String> {
        imePackagesCache?.let { return it }
        val imm = service.getSystemService(InputMethodManager::class.java) ?: return emptySet()
        val packages = runCatching {
            imm.enabledInputMethodList.mapTo(HashSet()) { it.packageName }
        }.getOrNull() ?: return emptySet()
        imePackagesCache = packages
        return packages
    }

    private fun recycleNode(node: AccessibilityNodeInfo?) {
        if (node == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
        @Suppress("DEPRECATION")
        node.recycle()
    }
}

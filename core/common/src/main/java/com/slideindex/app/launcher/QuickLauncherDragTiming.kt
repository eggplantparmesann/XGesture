package com.slideindex.app.launcher

/** 拖拽图标时用于触发"合成文件夹"等驻留动作的时间常量。 */
object QuickLauncherDragTiming {

    /**
     * 拖拽悬停在某个图标格上多久，才把该格认定为**合并目标**（松手即合成文件夹）。
     *
     * 设置页编辑器（[com.slideindex.app.ui.QuickLauncherGridEditor]）和 overlay 悬浮面板
     * （QuickLauncherPanelManagementHandler）共用此值，避免两处飘掉。
     *
     * 900ms 的理由：网格是密铺的，"想把图标放到第 N 格"必然要压着第 N 格的图标，
     * 而对准这个动作本身就会产生约 250~500ms 的驻留。计时器在指针移出 14dp 死区或换格时重置，
     * 所以 900ms 实际要求的是"对准之后稳稳停住约 0.9 秒"，把正常对准整个甩在窗口外；
     * 同时它高于系统 ViewConfiguration.LONG_PRESS_TIMEOUT（500ms），
     * 任何普通按压都不会误触发，而有意建文件夹的人对 0.9 秒是容忍的（iOS 约 1~1.5s）。
     *
     * 改小这个值会立刻带回"每次刚好想放就变成文件夹"的问题，请谨慎下调。
     */
    const val FOLDER_MERGE_DWELL_MS = 900L
}

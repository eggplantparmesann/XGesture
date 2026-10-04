package com.slideindex.app.translate

/** 关闭「即时翻译」时，取词翻译可以尝试的落地方式，按优先级排列。 */
enum class TranslateLaunchChannel {
    /** `ACTION_PROCESS_TEXT`：把文本直接交给目标 App，文本会进它的输入框（首选）。 */
    PROCESS_TEXT,

    /** `ACTION_SEND`：目标 App 作为分享目标接收文本（`PROCESS_TEXT` 拉不起来时的兜底）。 */
    SEND,

    /** `ACTION_VIEW`：打开网页翻译（最后的兜底）。 */
    WEB,
}

/**
 * 「跳转翻译」该按什么顺序落地。
 *
 * 这里只做纯逻辑：某个 App 是否声明了某个通道，由调用方通过 `PackageManager` 探测后传进来，
 * 所以可以直接单测，不需要真机或 Robolectric。
 *
 * 之所以不再发"裸的 https 链接"：Google 翻译注册了 `translate.google.com` 的 App Links，
 * 系统会把这种隐式 Intent 优先交给 App，而 App 并不消费 URL 里的 `text` 参数 —— 结果是
 * "App 打开了、输入框是空的"。显式指定包名并带上文本，才能既跳过去又带着文本。
 */
object TranslateLaunchPlanner {
    /** Google 翻译的包名。 */
    const val GOOGLE_TRANSLATE_PACKAGE: String = "com.google.android.apps.translate"

    /**
     * 给出可依次尝试的通道，最后一项恒为 [TranslateLaunchChannel.WEB]，保证"点了总有反应"。
     *
     * @param processTextPackages 能处理 `ACTION_PROCESS_TEXT` + `text/plain` 的包名
     * @param sendPackages 能处理 `ACTION_SEND` + `text/plain` 的包名
     * @param targetPackage 目标翻译 App；默认 Google 翻译
     */
    fun plan(
        processTextPackages: Set<String>,
        sendPackages: Set<String>,
        targetPackage: String = GOOGLE_TRANSLATE_PACKAGE,
    ): List<TranslateLaunchChannel> = buildList {
        if (targetPackage in processTextPackages) add(TranslateLaunchChannel.PROCESS_TEXT)
        if (targetPackage in sendPackages) add(TranslateLaunchChannel.SEND)
        add(TranslateLaunchChannel.WEB)
    }
}

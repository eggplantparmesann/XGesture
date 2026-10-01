package com.slideindex.app.service

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * vivo / OriginOS 小窗启动代理 Activity。
 *
 * 利用 OriginOS 对系统分享 (ACTION_SEND) 自动小窗化的系统机制：
 * 当通过带有 Intent.createChooser 的显式代理分发时，系统免弹选择器直接将此代理以小窗拉起，
 * 代理在 onCreate 中立即解包目标 Intent 并在同一小窗栈内拉起目标页面，然后迅速 finish。
 */
class FreeWindowShareProxyActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val targetIntent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_INTENT)
            }

            if (targetIntent != null) {
                // 剥离 NEW_TASK 与 MULTIPLE_TASK，确保目标页面沿用当前小窗栈
                targetIntent.flags = targetIntent.flags and
                    Intent.FLAG_ACTIVITY_NEW_TASK.inv() and
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK.inv()
                startActivity(targetIntent)
            }
        } catch (e: Exception) {
            android.util.Log.e("FreeWindowShareProxy", "Failed to launch target intent in freeform proxy", e)
        } finally {
            finish()
        }
    }
}

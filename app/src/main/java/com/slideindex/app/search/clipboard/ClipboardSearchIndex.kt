package com.slideindex.app.search.clipboard

import com.slideindex.app.clipboard.ClipboardAccess
import com.slideindex.app.clipboard.ClipboardEntry

/**
 * 搜索面板「剪贴板」候选：直接复用剪贴板历史仓库的全文检索（FTS，失败时回退 LIKE），
 * 结果按复制时间倒序。
 *
 * 不做敏感内容过滤（按产品要求），因此该分区默认开启但可单独关闭。
 */
object ClipboardSearchIndex {
    const val DEFAULT_LIMIT = 8

    suspend fun search(
        query: String,
        limit: Int = DEFAULT_LIMIT,
    ): List<ClipboardEntry> {
        val trimmed = query.trim()
        if (trimmed.isEmpty() || limit <= 0) return emptyList()
        val repository = ClipboardAccess.repository ?: return emptyList()
        return runCatching { repository.searchHistory(trimmed, limit) }.getOrDefault(emptyList())
    }
}

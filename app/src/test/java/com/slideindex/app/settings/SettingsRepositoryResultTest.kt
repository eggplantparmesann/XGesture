package com.slideindex.app.settings

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class SettingsRepositoryResultTest {
    @Test
    fun setServiceEnabled_returnsSuccessAndPersists() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val repository = testSettingsRepository(context)

        val result = repository.setServiceEnabled(true)

        assertTrue(result.isSuccess)
        // readSnapshot() 是异步 flow 维护的缓存，写完立刻读有竞态；这里直接读持久化结果。
        assertTrue(repository.readFreshSnapshot().serviceEnabled)
    }
}

package com.hightemp.proxy_switcher.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyRuntimeStateTest {

    @Test
    fun setRunning_updatesCurrentRuntimeState() {
        ProxyRuntimeState.setRunning(false)

        ProxyRuntimeState.setRunning(true)
        assertTrue(ProxyRuntimeState.isRunning.value)

        ProxyRuntimeState.setRunning(false)
        assertFalse(ProxyRuntimeState.isRunning.value)
    }
}

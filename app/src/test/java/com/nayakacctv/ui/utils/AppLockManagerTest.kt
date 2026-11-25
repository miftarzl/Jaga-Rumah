package com.nayakacctv.ui.utils

import org.junit.Assert.assertFalse
import org.junit.Test

class AppLockManagerTest {
    @Test
    fun disabledAutoLockDoesNotLockOnStartupOrAfterBackgrounding() {
        AppLockManager.isSuppressed = false
        AppLockManager.unlock()

        AppLockManager.lockImmediately()
        AppLockManager.onAppBackgrounded()
        AppLockManager.onAppForegrounded()

        assertFalse(AppLockManager.isLocked())
    }
}

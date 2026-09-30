package com.nicholaston.callscribe.shizuku

import org.junit.Assert.assertEquals
import org.junit.Test

class ShizukuHealthPolicyTest {
    @Test
    fun `health prioritizes server availability then permission`() {
        assertEquals(ShizukuHealth.NOT_RUNNING, ShizukuHealthPolicy.map(false, false))
        assertEquals(ShizukuHealth.NOT_RUNNING, ShizukuHealthPolicy.map(false, true))
        assertEquals(ShizukuHealth.PERMISSION_REQUIRED, ShizukuHealthPolicy.map(true, false))
        assertEquals(ShizukuHealth.RUNNING, ShizukuHealthPolicy.map(true, true))
    }
}

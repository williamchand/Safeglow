package com.safeglow.edge

import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers PRIV-01 and PRIV-03 (T-1-01).
 * Verifies that the INTERNET permission is NOT declared in AndroidManifest.xml.
 * If PackageManager.PERMISSION_GRANTED is returned, the manifest leaked a permission
 * and the privacy guarantee is broken.
 */
@RunWith(AndroidJUnit4::class)
class LiteRTNoNetworkTest {

    @Test
    fun internetPermissionAbsentFromManifest() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val pm = ctx.packageManager
        val result = pm.checkPermission(
            android.Manifest.permission.INTERNET,
            ctx.packageName
        )
        assertEquals(
            "PRIV-01/PRIV-03: INTERNET permission must NOT be declared in AndroidManifest.xml",
            PackageManager.PERMISSION_DENIED,
            result
        )
    }

    @Test
    fun networkStatePermissionAbsentFromManifest() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val pm = ctx.packageManager
        val result = pm.checkPermission(
            android.Manifest.permission.ACCESS_NETWORK_STATE,
            ctx.packageName
        )
        assertEquals(
            "PRIV-01/PRIV-03: ACCESS_NETWORK_STATE must NOT be declared",
            PackageManager.PERMISSION_DENIED,
            result
        )
    }
}

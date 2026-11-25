package com.parcha.spoilnot

import android.content.Context
import android.content.pm.ComponentInfo
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The merged manifest (the app's and every library's, as the build packs it) exports only what the app needs
 * (DESIGN 12.2; after v1, S5). Robolectric reads the merged manifest of this build; release builds merge the same
 * main manifest without the debug-only test activities.
 */
@RunWith(RobolectricTestRunner::class)
class ExportedComponentsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Suppress("DEPRECATION")
    private val info = context.packageManager.getPackageInfo(
        context.packageName,
        PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or
            PackageManager.GET_PROVIDERS,
    )

    private val components: List<ComponentInfo> =
        listOfNotNull(info.activities, info.services, info.receivers, info.providers).flatMap { it.asList() }

    @Test
    fun `Firebase Auth's phone and federated sign-in activities are not in the app`() {
        val names = components.map { it.name }
        assertTrue(names.none { it == "com.google.firebase.auth.internal.GenericIdpActivity" })
        assertTrue(names.none { it == "com.google.firebase.auth.internal.RecaptchaActivity" })
    }

    @Test
    fun `only the launcher activity is exported without a permission`() {
        val open = components
            .filter { it.exported && permissionOf(it) == null }
            .map { it.name }
            // Debug builds only: the screen Compose UI tests start (ui-test-manifest).
            .filterNot { it == "androidx.activity.ComponentActivity" }
        assertEquals(listOf("com.parcha.spoilnot.MainActivity"), open)
    }

    private fun permissionOf(c: ComponentInfo): String? = when (c) {
        is android.content.pm.ActivityInfo -> c.permission
        is android.content.pm.ServiceInfo -> c.permission
        is android.content.pm.ProviderInfo -> c.readPermission ?: c.writePermission
        else -> null
    }
}

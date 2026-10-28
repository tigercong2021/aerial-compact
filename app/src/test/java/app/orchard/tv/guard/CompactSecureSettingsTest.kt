package app.orchard.tv.guard

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.os.Handler
import android.provider.Settings
import app.orchard.tv.BuildConfig
import app.orchard.tv.dream.AerialDream
import app.orchard.tv.system.Brightness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class CompactSecureSettingsTest {
    private val app: Application = RuntimeEnvironment.getApplication()
    private val resolver get() = app.contentResolver
    private val keys = listOf("enable_running_backgroud_components", "enable_launcher_components", "screensaver_components")
    private val permission = Manifest.permission.WRITE_SECURE_SETTINGS

    @Before fun compactOnly() { assumeTrue(BuildConfig.COMPACT_PREVIEW) }

    private fun seed() {
        Settings.Secure.putString(resolver, keys[0], "vendor.a/vendor.b/")
        Settings.Secure.putString(resolver, keys[1], "vendor.home/${app.packageName}.other")
        Settings.Secure.putString(resolver, keys[2], "vendor.dream/.Dream")
    }

    private fun values() = keys.map { Settings.Secure.getString(resolver, it) }

    @Test fun deniedPermissionCreatesNoObserverAndChangesNothing() {
        seed()
        val before = values()
        shadowOf(app).denyPermissions(permission)
        CompactSecureSettings.reconcileOnce(app)
        assertNull(CompactSecureSettings.observe(app))
        assertEquals(before, values())
        keys.forEach { assertTrue(shadowOf(resolver).getContentObservers(Settings.Secure.getUriFor(it)).isEmpty()) }
    }

    @Test fun repairPreservesListBytesAndLeavesOtherSettingsAndStockVisitAlone() {
        seed()
        Settings.Secure.putString(resolver, "enabled_accessibility_services", "voice/.Service")
        Settings.Secure.putString(resolver, "screensaver_default_component", "keep/.Default")
        Settings.Secure.putInt(resolver, "screensaver_enabled", 0)
        Settings.Global.putInt(resolver, Settings.Global.ADB_ENABLED, 0)
        Settings.System.putString(resolver, "com.skyworthdigital.backgroundwhitelist", "keep,unchanged")
        app.getSharedPreferences("compact_home_return", 0).edit().putString("allowed_stock_package", "com.ktcp.launcher").commit()
        shadowOf(app).grantPermissions(permission)

        CompactSecureSettings.reconcileOnce(app)

        assertEquals("vendor.a/vendor.b/${app.packageName}", values()[0])
        assertEquals("vendor.home/${app.packageName}.other/${app.packageName}", values()[1])
        assertEquals(ComponentName(app, AerialDream::class.java).flattenToString(), values()[2])
        assertEquals("voice/.Service", Settings.Secure.getString(resolver, "enabled_accessibility_services"))
        assertEquals("keep/.Default", Settings.Secure.getString(resolver, "screensaver_default_component"))
        assertEquals(0, Settings.Secure.getInt(resolver, "screensaver_enabled"))
        assertEquals(0, Settings.Global.getInt(resolver, Settings.Global.ADB_ENABLED))
        assertEquals("keep,unchanged", Settings.System.getString(resolver, "com.skyworthdigital.backgroundwhitelist"))
        assertEquals("com.ktcp.launcher", app.getSharedPreferences("compact_home_return", 0).getString("allowed_stock_package", null))
        assertNull(shadowOf(app).nextStartedActivity)
    }

    @Test fun exactExistingPackageKeepsDuplicatesAndSeparatorsUnchanged() {
        seed()
        val original = "vendor.a/${app.packageName}/vendor.a//"
        Settings.Secure.putString(resolver, keys[0], original)
        shadowOf(app).grantPermissions(permission)
        CompactSecureSettings.reconcileOnce(app)
        CompactSecureSettings.reconcileOnce(app)
        assertEquals(original, values()[0])
    }

    @Test fun missingOemListsAreNotCreated() {
        Settings.Secure.putString(resolver, keys[0], null)
        Settings.Secure.putString(resolver, keys[1], "")
        shadowOf(app).grantPermissions(permission)
        CompactSecureSettings.reconcileOnce(app)
        assertNull(values()[0])
        assertEquals("", values()[1])
    }

    @Test fun wssDoesNotEnableLegacyXiaomiGlobalBrightnessWrites() {
        Settings.Global.putInt(resolver, "picture_backlight", 50)
        shadowOf(app).grantPermissions(permission)
        assertNull(Brightness.level(app))
        assertNull(Brightness.adjust(app, 1))
        assertEquals(50, Settings.Global.getInt(resolver, "picture_backlight"))
    }

    @Test fun closeRemovesAllThreeObserversBeforeTheQueuedRepair() {
        seed()
        val before = values()
        shadowOf(app).grantPermissions(permission)
        val session = CompactSecureSettings.observe(app)!!
        try {
            val handler = ReflectionHelpers.getField<Handler>(session, "handler")
            shadowOf(handler.looper).idle()
            keys.forEach { assertEquals(1, shadowOf(resolver).getContentObservers(Settings.Secure.getUriFor(it)).size) }
        } finally { session.close() }
        assertEquals(before, values())
        keys.forEach { assertTrue(shadowOf(resolver).getContentObservers(Settings.Secure.getUriFor(it)).isEmpty()) }
    }

    @Test fun revocationDetectedOnAnExistingEventClosesTheSessionWithoutWriting() {
        seed()
        val before = values()
        shadowOf(app).grantPermissions(permission)
        val session = CompactSecureSettings.observe(app)!!
        try {
            val handler = ReflectionHelpers.getField<Handler>(session, "handler")
            shadowOf(handler.looper).idle()
            shadowOf(app).denyPermissions(permission)
            assertTrue(session.stopIfPermissionMissing())
            assertEquals(before, values())
            keys.forEach { assertTrue(shadowOf(resolver).getContentObservers(Settings.Secure.getUriFor(it)).isEmpty()) }
        } finally { session.close() }
    }
}

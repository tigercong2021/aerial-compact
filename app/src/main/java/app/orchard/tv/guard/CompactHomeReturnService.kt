package app.orchard.tv.guard

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import app.orchard.tv.BuildConfig
import app.orchard.tv.MainActivity

/**
 * An opt-in compact-only return route. It reads window event package names, never window content,
 * class names, text or keys, and never enables itself. A separately granted WSS permission enables
 * only CompactSecureSettings' closed three-key maintenance, not the upstream guard setup.
 *
 * A firmware HOME package may also contain its video pages. Call [allowStockHome] before opening
 * it deliberately; that package stays allowed across process restarts until the next HOME.
 */
class CompactHomeReturnService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var receiverRegistered = false
    private var latestPackage: String? = null
    private var pendingReturn = false
    private var forcedHome = false
    private var lastLaunchAt = Long.MIN_VALUE / 2
    private var attemptedStockPackage: String? = null
    private var settingsMaintenance: CompactSecureSettings.Session? = null

    private val returnHome = object : Runnable {
        override fun run() {
            pendingReturn = false
            if (!BuildConfig.COMPACT_PREVIEW || instance !== this@CompactHomeReturnService) return
            // A dream detaching on screen-off can expose the stock window. A passive event must
            // not reopen Home while asleep; a trusted HOME request still represents user intent.
            if (!forcedHome && (getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isInteractive == false) return
            val pkg = latestPackage
            if (!forcedHome && (pkg == null || !isStockHome(this@CompactHomeReturnService, pkg) ||
                    allowedStockHome(this@CompactHomeReturnService) == pkg || attemptedStockPackage == pkg)) return

            val now = SystemClock.uptimeMillis()
            val earliest = lastLaunchAt + MIN_LAUNCH_INTERVAL_MS
            if (now < earliest) {
                // Keep the latest stock event queued instead of dropping it during the cooldown.
                pendingReturn = true
                handler.postAtTime(this, earliest)
                return
            }
            forcedHome = false
            lastLaunchAt = now
            // Some firmware silently rejects background starts. Attempt only once per stock
            // visit, even when startActivity returns normally; a new trusted HOME resets this.
            attemptedStockPackage = pkg?.takeIf { isStockHome(this@CompactHomeReturnService, it) }
            try {
                startActivity(MainActivity.bringBackIntent(this@CompactHomeReturnService))
            } catch (_: Exception) {
                // Keep the attempt latch and do not enqueue timer retries.
            }
        }
    }

    private val homeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!BuildConfig.COMPACT_PREVIEW || intent.action != Intent.ACTION_CLOSE_SYSTEM_DIALOGS ||
                intent.getStringExtra("reason") != "homekey") return
            clearAllowedStockHome(this@CompactHomeReturnService)
            attemptedStockPackage = null
            scheduleReturn(force = true)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (!BuildConfig.COMPACT_PREVIEW) return
        instance = this
        settingsMaintenance?.close()
        settingsMaintenance = CompactSecureSettings.observe(this)
        stockHomes(this, refresh = true)
        // CLOSE_SYSTEM_DIALOGS is not protected from ordinary senders on Android 9. The receiver
        // instead requires STATUS_BAR from its sender (signature|privileged in AOSP Android 9).
        // BroadcastQueue checks the sending UID; root/system UID pass that permission check.
        // This app neither requests nor receives STATUS_BAR. An OEM broadcasting from another
        // UID without that permission will not reach this receiver: verify HOME on the device.
        if (!receiverRegistered) {
            receiverRegistered = runCatching {
                ContextCompat.registerReceiver(
                    this, homeReceiver, IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS),
                    "android.permission.STATUS_BAR", handler, ContextCompat.RECEIVER_EXPORTED,
                )
                true
            }.getOrDefault(false)
        }
        // Connecting/reconnecting never clears a deliberate visit to the original desktop.
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        settingsMaintenance?.stopIfPermissionMissing()
        if (!BuildConfig.COMPACT_PREVIEW || event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString()?.takeIf { it.isNotBlank() }
        latestPackage = pkg
        if (pkg == null) {
            // An unknown window must not leave a stale stock package eligible to steal focus.
            if (!forcedHome) cancelPending()
            return
        }
        if (pkg == packageName) {
            attemptedStockPackage = null
            cancelPending()
            return
        }
        if (!isStockHome(this, pkg)) {
            attemptedStockPackage = null
            if (!forcedHome) cancelPending()
            return
        }
        if (allowedStockHome(this) == pkg || attemptedStockPackage == pkg) {
            if (!forcedHome) cancelPending()
            return
        }
        scheduleReturn(force = false)
    }

    private fun scheduleReturn(force: Boolean) {
        forcedHome = forcedHome || force
        if (pendingReturn) return
        pendingReturn = true
        val due = maxOf(SystemClock.uptimeMillis() + COALESCE_MS, lastLaunchAt + MIN_LAUNCH_INTERVAL_MS)
        handler.postAtTime(returnHome, due)
    }

    private fun cancelPending() {
        handler.removeCallbacks(returnHome)
        pendingReturn = false
        forcedHome = false
    }

    private fun cancelPendingOnMain() {
        if (Looper.myLooper() == handler.looper) cancelPending() else handler.post { cancelPending() }
    }

    override fun onInterrupt() { cancelPending() }

    override fun onUnbind(intent: Intent?): Boolean {
        settingsMaintenance?.close()
        settingsMaintenance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        settingsMaintenance?.close()
        settingsMaintenance = null
        cancelPending()
        handler.removeCallbacksAndMessages(null)
        if (receiverRegistered) runCatching { unregisterReceiver(homeReceiver) }
        receiverRegistered = false
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        private const val PREFS = "compact_home_return"
        private const val ALLOWED_PACKAGE = "allowed_stock_package"
        private const val COALESCE_MS = 100L
        private const val MIN_LAUNCH_INTERVAL_MS = 500L
        private const val STOCK_CACHE_MS = 30_000L
        @Volatile private var instance: CompactHomeReturnService? = null
        private var cachedForPackage: String? = null
        private var cachedAt = Long.MIN_VALUE / 2
        private var cachedStockHomes: Set<String> = emptySet()

        /** Only enabled/exported system HOME candidates, excluding this app and fallbacks. */
        fun isStockHome(context: Context, pkg: String): Boolean =
            BuildConfig.COMPACT_PREVIEW && pkg in stockHomes(context)

        /** Commit before the caller opens the stock tile; false means it must not assume allowed. */
        fun allowStockHome(context: Context, pkg: String): Boolean {
            if (!BuildConfig.COMPACT_PREVIEW || !isStockHome(context, pkg)) return false
            val saved = runCatching {
                prefs(context).edit().putString(ALLOWED_PACKAGE, pkg).commit()
            }.getOrDefault(false)
            if (saved) instance?.cancelPendingOnMain()
            return saved
        }

        /** Only a new HOME request should clear this; never an onResume or a service reconnect. */
        fun clearAllowedStockHome(context: Context): Boolean {
            if (!BuildConfig.COMPACT_PREVIEW) return false
            return runCatching { prefs(context).edit().remove(ALLOWED_PACKAGE).commit() }.getOrDefault(false)
        }

        /** Read-only: an already connected instance or this exact service in Android's enabled list. */
        fun isEnabledOrConnected(context: Context): Boolean {
            if (!BuildConfig.COMPACT_PREVIEW) return false
            if (instance != null) return true
            return runCatching {
                val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
                    ?: return@runCatching false
                manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any { enabled ->
                    val info = enabled.resolveInfo?.serviceInfo
                    info?.packageName == context.packageName && info.name == CompactHomeReturnService::class.java.name
                }
            }.getOrDefault(false)
        }

        private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        private fun allowedStockHome(context: Context): String? =
            runCatching { prefs(context).getString(ALLOWED_PACKAGE, null) }.getOrNull()

        @Synchronized
        private fun stockHomes(context: Context, refresh: Boolean = false): Set<String> {
            if (!BuildConfig.COMPACT_PREVIEW) return emptySet()
            val now = SystemClock.uptimeMillis()
            if (!refresh && cachedForPackage == context.packageName && now - cachedAt < STOCK_CACHE_MS) return cachedStockHomes
            cachedStockHomes = runCatching {
                context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
                    .asSequence()
                    .filter { result ->
                        val info = result.activityInfo
                        val system = info.applicationInfo.flags and
                            (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                        result.priority >= 0 && info.exported && info.enabled && info.applicationInfo.enabled &&
                            system && info.packageName != context.packageName
                    }
                    .map { it.activityInfo.packageName }
                    .toSet()
            }.getOrDefault(emptySet())
            cachedForPackage = context.packageName
            cachedAt = now
            return cachedStockHomes
        }
    }
}

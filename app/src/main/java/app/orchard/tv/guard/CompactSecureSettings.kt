package app.orchard.tv.guard

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import app.orchard.tv.BuildConfig
import app.orchard.tv.dream.AerialDream

/**
 * Compact-only maintenance after an externally granted WRITE_SECURE_SETTINGS permission.
 * The Android permission is broad; this closed key list is our narrower implementation contract.
 * No HOME selection, activity launch, accessibility setting, boot receiver or permission grant.
 */
object CompactSecureSettings {
    private const val TAG = "CompactSettings"
    private const val BACKGROUND = "enable_running_backgroud_components"
    private const val LAUNCHERS = "enable_launcher_components"
    private const val DREAM = "screensaver_components"
    private val keys = listOf(BACKGROUND, LAUNCHERS, DREAM)
    // Main's one-shot and the service's observer share this lock. Never replace a stale list.
    private val writeLock = Any()
    private const val COALESCE_MS = 250L
    private val backoffMs = longArrayOf(1_000L, 5_000L, 15_000L, 30_000L)

    fun hasPermission(context: Context): Boolean = BuildConfig.COMPACT_PREVIEW &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /** One bounded pass, called off the main thread. No observer or retry is created here. */
    fun reconcileOnce(context: Context) {
        if (!hasPermission(context)) return
        synchronized(writeLock) { reconcile(context.applicationContext, canWrite = true) { true } }
    }

    /** Only the existing, explicitly enabled accessibility service owns a continuing session. */
    fun observe(context: Context): Session? =
        if (!hasPermission(context)) null else Session(context.applicationContext).also { it.start() }

    private data class Result(val attempted: Boolean, val pending: Boolean, val permitted: Boolean)

    /** Called under writeLock. Read failures never become empty replacement lists. */
    private fun reconcile(context: Context, canWrite: Boolean, active: () -> Boolean): Result {
        var attempted = false
        var pending = false
        val resolver = context.contentResolver
        for (key in keys) {
            if (!active() || !hasPermission(context)) return Result(attempted, pending, false)
            val current = try {
                Settings.Secure.getString(resolver, key)
            } catch (_: Exception) {
                Log.w(TAG, "Could not read $key")
                pending = true
                continue
            }
            val desired = if (key == DREAM) ComponentName(context, AerialDream::class.java).flattenToString()
            else {
                // These are OEM slash-delimited lists, not arbitrary new settings on other TVs.
                // Preserve every existing byte, duplicate and token; append only our exact package.
                if (current.isNullOrBlank()) continue
                if (current.split('/').any { it == context.packageName }) current
                else current + (if (current.endsWith('/')) "" else "/") + context.packageName
            }
            if (current == desired) continue
            if (!canWrite) { pending = true; continue }
            if (!active() || !hasPermission(context)) return Result(attempted, true, false)
            try {
                // SettingsProvider has no compare-and-set API. If another writer changed the
                // value since our read, leave it for the next bounded pass instead of overwriting.
                if (Settings.Secure.getString(resolver, key) != current) {
                    pending = true
                    continue
                }
                if (!active() || !hasPermission(context)) return Result(attempted, true, false)
                attempted = true
                val saved = Settings.Secure.putString(resolver, key, desired)
                val verified = saved && Settings.Secure.getString(resolver, key) == desired
                if (!verified) { pending = true; Log.w(TAG, "Readback did not confirm $key") }
                else Log.i(TAG, "Reconciled $key")
            } catch (_: SecurityException) {
                // A grant may be revoked between the last check and the provider call.
                return Result(attempted, true, false)
            } catch (_: Exception) {
                pending = true
                Log.w(TAG, "Could not reconcile $key")
            }
        }
        return Result(attempted, pending, active() && hasPermission(context))
    }

    class Session internal constructor(private val app: Context) {
        private val worker = HandlerThread("CompactSettings").apply { start() }
        private val handler = Handler(worker.looper)
        @Volatile private var closed = false
        private var observing = false
        private var queued = false
        // A callback, including our own write notification, never resets this session's budget.
        private var passes = 0
        private var nextAllowedAt = 0L
        private val uris = keys.map(Settings.Secure::getUriFor).toSet()
        private val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) { request() }
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                if (uri == null || uri in uris) request()
            }
        }
        private val repair = Runnable {
            queued = false
            if (stopIfPermissionMissing()) return@Runnable
            val result = synchronized(writeLock) {
                if (closed) return@synchronized null
                reconcile(app, canWrite = passes < backoffMs.size) { !closed }
            } ?: return@Runnable
            if (!result.permitted) { close(); return@Runnable }
            if (result.attempted || result.pending) {
                // Read errors/racing writers consume the budget too: they cannot create a spin.
                passes++
                nextAllowedAt = SystemClock.uptimeMillis() + backoffMs[(passes - 1).coerceAtMost(backoffMs.lastIndex)]
            }
            if (result.pending && passes >= backoffMs.size) {
                Log.w(TAG, "Maintenance stopped after bounded attempts; inspect firmware changes")
                close()
            } else if (result.pending) request()
        }

        internal fun start() {
            handler.post {
                synchronized(writeLock) {
                    if (closed || stopIfPermissionMissing()) return@post
                    try {
                        // Register exactly these three Secure URIs, never their parent table.
                        observing = true
                        uris.forEach { app.contentResolver.registerContentObserver(it, false, observer) }
                    } catch (_: Exception) {
                        Log.w(TAG, "Could not register compact setting observers")
                        close()
                        return@post
                    }
                }
                request()
            }
        }

        private fun request() {
            if (stopIfPermissionMissing() || queued) return
            queued = true
            handler.postAtTime(repair, maxOf(SystemClock.uptimeMillis() + COALESCE_MS, nextAllowedAt))
        }

        /** Also called on existing window events; no extra permission observer or polling. */
        fun stopIfPermissionMissing(): Boolean {
            if (closed) return true
            if (hasPermission(app)) return false
            close()
            return true
        }

        /** Invalidate queued work first; after this returns no session write can still be running. */
        fun close() {
            closed = true
            handler.removeCallbacksAndMessages(null)
            synchronized(writeLock) {
                if (observing) runCatching { app.contentResolver.unregisterContentObserver(observer) }
                observing = false
            }
            worker.quitSafely()
        }
    }
}

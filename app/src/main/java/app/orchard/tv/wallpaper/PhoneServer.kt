package app.orchard.tv.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import androidx.core.content.getSystemService
import app.orchard.tv.apps.AppCatalog
import app.orchard.tv.BuildConfig
import app.orchard.tv.apps.FOLDER_KEY_PREFIX
import app.orchard.tv.apps.AppEntry
import app.orchard.tv.apps.AppNames
import app.orchard.tv.apps.CustomIcons
import app.orchard.tv.apps.IconLibrary
import app.orchard.tv.apps.SourceDevice
import app.orchard.tv.apps.SourceLooks
import app.orchard.tv.apps.TileArtFactory
import app.orchard.tv.settings.AerialSettings
import app.orchard.tv.system.Diagnostics
import app.orchard.tv.system.currentNetwork
import app.orchard.tv.settings.FocusStyle
import app.orchard.tv.weather.Weather
import androidx.core.graphics.createBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.Inet4Address
import java.security.SecureRandom

/**
 * The page a phone opens after scanning the TV's QR code: choose a wallpaper and the screensaver,
 * send photos, give an app's tile a picture of one's own, and name the weather's city. Choosing
 * pictures or typing a place with a remote is slow; with a phone it is not.
 *
 * It listens on the local network while Aerial runs (see [listen]), and every address carries a
 * random token shown only on the TV (the QR code and the address under it), so another device on
 * the network cannot use it by guessing. The token stays the same from one start to the next.
 */
class PhoneServer private constructor(private val context: Context, private val token: String) : NanoHTTPD(PORT) {

    override fun serve(session: IHTTPSession): Response {
        val prefix = "/$token"
        val uri = session.uri
        if (!uri.startsWith(prefix)) return text(Response.Status.NOT_FOUND, "Not found")
        val path = uri.removePrefix(prefix).ifEmpty { "/" }
        return runCatching {
            when {
                path == "/" -> newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", page())
                path == "/api/state" -> json(state())
                path == "/api/diagnostics" -> text(Response.Status.OK, Diagnostics.report(context))
                path.startsWith("/thumb/") -> thumb(path.removePrefix("/thumb/"))
                session.method == Method.POST && path == "/api/wallpaper" -> {
                    val id = form(session)["id"].orEmpty()
                    onMain { Wallpapers.select(id) }
                    json(state())
                }
                session.method == Method.POST && path == "/api/screensaver" -> {
                    val mode = ScreensaverMode.of(form(session)["mode"])
                    if (BuildConfig.COMPACT_PREVIEW && mode == ScreensaverMode.System) {
                        return text(Response.Status.FORBIDDEN, "System screensaver selection is managed outside this app")
                    }
                    onMain { Wallpapers.chooseScreensaver(mode) }
                    json(state())
                }
                session.method == Method.POST && path == "/api/setting" -> {
                    // The few on/off settings the phone page shows, as Personalize does.
                    val f = form(session)
                    val on = f["on"] == "1"
                    val known = when (f["key"]) {
                        "wallpaperAlwaysMoves" -> { onMain { AerialSettings.chooseWallpaperAlwaysMoves(on) }; true }
                        "showWeather" -> { onMain { AerialSettings.chooseShowWeather(on) }; true }
                        "screensaverClock" -> {
                            onMain { AerialSettings.chooseScreensaverClock(on) }; true
                        }
                        else -> false
                    }
                    json(state().put("saved", known))
                }
                session.method == Method.POST && path == "/api/focus" -> {
                    val style = FocusStyle.of(form(session)["style"])
                    onMain { AerialSettings.chooseFocusStyle(style) }
                    json(state())
                }
                session.method == Method.POST && path == "/api/delete" -> {
                    val id = form(session)["id"].orEmpty()
                    onMain { Wallpapers.deletePhoto(id) }
                    json(state())
                }
                session.method == Method.POST && path == "/api/upload" -> upload(session)
                session.method == Method.POST && path == "/api/videos/upload" -> video(session)
                session.method == Method.POST && path == "/api/videos/delete" -> {
                    val removed = ScreensaverVideos.delete(context, form(session)["id"].orEmpty())
                    if (removed) onMain { Wallpapers.videosChanged() }
                    json(state().put("removed", removed))
                }
                path.startsWith("/tile/") -> tile(java.net.URLDecoder.decode(path.removePrefix("/tile/"), "UTF-8"), own = session.parameters["art"]?.firstOrNull() == "own")
                path.startsWith("/library/") -> libraryPicture(java.net.URLDecoder.decode(path.removePrefix("/library/"), "UTF-8"))
                session.method == Method.POST && path == "/api/icon" -> icon(session)
                BuildConfig.COMPACT_PREVIEW && session.method == Method.POST && path == "/api/icon/native" -> nativeIcon(session)
                session.method == Method.POST && path == "/api/source/name" -> {
                    val f = form(session)
                    val source = AppCatalog.pinnedSources.firstOrNull { it.key == f["app"] }?.source
                    // On the main thread before answering, so the state sent back has the new name.
                    if (source != null) runBlocking(Dispatchers.Main) { SourceLooks.rename(source.id, f["name"]) }
                    json(state())
                }
                session.method == Method.POST && path == "/api/app/name" -> {
                    val f = form(session)
                    val key = f["app"].orEmpty()
                    val source = AppCatalog.pinnedSources.firstOrNull { it.key == key }?.source
                    // On the main thread before answering, so the state sent back has the new name.
                    val saved = when {
                        source != null -> { runBlocking(Dispatchers.Main) { SourceLooks.rename(source.id, f["name"]) }; true }
                        AppCatalog.loaded.any { it.key == key } -> { runBlocking(Dispatchers.Main) { AppNames.rename(key, f["name"]) }; true }
                        else -> false
                    }
                    json(state().put("saved", saved))
                }
                session.method == Method.POST && path == "/api/layout" -> layout(form(session))
                session.method == Method.POST && path == "/api/source/device" -> {
                    val f = form(session)
                    val source = AppCatalog.pinnedSources.firstOrNull { it.key == f["app"] }?.source
                    // As from the tile's menu: a device names the tile after it, "" goes back to the plain input.
                    if (source != null) runBlocking(Dispatchers.Main) { SourceLooks.chooseDevice(source.id, SourceDevice.of(f["device"])) }
                    json(state().put("saved", source != null))
                }
                session.method == Method.POST && path == "/api/source/pin" -> {
                    val f = form(session)
                    val id = f["id"].orEmpty()
                    val pin = AppCatalog.pinSource
                    val known = AppCatalog.sources.any { it.id == id }
                    if (known && pin != null) runBlocking(Dispatchers.Main) { pin(id, f["pinned"] == "1") }
                    json(state().put("saved", known && pin != null))
                }
                session.method == Method.POST && path == "/api/folder/name" -> {
                    val f = form(session)
                    val id = f["id"].orEmpty()
                    val name = f["name"].orEmpty().trim().take(FOLDER_NAME_MAX)
                    val rename = AppCatalog.renameFolder
                    val known = AppCatalog.folders.any { it.folder?.id == id }
                    // On the main thread before answering, so the state sent back has the new name.
                    if (known && name.isNotEmpty() && rename != null) runBlocking(Dispatchers.Main) { rename(id, name) }
                    json(state().put("saved", known && name.isNotEmpty() && rename != null))
                }
                session.method == Method.POST && path == "/api/icon/pick" -> {
                    // The library's picture or the app's own art for one app; either replaces a
                    // picture sent from the phone.
                    val f = form(session)
                    val pkg = f["app"].orEmpty()
                    val pick = when (f["pick"]) {
                        "library" -> IconLibrary.Pick.Library
                        "own" -> IconLibrary.Pick.Own
                        else -> null
                    }
                    val ok = installed(pkg) && pick != null && (pick != IconLibrary.Pick.Library || IconLibrary.has(pkg))
                    if (ok) {
                        CustomIcons.clear(pkg)
                        // The library is what every app uses anyway: kept as no pick.
                        IconLibrary.choose(pkg, pick.takeIf { it != IconLibrary.Pick.Library })
                        runBlocking(Dispatchers.Main) { CustomIcons.changed(); IconLibrary.changed() }
                    }
                    json(state().put("saved", ok))
                }
                session.method == Method.POST && path == "/api/icon/reset" -> {
                    val pkg = form(session)["app"].orEmpty()
                    if (installed(pkg)) { CustomIcons.clear(pkg); onMain { CustomIcons.changed() } }
                    json(state())
                }
                session.method == Method.POST && path == "/api/weather/city" -> {
                    val name = form(session)["name"].orEmpty()
                    val found = name.isNotBlank() && runBlocking(Dispatchers.Main) { Weather.choose(name) }
                    json(state().put("found", found))
                }
                session.method == Method.POST && path == "/api/weather/auto" -> {
                    onMain { Weather.useNetworkLocation() }
                    json(state())
                }
                else -> text(Response.Status.NOT_FOUND, "Not found")
            }
        }.getOrElse {
            Log.w(TAG, "request failed", it)
            text(Response.Status.INTERNAL_ERROR, "error")
        }
    }

    private fun page(): String = context.assets.open("web/index.html").bufferedReader().use { it.readText() }
        .replace("{{DEVICE}}", Build.MODEL.replace("_", " "))

    private fun state(): JSONObject {
        val list = JSONArray()
        val selected = Wallpapers.selectedId
        Wallpapers.items.forEach { w ->
            list.put(JSONObject().put("id", w.id).put("kind", w.kind.name.lowercase()).put("title", w.title).put("caption", w.caption ?: ""))
        }
        return JSONObject()
            .put("compactPreview", BuildConfig.COMPACT_PREVIEW)
            .put("selected", selected)
            .put("latestDaily", Wallpapers.LATEST_DAILY)
            .put("rotatePhotos", Wallpapers.ROTATE_PHOTOS)
            .put("screensaver", Wallpapers.screensaver.key)
            .put("screensaverFollows", Wallpapers.screensaverFollows)
            .put("videos", JSONArray().apply {
                ScreensaverVideos.all(context).forEach { put(JSONObject().put("id", it.id).put("name", it.name)) }
            })
            .put("focusStyle", AerialSettings.focusStyle.key)
            .put("wallpaperAlwaysMoves", AerialSettings.wallpaperAlwaysMoves)
            .put("showWeather", AerialSettings.showWeather)
            .put("screensaverClock", AerialSettings.screensaverClock)
            .put("wallpapers", list)
            .put("apps", JSONArray().apply {
                // Pinned inputs first, then apps by the name Home shows. [default] is the name a
                // blank one goes back to; [name] is the one typed on this page, if any.
                val apps = AppCatalog.loaded.map { it to (AppNames.of(it.key) ?: it.label) }.sortedBy { it.second }
                AppCatalog.pinnedSources.forEach { a ->
                    val src = a.source ?: return@forEach
                    // An input's label as Home will show it once it has arranged the new look.
                    val look = SourceLooks.of(src.id)
                    val label = look?.let { it.name ?: it.device?.let { d -> context.getString(d.label) } } ?: src.label
                    // The stamp makes the phone fetch the tile again once its picture changes.
                    put(JSONObject().put("key", a.key).put("label", label).put("default", src.label).put("custom", CustomIcons.has(a.key)).put("stamp", CustomIcons.stamp(a.key))
                        .put("source", true).put("port", src.label).put("name", look?.name ?: "").put("device", look?.device?.key ?: "").put("look", look.hashCode()))
                }
                apps.forEach { (a, label) ->
                    // [icon] is where the tile's picture comes from now; [library] whether the icon
                    // library has one for this app, which the phone can pick instead.
                    val icon = when {
                        CustomIcons.has(a.key) -> "custom"
                        IconLibrary.has(a.key) && IconLibrary.uses(a.key) -> "library"
                        else -> "own"
                    }
                    put(JSONObject().put("key", a.key).put("label", label).put("default", a.label).put("name", AppNames.of(a.key) ?: "")
                        .put("custom", CustomIcons.has(a.key)).put("library", IconLibrary.has(a.key)).put("icon", icon)
                        .put("stamp", CustomIcons.stamp(a.key) + IconLibrary.stamp(a.key) + icon.hashCode()))
                }
            })
            .put("layout", layoutState())
            .put("devices", JSONArray().apply { SourceDevice.entries.forEach { put(JSONObject().put("key", it.key).put("label", context.getString(it.label))) } })
            .put("inputs", JSONArray().apply {
                // Every input Home knows of, for pinning to Home from the phone.
                AppCatalog.sources.forEach { src ->
                    val look = SourceLooks.of(src.id)
                    put(JSONObject().put("id", src.id).put("port", src.label).put("device", src.deviceName ?: "")
                        .put("name", look?.let { it.name ?: it.device?.let { d -> context.getString(d.label) } } ?: "")
                        .put("pinned", src.id in AerialSettings.pinnedSources))
                }
            })
            .put("folders", JSONArray().apply {
                AppCatalog.folders.forEach { e ->
                    val f = e.folder ?: return@forEach
                    put(JSONObject().put("id", f.id).put("key", e.key).put("name", f.name).put("count", f.apps.size).put("stamp", e.art.hashCode()))
                }
            })
            .put("weather", JSONObject().apply {
                put("place", Weather.place ?: "")
                put("chosen", Weather.chosen)
                Weather.now?.let { put("celsius", it.celsius); put("kind", it.kind.name.lowercase()); put("day", it.day) }
            })
    }

    /** Home in order, each folder's apps and the hidden apps, by the names Home shows. */
    private fun layoutState(): JSONObject {
        fun tile(e: AppEntry) = JSONObject().put("key", e.key).put("label", e.label)
            .put("kind", when { e.folder != null -> "folder"; e.source != null -> "source"; else -> "app" })
            // Folder and input art changes without a picture from the phone (contents, chosen
            // device), an app's with the icon library too.
            .put("stamp", if (e.folder != null || e.source != null) e.art.hashCode().toLong() else CustomIcons.stamp(e.key) + IconLibrary.stamp(e.key))
            .apply { e.folder?.let { put("id", it.id) }; e.source?.let { put("port", it.label) } }
        val folders = JSONObject()
        AppCatalog.folderApps.forEach { (id, apps) -> folders.put(id, JSONArray().apply { apps.forEach { put(tile(it)) } }) }
        // With the parental PIN on, hidden apps stay hidden from here: on the TV, seeing them takes the PIN.
        val locked = AerialSettings.locked
        return JSONObject()
            .put("home", JSONArray().apply { AppCatalog.home.forEach { put(tile(it)) } })
            .put("folders", folders)
            .put("hidden", JSONArray().apply { if (!locked) AppCatalog.hiddenApps.forEach { put(tile(it)) } })
            .put("hiddenCount", AppCatalog.hiddenApps.size)
            .put("locked", locked)
            .put("ready", AppCatalog.layout != null)
    }

    /**
     * One step of arranging Home from the phone: [op] is move, movein, hide, unhide, folder (into
     * [folder], or a new one when it is empty), unfolder or ungroup. Done on the main thread before
     * answering, so the state sent back shows it.
     */
    private fun layout(f: Map<String, String>): Response {
        val ops = AppCatalog.layout ?: return json(state().put("saved", false))
        val key = f["key"].orEmpty()
        val target = f["target"].orEmpty()
        val folder = f["folder"].orEmpty()
        val known = (AppCatalog.home + AppCatalog.folderApps.values.flatten() + AppCatalog.hiddenApps).map { it.key }.toSet()
        val step: (() -> Unit)? = when (f["op"]) {
            "move" -> if (key in known && target in known) ({ ops.move(key, target) }) else null
            "movein" -> if (key in known && target in known) ({ ops.moveInFolder(key, target) }) else null
            "hide" -> if (key in known) ({ ops.hide(key) }) else null
            "unhide" -> if (key in known && !AerialSettings.locked) ({ ops.unhide(key) }) else null
            "folder" -> if (key in known && !key.startsWith(FOLDER_KEY_PREFIX)) ({ ops.toFolder(key, folder.ifEmpty { null }) }) else null
            "unfolder" -> if (key in known) ({ ops.outOfFolder(key) }) else null
            "ungroup" -> if (folder in AppCatalog.folderApps) ({ ops.ungroup(folder) }) else null
            else -> null
        }
        if (step != null) runBlocking(Dispatchers.Main) { step() }
        return json(state().put("saved", step != null))
    }

    /** An app on the TV or a pinned input: the only tiles a picture may be sent for. */
    /** An app, a firmware shortcut such as Xiaomi's children's mode (issue #530), or a pinned input. */
    private fun installed(pkg: String) =
        pkg.isNotEmpty() && (AppCatalog.loaded.any { it.key == pkg } || AppCatalog.pinnedSources.any { it.key == pkg })

    /** The tile as Home shows it; with [own], as the app's own art would draw it. */
    private fun tile(pkg: String, own: Boolean = false): Response {
        val app = (AppCatalog.pinnedSources + AppCatalog.loaded + AppCatalog.folders).firstOrNull { it.key == pkg } ?: return text(Response.Status.NOT_FOUND, "")
        val art = if (own && app.source == null && app.folder == null) AppCatalog.ownArt(context, app) ?: app.art else app.art
        val bmp = TileArtFactory.flatten(art)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
        bmp.recycle()
        val bytes = out.toByteArray()
        return newFixedLengthResponse(Response.Status.OK, "image/jpeg", ByteArrayInputStream(bytes), bytes.size.toLong()).apply {
            addHeader("Cache-Control", "max-age=600")
        }
    }

    /** The icon library's picture of an app, as downloaded. */
    private fun libraryPicture(pkg: String): Response {
        val f = IconLibrary.file(pkg) ?: return text(Response.Status.NOT_FOUND, "")
        val type = if (f.name.endsWith(".png")) "image/png" else "image/jpeg"
        return newFixedLengthResponse(Response.Status.OK, type, f.inputStream(), f.length()).apply {
            addHeader("Cache-Control", "max-age=600")
        }
    }

    private fun icon(session: IHTTPSession): Response {
        if (tooLarge(session)) return text(Response.Status.BAD_REQUEST, "too large")
        val files = HashMap<String, String>()
        session.parseBody(files)
        val pkg = session.parameters["app"]?.firstOrNull().orEmpty()
        if (!installed(pkg)) return text(Response.Status.BAD_REQUEST, "unknown app")
        val f = files.values.map(::File).firstOrNull { it.exists() && it.length() > 0 }
            ?: return text(Response.Status.BAD_REQUEST, "no picture")
        val saved = f.inputStream().use { CustomIcons.save(pkg, it, context.cacheDir) }
        if (saved) onMain { CustomIcons.changed() }
        return json(state().put("saved", saved))
    }

    /** A single app's icon; never an APK export or a read of that app's private data. */
    private fun nativeIcon(session: IHTTPSession): Response {
        // This endpoint accepts only a small form, never an uploaded file, path or remote URL.
        val type = session.headers["content-type"].orEmpty().substringBefore(';').trim()
        val length = session.headers["content-length"]?.toLongOrNull()
        if (!type.equals("application/x-www-form-urlencoded", ignoreCase = true) ||
            length == null || length !in 0L..4096L
        ) return text(Response.Status.BAD_REQUEST, "small form required")
        val fields = form(session)
        val pkg = fields["app"].orEmpty()
        if (!CustomIcons.isValidKey(pkg)) return text(Response.Status.BAD_REQUEST, "unknown app")
        val entry = AppCatalog.loaded.firstOrNull {
            it.packageName == pkg && it.component.packageName == pkg &&
                it.source == null && it.folder == null && it.shortcut == null
        } ?: return text(Response.Status.BAD_REQUEST, "unknown app")
        val apply = fields["apply"] == "1"
        if (fields["apply"] != null && fields["apply"] !in setOf("0", "1")) {
            return text(Response.Status.BAD_REQUEST, "invalid apply mode")
        }
        // Two approved public originals replace low-resolution installed icons in compact only.
        // These are byte-for-byte assets; the complete branding is drawn without alteration.
        val officialAsset = if (BuildConfig.COMPACT_PREVIEW) when (pkg) {
            "com.tencent.qqmusictv" -> "official-icons/qqmusic_appstore_512.jpg"
            "org.xbmc.kodi" -> "official-icons/kodi_official_1024.png"
            else -> null
        } else null
        val assetBitmap = officialAsset?.let { path ->
            runCatching { context.assets.open(path).use { BitmapFactory.decodeStream(it) } }.getOrNull()
                ?: return text(Response.Status.INTERNAL_ERROR, "official icon unavailable")
        }
        val pm = context.packageManager
        fun wideBanner(candidate: Drawable?): Drawable? = candidate?.takeIf {
            val bitmap = (it as? BitmapDrawable)?.bitmap
            val width = bitmap?.width ?: it.intrinsicWidth
            val height = bitmap?.height ?: it.intrinsicHeight
            // TV banners fit the whole card. Tolerate one source-pixel density rounding, never
            // accept a square icon masquerading as a banner, stretch it, or crop its branding.
            width > 0 && height > 0 && kotlin.math.abs(width.toLong() * 9 - height.toLong() * 16) <= 16L
        }
        var sourceRole = if (assetBitmap != null) "official_icon" else "native_icon"
        // Bilibili's installed banner is low resolution and includes an unwanted app caption;
        // its native 1024px icon is the complete, caption-free mark selected for compact.
        val banner = if (pkg in setOf("com.ktcp.launcher", "com.gitvvideo.skyworthopen")) {
            val appBanner = wideBanner(runCatching { pm.getApplicationBanner(pkg) }.getOrNull())
            if (appBanner != null) {
                sourceRole = "application_banner"
                appBanner
            } else {
                // Tencent's entry is HOME-only, so do not require a LEANBACK category here.
                wideBanner(runCatching { pm.getActivityBanner(entry.component) }.getOrNull())?.also {
                    sourceRole = "activity_banner"
                }
            }
        } else null
        val native = assetBitmap?.let { BitmapDrawable(context.resources, it) } ?: banner
            ?: runCatching { pm.getApplicationIcon(pkg) }.getOrNull()
            ?: return text(Response.Status.NOT_FOUND, "native icon unavailable")
        val sourceKind = if (assetBitmap != null) "official_asset" else "native"
        // A fresh drawable avoids changing the bounds/filtering of an icon another view uses.
        val drawable = (native.constantState?.newDrawable(context.resources) ?: native).mutate()
        val bitmapSource = (drawable as? BitmapDrawable)?.bitmap
        val sourceWidth = bitmapSource?.width
        val sourceHeight = bitmapSource?.height
        val iw = sourceWidth ?: drawable.intrinsicWidth.takeIf { it > 0 } ?: 1
        val ih = sourceHeight ?: drawable.intrinsicHeight.takeIf { it > 0 } ?: 1
        // Fill with the complete original at its largest fitting size: a 16:9 banner reaches
        // every edge; square official/native icons occupy the full height without distortion.
        // Use checked integer geometry, not a float Canvas transform. The target Android 9 box
        // produced an invalid near-zero width for QQ's 512px square on the V7 float path.
        val fitByWidth = iw.toLong() * 720L >= ih.toLong() * 1280L
        val drawnWidth = if (fitByWidth) 1280 else (iw.toLong() * 720L / ih).toInt().coerceAtLeast(1)
        val drawnHeight = if (fitByWidth) (ih.toLong() * 1280L / iw).toInt().coerceAtLeast(1) else 720
        check(drawnWidth in 1..1280 && drawnHeight in 1..720)
        val drawnLeft = (1280 - drawnWidth) / 2
        val drawnTop = (720 - drawnHeight) / 2
        val backgroundColor = when (pkg) {
            "com.xiaodianshi.tv.yst" -> Color.rgb(251, 114, 153)
            "com.gitvvideo.skyworthopen" -> Color.rgb(0, 190, 6)
            else -> Color.WHITE
        }
        fun edgeColor(left: Int, right: Int): Int {
            val leftAlpha = Color.alpha(left)
            val rightAlpha = Color.alpha(right)
            val weight = leftAlpha + rightAlpha
            if (weight == 0) return backgroundColor
            return Color.rgb(
                (Color.red(left) * leftAlpha + Color.red(right) * rightAlpha) / weight,
                (Color.green(left) * leftAlpha + Color.green(right) * rightAlpha) / weight,
                (Color.blue(left) * leftAlpha + Color.blue(right) * rightAlpha) / weight,
            )
        }
        // Extend only the icon's existing edge colors behind it. Its original pixels, including
        // the complete logo, are then drawn unchanged and uncropped over this native UI gradient.
        val gradientColors = if (pkg == "com.xiaodianshi.tv.yst" && bitmapSource != null) intArrayOf(
            edgeColor(bitmapSource.getPixel(0, 0), bitmapSource.getPixel(bitmapSource.width - 1, 0)),
            edgeColor(bitmapSource.getPixel(0, bitmapSource.height - 1), bitmapSource.getPixel(bitmapSource.width - 1, bitmapSource.height - 1)),
        ) else null
        val upscaled: Boolean? = if (sourceWidth != null && sourceHeight != null) {
            drawnWidth > sourceWidth || drawnHeight > sourceHeight
        } else null
        val sourceType = drawable.javaClass.simpleName.ifBlank { "Drawable" }
        val metadata = JSONObject()
            .put("sourceKind", sourceKind)
            .put("sourceRole", sourceRole)
            .put("sourceAsset", officialAsset ?: JSONObject.NULL)
            .put("sourceType", sourceType)
            .put("sourceWidth", sourceWidth ?: JSONObject.NULL)
            .put("sourceHeight", sourceHeight ?: JSONObject.NULL)
            .put("upscaledInMaster", upscaled ?: JSONObject.NULL)
            .put("outputWidth", 1280)
            .put("outputHeight", 720)
            .put("drawnWidth", drawnWidth)
            .put("drawnHeight", drawnHeight)
            .put("drawnLeft", drawnLeft)
            .put("drawnTop", drawnTop)
            .put("backgroundKind", if (gradientColors != null) "source_edge_gradient" else "solid")
        val oldBounds = Rect(drawable.bounds)
        val oldFiltering = (drawable as? BitmapDrawable)?.paint?.isFilterBitmap
        val master = createBitmap(1280, 720)
        val bytes = try {
            if (drawable is BitmapDrawable) drawable.paint.isFilterBitmap = true
            Canvas(master).apply {
                drawColor(backgroundColor)
                if (gradientColors != null) {
                    val backgroundPaint = Paint().apply {
                        shader = LinearGradient(0f, 0f, 0f, 720f, gradientColors[0], gradientColors[1], Shader.TileMode.CLAMP)
                    }
                    drawRect(0f, 0f, 1280f, 720f, backgroundPaint)
                }
                drawable.setBounds(drawnLeft, drawnTop, drawnLeft + drawnWidth, drawnTop + drawnHeight)
                // Keep the complete native mark, its colors/background and its aspect ratio.
                drawable.draw(this)
            }
            ByteArrayOutputStream().use { output ->
                check(master.compress(Bitmap.CompressFormat.PNG, 100, output))
                output.toByteArray()
            }
        } finally {
            drawable.bounds = oldBounds
            if (drawable is BitmapDrawable && oldFiltering != null) drawable.paint.isFilterBitmap = oldFiltering
            master.recycle()
            assetBitmap?.recycle()
        }
        if (apply) {
            val saved = ByteArrayInputStream(bytes).use { CustomIcons.save(pkg, it, context.cacheDir) }
            if (saved) onMain { CustomIcons.changed() }
            return json(JSONObject().put("app", pkg).put("saved", saved).put("nativeIcon", metadata))
        }
        // Output dimensions describe the card, not the source's detail. Mixed/adaptive drawables
        // have no single proven raster resolution, so they are reported as unknown, never "HD".
        return newFixedLengthResponse(Response.Status.OK, "image/png", ByteArrayInputStream(bytes), bytes.size.toLong()).apply {
            addHeader("Cache-Control", "no-store")
            addHeader("X-Icon-Source-Kind", sourceKind)
            addHeader("X-Icon-Source-Role", sourceRole)
            officialAsset?.let { addHeader("X-Icon-Source-Asset", it) }
            addHeader("X-Icon-Source-Type", sourceType)
            addHeader("X-Icon-Source-Width", sourceWidth?.toString() ?: "unknown")
            addHeader("X-Icon-Source-Height", sourceHeight?.toString() ?: "unknown")
            addHeader("X-Icon-Master-Upscaled", upscaled?.toString() ?: "unknown")
            addHeader("X-Icon-Output-Size", "1280x720")
            addHeader("X-Icon-Drawn-Size", "${drawnWidth}x${drawnHeight}")
            addHeader("X-Icon-Drawn-Origin", "${drawnLeft},${drawnTop}")
            addHeader("X-Icon-Background-Kind", if (gradientColors != null) "source_edge_gradient" else "solid")
        }
    }

    private fun thumb(id: String): Response {
        val bmp = Wallpapers.thumbnail(java.net.URLDecoder.decode(id, "UTF-8")) ?: return text(Response.Status.NOT_FOUND, "")
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 82, out)
        val bytes = out.toByteArray()
        return newFixedLengthResponse(Response.Status.OK, "image/jpeg", ByteArrayInputStream(bytes), bytes.size.toLong()).apply {
            addHeader("Cache-Control", "max-age=600")
        }
    }

    private fun upload(session: IHTTPSession): Response {
        if (tooLarge(session)) return text(Response.Status.BAD_REQUEST, "too large")
        val files = HashMap<String, String>()
        session.parseBody(files)
        var added = 0
        // NanoHTTPD stores each part in a temp file; several photos arrive as photo, photo1, photo2...
        files.values.forEach { path ->
            val f = File(path)
            if (f.exists() && f.length() > 0) {
                val id = f.inputStream().use { input -> runBlocking(Dispatchers.Main) { Wallpapers.addPhoto(input) } }
                if (id != null) added++
            }
        }
        return json(state().put("added", added))
    }

    /** One film per request: a batch never needs twice its total size free on the TV. */
    private fun video(session: IHTTPSession): Response {
        val length = session.headers["content-length"]?.toLongOrNull() ?: 0L
        // Include room for multipart headers, but do not accept an unbounded chunked body.
        if (length <= 0 || length > ScreensaverVideos.MAX_FILE_BYTES + 1024 * 1024) return text(Response.Status.BAD_REQUEST, "too large")
        val files = HashMap<String, String>()
        session.parseBody(files)
        val path = files["video"] ?: return text(Response.Status.BAD_REQUEST, "no video")
        val name = session.parameters["video"]?.firstOrNull().orEmpty()
        val saved = ScreensaverVideos.add(context, File(path), name) != null
        if (saved) onMain { Wallpapers.videosChanged() }
        return json(state().put("saved", saved))
    }

    // NanoHTTPD writes the whole body to the cache before we see it; keep a stray or hostile
    // upload from filling the TV's storage (#57).
    private fun tooLarge(session: IHTTPSession): Boolean =
        (session.headers["content-length"]?.toLongOrNull() ?: 0L) > MAX_UPLOAD

    private fun form(session: IHTTPSession): Map<String, String> {
        session.parseBody(HashMap())
        return session.parameters.mapValues { it.value.firstOrNull().orEmpty() }
    }

    private fun onMain(block: () -> Unit) = runBlocking(Dispatchers.Main) { block() }

    private fun json(o: JSONObject) = newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", o.toString())

    private fun text(status: Response.Status, msg: String) = newFixedLengthResponse(status, "text/plain; charset=utf-8", msg)

    companion object {
        const val PORT = BuildConfig.PHONE_PORT
        private const val TAG = "AerialPhone"
        /** Enough for a batch of phone photos at full size. */
        private const val MAX_UPLOAD = 200L * 1024 * 1024
        /** As for an input's name: Home's caption has room for about this much. */
        private const val FOLDER_NAME_MAX = 24

        @Volatile
        private var running: PhoneServer? = null

        /**
         * Listens for as long as Aerial runs, from Home's start (MainActivity.startWork), so a
         * computer can open the page at any time without Personalize open on the TV. On every
         * interface: the address the TV has may change, the page stays the same.
         */
        @Synchronized
        fun listen(context: Context): PhoneServer? {
            running?.let { if (it.isAlive) return it }
            return runCatching {
                PhoneServer(context.applicationContext, token(context)).also {
                    it.start(SOCKET_READ_TIMEOUT, true)
                    running = it
                }
            }.onFailure { Log.w(TAG, "could not listen", it) }.getOrNull()
        }

        /** The address to put in the QR code, listening first if need be, or null offline. */
        fun start(context: Context): String? {
            val ip = lanAddress(context) ?: return null
            val server = listen(context) ?: return null
            return "http://$ip:$PORT/${server.token}/"
        }

        /**
         * Kept from one start to the next, so the address can be bookmarked on a computer and the
         * phone's tab still works the next day.
         */
        private fun token(context: Context): String {
            val prefs = context.getSharedPreferences("phone_page", Context.MODE_PRIVATE)
            prefs.getString("token", null)?.takeIf { it.length == 8 }?.let { return it }
            return newToken().also { prefs.edit().putString("token", it).apply() }
        }

        private fun newToken(): String {
            val alphabet = "abcdefghjkmnpqrstuvwxyz23456789"
            val rnd = SecureRandom()
            return (1..8).map { alphabet[rnd.nextInt(alphabet.length)] }.joinToString("")
        }

        fun lanAddress(context: Context): String? {
            val cm = context.getSystemService<ConnectivityManager>() ?: return null
            val current = cm.currentNetwork()
            // Under a proxy app's VPN the default network is the tunnel, whose address the phone
            // cannot reach (Clash and the like use 198.18.0.1; Xiaomi S55, issue #667). The Wi-Fi
            // or Ethernet network under it is still in allNetworks: take its address first.
            val networks = runCatching { cm.allNetworks.toList() }.getOrDefault(emptyList())
            val lan = networks.filter { n ->
                val caps = runCatching { cm.getNetworkCapabilities(n) }.getOrNull() ?: return@filter false
                !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                    (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
            }.sortedByDescending { it == current }
            return (lan + listOfNotNull(current)).firstNotNullOfOrNull { n ->
                runCatching { cm.getLinkProperties(n) }.getOrNull()?.linkAddresses?.map { it.address }
                    ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress && !it.isLinkLocalAddress && !isBenchmark(it) }
            }?.hostAddress
        }

        /** 198.18.0.0/15, the range proxy apps put their tunnel in; never a home network. */
        private fun isBenchmark(address: java.net.InetAddress): Boolean {
            val b = address.address
            return b.size == 4 && (b[0].toInt() and 0xff) == 198 && (b[1].toInt() and 0xfe) == 18
        }

        /** A QR code, dark modules on white, each module [scale] pixels square. */
        fun qr(text: String, scale: Int = 8): Bitmap {
            val matrix = QRCodeWriter().encode(
                text, BarcodeFormat.QR_CODE, 0, 0,
                mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M),
            )
            val n = matrix.width
            val bmp = createBitmap(n * scale, n * scale)
            for (y in 0 until n) for (x in 0 until n) {
                if (matrix[x, y]) for (dy in 0 until scale) for (dx in 0 until scale) bmp.setPixel(x * scale + dx, y * scale + dy, Color.BLACK)
                else for (dy in 0 until scale) for (dx in 0 until scale) bmp.setPixel(x * scale + dx, y * scale + dy, Color.WHITE)
            }
            return bmp
        }
    }
}

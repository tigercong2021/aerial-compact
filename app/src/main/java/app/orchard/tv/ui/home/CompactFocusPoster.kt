package app.orchard.tv.ui.home

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import app.orchard.tv.BuildConfig
import app.orchard.tv.ui.theme.LocalReducedMotion
import app.orchard.tv.ui.theme.Motion
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.roundToInt

/** A local editorial snapshot over the owner's wallpaper; never a viewing-history feed. */
@Composable
internal fun CompactFocusPoster(packageName: String?, fallback: @Composable () -> Unit = {}) {
    if (!BuildConfig.COMPACT_PREVIEW) {
        fallback()
        return
    }
    val assets = LocalContext.current.applicationContext.assets
    val store = remember(assets) { CompactPosterStore(assets) }
    var screen by remember { mutableStateOf(IntSize.Zero) }
    val key = posterKey(packageName, screen)
    val latestKey by rememberUpdatedState(key)
    val duration by rememberUpdatedState(if (LocalReducedMotion.current) 0 else POSTER_FADE_MS)
    val requests = remember { Channel<PosterRequest>(Channel.CONFLATED) }
    var top by remember { mutableStateOf<ImageBitmap?>(null) }
    var bottom by remember { mutableStateOf<ImageBitmap?>(null) }
    var show by remember { mutableStateOf(false) }
    val mix = remember { Animatable(1f) }
    val opacity = remember { Animatable(0f) }

    // Changing focus cancels the dwell and the IO result. A blocking decode may finish on its
    // worker, but withContext cannot deliver that old result to a cancelled focus request.
    LaunchedEffect(key) {
        if (key == null) {
            show = false
            return@LaunchedEffect
        }
        delay(POSTER_DWELL_MS)
        val image = store.load(key)
        if (image == null) show = false else requests.send(PosterRequest(key, image))
    }
    LaunchedEffect(show, key != null) {
        opacity.animateTo(if (show && key != null) 1f else 0f, tween(duration, easing = Motion.EaseOut))
    }
    // Finish each two-picture blend without dropping a visible layer. Only the latest waiting
    // request is retained, and stale focus requests never start another transition.
    LaunchedEffect(store) {
        for (request in requests) {
            if (request.key != latestKey) continue
            if (request.image === top) {
                show = true
                continue
            }
            if (top == null || opacity.value < 0.001f) {
                bottom = null
                mix.snapTo(1f)
                if (request.key != latestKey) continue
                top = request.image
                show = true
                continue
            }
            bottom = top
            mix.snapTo(0f)
            if (request.key != latestKey) {
                bottom = null
                mix.snapTo(1f)
                continue
            }
            top = request.image
            show = true
            mix.animateTo(1f, tween(duration, easing = Motion.EaseOut))
            bottom = null
        }
    }

    // Do not keep painting a full-screen wallpaper underneath an opaque, settled poster.
    // This derived boolean changes only at the ends of fades, not on every animation frame.
    val needsFallback by remember { derivedStateOf { top == null || opacity.value < 0.9999f } }
    if (needsFallback) fallback()
    Canvas(Modifier.fillMaxSize().onSizeChanged { screen = it }) {
        val front = top ?: return@Canvas
        val alpha = opacity.value.coerceIn(0f, 1f)
        if (alpha < 0.001f) return@Canvas
        val under = bottom
        if (under == null) {
            drawPoster(front, alpha)
        } else {
            val t = mix.value.coerceIn(0f, 1f)
            val frontAlpha = alpha * t
            // Source-over correction keeps total coverage at alpha while both images fade
            // back to the wallpaper; no extra offscreen buffer or captured composite is needed.
            val underAlpha = if (frontAlpha < 0.9999f) alpha * (1f - t) / (1f - frontAlpha) else 0f
            if (underAlpha > 0.001f) drawPoster(under, underAlpha)
            if (frontAlpha > 0.001f) drawPoster(front, frontAlpha)
        }
    }
}

private const val POSTER_DWELL_MS = 180L
private const val POSTER_FADE_MS = 360
private const val POSTER_MAX_WIDTH = 1920
private const val POSTER_MAX_HEIGHT = 1080
private const val POSTER_ASSETS = "preview-posters"
private val posterPackages = setOf("com.ktcp.launcher", "com.gitvvideo.skyworthopen", "com.xiaodianshi.tv.yst")
private val posterFileName = Regex("[A-Za-z0-9][A-Za-z0-9._-]*\\.(jpg|jpeg|webp|png)", RegexOption.IGNORE_CASE)

private data class PosterKey(val packageName: String, val width: Int, val height: Int)
private data class PosterRequest(val key: PosterKey, val image: ImageBitmap)

private fun posterKey(packageName: String?, screen: IntSize): PosterKey? {
    if (packageName !in posterPackages || screen.width <= 0 || screen.height <= 0) return null
    val scale = minOf(1f, POSTER_MAX_WIDTH.toFloat() / screen.width, POSTER_MAX_HEIGHT.toFloat() / screen.height)
    return PosterKey(packageName!!, (screen.width * scale).roundToInt().coerceAtLeast(1), (screen.height * scale).roundToInt().coerceAtLeast(1))
}

/** Centre crop also during a size change, before the new size has finished decoding. */
private fun DrawScope.drawPoster(image: ImageBitmap, alpha: Float) {
    if (size.width <= 0f || size.height <= 0f) return
    val crop = posterCrop(image.width, image.height, size.width / size.height)
    drawImage(
        image,
        srcOffset = IntOffset(crop.left, crop.top),
        srcSize = IntSize(crop.width(), crop.height()),
        dstSize = IntSize(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1)),
        alpha = alpha,
        filterQuality = FilterQuality.Low,
    )
}

private fun posterCrop(width: Int, height: Int, ratio: Float): Rect {
    val w = minOf(width, (height * ratio).roundToInt().coerceAtLeast(1))
    val h = minOf(height, (width / ratio).roundToInt().coerceAtLeast(1))
    val left = (width - w) / 2
    val top = (height - h) / 2
    return Rect(left, top, left + w, top + h)
}

/** At most one cached image per allowed app. Published bitmaps are never manually recycled. */
private class CompactPosterStore(private val assets: AssetManager) {
    private val mutex = Mutex()
    private var catalog: Map<String, String>? = null
    private val cache = HashMap<String, PosterRequest>()

    suspend fun load(key: PosterKey): ImageBitmap? = withContext(Dispatchers.IO) {
        mutex.withLock {
            cache[key.packageName]?.takeIf { it.key == key }?.image ?: try {
                val files = catalog ?: readCatalog().also { catalog = it }
                val file = files[key.packageName] ?: return@withLock null
                decode(file, key)?.also { cache[key.packageName] = PosterRequest(key, it) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            } catch (_: OutOfMemoryError) {
                // Dropping cache references is safe even if Compose still holds a displayed image.
                cache.clear()
                null
            }
        }
    }

    private fun readCatalog(): Map<String, String> {
        val json = assets.open("$POSTER_ASSETS/catalog.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val apps = JSONObject(json).optJSONObject("apps") ?: return emptyMap()
        return posterPackages.mapNotNull { pkg ->
            val item = apps.optJSONObject(pkg) ?: return@mapNotNull null
            val file = item.optString("file")
            val source = runCatching { URI(item.optString("source")) }.getOrNull()
            if (!posterFileName.matches(file) || item.optString("title").isBlank() ||
                source?.scheme != "https" || source?.host.isNullOrBlank()) null else pkg to file
        }.toMap()
    }

    private fun decode(file: String, key: PosterKey): ImageBitmap? {
        val path = "$POSTER_ASSETS/$file"
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true; inScaled = false }
        assets.open(path).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while ((bounds.outWidth.toLong() + sample - 1) / sample > key.width ||
            (bounds.outHeight.toLong() + sample - 1) / sample > key.height) {
            if (sample >= 1 shl 29) return null
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inMutable = true
            inScaled = false
        }
        val decoded = assets.open(path).use { BitmapFactory.decodeStream(it, null, options) } ?: return null
        var display: Bitmap? = null
        try {
            val bitmap = if (decoded.width == key.width && decoded.height == key.height && !decoded.hasAlpha()) decoded else {
                Bitmap.createBitmap(key.width, key.height, Bitmap.Config.ARGB_8888).also { out ->
                    android.graphics.Canvas(out).apply {
                        drawColor(android.graphics.Color.BLACK)
                        drawBitmap(
                            decoded, posterCrop(decoded.width, decoded.height, key.width.toFloat() / key.height),
                            Rect(0, 0, key.width, key.height), Paint(Paint.FILTER_BITMAP_FLAG),
                        )
                    }
                }
            }
            display = bitmap
            // Bake only the display copy: one full-screen draw in the settled state, no live
            // gradient layer, and the bundled original and owner's wallpaper stay untouched.
            android.graphics.Canvas(bitmap).drawRect(0f, key.height * 0.62f, key.width.toFloat(), key.height.toFloat(), Paint().apply {
                shader = LinearGradient(0f, key.height * 0.62f, 0f, key.height.toFloat(),
                    android.graphics.Color.TRANSPARENT, android.graphics.Color.argb(158, 0, 0, 0), Shader.TileMode.CLAMP)
            })
            bitmap.prepareToDraw()
            return bitmap.asImageBitmap()
        } finally {
            // This temporary decode has never been published to Compose or the cache.
            if (display !== decoded) decoded.recycle()
        }
    }
}

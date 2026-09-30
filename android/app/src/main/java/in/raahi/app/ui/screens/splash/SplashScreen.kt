package `in`.raahi.app.ui.screens.splash

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import `in`.raahi.app.R
import `in`.raahi.app.ui.theme.RaahiAmber
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Raahi launch splash.
 *
 * Everything is drawn by ONE Canvas on top of a near-black backdrop:
 *   - backdrop      : analytic radial glow (vector, 0 KB) — fitted to the reference artwork
 *   - splash_art    : 21 KB WebP holding ONLY the bright parts of the artwork (silver R, road
 *                     light, faint hills) as a residual, added onto the backdrop (BlendMode.Plus).
 *                     Its dark areas are black = "add nothing", so there is no visible image
 *                     box/container and no banding from a baked-in dark gradient.
 *   - route light   : one soft dot travelling along a vector path that follows the road line
 *   - wordmark      : RAAHI as vector paths (no font download needed, renders on frame 1)
 *   - tagline       : small tracked-out Text, system sans-serif (no downloadable font)
 *   - signal line   : 1.5dp nav/energy line with a warm segment sweeping left -> right
 *
 * No shader, particles, Lottie or new dependency. All timing is one Animatable (0..1 over
 * ~1s) plus one looping sweep that lives only while this composable is in composition.
 */

// ── Layout, in "art units": 1 unit = 1/941 of the layout width (the reference's pixel grid) ──
private const val ART_W = 941f
private const val ART_H = 600f            // rows 320..920 of the reference
private const val WORD_GAP = 15f
private const val WORD_W = 435f
private const val WORD_H = 64f
private const val TAG_GAP = 33f
private const val TAG_FONT = 22f          // tagline font size, art units
private const val TAG_LINE = 26f
private const val GROUP_H = ART_H + WORD_GAP + WORD_H + TAG_GAP + TAG_LINE
private const val OPTICAL_CENTER = 0.40f  // group sits slightly above true centre, like the reference
private const val MAX_GROUP_FRACTION = 0.92f
private val SYMBOL_PIVOT = Offset(490f, 455f) // centre of the R inside the art crop

// Backdrop glow fitted to the reference (values in reference pixels; least-squares RMS ≈ 1.5/255).
private const val BG_CENTER_Y = 349f - 320f   // relative to the art crop's top row
private const val BG_SIGMA_X = 365f
private const val BG_SIGMA_Y = 1132f
private val BG_PEAK = floatArrayOf(13.13f, 17.66f, 22.79f)

// ── Timing (ms) ──
private const val TOTAL_MS = 1000
private const val LINE_START_MS = 500L
private const val LINE_SWEEP_MS = 520
private const val REDUCED_MOTION_HOLD_MS = 600L

private val TaglineColor = Color(0xFF9AA3B0)
private val LineBaseColor = Color(0xFF9AA4B2)
private val LINE_WIDTH = 96.dp
private val LINE_HEIGHT = 1.5.dp
private val LINE_BOTTOM = 44.dp

private fun ease(x: Float) = FastOutSlowInEasing.transform(x.coerceIn(0f, 1f))

@Composable
fun RaahiSplashScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    // System "remove animations" (animator scale 0): show the finished, static splash briefly.
    val reduceMotion = remember { animationsDisabled(context) }
    val progress = remember { Animatable(if (reduceMotion) 1f else 0f) }
    val sweep = remember { Animatable(0f) }
    val finish by rememberUpdatedState(onFinished)

    // One-shot timeline. Cancelled automatically if the splash leaves composition.
    LaunchedEffect(Unit) {
        if (reduceMotion) delay(REDUCED_MOTION_HOLD_MS)
        else progress.animateTo(1f, tween(TOTAL_MS, easing = LinearEasing))
        finish()
    }
    // Signal line: repeats only while the splash is alive (loop is cancelled with the effect).
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) return@LaunchedEffect
        delay(LINE_START_MS)
        while (isActive) {
            sweep.snapTo(0f)
            sweep.animateTo(1f, tween(LINE_SWEEP_MS, easing = FastOutSlowInEasing))
        }
    }

    SplashSystemBars()

    val art: ImageBitmap = ImageBitmap.imageResource(R.drawable.splash_art)
    val roadMeasure = remember { PathMeasure().also { it.setPath(buildRoadPath(), false) } }
    val wordmark = remember { Wordmark() }
    val wordmarkBrush = remember {
        Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFC3CBD4)), startY = 0f, endY = WORD_H)
    }
    val density = LocalDensity.current
    val amber = RaahiAmber

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()
        val g = remember(wPx, hPx) { Geometry(wPx, hPx) }
        val backdrop = remember(g) { buildBackdropBrush(g) }

        Canvas(Modifier.fillMaxSize()) {
            val p = progress.value

            // Backdrop glow (vector).
            withTransform({ scale(1f, BG_SIGMA_Y / BG_SIGMA_X, Offset(g.bgCx, g.bgCy)) }) {
                drawCircle(
                    brush = backdrop, radius = g.bgRadius,
                    center = Offset(g.bgCx, g.bgCy), blendMode = BlendMode.Plus,
                )
            }

            withTransform({ translate(g.left, g.top); scale(g.s, g.s, Offset.Zero) }) {
                // 150–450 ms: symbol + road scene fade in, 0.98 -> 1.0 scale.
                val symbol = ease((p - 0.15f) / 0.30f)
                if (symbol > 0f) {
                    val sc = 0.98f + 0.02f * symbol
                    withTransform({ scale(sc, sc, SYMBOL_PIVOT) }) {
                        drawImage(
                            image = art, dstSize = IntSize(ART_W.toInt(), ART_H.toInt()),
                            alpha = symbol, blendMode = BlendMode.Plus, filterQuality = FilterQuality.Medium,
                        )
                    }
                }
                // 300–650 ms: a tiny light travels along the road line.
                drawRouteLight((p - 0.30f) / 0.35f, roadMeasure, amber)
                // 450–750 ms: wordmark.
                val word = ease((p - 0.45f) / 0.30f)
                if (word > 0f) {
                    withTransform({ translate((ART_W - WORD_W) / 2f, ART_H + WORD_GAP) }) {
                        drawPath(
                            wordmark.strokes, wordmarkBrush, alpha = word,
                            style = Stroke(width = Wordmark.STROKE, cap = StrokeCap.Butt, join = StrokeJoin.Miter),
                        )
                        drawPath(wordmark.fills, wordmarkBrush, alpha = word)
                        drawRect(amber, Offset(Wordmark.I_X, 0f), Size(Wordmark.I_W, Wordmark.I_ACCENT_H), alpha = word)
                    }
                }
            }
        }

        // 600–900 ms: tagline. Sized in px from the layout width, so it is immune to the user's font scale.
        val taglinePx = TAG_FONT * g.s
        Text(
            text = "MOVE. CONNECT. RESOLVE.",
            style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Light,
                fontSize = with(density) { taglinePx.toSp() },
                letterSpacing = 0.22.em,
            ),
            color = TaglineColor,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .align(Alignment.TopCenter)
                // + half the letter-spacing: tracking adds trailing space that would push the visible text off-centre.
                .offset { IntOffset((taglinePx * 0.11f).roundToInt(), g.taglineTop.roundToInt()) }
                .graphicsLayer { alpha = ease((progress.value - 0.60f) / 0.30f) * 0.72f },
        )

        SignalLine(
            progress = progress, sweep = sweep, reduceMotion = reduceMotion, glow = amber,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = LINE_BOTTOM),
        )
    }
}

/** All splash positions derive from the layout size — nothing is a fixed pixel coordinate. */
private class Geometry(w: Float, h: Float) {
    val s = min(w / ART_W, h * MAX_GROUP_FRACTION / GROUP_H)
    val left = (w - ART_W * s) / 2f
    val top = (h - GROUP_H * s) * OPTICAL_CENTER
    val taglineTop = top + (ART_H + WORD_GAP + WORD_H + TAG_GAP) * s
    val bgCx = w / 2f
    val bgCy = top + BG_CENTER_Y * s
    val bgRadius = 2.5f * BG_SIGMA_X * s
}

/** Gaussian glow as radial-gradient stops; drawn additively over black so it reproduces the reference exactly. */
private fun buildBackdropBrush(g: Geometry): Brush {
    val radii = floatArrayOf(0f, 0.35f, 0.7f, 1.05f, 1.4f, 1.75f, 2.1f, 2.5f)
    val stops = Array(radii.size) { i ->
        val k = exp(-radii[i] * radii[i])
        radii[i] / 2.5f to Color(BG_PEAK[0] * k / 255f, BG_PEAK[1] * k / 255f, BG_PEAK[2] * k / 255f)
    }
    return Brush.radialGradient(*stops, center = Offset(g.bgCx, g.bgCy), radius = g.bgRadius)
}

/** [q] runs 0..1 over the light's journey; nothing is drawn outside that range. */
private fun DrawScope.drawRouteLight(q: Float, road: PathMeasure, amber: Color) {
    if (q <= 0f || q >= 1f) return
    val env = sin(PI.toFloat() * q)                       // soft fade in and out
    val len = road.length
    for (k in 4 downTo 1) {                               // short, faint trail
        val pos = road.getPosition(len * ease(q - k * 0.03f))
        drawCircle(amber, radius = 7f - k, center = pos, alpha = env * 0.10f * (5 - k), blendMode = BlendMode.Plus)
    }
    val head = road.getPosition(len * ease(q))
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color.White.copy(alpha = 0.95f), 0.25f to amber.copy(alpha = 0.6f), 1f to Color.Transparent,
            center = head, radius = 24f,
        ),
        radius = 24f, center = head, alpha = env, blendMode = BlendMode.Plus,
    )
}

/** Road line of the artwork (art units), traced from the reference; Catmull-Rom -> cubic Béziers. */
private fun buildRoadPath(): Path {
    val pts = listOf(
        Offset(130f, 392f), Offset(240f, 333f), Offset(320f, 308f), Offset(400f, 291f), Offset(480f, 277f),
        Offset(560f, 265f), Offset(640f, 251f), Offset(720f, 232f), Offset(752f, 218f),
    )
    val path = Path().apply { moveTo(pts.first().x, pts.first().y) }
    for (i in 0 until pts.size - 1) {
        val p0 = pts[maxOf(i - 1, 0)]; val p1 = pts[i]; val p2 = pts[i + 1]; val p3 = pts[minOf(i + 2, pts.size - 1)]
        path.cubicTo(
            p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f,
            p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f,
            p2.x, p2.y,
        )
    }
    return path
}

/** RAAHI drawn as geometric vector letters (reference: R, two chevron A's without a bar, H, I with a warm tip). */
private class Wordmark {
    val strokes = Path().apply {                                   // R: stem + bowl, as a 11-unit stroke
        moveTo(5.5f, 64f); lineTo(5.5f, 5.5f); lineTo(48f, 5.5f)
        quadraticBezierTo(60.5f, 5.5f, 60.5f, 17f); lineTo(60.5f, 26f)
        quadraticBezierTo(60.5f, 37.5f, 49f, 37.5f); lineTo(5.5f, 37.5f)
    }
    val fills = Path().apply {
        polygon(0f, 26f to 37f, 39f to 37f, 66f to 64f, 52f to 64f)             // R leg
        chevron(104f); chevron(215f)                                            // A A
        addRect(androidx.compose.ui.geometry.Rect(326f, 0f, 337f, 64f))         // H
        addRect(androidx.compose.ui.geometry.Rect(379f, 0f, 390f, 64f))
        addRect(androidx.compose.ui.geometry.Rect(326f, 27f, 390f, 37f))
        addRect(androidx.compose.ui.geometry.Rect(I_X, I_ACCENT_H - 1f, I_X + I_W, 64f)) // I (below the warm tip)
    }

    private fun Path.chevron(x: Float) =
        polygon(x, 0f to 64f, 34f to 0f, 46f to 0f, 80f to 64f, 67f to 64f, 40f to 13.2f, 13f to 64f)

    private fun Path.polygon(dx: Float, vararg pts: Pair<Float, Float>) {
        moveTo(dx + pts[0].first, pts[0].second)
        for (i in 1 until pts.size) lineTo(dx + pts[i].first, pts[i].second)
        close()
    }

    companion object {
        const val STROKE = 11f
        const val I_X = 423f
        const val I_W = 12f
        const val I_ACCENT_H = 14f
    }
}

/** A 1.5dp navigation/energy line: dark base + a warm segment sweeping left -> right, then fading and resetting. */
@Composable
private fun SignalLine(
    progress: Animatable<Float, AnimationVector1D>,
    sweep: Animatable<Float, AnimationVector1D>,
    reduceMotion: Boolean,
    glow: Color,
    modifier: Modifier,
) {
    Canvas(modifier.size(LINE_WIDTH, LINE_HEIGHT)) {
        val visible = if (reduceMotion) 1f else ease((progress.value - 0.45f) / 0.20f)
        if (visible <= 0f) return@Canvas
        drawRoundRect(
            color = LineBaseColor.copy(alpha = 0.14f * visible),
            cornerRadius = CornerRadius(size.height / 2f),
        )
        val seg = size.width * 0.36f
        val f = if (reduceMotion) 0.5f else sweep.value
        val x = -seg + (size.width + seg) * f
        val env = sin(PI.toFloat() * f) * visible              // 0 at both ends -> the light fades in/out
        clipRect {
            drawRect(
                brush = Brush.horizontalGradient(listOf(Color.Transparent, glow, Color.Transparent), startX = x, endX = x + seg),
                topLeft = Offset(x, 0f), size = Size(seg, size.height), alpha = env * 0.9f,
            )
        }
    }
}

/** Tints the status/navigation bars to the splash colour while it is on screen, then restores them. */
@Composable
private fun SplashSystemBars() {
    val window = LocalContext.current.findActivity()?.window ?: return
    val splashBar = Color(0xFF020407).toArgb()
    DisposableEffect(window) {
        val prevStatus = window.statusBarColor
        val prevNav = window.navigationBarColor
        window.statusBarColor = splashBar
        window.navigationBarColor = splashBar
        onDispose {
            window.statusBarColor = prevStatus
            window.navigationBarColor = prevNav
        }
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

private fun animationsDisabled(context: Context): Boolean =
    runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)

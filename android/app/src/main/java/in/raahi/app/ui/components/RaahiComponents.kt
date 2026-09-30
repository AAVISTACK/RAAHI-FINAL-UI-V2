package `in`.raahi.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import `in`.raahi.app.ui.theme.*

/**
 * Shared building blocks for the "concept redesign" visual language (ported from the
 * approved HTML mockup) — glass cards, the circular Car Health score ring, the scrolling
 * info ticker, and section labels/chips reused across every re-skinned screen. Keeping these
 * in one place is what makes the whole app restyle consistently instead of five screens each
 * reinventing "what a card looks like."
 */

// ---------------------------------------------------------------------- Glass surfaces ---

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RaahiShapeLarge,
    background: Color = RaahiGlass,
    borderColor: Color = RaahiBorderSoft,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val clickMod = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Column(
        modifier
            .background(background, shape)
            .border(1.dp, borderColor, shape)
            .then(clickMod)
    ) { content() }
}

@Composable
fun RowCard(
    modifier: Modifier = Modifier,
    urgent: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val bg = if (urgent) RaahiRed.copy(alpha = 0.08f) else RaahiGlass
    val border = if (urgent) RaahiRed.copy(alpha = 0.25f) else RaahiBorderSoft
    val clickMod = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(
        modifier
            .fillMaxWidth()
            .background(bg, RaahiShapeMedium)
            .border(1.dp, border, RaahiShapeMedium)
            .then(clickMod)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
fun IconBadge(icon: ImageVector, tint: Color, size: Dp = 32.dp, shape: androidx.compose.ui.graphics.Shape = RaahiShapeSmall) {
    Box(
        modifier = Modifier.size(size).background(tint.copy(alpha = 0.16f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun SectionLabel(text: String, trailing: String? = null, onTrailingClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = RaahiTextFaint, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        if (trailing != null) {
            Text(
                trailing, color = RaahiOrange, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = if (onTrailingClick != null) Modifier.clickable(onClick = onTrailingClick) else Modifier,
            )
        }
    }
}

@Composable
fun RaahiChip(label: String, active: Boolean, onClick: () -> Unit, icon: ImageVector? = null) {
    val bg = if (active) RaahiBrandGradient else Brush.linearGradient(listOf(RaahiGlassStrong, RaahiGlassStrong))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(bg, RaahiShapePill)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (active) Color.White else RaahiTextDim, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(label, color = if (active) Color.White else RaahiTextDim, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

// ------------------------------------------------------------------------- Score ring ----

/** Circular Car Health score indicator — a stroked arc (not a true conic/sweep gradient,
 * which drawArc doesn't support directly) colored by band, matching the mockup's ring. */
@Composable
fun ScoreRing(score: Int, ringSize: Dp = 48.dp, big: Boolean = false) {
    val color = when {
        score >= 70 -> RaahiGreen
        score >= 40 -> RaahiAmber
        else -> RaahiRed
    }
    val sweep = (score.coerceIn(0, 100) / 100f) * 360f
    Box(modifier = Modifier.size(ringSize), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(width = size.minDimension * 0.13f, cap = StrokeCap.Round)
            drawArc(color = RaahiGlassStrong, startAngle = -90f, sweepAngle = 360f, useCenter = false, style = stroke)
            drawArc(color = color, startAngle = -90f, sweepAngle = sweep, useCenter = false, style = stroke)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$score", color = RaahiText, fontFamily = RaahiDisplayFont,
                fontWeight = FontWeight.Bold, fontSize = if (big) 18.sp else 13.sp,
            )
            Text("SCORE", color = RaahiTextFaint, fontSize = if (big) 8.sp else 6.5.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ---------------------------------------------------------------------------- Ticker -----

/** Continuously auto-scrolling info strip. Not a perfectly seamless CSS-style infinite loop
 * (this scrolls to the end, then snaps back to the start) — a deliberate simplification to
 * avoid custom Layout/measurement code for a purely decorative element. */
@Composable
fun Ticker(items: List<String>, modifier: Modifier = Modifier) {
    val scrollState = rememberScrollState()
    LaunchedEffect(scrollState.maxValue) {
        if (scrollState.maxValue <= 0) return@LaunchedEffect
        while (true) {
            scrollState.animateScrollTo(
                scrollState.maxValue,
                animationSpec = tween((scrollState.maxValue * 14).coerceAtLeast(4000), easing = LinearEasing),
            )
            delay(400)
            scrollState.scrollTo(0)
            delay(300)
        }
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(RaahiGlass, RaahiShapeSmall)
            .border(1.dp, RaahiBorderSoft, RaahiShapeSmall),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier.horizontalScroll(scrollState, enabled = false).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            items.forEach { text ->
                Text(text, color = RaahiTextDim, fontSize = 10.5.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
        }
    }
}

// ------------------------------------------------------------------------ Bottom nav -----

enum class RaahiTab(val label: String) {
    HOME("Home"), MECHANICS("Mechanics"), AI_MECHANIC("AI Mechanic"), SHOP("Shop"), PROFILE("Profile"),
}

/** Floating glass pill nav bar with a raised gradient center (AI Mechanic) button — matches
 * the mockup exactly, including the icon-bounce feedback on tab change. True backdrop blur
 * (blurring whatever scrolls behind the bar) isn't used here — Compose's blur modifier blurs
 * its own content, not content behind it, so this approximates "frosted glass" with a
 * semi-transparent dark fill instead, which is what most production apps below API 31 do
 * anyway. */
@Composable
fun RaahiBottomNavBar(current: RaahiTab, onSelect: (RaahiTab) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .height(58.dp)
            .background(Color(0xE6121620), RaahiShapeLarge)
            .border(1.dp, RaahiBorder, RaahiShapeLarge),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavItem(RaahiTab.HOME, Icons.Outlined.Home, current, onSelect)
            NavItem(RaahiTab.MECHANICS, RaahiIcons.Wrench, current, onSelect)
            CenterNavItem(current, onSelect)
            NavItem(RaahiTab.SHOP, Icons.Outlined.ShoppingBag, current, onSelect)
            NavItem(RaahiTab.PROFILE, Icons.Outlined.Person, current, onSelect)
        }
    }
}

@Composable
private fun NavItem(tab: RaahiTab, icon: ImageVector, current: RaahiTab, onSelect: (RaahiTab) -> Unit) {
    val active = tab == current
    val tint = if (active) RaahiText else RaahiTextFaint
    val scale by animateFloatAsState(
        targetValue = if (active) 1.25f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "navIconScale",
    )
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .clickable(interactionSource = interactionSource, indication = null, onClick = { onSelect(tab) })
            .padding(vertical = 2.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon, contentDescription = tab.label, tint = tint,
            modifier = Modifier.size(17.dp).scale(if (active) scale else 1f),
        )
        Spacer(Modifier.height(3.dp))
        Text(tab.label.take(4), color = tint, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CenterNavItem(current: RaahiTab, onSelect: (RaahiTab) -> Unit) {
    val active = current == RaahiTab.AI_MECHANIC
    val scale by animateFloatAsState(
        targetValue = if (active) 1.15f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "navCenterScale",
    )
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(42.dp)
            .scale(scale)
            .offset(y = (-22).dp)
            .background(if (active) RaahiAiGradient else RaahiBrandGradient, RaahiShapeMedium)
            .clickable(interactionSource = interactionSource, indication = null, onClick = { onSelect(RaahiTab.AI_MECHANIC) }),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.AutoAwesome, contentDescription = RaahiTab.AI_MECHANIC.label, tint = Color.White, modifier = Modifier.size(19.dp))
    }
}

private fun Modifier.graphicsScale(scale: Float): Modifier = this.then(
    Modifier.graphicsLayerScale(scale)
)
private fun Modifier.graphicsLayerScale(scale: Float): Modifier =
    this.then(androidx.compose.ui.draw.scale(scale))

// ----------------------------------------------------------------------- Misc icons ------

/** A couple of small icons Material's extended set names differently than the mockup used
 * them for — centralized here rather than duplicated per screen. */
object RaahiIcons {
    val Wrench: ImageVector get() = androidx.compose.material.icons.Icons.Outlined.Build
    val ArrowRight: ImageVector get() = Icons.AutoMirrored.Outlined.ArrowForward
}

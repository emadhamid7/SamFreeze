package com.samfreeze.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// Colors sampled from One UI 6 screenshots.
private val SwitchOn = Color(0xFF377AFF)
private val SwitchOff = Color(0xFF646368)
private val SwitchThumb = Color(0xFFFCFCFE)

/**
 * One UI style switch: slim 34x21dp pill track with a small white thumb
 * inset 2dp. Wrapped in a 44x40dp touch target so it stays easy to hit.
 */
@Composable
fun SamsungSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val track by animateColorAsState(if (checked) SwitchOn else SwitchOff, tween(180), label = "swTrack")
    val thumbX by animateDpAsState(if (checked) 15.dp else 2.dp, tween(180), label = "swThumb")

    val touch = if (onCheckedChange != null) {
        Modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onValueChange = onCheckedChange
        )
    } else Modifier

    Box(
        modifier = modifier.size(44.dp, 40.dp).then(touch).alpha(if (enabled) 1f else 0.4f),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(34.dp, 21.dp)
                .clip(CircleShape)
                .background(track)
        ) {
            Box(
                modifier = Modifier
                    .offset(x = thumbX)
                    .align(Alignment.CenterStart)
                    .size(17.dp)
                    .clip(CircleShape)
                    .background(SwitchThumb)
            )
        }
    }
}

/** Samsung Display-screen style Light / Dark picker: two preview tiles, label, radio under each. */
@Composable
fun SamsungThemeSelector(isDark: Boolean, onSelect: (dark: Boolean) -> Unit, lightLabel: String, darkLabel: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        ThemeOption(dark = false, selected = !isDark, label = lightLabel, onClick = { onSelect(false) }, modifier = Modifier.weight(1f))
        ThemeOption(dark = true, selected = isDark, label = darkLabel, onClick = { onSelect(true) }, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ThemeOption(dark: Boolean, selected: Boolean, label: String, onClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier.selectable(
            selected = selected,
            role = Role.RadioButton,
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ThemePreviewTile(dark)
        Spacer(Modifier.height(12.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        SamsungRadio(selected)
    }
}

@Composable
private fun ThemePreviewTile(dark: Boolean) {
    val outer = if (dark) Color(0xFF353A40) else Color(0xFFDFE3E6)
    val card = if (dark) Color(0xFF596269) else Color.White
    val line = if (dark) Color(0xFF899096) else Color(0xFFD3D3D3)
    val dotA = Color(0xFF5294EB)
    val dotB = Color(0xFF2196FF)

    Column(
        modifier = Modifier
            .size(128.dp, 90.dp)
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(outer)
            .padding(horizontal = 10.dp)
    ) {
        Spacer(Modifier.height(21.dp))
        Row(
            modifier = Modifier.fillMaxWidth().height(53.dp).clip(RoundedCornerShape(12.dp)).background(card).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PreviewRow(dotA, line)
                PreviewRow(dotB, line)
            }
        }
        Spacer(Modifier.height(4.dp))
        Box(modifier = Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)).background(card))
    }
}

@Composable
private fun PreviewRow(dot: Color, line: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(11.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.width(62.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(line))
    }
}

@Composable
fun SamsungRadio(selected: Boolean) {
    val ring = if (selected) SwitchOn else MaterialTheme.colorScheme.outline
    Box(
        modifier = Modifier.size(24.dp).border(2.dp, ring, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (selected) Box(Modifier.size(12.dp).clip(CircleShape).background(SwitchOn))
    }
}

/**
 * The one caption style used app-wide (section titles, counts, small
 * headings): bold, muted gray like One UI's "Privacy" / "Additional
 * security settings" captions, instead of the accent color.
 */
@Composable
fun SectionCaption(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        // Dimmer than the option text so captions sit back, like One UI.
        color = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF8F8F95) else Color(0xFF6A6B71),
        modifier = modifier
    )
}

/**
 * Quick-return header state: the header slides up as soon as the list is
 * scrolled down and slides back in as soon as it is scrolled up again
 * (no need to reach the top first).
 */
@Stable
class CollapsingHeaderState {
    var headerHeightPx by mutableStateOf(0f)
    var offsetPx by mutableStateOf(0f)

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            // Follows the finger but never consumes — the list scrolls too,
            // so header and content stay visually in step.
            offsetPx = (offsetPx + available.y).coerceIn(-headerHeightPx, 0f)
            return Offset.Zero
        }
    }
}

/**
 * Lays [content] (normally a LazyColumn that uses the given header height as
 * its top contentPadding) under a [header] that hides on scroll-down and
 * returns on scroll-up. The status-bar strip stays covered so list rows never
 * run into the clock / icons.
 */
@Composable
fun CollapsingHeaderLayout(
    modifier: Modifier = Modifier,
    header: @Composable ColumnScope.() -> Unit,
    content: @Composable (state: CollapsingHeaderState, headerHeight: Dp) -> Unit
) {
    val state = remember { CollapsingHeaderState() }
    val density = LocalDensity.current
    val bg = MaterialTheme.colorScheme.background
    val primary = MaterialTheme.colorScheme.primary
    val dark = bg.luminance() < 0.5f
    Box(modifier.nestedScroll(state.connection)) {
        content(state, with(density) { state.headerHeightPx.toDp() })
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, state.offsetPx.roundToInt()) }
                .onSizeChanged { state.headerHeightPx = it.height.toFloat() }
                // Same colour + same top glow as the wallpaper, so the header
                // is seamless against it while still hiding rows scrolling under.
                .drawBehind {
                    drawRect(bg)
                    drawTopGlow(primary, dark, -state.offsetPx)
                },
            content = header
        )
        Box(
            Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .drawBehind {
                    drawRect(bg)
                    drawTopGlow(primary, dark, 0f)
                }
        )
    }
}

/** Keeps the header visible whenever the list is at its top (or too short to scroll). */
@Composable
fun SyncHeaderWithList(header: CollapsingHeaderState, listState: LazyListState) {
    val atTop by remember { derivedStateOf { !listState.canScrollBackward } }
    LaunchedEffect(atTop) { if (atTop) header.offsetPx = 0f }
}

/** Evenly spaced One UI-style filter pills: selected = tinted accent, others = quiet surface. */
@Composable
fun <T> FilterPills(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            val bg by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface,
                tween(160), label = "pillBg"
            )
            val fg by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                tween(160), label = "pillFg"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(CircleShape)
                    .background(bg)
                    .selectable(
                        selected = isSelected,
                        role = Role.RadioButton,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSelect(value) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = fg,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

/** Extra list padding so the last rows can scroll clear of the floating bottom bar. */
val FloatingBarClearance = 84.dp

/** Soft accent glow near the top edge; [originY] shifts it when drawn inside a moving header. */
fun DrawScope.drawTopGlow(color: Color, dark: Boolean, originY: Float) {
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = if (dark) 0.10f else 0.07f), color.copy(alpha = 0f)),
            center = Offset(size.width * 0.85f, originY + 90.dp.toPx()),
            radius = size.width * 0.95f
        )
    )
}

/**
 * App-wide faint "wallpaper": base background plus two very soft accent glows
 * (top-right, bottom-left). Deliberately subtle.
 */
@Composable
fun AppBackdrop(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val bg = MaterialTheme.colorScheme.background
    val primary = MaterialTheme.colorScheme.primary
    val dark = bg.luminance() < 0.5f
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(bg)
                drawTopGlow(primary, dark, 0f)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(primary.copy(alpha = if (dark) 0.07f else 0.05f), primary.copy(alpha = 0f)),
                        center = Offset(size.width * 0.05f, size.height * 0.95f),
                        radius = size.width * 1.0f
                    )
                )
            }
    ) {
        Surface(
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            content = content
        )
    }
}

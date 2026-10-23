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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
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

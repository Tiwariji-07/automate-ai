package com.devfest.automation.ui

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.devfest.automation.ui.theme.App
import com.devfest.automation.ui.theme.Type

@Composable
fun IconCircle(icon: ImageVector, description: String, onClick: () -> Unit) {
    val colors = App.colors
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(colors.surface)
            .border(1.dp, colors.line, CircleShape).clickable(onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, Modifier.size(20.dp), tint = colors.ink)
    }
}

@Composable
fun TopBar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        IconCircle(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
        Text(title, style = Type.bar, color = App.colors.ink, modifier = Modifier.weight(1f), maxLines = 1)
        actions()
    }
}

@Composable
fun SectionHeader(text: String, trailing: String? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 7.dp)) {
        Text(text, style = Type.smallStrong, color = App.colors.muted, modifier = Modifier.weight(1f))
        trailing?.let { Text(it, style = Type.smallStrong, color = App.colors.muted) }
    }
}

@Composable
fun Card(modifier: Modifier = Modifier, border: Color = App.colors.line, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier.fillMaxWidth().padding(horizontal = 12.dp),
        shape = RoundedCornerShape(22.dp),
        color = App.colors.surface,
        border = BorderStroke(1.dp, border)
    ) {
        Column(content = content)
    }
}

@Composable
fun Panel(title: String?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.padding(top = 12.dp)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            title?.let { Text(it, style = Type.smallStrong, color = App.colors.muted, modifier = Modifier.padding(bottom = 6.dp)) }
            content()
        }
    }
}

enum class ButtonKind { PRIMARY, INK, QUIET, DANGER }

@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.PRIMARY,
    enabled: Boolean = true,
    busy: Boolean = false
) {
    val colors = App.colors
    val haptics = LocalHapticFeedback.current
    val (bg, fg) = when (kind) {
        ButtonKind.PRIMARY -> colors.brand to colors.onBrand
        ButtonKind.INK -> colors.ink to colors.bg
        ButtonKind.QUIET -> colors.surface to colors.ink
        ButtonKind.DANGER -> colors.failSoft to colors.fail
    }
    Box(
        modifier
            .heightIn(min = 50.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) bg else bg.copy(alpha = 0.4f))
            .then(if (kind == ButtonKind.QUIET) Modifier.border(1.dp, colors.line, RoundedCornerShape(16.dp)) else Modifier)
            .clickable(enabled = enabled && !busy) {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(20.dp), color = fg, strokeWidth = 2.dp)
        } else {
            Text(text, style = Type.bodyStrong, color = if (enabled) fg else fg.copy(alpha = 0.7f), textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun FlowSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = App.colors
    val haptics = LocalHapticFeedback.current
    Switch(
        checked = checked,
        onCheckedChange = {
            haptics.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
            onChange(it)
        },
        colors = SwitchDefaults.colors(
            checkedTrackColor = colors.brand, checkedThumbColor = colors.onBrand, checkedBorderColor = colors.brand,
            uncheckedTrackColor = colors.idle, uncheckedThumbColor = Color.White, uncheckedBorderColor = colors.idle
        )
    )
}

/** Buttons pinned above the navigation bar, with a fade so scrolled content does not end in a hard edge. */
@Composable
fun BottomBar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val colors = App.colors
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(24.dp).background(Brush.verticalGradient(listOf(Color.Transparent, colors.bg))))
        Row(
            Modifier.fillMaxWidth().background(colors.bg).navigationBarsPadding().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

/** The dark pill at the bottom of Home and Chat. */
@Composable
fun Composer(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false
) {
    val colors = App.colors
    val canSend = value.isNotBlank() && !busy
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(colors.composer)
            .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f).padding(vertical = 10.dp)) {
            if (value.isEmpty()) Text(placeholder, style = Type.body, color = Color.White.copy(alpha = 0.72f))
            BasicTextField(
                value = value,
                onValueChange = onChange,
                textStyle = Type.body.copy(color = Color.White),
                cursorBrush = SolidColor(Color.White),
                maxLines = 5,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier.size(48.dp).clip(CircleShape)
                .background(if (canSend) Color(0xFF1337EC) else Color.White.copy(alpha = 0.12f))
                .clickable(enabled = canSend, onClickLabel = "Send", onClick = onSend),
            contentAlignment = Alignment.Center
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, "Send", Modifier.size(20.dp), tint = Color.White)
            }
        }
    }
}

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = App.colors
    Row(
        modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(16.dp))
            .background(colors.surface).border(1.dp, colors.line, RoundedCornerShape(16.dp)).padding(4.dp)
    ) {
        options.forEachIndexed { index, option ->
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                    .background(if (index == selected) colors.brandSoft else Color.Transparent)
                    .clickable { onSelect(index) }.padding(vertical = 11.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(option, style = Type.smallStrong, color = if (index == selected) colors.ink else colors.muted)
            }
        }
    }
}

/** A labelled text field in the mockup's style. */
@Composable
fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    secret: Boolean = false
) {
    val colors = App.colors
    Column(
        modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(16.dp))
            .background(colors.surface).border(1.dp, colors.line, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(label, style = Type.tiny, color = colors.faint)
        Box {
            if (value.isEmpty()) Text(placeholder, style = Type.body, color = colors.faint)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = Type.body.copy(color = colors.ink),
                cursorBrush = SolidColor(colors.brand),
                visualTransformation = if (secret) SecretTransformation else VisualTransformation.None,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** Shows the first 4 and last 4 characters of a key. */
private val SecretTransformation = VisualTransformation { text ->
    val s = text.text
    val masked = if (s.length <= 10) "•".repeat(s.length) else s.take(4) + "•".repeat(s.length - 8) + s.takeLast(4)
    TransformedText(AnnotatedString(masked), OffsetMapping.Identity)
}

fun ago(time: Long, now: Long = System.currentTimeMillis()): String =
    if (now - time < DateUtils.MINUTE_IN_MILLIS) "just now"
    else DateUtils.getRelativeTimeSpanString(time, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()

fun inTime(millis: Long): String {
    val minutes = (millis / 60_000).coerceAtLeast(1)
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h >= 24 -> "${h / 24} d ${h % 24} h"
        h > 0 -> "$h h $m min"
        else -> "$m min"
    }
}

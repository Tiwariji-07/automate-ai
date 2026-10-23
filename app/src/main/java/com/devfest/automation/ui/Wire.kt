package com.devfest.automation.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.devfest.automation.ui.theme.App
import com.devfest.automation.ui.theme.AppColors
import com.devfest.automation.ui.theme.Type
import com.devfest.core.catalog.Blocks
import com.devfest.core.catalog.Category
import com.devfest.core.model.Automation
import com.devfest.core.model.Step

/** Where a step is in a live or finished run. */
enum class NodeState { DONE, PASSED, NOT_PASSED, FAILED, RUNNING, WAITING, SKIPPED }

data class NodeStatus(val state: NodeState, val text: String = "")

/** One line of the wire. [label] is shown only where the kind of step changes. */
data class WireRow(val step: Step, val category: Category, val label: String?, val depth: Int, val gateAfter: Boolean)

fun wireRows(flow: Automation): List<WireRow> {
    val rows = mutableListOf(WireRow(flow.trigger, Category.TRIGGER, "When", 0, false))
    fun add(steps: List<Step>, depth: Int, heading: String?) {
        var previous: Category? = if (heading == null) Category.TRIGGER else null
        steps.forEachIndexed { index, step ->
            val category = Blocks[step.type]?.category ?: Category.ACTION
            val label = when {
                index == 0 && heading != null -> heading
                category == previous -> null
                category == Category.CONDITION -> "Only if"
                else -> "Then"
            }
            val isGuard = category == Category.CONDITION && !step.hasBranches && index < steps.lastIndex
            rows += WireRow(step, category, label, depth, isGuard)
            if (step.hasBranches) {
                add(step.then, depth + 1, "If yes")
                add(step.otherwise, depth + 1, "Otherwise")
            }
            previous = category
        }
    }
    add(flow.steps, 0, null)
    return rows
}

fun categoryColor(category: Category, colors: AppColors): Pair<Color, Color> = when (category) {
    Category.TRIGGER -> colors.whenColor to colors.whenSoft
    Category.CONDITION -> colors.ifColor to colors.ifSoft
    Category.ACTION -> colors.thenColor to colors.thenSoft
}

/**
 * The app's signature: a vertical line through every step, coloured by kind.
 * [statuses] turns it into a live run; [changed] marks steps the last chat reply touched.
 */
@Composable
fun Wire(
    flow: Automation,
    modifier: Modifier = Modifier,
    statuses: Map<String, NodeStatus> = emptyMap(),
    changed: Map<String, String> = emptyMap(),
    warnings: Map<String, String> = emptyMap()
) {
    val rows = wireRows(flow)
    // The line between two steps lights up once the run has passed through both.
    val lit = rows.mapIndexed { index, row ->
        statuses[row.step.id]?.state in LIT &&
            statuses[rows.getOrNull(index + 1)?.step?.id]?.state.let { it != null && it != NodeState.SKIPPED }
    }
    Column(modifier) {
        rows.forEachIndexed { index, row ->
            WireNode(
                row = row,
                status = statuses[row.step.id],
                change = changed[row.step.id],
                warning = warnings[row.step.id],
                first = index == 0,
                last = index == rows.lastIndex,
                litAbove = lit.getOrElse(index - 1) { false },
                litBelow = lit[index]
            )
            if (row.gateAfter) Gate(row.depth, if (lit[index]) App.colors.brand else App.colors.line)
        }
    }
}

private val LIT = setOf(NodeState.DONE, NodeState.PASSED, NodeState.NOT_PASSED)

@Composable
private fun WireNode(
    row: WireRow,
    status: NodeStatus?,
    change: String?,
    warning: String?,
    first: Boolean,
    last: Boolean,
    litAbove: Boolean,
    litBelow: Boolean
) {
    val colors = App.colors
    val (tint, soft) = categoryColor(row.category, colors)
    val text = describe(row.step)
    val waiting = status?.state == NodeState.WAITING || status?.state == NodeState.SKIPPED
    val above by animateColorAsState(if (litAbove) colors.brand else colors.line, label = "above")
    val below by animateColorAsState(if (litBelow) colors.brand else colors.line, label = "below")
    val indent = (row.depth * 22).dp
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                val x = 16.dp.toPx()
                val center = size.height / 2
                if (!first) drawLine(above, Offset(x, 0f), Offset(x, center), 2.dp.toPx())
                if (!last) drawLine(below, Offset(x, center), Offset(x, size.height), 2.dp.toPx())
            }
            .then(if (change != null) Modifier.background(colors.brandSoft, RoundedCornerShape(12.dp)) else Modifier)
            .padding(vertical = 7.dp)
            .alpha(if (status?.state == NodeState.SKIPPED) 0.5f else 1f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(indent))
        StepDot(row.step.type, tint, soft, status?.state)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            row.label?.let { Text(it, style = Type.tiny, color = colors.faint) }
            Text(
                text.title,
                style = Type.bodyStrong.copy(fontSize = Type.small.fontSize),
                color = if (waiting) colors.faint else colors.ink
            )
            text.detail?.let { Text(it, style = Type.tiny, color = colors.muted, maxLines = 3) }
            change?.let { old ->
                Text(
                    if (old.isEmpty()) AnnotatedString("New") else buildAnnotatedString {
                        append("Was ")
                        withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, fontWeight = FontWeight.Normal)) { append(old) }
                    },
                    style = Type.tiny.copy(fontWeight = FontWeight.SemiBold), color = colors.brand
                )
            }
            warning?.let { Text("⚠ $it", style = Type.tiny, color = colors.warn) }
            if (status != null && status.text.isNotBlank() && status.state != NodeState.RUNNING) {
                Text(
                    status.text,
                    style = Type.tiny,
                    color = if (status.state == NodeState.FAILED) colors.fail else colors.muted,
                    maxLines = 3,
                    textDecoration = if (status.state == NodeState.SKIPPED) TextDecoration.LineThrough else null
                )
            }
        }
        status?.let { StatusTag(it.state) }
    }
}

@Composable
private fun StepDot(type: String, tint: Color, soft: Color, state: NodeState?) {
    val colors = App.colors
    val waiting = state == NodeState.WAITING || state == NodeState.SKIPPED
    val pulse = if (state == NodeState.RUNNING) {
        val transition = rememberInfiniteTransition(label = "pulse")
        transition.animateFloat(0.35f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulse").value
    } else 0f
    Box(
        Modifier
            .size(32.dp)
            .then(
                if (state == NodeState.RUNNING) {
                    Modifier.border(2.dp, colors.brand.copy(alpha = pulse), RoundedCornerShape(12.dp)).padding(3.dp)
                } else Modifier
            )
            .clip(RoundedCornerShape(11.dp))
            .background(if (waiting) colors.surface else soft)
            .then(
                if (waiting) Modifier.drawBehind {
                    drawRoundRect(
                        colors.faint, cornerRadius = CornerRadius(11.dp.toPx()),
                        style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())))
                    )
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon(type), null, Modifier.size(17.dp), tint = if (waiting) colors.faint else tint)
    }
}

@Composable
private fun StatusTag(state: NodeState) {
    val colors = App.colors
    val (text, color) = when (state) {
        NodeState.DONE -> "✓ Done" to colors.ok
        NodeState.PASSED -> "✓ Passed" to colors.ok
        NodeState.NOT_PASSED -> "Not met" to colors.warn
        NodeState.FAILED -> "✕ Failed" to colors.fail
        NodeState.RUNNING -> "● Running" to colors.brand
        NodeState.WAITING -> "Waiting" to colors.faint
        NodeState.SKIPPED -> "Skipped" to colors.faint
    }
    Text(text, style = Type.tiny.copy(fontWeight = FontWeight.SemiBold), color = color, modifier = Modifier.padding(start = 8.dp))
}

@Composable
private fun Gate(depth: Int, line: Color) {
    val colors = App.colors
    val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
    Row(
        Modifier.fillMaxWidth().height(22.dp)
            .drawBehind { drawLine(line, Offset(16.dp.toPx(), 0f), Offset(16.dp.toPx(), size.height), 2.dp.toPx()) }
            .padding(start = (44 + depth * 22).dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.width(2.dp).height(18.dp).drawBehind {
                drawLine(colors.ifColor.copy(alpha = 0.5f), Offset(0f, 0f), Offset(0f, size.height), 2.dp.toPx(), pathEffect = dash)
            }
        )
        Text("Continues only if this passes", style = Type.tiny, color = colors.ifColor, modifier = Modifier.padding(start = 10.dp))
    }
}

/** The short wire on a Home row: one dot per top-level step. */
@Composable
fun MiniWire(flow: Automation?, active: Boolean, modifier: Modifier = Modifier) {
    val colors = App.colors
    val categories = buildList {
        add(Category.TRIGGER)
        flow?.steps?.take(4)?.forEach { add(Blocks[it.type]?.category ?: Category.ACTION) }
    }
    Column(modifier.padding(top = 5.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Top) {
        categories.forEachIndexed { index, category ->
            if (index > 0) Box(Modifier.width(2.dp).height(5.dp).background(colors.line))
            Box(
                Modifier.size(9.dp).clip(CircleShape)
                    .background(if (active) categoryColor(category, colors).first else colors.idle)
            )
        }
    }
}

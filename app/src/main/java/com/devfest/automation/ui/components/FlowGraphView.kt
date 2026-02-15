package com.devfest.automation.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devfest.runtime.model.BlockCategory
import com.devfest.runtime.model.FlowBlock
import com.devfest.runtime.model.FlowGraph

@Composable
fun FlowGraphView(
    graph: FlowGraph,
    modifier: Modifier = Modifier,
    nodeWidth: Dp = 140.dp,
    nodeHeight: Dp = 60.dp
) {
    val textMeasurer = rememberTextMeasurer()
    
    // Calculate layout
    val layout = remember(graph) {
        calculateLayout(graph)
    }

    val totalHeight = 120.dp * (layout.maxRank + 1) + 100.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(totalHeight)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val nodeW = nodeWidth.toPx()
            val nodeH = nodeHeight.toPx()
            val centerX = size.width / 2

            // Draw Grid Background
            val gridSize = 40.dp.toPx()
            val verticalLines = (size.width / gridSize).toInt()
            val horizontalLines = (size.height / gridSize).toInt()

            for (i in 0..verticalLines) {
                 drawLine(
                    color = Color.White.copy(alpha = 0.05f),
                    start = Offset(i * gridSize, 0f),
                    end = Offset(i * gridSize, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }
            for (i in 0..horizontalLines) {
                drawLine(
                    color = Color.White.copy(alpha = 0.05f),
                    start = Offset(0f, i * gridSize),
                    end = Offset(size.width, i * gridSize),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // Draw Edges first
            graph.edges.forEach { edge ->
                val fromNode = layout.nodes[edge.from]
                val toNode = layout.nodes[edge.to]

                if (fromNode != null && toNode != null) {
                    val startX = centerX + fromNode.x * (nodeW + 40f) // Removed - nodeW/2 logic from x coord here? No need consistent center
                    val startY = fromNode.y * 250f + nodeH + 50f
                    val endX = centerX + toNode.x * (nodeW + 40f)
                    val endY = toNode.y * 250f + 50f

                    drawCurve(
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        color = Color.Gray
                    )
                }
            }

            // Draw Nodes
            layout.nodes.forEach { (id, pos) ->
                val block = graph.blocks.find { it.id == id } ?: return@forEach
                val x = centerX + pos.x * (nodeW + 40f) - nodeW / 2
                val y = pos.y * 250f + 50f

                val color = when (block.type.category) {
                    BlockCategory.TRIGGER -> com.devfest.automation.ui.theme.TriggerBlue
                    BlockCategory.CONDITION -> com.devfest.automation.ui.theme.ElectricBlue
                    BlockCategory.ACTION -> com.devfest.automation.ui.theme.ActionGreen
                    BlockCategory.UTILITY -> com.devfest.automation.ui.theme.ActionPurple
                }
                
                // Helper function call
                drawNode(
                    block = block,
                    rect = Rect(x, y, x + nodeW, y + nodeH),
                    color = color,
                    textColor = Color.White,
                    textMeasurer = textMeasurer
                )
            }
        }
    }
}

private fun DrawScope.drawNode(
    block: FlowBlock,
    rect: Rect,
    color: Color,
    textColor: Color,
    textMeasurer: TextMeasurer
) {
    drawRoundRect(
        color = color,
        topLeft = rect.topLeft,
        size = rect.size,
        cornerRadius = CornerRadius(16f, 16f)
    )

    // Simplified text drawing
    val text = block.type.name.split("_")
        .joinToString(" ") { it.lowercase().replaceFirstChar { c -> c.uppercase() } }
        .replace(" Action", "")
        .replace(" Trigger", "")
        .replace(" Condition", "")

    val textLayoutResult = textMeasurer.measure(
        text = text,
        style = TextStyle(fontSize = 12.sp, color = textColor)
    )
    
    // Center text
    val textX = rect.center.x - textLayoutResult.size.width / 2
    val textY = rect.center.y - textLayoutResult.size.height / 2

    drawText(
        textLayoutResult = textLayoutResult,
        topLeft = Offset(textX, textY)
    )
}

private fun DrawScope.drawCurve(
    start: Offset,
    end: Offset,
    color: Color
) {
    val path = Path().apply {
        moveTo(start.x, start.y)
        cubicTo(
            start.x, start.y + 80f,
            end.x, end.y - 80f,
            end.x, end.y
        )
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = 4f)
    )
}

// Private models for layout calculation
private data class NodePos(val x: Float, val y: Int)
private data class GraphLayout(val nodes: Map<String, NodePos>, val maxRank: Int)

private fun calculateLayout(graph: FlowGraph): GraphLayout {
    val ranks = mutableMapOf<String, Int>()
    val nodes = mutableMapOf<String, NodePos>()
    
    // Use the blocks list order as a fallback for topological sort
    // A real topo sort would be better, but simple rank calculation is sufficient for demo
    
    // Initialize ranks for all blocks to 0
    graph.blocks.forEach { ranks[it.id] = 0 }

    // Multi-pass rank adjustment
    if (graph.edges.isEmpty() && graph.blocks.size > 1) {
        // Fallback: Linear layout if no edges defined but multiple blocks exist
        graph.blocks.forEachIndexed { index, block ->
            ranks[block.id] = index
        }
    } else {
        for (i in 0 until graph.blocks.size) { // Relax edges N times
            var changed = false
            graph.edges.forEach { edge ->
                val fromRank = ranks[edge.from] ?: 0
                val toRank = ranks[edge.to] ?: 0
                if (fromRank >= toRank) {
                    ranks[edge.to] = fromRank + 1
                    changed = true
                }
            }
            if (!changed) break
        }
    }
    
    // Group by rank to assign X
    val rankGroups = ranks.entries.groupBy({ it.value }, { it.key })
    var maxRank = 0
    
    rankGroups.forEach { (rank, ids) ->
        maxRank = maxOf(maxRank, rank)
        val width = ids.size
        // Center the group: if 2 items -> -0.5, 0.5. if 1 item -> 0.
        val startX = -(width - 1) / 2f
        ids.forEachIndexed { index, id ->
            nodes[id] = NodePos(startX + index * 1.5f, rank) // 1.5f spacing
        }
    }

    return GraphLayout(nodes, maxRank)
}

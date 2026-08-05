package co.zw.nissangtr.ui.shop

/**
 * PDP primitives forked from Shopping-By-KMP detail / ExpandingText /
 * cart sticky patterns (MIT © 2023 Mahdi Razzaghi Ghaleh). Fitment / core-charge
 * slots are GTR host content — layout chrome is KMP.
 */

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.ui.theme.GtrColors

/** KMP detail sticky CTA — price + primary button. */
@Composable
fun ShopStickyCtaBar(
    priceLabel: String,
    ctaLabel: String,
    onCta: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: @Composable (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            leading?.invoke()
            Column(modifier = Modifier.weight(1f)) {
                Text("Total", style = MaterialTheme.typography.labelSmall)
                Text(priceLabel, style = MaterialTheme.typography.titleMedium)
            }
            androidx.compose.material3.Button(
                onClick = onCta,
                enabled = enabled,
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Text(ctaLabel)
            }
        }
    }
}

/**
 * KMP [ExpandingText] — collapse to 2 lines with inline Read More / Show Less.
 */
@Composable
fun ShopExpandableDescription(
    text: String,
    modifier: Modifier = Modifier,
    collapsedLines: Int = 2,
) {
    var isExpanded by remember { mutableStateOf(false) }
    val textLayoutResultState = remember { mutableStateOf<TextLayoutResult?>(null) }
    var isClickable by remember { mutableStateOf(false) }
    var finalText by remember(text) { mutableStateOf(text) }
    val endLabel = if (isExpanded) "Show Less" else "Read More"

    val textLayoutResult = textLayoutResultState.value
    LaunchedEffect(textLayoutResult, isExpanded, text) {
        if (textLayoutResult == null) return@LaunchedEffect
        when {
            isExpanded -> finalText = "$text $endLabel"
            !isExpanded && textLayoutResult.hasVisualOverflow -> {
                val lastCharIndex = textLayoutResult.getLineEnd(collapsedLines - 1)
                val showMoreString = "... $endLabel"
                val cut = lastCharIndex.coerceAtMost(text.length)
                val adjusted = text
                    .substring(0, cut)
                    .dropLast(showMoreString.length.coerceAtMost(cut))
                    .dropLastWhile { it == ' ' || it == '.' }
                finalText = "$adjusted$showMoreString"
                isClickable = true
            }
            else -> finalText = text
        }
    }

    Text(
        text = finalText,
        maxLines = if (isExpanded) Int.MAX_VALUE else collapsedLines,
        style = MaterialTheme.typography.bodyMedium,
        onTextLayout = { textLayoutResultState.value = it },
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = isClickable || isExpanded) {
                isExpanded = !isExpanded
            }
            .animateContentSize(),
    )
}

@Composable
fun ShopRatingRow(
    avgRating: Double,
    reviewCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            Icons.Filled.Star,
            contentDescription = null,
            tint = GtrColors.Warning,
            modifier = Modifier.size(18.dp),
        )
        Text(
            if (reviewCount == 0) "No reviews yet"
            else "%.1f · %d review(s)".format(avgRating, reviewCount),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

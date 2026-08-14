package com.joker.coolmall.feature.common.skeleton

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joker.coolmall.core.designsystem.theme.AppTheme
import com.joker.coolmall.core.designsystem.theme.SpacePaddingLarge
import com.joker.coolmall.core.designsystem.theme.SpacePaddingMedium
import com.joker.coolmall.core.designsystem.theme.SpaceVerticalMedium
import com.joker.coolmall.core.designsystem.theme.SpaceVerticalSmall
import com.joker.coolmall.core.ui.component.skeleton.SkeletonTextLine

/**
 * 文本文档骨架屏
 *
 * 适用于协议、政策等长文本页面，按标题、元信息和正文段落展示占位内容。
 *
 * @author Joker.X
 */
@Composable
internal fun DocumentLoadingSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                horizontal = SpacePaddingMedium,
                vertical = SpacePaddingLarge,
            ),
        verticalArrangement = Arrangement.spacedBy(SpaceVerticalMedium),
    ) {
        SkeletonTextLine(widthFraction = 0.36f, height = 36.dp)
        SkeletonTextLine(widthFraction = 0.44f, height = 18.dp)

        DocumentParagraphSkeleton(
            lineFractions = listOf(0.96f, 0.92f, 0.98f, 0.9f),
        )
        DocumentParagraphSkeleton(
            lineFractions = listOf(0.94f, 0.88f, 0.96f),
        )

        SkeletonTextLine(widthFraction = 0.52f, height = 30.dp)
        SkeletonTextLine(widthFraction = 0.4f, height = 24.dp)

        DocumentParagraphSkeleton(
            lineFractions = listOf(0.97f, 0.95f, 0.93f, 0.9f, 0.86f),
        )
        DocumentParagraphSkeleton(
            lineFractions = listOf(0.96f, 0.9f, 0.82f),
        )
    }
}

/**
 * 文本文档段落骨架屏
 *
 * @param lineFractions 行宽比例列表
 * @author Joker.X
 */
@Composable
private fun DocumentParagraphSkeleton(
    lineFractions: List<Float>,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(SpaceVerticalSmall),
    ) {
        lineFractions.forEach { widthFraction ->
            SkeletonTextLine(
                widthFraction = widthFraction,
                height = 18.dp,
            )
        }
    }
}

/**
 * 文本文档骨架屏预览 - 浅色主题
 *
 * @author Joker.X
 */
@Preview(showBackground = true)
@Composable
private fun DocumentLoadingSkeletonPreviewLight() {
    AppTheme(darkTheme = false) {
        DocumentLoadingSkeleton()
    }
}

/**
 * 文本文档骨架屏预览 - 深色主题
 *
 * @author Joker.X
 */
@Preview(showBackground = true)
@Composable
private fun DocumentLoadingSkeletonPreviewDark() {
    AppTheme(darkTheme = true) {
        DocumentLoadingSkeleton()
    }
}

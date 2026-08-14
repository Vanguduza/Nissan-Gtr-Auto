package com.joker.coolmall.feature.goods.skeleton

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.joker.coolmall.core.designsystem.theme.AppTheme
import com.joker.coolmall.core.designsystem.theme.ShapeMedium
import com.joker.coolmall.core.designsystem.theme.SpacePaddingMedium
import com.joker.coolmall.core.designsystem.theme.SpaceVerticalSmall
import com.joker.coolmall.core.ui.component.card.AppCard
import com.joker.coolmall.core.ui.component.skeleton.SkeletonBlock
import com.joker.coolmall.core.ui.component.skeleton.SkeletonTextLine

/**
 * 商品分类页面骨架屏
 *
 * 按商品分类页默认双列网格布局展示商品卡片占位内容。
 *
 * @author Joker.X
 */
@Composable
internal fun GoodsCategoryLoadingSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(SpacePaddingMedium)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(SpacePaddingMedium),
    ) {
        repeat(4) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SpacePaddingMedium),
            ) {
                repeat(2) {
                    GoodsCategoryGridSkeletonItem(
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * 商品分类网格骨架项
 *
 * @param modifier 修饰符
 * @author Joker.X
 */
@Composable
private fun GoodsCategoryGridSkeletonItem(
    modifier: Modifier = Modifier,
) {
    AppCard(modifier = modifier) {
        Column(
            verticalArrangement = Arrangement.spacedBy(SpaceVerticalSmall),
        ) {
            SkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                shape = ShapeMedium,
            )
            SkeletonTextLine(widthFraction = 0.82f)
            SkeletonTextLine(widthFraction = 1f)
            SkeletonTextLine(widthFraction = 0.44f)
        }
    }
}

/**
 * 商品分类骨架屏预览 - 浅色主题
 *
 * @author Joker.X
 */
@Preview(showBackground = true)
@Composable
private fun GoodsCategoryLoadingSkeletonPreviewLight() {
    AppTheme(darkTheme = false) {
        GoodsCategoryLoadingSkeleton()
    }
}

/**
 * 商品分类骨架屏预览 - 深色主题
 *
 * @author Joker.X
 */
@Preview(showBackground = true)
@Composable
private fun GoodsCategoryLoadingSkeletonPreviewDark() {
    AppTheme(darkTheme = true) {
        GoodsCategoryLoadingSkeleton()
    }
}

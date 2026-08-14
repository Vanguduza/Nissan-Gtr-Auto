package com.joker.coolmall.feature.main.skeleton

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joker.coolmall.core.designsystem.theme.AppTheme
import com.joker.coolmall.core.designsystem.theme.ShapeMedium
import com.joker.coolmall.core.designsystem.theme.SpaceHorizontalMedium
import com.joker.coolmall.core.designsystem.theme.SpaceHorizontalSmall
import com.joker.coolmall.core.designsystem.theme.SpacePaddingMedium
import com.joker.coolmall.core.designsystem.theme.SpaceVerticalMedium
import com.joker.coolmall.core.designsystem.theme.SpaceVerticalSmall
import com.joker.coolmall.core.designsystem.theme.SpaceVerticalXSmall
import com.joker.coolmall.core.ui.component.card.AppCard
import com.joker.coolmall.core.ui.component.skeleton.SkeletonBlock
import com.joker.coolmall.core.ui.component.skeleton.SkeletonCircle
import com.joker.coolmall.core.ui.component.skeleton.SkeletonTextLine

/**
 * 首页加载骨架屏
 *
 * 按首页实际模块顺序展示首屏占位内容，用于提升首页加载阶段的视觉连贯性。
 *
 * @author Joker.X
 */
@Composable
internal fun HomeLoadingSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = SpacePaddingMedium)
            .padding(top = SpacePaddingMedium)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(SpacePaddingMedium),
    ) {
        SkeletonBlock(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f),
            shape = ShapeMedium,
        )

        HomeCouponSkeleton()
        HomeCategorySkeleton()
        HomeFlashSaleSkeleton()

        HomeSectionTitleSkeleton()
        HomeRecommendSkeleton()
    }
}

/**
 * 首页标题骨架
 *
 * @author Joker.X
 */
@Composable
private fun HomeSectionTitleSkeleton() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpaceHorizontalSmall),
        modifier = Modifier.padding(vertical = SpaceVerticalSmall),
    ) {
        SkeletonBlock(
            modifier = Modifier
                .size(width = 3.dp, height = 18.dp)
                .clip(RoundedCornerShape(999.dp)),
            shape = RoundedCornerShape(999.dp),
        )
        SkeletonTextLine(
            modifier = Modifier.padding(vertical = 2.dp),
            widthFraction = 0.32f,
            height = 18.dp,
        )
    }
}

/**
 * 首页优惠券骨架
 *
 * @author Joker.X
 */
@Composable
private fun HomeCouponSkeleton() {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpacePaddingMedium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpaceHorizontalMedium),
        ) {
            SkeletonBlock(
                modifier = Modifier.size(28.dp),
                shape = RoundedCornerShape(4.dp),
            )
            SkeletonTextLine(
                modifier = Modifier.weight(1f),
                widthFraction = 1f,
            )
            SkeletonTextLine(
                modifier = Modifier.padding(vertical = 2.dp),
                widthFraction = 0.18f,
            )
        }
    }
}

/**
 * 首页分类骨架
 *
 * @author Joker.X
 */
@Composable
private fun HomeCategorySkeleton() {
    AppCard {
        repeat(2) { rowIndex ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                repeat(5) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        SkeletonCircle(
                            modifier = Modifier.fillMaxWidth(0.8f),
                            size = 50.dp,
                        )
                        SpaceVerticalXSmall()
                        SkeletonTextLine(
                            widthFraction = 0.72f,
                            height = 12.dp,
                        )
                    }
                }
            }
            if (rowIndex == 0) {
                SpaceVerticalSmall()
            }
        }
    }
}

/**
 * 首页限时精选骨架
 *
 * @author Joker.X
 */
@Composable
private fun HomeFlashSaleSkeleton() {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SpacePaddingMedium, vertical = SpaceVerticalMedium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkeletonBlock(
                modifier = Modifier.size(18.dp),
                shape = RoundedCornerShape(4.dp),
            )
            SkeletonTextLine(
                modifier = Modifier
                    .padding(start = SpaceHorizontalSmall)
                    .weight(1f),
                widthFraction = 1f,
            )
            SkeletonTextLine(
                widthFraction = 0.18f,
                height = 14.dp,
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(SpaceHorizontalSmall),
            modifier = Modifier.padding(SpacePaddingMedium),
        ) {
            items(4) {
                Column(
                    modifier = Modifier.fillParentMaxWidth(0.3f),
                    verticalArrangement = Arrangement.spacedBy(SpaceVerticalSmall),
                ) {
                    SkeletonBlock(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                    )
                    SkeletonTextLine(widthFraction = 0.84f)
                    SkeletonTextLine(widthFraction = 0.52f)
                }
            }
        }
    }
}

/**
 * 首页推荐列表骨架
 *
 * @author Joker.X
 */
@Composable
private fun HomeRecommendSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(SpacePaddingMedium)) {
        repeat(3) {
            AppCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(SpacePaddingMedium),
                ) {
                    SkeletonBlock(
                        modifier = Modifier
                            .size(108.dp)
                            .clip(ShapeMedium),
                        shape = ShapeMedium,
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = SpaceVerticalXSmall),
                        verticalArrangement = Arrangement.spacedBy(SpaceVerticalSmall),
                    ) {
                        SkeletonTextLine(widthFraction = 0.72f)
                        SkeletonTextLine(widthFraction = 1f)
                        SkeletonTextLine(widthFraction = 0.88f)
                        Spacer(modifier = Modifier.weight(1f))
                        SkeletonTextLine(widthFraction = 0.32f)
                    }
                }
            }
        }
    }
}

/**
 * 首页骨架屏预览 - 浅色主题
 *
 * @author Joker.X
 */
@Preview(showBackground = true)
@Composable
private fun HomeLoadingSkeletonPreviewLight() {
    AppTheme(darkTheme = false) {
        HomeLoadingSkeleton()
    }
}

/**
 * 首页骨架屏预览 - 深色主题
 *
 * @author Joker.X
 */
@Preview(showBackground = true)
@Composable
private fun HomeLoadingSkeletonPreviewDark() {
    AppTheme(darkTheme = true) {
        HomeLoadingSkeleton()
    }
}

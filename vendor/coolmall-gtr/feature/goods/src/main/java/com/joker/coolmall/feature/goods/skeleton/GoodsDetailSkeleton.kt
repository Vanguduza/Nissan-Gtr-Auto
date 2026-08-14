package com.joker.coolmall.feature.goods.skeleton

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joker.coolmall.core.designsystem.theme.AppTheme
import com.joker.coolmall.core.designsystem.theme.RadiusMedium
import com.joker.coolmall.core.designsystem.theme.ShapeSmall
import com.joker.coolmall.core.designsystem.theme.SpaceHorizontalSmall
import com.joker.coolmall.core.designsystem.theme.SpacePaddingMedium
import com.joker.coolmall.core.designsystem.theme.SpacePaddingXSmall
import com.joker.coolmall.core.designsystem.theme.SpaceVerticalMedium
import com.joker.coolmall.core.designsystem.theme.SpaceVerticalSmall
import com.joker.coolmall.core.designsystem.theme.SpaceVerticalXSmall
import com.joker.coolmall.core.ui.component.card.AppCard
import com.joker.coolmall.core.ui.component.skeleton.SkeletonBlock
import com.joker.coolmall.core.ui.component.skeleton.SkeletonCircle
import com.joker.coolmall.core.ui.component.skeleton.SkeletonTextLine

/**
 * 商品详情页面骨架屏
 *
 * 按详情页实际层级组合头图、信息卡、评论区、图文详情和底部操作栏。
 *
 * @author Joker.X
 */
@Composable
internal fun GoodsDetailLoadingSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        SkeletonBlock(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            shape = RoundedCornerShape(
                bottomStart = RadiusMedium,
                bottomEnd = RadiusMedium,
            ),
        )

        Column(
            modifier = Modifier.padding(SpacePaddingMedium),
            verticalArrangement = Arrangement.spacedBy(SpaceVerticalMedium),
        ) {
            GoodsInfoSkeletonCard()
            GoodsDeliverySkeletonCard()
            GoodsCommentsSkeletonCard()
            GoodsContentSkeletonSection()
        }

        Spacer(modifier = Modifier.height(120.dp))
    }
}

/**
 * 商品详情信息卡骨架
 *
 * @author Joker.X
 */
@Composable
private fun GoodsInfoSkeletonCard() {
    AppCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SkeletonTextLine(
                widthFraction = 0.34f,
                height = 28.dp,
            )
            SkeletonBlock(
                modifier = Modifier
                    .width(72.dp)
                    .height(28.dp),
                shape = RoundedCornerShape(999.dp),
            )
        }

        SpaceVerticalMedium()

        Row(horizontalArrangement = Arrangement.spacedBy(SpaceHorizontalSmall)) {
            repeat(2) {
                SkeletonBlock(
                    modifier = Modifier
                        .width(92.dp)
                        .height(28.dp),
                    shape = RoundedCornerShape(SpaceVerticalXSmall),
                )
            }
        }

        SpaceVerticalMedium()

        SkeletonTextLine(widthFraction = 0.86f, height = 24.dp)
        SpaceVerticalXSmall()
        SkeletonTextLine(widthFraction = 1f)

        SpaceVerticalMedium()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = ShapeSmall,
                )
                .padding(SpacePaddingMedium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SkeletonTextLine(widthFraction = 0.56f)
            SkeletonBlock(
                modifier = Modifier
                    .size(18.dp),
                shape = RoundedCornerShape(999.dp),
            )
        }
    }
}

/**
 * 商品详情配送卡骨架
 *
 * @author Joker.X
 */
@Composable
private fun GoodsDeliverySkeletonCard() {
    AppCard {
        Column(
            verticalArrangement = Arrangement.spacedBy(SpaceVerticalXSmall),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = SpaceVerticalXSmall),
            ) {
                GoodsSectionTitleSkeleton()
            }

            GoodsListItemSkeleton()
            GoodsListItemSkeleton(showDivider = false)
        }
    }
}

/**
 * 商品详情评论卡骨架
 *
 * @author Joker.X
 */
@Composable
private fun GoodsCommentsSkeletonCard() {
    Card {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SpacePaddingMedium, vertical = SpaceVerticalMedium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GoodsSectionTitleSkeleton(modifier = Modifier.weight(1f))
                SkeletonTextLine(
                    widthFraction = 0.18f,
                    height = 14.dp,
                )
            }

            repeat(2) { index ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SpacePaddingMedium, vertical = SpaceVerticalSmall),
                    horizontalArrangement = Arrangement.spacedBy(SpacePaddingMedium),
                ) {
                    SkeletonCircle(size = 40.dp)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(SpaceVerticalSmall),
                    ) {
                        SkeletonTextLine(widthFraction = 0.32f)
                        SkeletonTextLine(widthFraction = 1f)
                        SkeletonTextLine(widthFraction = 0.74f)
                    }
                }
                if (index == 0) {
                    SkeletonBlock(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .padding(horizontal = SpacePaddingMedium),
                        shape = RoundedCornerShape(0.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                        shimmerEnabled = false,
                    )
                }
            }
        }
    }
}

/**
 * 商品详情图文内容骨架
 *
 * @author Joker.X
 */
@Composable
private fun GoodsContentSkeletonSection() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = RadiusMedium, topEnd = RadiusMedium),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SpacePaddingMedium, vertical = SpaceVerticalMedium),
            ) {
                GoodsSectionTitleSkeleton()
            }

            repeat(3) {
                SkeletonBlock(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .padding(bottom = if (it == 2) 0.dp else SpaceVerticalXSmall),
                    shape = if (it == 2) {
                        RoundedCornerShape(
                            bottomStart = RadiusMedium,
                            bottomEnd = RadiusMedium,
                        )
                    } else {
                        RoundedCornerShape(0.dp)
                    },
                )
            }
        }
    }
}

/**
 * 商品详情分组标题骨架
 *
 * @param modifier 修饰符
 * @author Joker.X
 */
@Composable
private fun GoodsSectionTitleSkeleton(
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpaceHorizontalSmall),
    ) {
        SkeletonBlock(
            modifier = Modifier
                .width(3.dp)
                .height(18.dp),
            shape = RoundedCornerShape(999.dp),
        )
        SkeletonTextLine(
            widthFraction = 0.28f,
            height = 18.dp,
        )
    }
}

/**
 * 商品详情列表项骨架
 *
 * @param showDivider 是否显示分割间距
 * @author Joker.X
 */
@Composable
private fun GoodsListItemSkeleton(
    showDivider: Boolean = true,
) {
    Column(
        modifier = Modifier.padding(horizontal = SpacePaddingMedium),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = SpaceVerticalSmall),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SkeletonTextLine(widthFraction = 0.18f)
            SkeletonTextLine(
                modifier = Modifier.width(120.dp),
                widthFraction = 1f,
            )
        }
        if (showDivider) {
            Spacer(modifier = Modifier.height(SpacePaddingXSmall))
        }
    }
}


/**
 * 商品详情骨架屏预览 - 浅色主题
 *
 * @author Joker.X
 */
@Preview(showBackground = true)
@Composable
private fun GoodsDetailLoadingSkeletonPreviewLight() {
    AppTheme(darkTheme = false) {
        GoodsDetailLoadingSkeleton()
    }
}

/**
 * 商品详情骨架屏预览 - 深色主题
 *
 * @author Joker.X
 */
@Preview(showBackground = true)
@Composable
private fun GoodsDetailLoadingSkeletonPreviewDark() {
    AppTheme(darkTheme = true) {
        GoodsDetailLoadingSkeleton()
    }
}

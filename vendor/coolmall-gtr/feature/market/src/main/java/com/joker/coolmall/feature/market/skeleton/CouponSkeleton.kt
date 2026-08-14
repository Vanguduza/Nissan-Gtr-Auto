package com.joker.coolmall.feature.market.skeleton

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joker.coolmall.core.designsystem.theme.AppTheme
import com.joker.coolmall.core.designsystem.theme.ShapeSmall
import com.joker.coolmall.core.designsystem.theme.SpaceHorizontalMedium
import com.joker.coolmall.core.designsystem.theme.SpaceHorizontalXSmall
import com.joker.coolmall.core.designsystem.theme.SpacePaddingMedium
import com.joker.coolmall.core.designsystem.theme.SpaceVerticalMedium
import com.joker.coolmall.core.designsystem.theme.SpaceVerticalSmall
import com.joker.coolmall.core.ui.component.divider.WeDivider
import com.joker.coolmall.core.ui.component.skeleton.SkeletonBlock
import com.joker.coolmall.core.ui.component.skeleton.SkeletonTextLine

/**
 * 优惠券列表骨架屏
 *
 * 按优惠券列表卡片的图标、文本信息和操作按钮结构展示占位内容。
 *
 * @author Joker.X
 */
@Composable
internal fun CouponLoadingSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(SpacePaddingMedium),
        verticalArrangement = Arrangement.spacedBy(SpacePaddingMedium),
    ) {
        repeat(3) {
            CouponItemSkeleton()
        }
    }
}

/**
 * 优惠券条目骨架屏
 *
 * @author Joker.X
 */
@Composable
private fun CouponItemSkeleton() {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(SpacePaddingMedium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkeletonBlock(
                    modifier = Modifier.size(46.dp),
                    shape = ShapeSmall,
                )

                Spacer(modifier = Modifier.width(SpaceHorizontalMedium))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(SpaceVerticalSmall),
                ) {
                    SkeletonTextLine(widthFraction = 0.82f)
                    SkeletonTextLine(widthFraction = 0.48f)
                }

                Spacer(modifier = Modifier.width(SpaceHorizontalMedium))

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(SpaceHorizontalXSmall),
                ) {
                    SkeletonTextLine(widthFraction = 0.42f)
                    SkeletonTextLine(widthFraction = 0.58f)
                }
            }

            WeDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = SpacePaddingMedium,
                        vertical = SpaceVerticalMedium,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SkeletonTextLine(
                        modifier = Modifier.fillMaxWidth(0.32f),
                        widthFraction = 1f,
                    )
                    Spacer(modifier = Modifier.width(SpaceHorizontalXSmall))
                    SkeletonBlock(
                        modifier = Modifier
                            .width(16.dp)
                            .height(16.dp),
                        shape = ShapeSmall,
                    )
                }

                Spacer(modifier = Modifier.width(SpaceHorizontalMedium))

                SkeletonBlock(
                    modifier = Modifier
                        .width(92.dp)
                        .height(32.dp),
                    shape = ShapeSmall,
                )
            }
        }
    }
}

/**
 * 优惠券列表骨架屏预览 - 浅色主题
 *
 * @author Joker.X
 */
@Preview(showBackground = true)
@Composable
private fun CouponLoadingSkeletonPreviewLight() {
    AppTheme(darkTheme = false) {
        CouponLoadingSkeleton()
    }
}

/**
 * 优惠券列表骨架屏预览 - 深色主题
 *
 * @author Joker.X
 */
@Preview(showBackground = true)
@Composable
private fun CouponLoadingSkeletonPreviewDark() {
    AppTheme(darkTheme = true) {
        CouponLoadingSkeleton()
    }
}

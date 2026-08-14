package com.joker.coolmall.feature.order.skeleton

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joker.coolmall.core.designsystem.component.SpaceBetweenRow
import com.joker.coolmall.core.designsystem.theme.AppTheme
import com.joker.coolmall.core.designsystem.theme.ShapeSmall
import com.joker.coolmall.core.designsystem.theme.SpaceHorizontalSmall
import com.joker.coolmall.core.designsystem.theme.SpacePaddingMedium
import com.joker.coolmall.core.designsystem.theme.SpaceVerticalMedium
import com.joker.coolmall.core.designsystem.theme.SpaceVerticalSmall
import com.joker.coolmall.core.ui.component.divider.WeDivider
import com.joker.coolmall.core.ui.component.skeleton.SkeletonBlock
import com.joker.coolmall.core.ui.component.skeleton.SkeletonTextLine

/**
 * 订单列表骨架屏
 *
 * 按订单列表页卡片结构展示订单编号、商品缩略图、价格信息与操作按钮占位内容。
 *
 * @author Joker.X
 */
@Composable
internal fun OrderListLoadingSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(SpacePaddingMedium),
        verticalArrangement = Arrangement.spacedBy(SpacePaddingMedium),
    ) {
        repeat(3) {
            OrderListCardSkeleton()
        }
    }
}

/**
 * 订单列表卡片骨架屏
 *
 * @author Joker.X
 */
@Composable
private fun OrderListCardSkeleton() {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            SpaceBetweenRow(
                modifier = Modifier.padding(SpacePaddingMedium),
            ) {
                SkeletonTextLine(widthFraction = 0.38f)
                SkeletonTextLine(widthFraction = 0.18f)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SpacePaddingMedium)
                    .padding(bottom = SpacePaddingMedium),
            ) {
                repeat(3) { index ->
                    SkeletonBlock(
                        modifier = Modifier.size(80.dp),
                        shape = ShapeSmall,
                    )

                    if (index != 2) {
                        Spacer(modifier = Modifier.width(SpaceHorizontalSmall))
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Column(
                    verticalArrangement = Arrangement.spacedBy(SpaceVerticalSmall),
                ) {
                    SkeletonTextLine(widthFraction = 0.64f)
                    SkeletonTextLine(widthFraction = 0.44f)
                }
            }

            WeDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(SpacePaddingMedium),
                horizontalArrangement = Arrangement.End,
            ) {
                repeat(2) { index ->
                    SkeletonBlock(
                        modifier = Modifier
                            .width(74.dp)
                            .height(30.dp),
                        shape = ShapeSmall,
                    )

                    if (index != 1) {
                        Spacer(modifier = Modifier.width(SpaceHorizontalSmall))
                    }
                }
            }
        }
    }
}

/**
 * 订单列表骨架屏预览 - 浅色主题
 *
 * @author Joker.X
 */
@Preview(showBackground = true)
@Composable
private fun OrderListLoadingSkeletonPreviewLight() {
    AppTheme(darkTheme = false) {
        OrderListLoadingSkeleton()
    }
}

/**
 * 订单列表骨架屏预览 - 深色主题
 *
 * @author Joker.X
 */
@Preview(showBackground = true)
@Composable
private fun OrderListLoadingSkeletonPreviewDark() {
    AppTheme(darkTheme = true) {
        OrderListLoadingSkeleton()
    }
}

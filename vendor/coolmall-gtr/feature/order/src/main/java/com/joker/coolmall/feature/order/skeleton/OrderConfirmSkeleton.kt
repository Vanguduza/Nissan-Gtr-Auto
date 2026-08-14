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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joker.coolmall.core.designsystem.component.SpaceBetweenRow
import com.joker.coolmall.core.designsystem.theme.AppTheme
import com.joker.coolmall.core.designsystem.theme.ShapeMedium
import com.joker.coolmall.core.designsystem.theme.ShapeSmall
import com.joker.coolmall.core.designsystem.theme.SpaceHorizontalMedium
import com.joker.coolmall.core.designsystem.theme.SpacePaddingLarge
import com.joker.coolmall.core.designsystem.theme.SpacePaddingMedium
import com.joker.coolmall.core.designsystem.theme.SpacePaddingSmall
import com.joker.coolmall.core.designsystem.theme.SpaceVerticalMedium
import com.joker.coolmall.core.designsystem.theme.SpaceVerticalSmall
import com.joker.coolmall.core.designsystem.theme.SpaceVerticalXSmall
import com.joker.coolmall.core.ui.component.address.AddressCardSkeleton
import com.joker.coolmall.core.ui.component.skeleton.SkeletonBlock
import com.joker.coolmall.core.ui.component.skeleton.SkeletonTextLine

/**
 * 确认订单骨架屏
 *
 * 按确认订单页面的地址、商品、价格和备注结构展示骨架占位内容。
 *
 * @author Joker.X
 */
@Composable
internal fun OrderConfirmLoadingSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(SpacePaddingMedium),
        verticalArrangement = Arrangement.spacedBy(SpacePaddingMedium),
    ) {
        AddressCardSkeleton(addressSelected = true)
        OrderGoodsCardSkeleton()
        OrderGoodsCardSkeleton()
        OrderPriceDetailSkeleton(showDiscount = true)
        OrderRemarkSkeleton()
    }
}

/**
 * 订单商品卡片骨架屏
 *
 * @author Joker.X
 */
@Composable
internal fun OrderGoodsCardSkeleton() {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(vertical = SpacePaddingMedium),
            verticalArrangement = Arrangement.spacedBy(SpaceVerticalMedium),
        ) {
            SkeletonTextLine(
                modifier = Modifier.padding(horizontal = SpacePaddingMedium),
                widthFraction = 0.38f,
            )

            OrderGoodsItemSkeleton()
        }
    }
}

/**
 * 订单商品条目骨架屏
 *
 * @author Joker.X
 */
@Composable
private fun OrderGoodsItemSkeleton() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = SpacePaddingLarge,
                end = SpacePaddingLarge,
                bottom = SpacePaddingLarge,
            ),
    ) {
        SkeletonBlock(
            modifier = Modifier.size(90.dp),
            shape = ShapeSmall,
        )

        Spacer(modifier = Modifier.width(SpaceHorizontalMedium))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(SpaceVerticalMedium),
        ) {
            SkeletonBlock(
                modifier = Modifier.fillMaxWidth(0.48f),
                shape = RoundedCornerShape(4.dp),
            )

            SpaceBetweenRow {
                SkeletonTextLine(widthFraction = 0.32f)
                SkeletonTextLine(widthFraction = 0.16f)
            }
        }
    }
}

/**
 * 订单价格明细骨架屏
 *
 * @param showDiscount 是否显示优惠信息
 * @author Joker.X
 */
@Composable
private fun OrderPriceDetailSkeleton(
    showDiscount: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(SpacePaddingMedium),
            verticalArrangement = Arrangement.spacedBy(SpaceVerticalMedium),
        ) {
            SkeletonTextLine(widthFraction = 0.28f)
            OrderInfoRowSkeleton()
            OrderInfoRowSkeleton()
            if (showDiscount) {
                OrderInfoRowSkeleton()
            }
            OrderInfoRowSkeleton(last = true)
        }
    }
}

/**
 * 订单备注骨架屏
 *
 * @author Joker.X
 */
@Composable
private fun OrderRemarkSkeleton() {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(SpacePaddingMedium),
            verticalArrangement = Arrangement.spacedBy(SpaceVerticalMedium),
        ) {
            SkeletonTextLine(widthFraction = 0.24f)
            SkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp),
                shape = ShapeMedium,
            )
        }
    }
}

/**
 * 订单信息行骨架屏
 *
 * @param last 是否为最后一行
 * @author Joker.X
 */
@Composable
internal fun OrderInfoRowSkeleton(
    last: Boolean = false,
) {
    SpaceBetweenRow {
        SkeletonTextLine(widthFraction = 0.26f)
        SkeletonTextLine(widthFraction = if (last) 0.2f else 0.3f)
    }
}

/**
 * 确认订单骨架屏预览 - 浅色主题
 *
 * @author Joker.X
 */
@Preview(showBackground = true)
@Composable
private fun OrderConfirmLoadingSkeletonPreviewLight() {
    AppTheme(darkTheme = false) {
        OrderConfirmLoadingSkeleton()
    }
}

/**
 * 确认订单骨架屏预览 - 深色主题
 *
 * @author Joker.X
 */
@Preview(showBackground = true)
@Composable
private fun OrderConfirmLoadingSkeletonPreviewDark() {
    AppTheme(darkTheme = true) {
        OrderConfirmLoadingSkeleton()
    }
}

package com.joker.coolmall.feature.order.skeleton

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.joker.coolmall.core.designsystem.theme.AppTheme
import com.joker.coolmall.core.designsystem.theme.SpacePaddingMedium
import com.joker.coolmall.core.designsystem.theme.SpaceVerticalMedium
import com.joker.coolmall.core.ui.component.address.AddressCardSkeleton
import com.joker.coolmall.core.ui.component.skeleton.SkeletonTextLine

/**
 * 订单详情骨架屏
 *
 * 按订单详情页面的地址、商品、价格和订单信息结构展示骨架占位内容。
 *
 * @author Joker.X
 */
@Composable
internal fun OrderDetailLoadingSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(SpacePaddingMedium)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(SpacePaddingMedium),
    ) {
        AddressCardSkeleton()
        OrderGoodsCardSkeleton()
        OrderGoodsCardSkeleton()
        OrderDetailPriceSkeleton()
        OrderDetailInfoSkeleton()
    }
}

/**
 * 订单详情价格卡片骨架屏
 *
 * @author Joker.X
 */
@Composable
private fun OrderDetailPriceSkeleton() {
    Card {
        Column(
            modifier = Modifier.padding(SpacePaddingMedium),
            verticalArrangement = Arrangement.spacedBy(SpaceVerticalMedium),
        ) {
            SkeletonTextLine(widthFraction = 0.28f)
            OrderInfoRowSkeleton()
            OrderInfoRowSkeleton()
            OrderInfoRowSkeleton(last = true)
        }
    }
}

/**
 * 订单详情信息卡片骨架屏
 *
 * @author Joker.X
 */
@Composable
private fun OrderDetailInfoSkeleton() {
    Card {
        Column(
            modifier = Modifier.padding(SpacePaddingMedium),
            verticalArrangement = Arrangement.spacedBy(SpaceVerticalMedium),
        ) {
            SkeletonTextLine(widthFraction = 0.24f)
            repeat(4) {
                OrderInfoRowSkeleton()
            }
        }
    }
}

/**
 * 订单详情骨架屏预览 - 浅色主题
 *
 * @author Joker.X
 */
@Preview(showBackground = true)
@Composable
private fun OrderDetailLoadingSkeletonPreviewLight() {
    AppTheme(darkTheme = false) {
        OrderDetailLoadingSkeleton()
    }
}

/**
 * 订单详情骨架屏预览 - 深色主题
 *
 * @author Joker.X
 */
@Preview(showBackground = true)
@Composable
private fun OrderDetailLoadingSkeletonPreviewDark() {
    AppTheme(darkTheme = true) {
        OrderDetailLoadingSkeleton()
    }
}

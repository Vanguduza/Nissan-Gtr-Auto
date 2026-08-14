package com.joker.coolmall.feature.order.skeleton

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joker.coolmall.core.designsystem.theme.AppTheme
import com.joker.coolmall.core.designsystem.theme.ShapeSmall
import com.joker.coolmall.core.designsystem.theme.SpaceHorizontalMedium
import com.joker.coolmall.core.designsystem.theme.SpacePaddingMedium
import com.joker.coolmall.core.designsystem.theme.SpaceVerticalMedium
import com.joker.coolmall.core.ui.component.address.AddressCardSkeleton
import com.joker.coolmall.core.ui.component.skeleton.SkeletonBlock
import com.joker.coolmall.core.ui.component.skeleton.SkeletonCircle
import com.joker.coolmall.core.ui.component.skeleton.SkeletonTextLine

/**
 * 订单物流骨架屏
 *
 * 按订单物流页面的地址、物流信息和物流轨迹结构展示骨架占位内容。
 *
 * @author Joker.X
 */
@Composable
internal fun OrderLogisticsLoadingSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(SpacePaddingMedium)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(SpacePaddingMedium),
    ) {
        AddressCardSkeleton()
        OrderLogisticsInfoSkeleton()
        OrderLogisticsTrackSkeleton()
    }
}

/**
 * 订单物流信息骨架屏
 *
 * @author Joker.X
 */
@Composable
private fun OrderLogisticsInfoSkeleton() {
    Card {
        Column(
            modifier = Modifier.padding(SpacePaddingMedium),
            verticalArrangement = Arrangement.spacedBy(SpaceVerticalMedium),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SpaceHorizontalMedium),
            ) {
                SkeletonTextLine(
                    modifier = Modifier.weight(1f),
                    widthFraction = 0.28f,
                )
                SkeletonBlock(
                    modifier = Modifier
                        .width(18.dp)
                        .height(18.dp),
                    shape = ShapeSmall,
                )
            }
            repeat(4) {
                OrderInfoRowSkeleton()
            }
        }
    }
}

/**
 * 订单物流轨迹骨架屏
 *
 * @author Joker.X
 */
@Composable
private fun OrderLogisticsTrackSkeleton() {
    Card {
        Column(
            modifier = Modifier.padding(SpacePaddingMedium),
            verticalArrangement = Arrangement.spacedBy(SpaceVerticalMedium),
        ) {
            SkeletonTextLine(widthFraction = 0.24f)
            repeat(4) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(SpaceHorizontalMedium),
                ) {
                    SkeletonCircle(size = 14.dp)

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SkeletonTextLine(widthFraction = 0.9f)
                        SkeletonTextLine(widthFraction = 0.56f)
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                    SkeletonTextLine(widthFraction = 0.18f)
                }
            }
        }
    }
}

/**
 * 订单物流骨架屏预览 - 浅色主题
 *
 * @author Joker.X
 */
@Preview(showBackground = true)
@Composable
private fun OrderLogisticsLoadingSkeletonPreviewLight() {
    AppTheme(darkTheme = false) {
        OrderLogisticsLoadingSkeleton()
    }
}

/**
 * 订单物流骨架屏预览 - 深色主题
 *
 * @author Joker.X
 */
@Preview(showBackground = true)
@Composable
private fun OrderLogisticsLoadingSkeletonPreviewDark() {
    AppTheme(darkTheme = true) {
        OrderLogisticsLoadingSkeleton()
    }
}

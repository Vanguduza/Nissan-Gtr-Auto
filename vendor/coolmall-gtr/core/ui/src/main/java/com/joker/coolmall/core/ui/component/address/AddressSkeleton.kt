package com.joker.coolmall.core.ui.component.address

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joker.coolmall.core.designsystem.theme.AppTheme
import com.joker.coolmall.core.designsystem.theme.ShapeMedium
import com.joker.coolmall.core.designsystem.theme.SpaceHorizontalLarge
import com.joker.coolmall.core.designsystem.theme.SpacePaddingMedium
import com.joker.coolmall.core.designsystem.theme.SpaceVerticalMedium
import com.joker.coolmall.core.ui.component.divider.WeDivider
import com.joker.coolmall.core.ui.component.skeleton.SkeletonBlock
import com.joker.coolmall.core.ui.component.skeleton.SkeletonCircle
import com.joker.coolmall.core.ui.component.skeleton.SkeletonTextLine

/**
 * 收货地址卡片骨架屏
 *
 * @param modifier 修饰符
 * @param showActionButtons 是否显示操作按钮
 * @param addressSelected 是否显示选中箭头
 * @param showBottomBar 是否显示底部栏
 * @author Joker.X
 */
@Composable
fun AddressCardSkeleton(
    modifier: Modifier = Modifier,
    showActionButtons: Boolean = false,
    addressSelected: Boolean = false,
    showBottomBar: Boolean = true,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(SpacePaddingMedium),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SkeletonTextLine(widthFraction = 0.54f)
                    SkeletonTextLine(widthFraction = 0.92f)
                }

                when {
                    showActionButtons -> {
                        Spacer(modifier = Modifier.width(SpaceHorizontalLarge))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SkeletonCircle(size = 32.dp)
                            SkeletonCircle(size = 32.dp)
                        }
                    }

                    addressSelected -> {
                        Spacer(modifier = Modifier.width(SpaceHorizontalLarge))
                        SkeletonBlock(
                            modifier = Modifier
                                .width(18.dp)
                                .height(18.dp),
                            shape = ShapeMedium,
                        )
                    }
                }
            }

            if (showBottomBar) {
                WeDivider()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = SpaceHorizontalLarge,
                            vertical = SpaceVerticalMedium,
                        ),
                ) {
                    SkeletonTextLine(
                        modifier = Modifier.weight(1f),
                        widthFraction = 0.42f,
                    )

                    Spacer(modifier = Modifier.width(SpaceHorizontalLarge))

                    SkeletonTextLine(
                        modifier = Modifier.weight(1f),
                        widthFraction = 0.64f,
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    SkeletonBlock(
                        modifier = Modifier
                            .width(52.dp)
                            .height(22.dp),
                        shape = ShapeMedium,
                    )
                }
            }
        }
    }
}

/**
 * 收货地址卡片骨架屏预览 - 浅色主题
 *
 * @author Joker.X
 */
@Preview(showBackground = true)
@Composable
private fun AddressCardSkeletonPreviewLight() {
    AppTheme(darkTheme = false) {
        AddressCardSkeleton(
            modifier = Modifier.padding(SpacePaddingMedium),
            showActionButtons = true,
        )
    }
}

/**
 * 收货地址卡片骨架屏预览 - 深色主题
 *
 * @author Joker.X
 */
@Preview(showBackground = true)
@Composable
private fun AddressCardSkeletonPreviewDark() {
    AppTheme(darkTheme = true) {
        AddressCardSkeleton(
            modifier = Modifier.padding(SpacePaddingMedium),
            showActionButtons = true,
        )
    }
}

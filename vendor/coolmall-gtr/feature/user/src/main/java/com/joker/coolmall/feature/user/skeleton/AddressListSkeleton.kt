package com.joker.coolmall.feature.user.skeleton

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.joker.coolmall.core.designsystem.theme.AppTheme
import com.joker.coolmall.core.designsystem.theme.SpacePaddingMedium
import com.joker.coolmall.core.ui.component.address.AddressCardSkeleton

/**
 * 收货地址列表骨架屏
 *
 * 按地址列表页面的卡片间距和内边距展示骨架占位内容。
 *
 * @author Joker.X
 */
@Composable
internal fun AddressListLoadingSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(SpacePaddingMedium),
        verticalArrangement = Arrangement.spacedBy(SpacePaddingMedium),
    ) {
        repeat(3) {
            AddressCardSkeleton(showActionButtons = true)
        }
    }
}

/**
 * 收货地址列表骨架屏预览 - 浅色主题
 *
 * @author Joker.X
 */
@Preview(showBackground = true)
@Composable
private fun AddressListLoadingSkeletonPreviewLight() {
    AppTheme(darkTheme = false) {
        AddressListLoadingSkeleton()
    }
}

/**
 * 收货地址列表骨架屏预览 - 深色主题
 *
 * @author Joker.X
 */
@Preview(showBackground = true)
@Composable
private fun AddressListLoadingSkeletonPreviewDark() {
    AppTheme(darkTheme = true) {
        AddressListLoadingSkeleton()
    }
}

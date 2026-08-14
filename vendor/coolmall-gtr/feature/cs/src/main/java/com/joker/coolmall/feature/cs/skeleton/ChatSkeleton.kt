package com.joker.coolmall.feature.cs.skeleton

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.joker.coolmall.core.designsystem.theme.AppTheme
import com.joker.coolmall.core.designsystem.theme.ShapeSmall
import com.joker.coolmall.core.designsystem.theme.SpaceHorizontalSmall
import com.joker.coolmall.core.designsystem.theme.SpacePaddingLarge
import com.joker.coolmall.core.designsystem.theme.SpacePaddingMedium
import com.joker.coolmall.core.designsystem.theme.SpacePaddingSmall
import com.joker.coolmall.core.designsystem.theme.SpaceVerticalMedium
import com.joker.coolmall.core.designsystem.theme.SpaceVerticalSmall
import com.joker.coolmall.core.ui.component.skeleton.SkeletonBlock
import com.joker.coolmall.core.ui.component.skeleton.SkeletonCircle
import com.joker.coolmall.core.ui.component.skeleton.SkeletonTextLine

/**
 * 客服消息气泡骨架形状
 */
private val OtherChatBubbleSkeletonShape = RoundedCornerShape(
    topStart = 4.dp,
    topEnd = 16.dp,
    bottomStart = 16.dp,
    bottomEnd = 16.dp,
)

/**
 * 用户消息气泡骨架形状
 */
private val UserChatBubbleSkeletonShape = RoundedCornerShape(
    topStart = 16.dp,
    topEnd = 4.dp,
    bottomStart = 16.dp,
    bottomEnd = 16.dp,
)

/**
 * 客服聊天骨架屏
 *
 * 按聊天页的时间头部、左右消息气泡和底部输入栏结构展示占位内容。
 *
 * @author Joker.X
 */
@Composable
internal fun ChatLoadingSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = SpacePaddingMedium),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = SpacePaddingSmall, bottom = SpacePaddingMedium),
            verticalArrangement = Arrangement.spacedBy(SpaceVerticalMedium),
        ) {
            ChatDayHeaderSkeleton()
            ChatOtherMessageSkeleton(
                nameWidthFraction = 0.14f,
                bubbleWidth = 84.dp,
            )
            ChatUserMessageSkeleton(
                nameWidthFraction = 0.12f,
                bubbleWidth = 66.dp,
            )
            ChatOtherMessageSkeleton(
                nameWidthFraction = 0.16f,
                bubbleWidth = 92.dp,
            )
            ChatUserMessageSkeleton(
                nameWidthFraction = 0.12f,
                bubbleWidth = 74.dp,
            )
        }

        ChatInputSkeleton()
    }
}

/**
 * 聊天日期头部骨架屏
 *
 * @author Joker.X
 */
@Composable
private fun ChatDayHeaderSkeleton() {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        SkeletonBlock(
            modifier = Modifier
                .width(88.dp)
                .height(24.dp),
            shape = ShapeSmall,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }
}

/**
 * 客服消息骨架屏
 *
 * @param nameWidthFraction 昵称占位宽度比例
 * @param bubbleWidth 气泡宽度
 * @author Joker.X
 */
@Composable
private fun ChatOtherMessageSkeleton(
    nameWidthFraction: Float,
    bubbleWidth: androidx.compose.ui.unit.Dp,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        SkeletonCircle(size = 36.dp)
        Spacer(modifier = Modifier.width(SpaceHorizontalSmall))
        Column(
            verticalArrangement = Arrangement.spacedBy(SpaceVerticalSmall),
        ) {
            SkeletonTextLine(widthFraction = nameWidthFraction)
            SkeletonBlock(
                modifier = Modifier
                    .width(bubbleWidth)
                    .height(42.dp),
                shape = OtherChatBubbleSkeletonShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            )
        }
    }
}

/**
 * 用户消息骨架屏
 *
 * @param nameWidthFraction 昵称占位宽度比例
 * @param bubbleWidth 气泡宽度
 * @author Joker.X
 */
@Composable
private fun ChatUserMessageSkeleton(
    nameWidthFraction: Float,
    bubbleWidth: androidx.compose.ui.unit.Dp,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(SpaceVerticalSmall),
        ) {
            SkeletonTextLine(widthFraction = nameWidthFraction)
            SkeletonBlock(
                modifier = Modifier
                    .width(bubbleWidth)
                    .height(42.dp),
                shape = UserChatBubbleSkeletonShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
            )
        }
        Spacer(modifier = Modifier.width(SpaceHorizontalSmall))
        SkeletonCircle(size = 36.dp)
    }
}

/**
 * 聊天输入栏骨架屏
 *
 * @author Joker.X
 */
@Composable
private fun ChatInputSkeleton() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = SpacePaddingLarge)
            .padding(top = SpacePaddingSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkeletonBlock(
            modifier = Modifier
                .weight(1f)
                .height(44.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
        Spacer(modifier = Modifier.width(SpaceHorizontalSmall))
        SkeletonBlock(
            modifier = Modifier
                .width(68.dp)
                .height(36.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
        )
    }
}

/**
 * 客服聊天骨架屏预览 - 浅色主题
 *
 * @author Joker.X
 */
@Preview(showBackground = true)
@Composable
private fun ChatLoadingSkeletonPreviewLight() {
    AppTheme(darkTheme = false) {
        ChatLoadingSkeleton()
    }
}

/**
 * 客服聊天骨架屏预览 - 深色主题
 *
 * @author Joker.X
 */
@Preview(showBackground = true)
@Composable
private fun ChatLoadingSkeletonPreviewDark() {
    AppTheme(darkTheme = true) {
        ChatLoadingSkeleton()
    }
}

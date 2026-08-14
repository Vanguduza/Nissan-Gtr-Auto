package com.joker.coolmall.feature.feedback.skeleton

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
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
import com.joker.coolmall.core.ui.component.skeleton.SkeletonBlock
import com.joker.coolmall.core.ui.component.skeleton.SkeletonTextLine

/**
 * 反馈列表骨架屏
 *
 * 按反馈列表卡片的标签、时间、正文、图片和回复区域展示占位内容。
 *
 * @author Joker.X
 */
@Composable
internal fun FeedbackListLoadingSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(SpacePaddingMedium),
        verticalArrangement = Arrangement.spacedBy(SpacePaddingMedium),
    ) {
        FeedbackItemSkeleton(showImages = true, showReply = true)
        FeedbackItemSkeleton(showImages = false, showReply = false)
    }
}

/**
 * 反馈条目骨架屏
 *
 * @param showImages 是否显示图片区占位
 * @param showReply 是否显示客服回复区域占位
 * @author Joker.X
 */
@Composable
private fun FeedbackItemSkeleton(
    showImages: Boolean,
    showReply: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpacePaddingMedium),
        ) {
            SpaceBetweenRow(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(SpaceHorizontalSmall),
                ) {
                    SkeletonBlock(
                        modifier = Modifier
                            .width(56.dp)
                            .height(22.dp),
                    )
                    SkeletonBlock(
                        modifier = Modifier
                            .width(64.dp)
                            .height(22.dp),
                    )
                }

                SkeletonTextLine(widthFraction = 0.24f)
            }

            Column(
                modifier = Modifier.padding(top = SpaceVerticalMedium),
                verticalArrangement = Arrangement.spacedBy(SpaceVerticalSmall),
            ) {
                SkeletonTextLine(widthFraction = 0.94f)
                SkeletonTextLine(widthFraction = 0.82f)
            }

            if (showImages) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = SpaceVerticalMedium),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    repeat(3) {
                        SkeletonBlock(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f),
                            shape = ShapeSmall,
                        )
                    }
                }
            }

            if (showReply) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = SpaceVerticalMedium)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = ShapeSmall,
                        )
                        .padding(SpacePaddingMedium),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(SpaceVerticalSmall),
                    ) {
                        SkeletonTextLine(widthFraction = 0.22f)
                        SkeletonTextLine(widthFraction = 0.88f)
                    }
                }
            }
        }
    }
}

/**
 * 反馈列表骨架屏预览 - 浅色主题
 *
 * @author Joker.X
 */
@Preview(showBackground = true)
@Composable
private fun FeedbackListLoadingSkeletonPreviewLight() {
    AppTheme(darkTheme = false) {
        FeedbackListLoadingSkeleton()
    }
}

/**
 * 反馈列表骨架屏预览 - 深色主题
 *
 * @author Joker.X
 */
@Preview(showBackground = true)
@Composable
private fun FeedbackListLoadingSkeletonPreviewDark() {
    AppTheme(darkTheme = true) {
        FeedbackListLoadingSkeleton()
    }
}

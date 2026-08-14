package com.joker.coolmall.feature.goods.skeleton

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.joker.coolmall.core.designsystem.theme.SpaceVerticalSmall
import com.joker.coolmall.core.designsystem.theme.SpaceVerticalXSmall
import com.joker.coolmall.core.ui.component.skeleton.SkeletonBlock
import com.joker.coolmall.core.ui.component.skeleton.SkeletonCircle
import com.joker.coolmall.core.ui.component.skeleton.SkeletonTextLine

/**
 * 商品评论页面骨架屏
 *
 * 按评论卡片布局展示骨架占位内容。
 *
 * @author Joker.X
 */
@Composable
internal fun GoodsCommentLoadingSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(SpacePaddingMedium)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(SpacePaddingMedium),
    ) {
        GoodsCommentCardSkeleton(showImages = true)
        GoodsCommentCardSkeleton(showImages = false)
    }
}

/**
 * 商品评论卡片骨架项
 *
 * @param showImages 是否显示图片占位
 * @author Joker.X
 */
@Composable
private fun GoodsCommentCardSkeleton(
    showImages: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpacePaddingMedium),
        ) {
            SkeletonCircle(size = 40.dp)

            Spacer(modifier = Modifier.width(SpaceHorizontalMedium))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(SpaceVerticalXSmall),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(SpaceHorizontalMedium),
                ) {
                    SkeletonTextLine(
                        modifier = Modifier.weight(1f),
                        widthFraction = 0.42f,
                    )
                    SkeletonTextLine(
                        modifier = Modifier.weight(1f),
                        widthFraction = 0.52f,
                    )
                }

                SkeletonTextLine(widthFraction = 0.3f)

                Column(
                    verticalArrangement = Arrangement.spacedBy(SpaceVerticalXSmall),
                ) {
                    SkeletonTextLine(widthFraction = 1f)
                    SkeletonTextLine(widthFraction = 0.92f)
                    SkeletonTextLine(widthFraction = 0.58f)
                }

                if (showImages) {
                    SpaceVerticalSmall()
                    GoodsCommentImageGridSkeleton()
                }
            }
        }
    }
}

/**
 * 商品评论图片九宫格骨架
 *
 * @author Joker.X
 */
@Composable
private fun GoodsCommentImageGridSkeleton() {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(2) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier.weight(1f),
                    ) {
                        SkeletonBlock(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f),
                            shape = ShapeSmall,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 商品评论骨架屏预览 - 浅色主题
 *
 * @author Joker.X
 */
@Preview(showBackground = true)
@Composable
private fun GoodsCommentLoadingSkeletonPreviewLight() {
    AppTheme(darkTheme = false) {
        GoodsCommentLoadingSkeleton()
    }
}

/**
 * 商品评论骨架屏预览 - 深色主题
 *
 * @author Joker.X
 */
@Preview(showBackground = true)
@Composable
private fun GoodsCommentLoadingSkeletonPreviewDark() {
    AppTheme(darkTheme = true) {
        GoodsCommentLoadingSkeleton()
    }
}

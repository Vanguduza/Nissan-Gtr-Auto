package com.joker.coolmall.feature.main.skeleton

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joker.coolmall.core.designsystem.theme.AppTheme
import com.joker.coolmall.core.designsystem.theme.RadiusLarge
import com.joker.coolmall.core.designsystem.theme.ShapeMedium
import com.joker.coolmall.core.designsystem.theme.SpacePaddingMedium
import com.joker.coolmall.core.designsystem.theme.SpaceVerticalMedium
import com.joker.coolmall.core.designsystem.theme.SpaceVerticalSmall
import com.joker.coolmall.core.ui.component.skeleton.SkeletonBlock
import com.joker.coolmall.core.ui.component.skeleton.SkeletonTextLine

/**
 * 分类页面骨架屏
 *
 * 贴合分类页左右分栏结构，便于在数据加载阶段维持页面节奏。
 *
 * @author Joker.X
 */
@Composable
internal fun CategoryLoadingSkeleton() {
    Row(modifier = Modifier.fillMaxSize()) {
        CategoryLeftMenuSkeleton(
            modifier = Modifier
                .width(100.dp)
                .fillMaxHeight(),
        )

        CategoryRightContentSkeleton(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
    }
}

/**
 * 分类左侧菜单骨架
 *
 * @param modifier 修饰符
 * @author Joker.X
 */
@Composable
private fun CategoryLeftMenuSkeleton(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.background(MaterialTheme.colorScheme.background),
    ) {
        repeat(8) { index ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .background(
                        if (index == 0) MaterialTheme.colorScheme.surface
                        else MaterialTheme.colorScheme.background
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (index == 0) {
                    SkeletonBlock(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 0.dp)
                            .width(3.dp)
                            .height(24.dp),
                        shape = RoundedCornerShape(topEnd = RadiusLarge, bottomEnd = RadiusLarge),
                    )
                }
                SkeletonTextLine(
                    widthFraction = 0.56f,
                    height = 14.dp,
                )
            }
        }
    }
}

/**
 * 分类右侧内容骨架
 *
 * @param modifier 修饰符
 * @author Joker.X
 */
@Composable
private fun CategoryRightContentSkeleton(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        repeat(4) {
            Column(verticalArrangement = Arrangement.spacedBy(SpaceVerticalMedium)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SpaceVerticalSmall),
                ) {
                    SkeletonBlock(
                        modifier = Modifier
                            .width(3.dp)
                            .height(18.dp),
                        shape = RoundedCornerShape(999.dp),
                    )
                    SkeletonTextLine(
                        widthFraction = 0.3f,
                        height = 18.dp,
                    )
                }

                repeat(2) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(SpacePaddingMedium),
                    ) {
                        repeat(3) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(SpaceVerticalSmall),
                            ) {
                                SkeletonBlock(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(92.dp),
                                    shape = ShapeMedium,
                                )
                                SkeletonTextLine(
                                    widthFraction = 0.72f,
                                    height = 12.dp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 分类骨架屏预览 - 浅色主题
 *
 * @author Joker.X
 */
@Preview(showBackground = true)
@Composable
private fun CategoryLoadingSkeletonPreviewLight() {
    AppTheme(darkTheme = false) {
        CategoryLoadingSkeleton()
    }
}

/**
 * 分类骨架屏预览 - 深色主题
 *
 * @author Joker.X
 */
@Preview(showBackground = true)
@Composable
private fun CategoryLoadingSkeletonPreviewDark() {
    AppTheme(darkTheme = true) {
        CategoryLoadingSkeleton()
    }
}

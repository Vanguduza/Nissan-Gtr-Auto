package com.joker.coolmall.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * 颜色规范
 * 定义应用程序中使用的所有颜色，包括浅色和深色主题
 */

// 主色
/**
 * GTR brand primary：#C8102E (packages/ui/brand-tokens.json)
 * CoolMall fashion blue replaced — colours only from GTR.
 */
val PrimaryDefault = Color(0xFFC8102E)

/**
 * 当前主题主色（受主题切换影响）
 */
val Primary: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.primary

// 辅助色
/** GTR danger = brand primary #C8102E */
val ColorDanger = Color(0xFFC8102E)

/** GTR warning #B45309 */
val ColorWarning = Color(0xFFB45309)

/** GTR steel-lift #1E2430 (replaces CoolMall purple accent) */
val ColorPurple = Color(0xFF1E2430)

/** GTR accent / success #0B6E4F */
val ColorSuccess = Color(0xFF0B6E4F)

// 字体颜色 - 浅色模式
/**
 * 浅色模式下主要文字颜色：#181818
 * 适用场景：标题、重要文本内容
 */
/** GTR steel #12151C */
val TextPrimaryLight = Color(0xFF12151C)

/** GTR steel-lift #1E2430 */
val TextSecondaryLight = Color(0xFF1E2430)

/** GTR silver-dim #8B929E */
val TextTertiaryLight = Color(0xFF8B929E)

/** GTR silver #C0C5CE */
val TextQuaternaryLight = Color(0xFFC0C5CE)

/**
 * 白色文字：#FFFFFF
 * 适用场景：深色背景上的文字、按钮文字
 */
val TextWhite = Color(0xFFFFFFFF) // 白色文字

// 字体颜色 - 深色模式
/**
 * 深色模式下主要文字颜色：#D1D1D1
 * 适用场景：深色模式下的标题、重要文本内容
 */
val TextPrimaryDark = Color(0xFFD1D1D1) // 深色模式下的主要文字

/**
 * 深色模式下次要文字颜色：#A3A3A3
 * 适用场景：深色模式下的正文内容、次要标题
 */
val TextSecondaryDark = Color(0xFFA3A3A3) // 深色模式下的次要文字

/**
 * 深色模式下三级文字颜色：#8D8D8D
 * 适用场景：深色模式下的辅助说明、标签文字
 */
val TextTertiaryDark = Color(0xFF8D8D8D) // 深色模式下的三级文字

/**
 * 深色模式下四级文字颜色：#5E5E5E
 * 适用场景：深色模式下的次要辅助信息、禁用状态文字
 */
val TextQuaternaryDark = Color(0xFF5E5E5E) // 深色模式下的四级文字

// 背景色 - 浅色模式
/**
 * 浅色模式下页面背景色：#F1F4FA
 * 适用场景：应用整体背景、页面底色
 */
/** GTR chalk #F4F5F7 */
val BgGreyLight = Color(0xFFF4F5F7)

/**
 * 浅色模式下白色背景：#FFFFFF
 * 适用场景：卡片、弹窗等内容区域背景
 */
val BgWhiteLight = Color(0xFFFFFFFF) // 白色背景

/**
 * 浅色模式下内容模块背景色：#F8F8F8
 * 适用场景：次级内容区域、列表项底色
 */
val BgContentLight = Color(0xFFF8F8F8) // 内容模块底色

/**
 * 浅色模式下红色背景：#FFEDED (5%透明度)
 * 适用场景：红色主题的轻量化背景、提示区域
 */
val BgRedLight = Color(0xFFFFEDED) // rgba(255, 43, 43, .05)

/**
 * 浅色模式下黄色背景：#FFF6E0 (10%透明度)
 * 适用场景：黄色主题的轻量化背景、警告区域
 */
val BgYellowLight = Color(0xFFFFF6E0) // rgba(255, 183, 3, .1)

/**
 * 浅色模式下紫色背景：#F3EEFF (5%透明度)
 * 适用场景：紫色主题的轻量化背景、特殊区域
 */
val BgPurpleLight = Color(0xFFF3EEFF) // rgba(104, 49, 255, .05)

/**
 * 浅色模式下绿色背景：#E8FBF0 (5%透明度)
 * 适用场景：绿色主题的轻量化背景、成功提示区域
 */
val BgGreenLight = Color(0xFFE8FBF0) // rgba(9, 190, 79, .05)

// 背景色 - 深色模式
/**
 * 深色模式下页面背景色：#111111
 * 适用场景：深色模式下的应用整体背景、页面底色
 */
val BgGreyDark = Color(0xFF111111) // 深色模式下的页面背景底色

/**
 * 深色模式下白色背景：#1B1B1B
 * 适用场景：深色模式下的卡片、弹窗等内容区域背景
 */
val BgWhiteDark = Color(0xFF1B1B1B) // 深色模式下的白色背景

/**
 * 深色模式下内容模块背景色：#222222
 * 适用场景：深色模式下的次级内容区域、列表项底色
 */
val BgContentDark = Color(0xFF222222) // 深色模式下的内容模块底色

/**
 * 深色模式下彩色背景统一值：#222222
 * 适用场景：深色模式下所有彩色背景的统一替代色
 */
val BgColorDark = Color(0xFF222222) // 深色模式下的所有彩色背景

// 遮罩颜色
/**
 * 浅色模式下遮罩颜色：60%透明度黑色
 * 适用场景：弹窗背景、加载状态遮罩
 */
val MaskLight = Color(0x99000000) // rgba(0, 0, 0, 0.6) - 浅色模式

/**
 * 深色模式下遮罩颜色：60%透明度黑色
 * 适用场景：深色模式下的弹窗背景、加载状态遮罩
 */
val MaskDark = Color(0x99000000) // rgba(0, 0, 0, 0.6) - 深色模式

/**
 * 浅色模式下按压状态颜色：20%透明度黑色
 * 适用场景：浅色模式下的按钮、卡片等组件的点击反馈
 */
val PressLight = Color(0x33000000) // rgba(0, 0, 0, 0.2) - 浅色模式点击

/**
 * 深色模式下按压状态颜色：20%透明度白色
 * 适用场景：深色模式下的按钮、卡片等组件的点击反馈
 */
val PressDark = Color(0x33FFFFFF) // rgba(255, 255, 255, .2) - 深色模式点击

// 边框颜色
/**
 * 浅色模式下边框颜色：#EEEEEE
 * 适用场景：分割线、边框、描边等
 */
val BorderLight = Color(0xFFEEEEEE) // 浅色模式边框

/**
 * 深色模式下边框颜色：#242424
 * 适用场景：深色模式下的分割线、边框、描边等
 */
val BorderDark = Color(0xFF242424) // 深色模式边框

// 渐变色起点和终点颜色
private val GradientPrimaryStartDefault = Color(0xFF465CFF)
private val GradientPrimaryEndDefault = Color(0xFF6831FF)

/**
 * 主色渐变起点：根据当前主题主色，在默认起点的基础上略微增亮
 */
val GradientPrimaryStart: Color
    @Composable
    @ReadOnlyComposable
    get() = lerp(
        start = Primary,
        stop = Color.White,
        fraction = 0.15f
    ).copy(alpha = GradientPrimaryStartDefault.alpha)

/**
 * 主色渐变终点：根据当前主题主色，在默认终点的基础上略微加深
 */
val GradientPrimaryEnd: Color
    @Composable
    @ReadOnlyComposable
    get() = lerp(
        start = Primary,
        stop = Color.Black,
        fraction = 0.15f
    ).copy(alpha = GradientPrimaryEndDefault.alpha)

/**
 * 红色渐变起点：#FD8C8C
 * 适用场景：与红色渐变终点配合使用，用于警告类渐变效果
 */
val GradientRedStart = Color(0xFFFD8C8C) // 红色渐变起点

/**
 * 红色渐变终点：#FF2B2B
 * 适用场景：与红色渐变起点配合使用，用于警告类渐变效果
 */
val GradientRedEnd = Color(0xFFFF2B2B) // 红色渐变终点

// 其他颜色
/**
 * 右箭头灰色：#A3A3A3
 */
val RightArrowGray = Color(0xFFA3A3A3) // 右箭头灰色

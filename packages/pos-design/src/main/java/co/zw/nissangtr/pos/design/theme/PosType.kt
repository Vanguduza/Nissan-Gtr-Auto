package co.zw.nissangtr.pos.design.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * 12 type roles with optical tracking, tnum, and compact scaling (Blueprint §4.6 / SYS-09).
 */
@Immutable
data class PosType(
    val displayHero: TextStyle,
    val heading1: TextStyle,
    val heading2: TextStyle,
    val heading3: TextStyle,
    val bodyPrimary: TextStyle,
    val bodySecondary: TextStyle,
    val labelAction: TextStyle,
    val labelMeta: TextStyle,
    val numericPrice: TextStyle,
    val numericTotal: TextStyle,
    val numericQuantity: TextStyle,
    val monoReference: TextStyle,
) {
    companion object {
        fun resolve(windowClass: PosWindowClass): PosType {
            val isCompact = windowClass == PosWindowClass.CompactPortrait ||
                windowClass == PosWindowClass.CompactLandscape

            return PosType(
                displayHero = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (isCompact) 24.sp else 32.sp,
                    lineHeight = if (isCompact) 30.sp else 38.sp,
                    letterSpacing = (-0.02).em,
                ),
                heading1 = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (isCompact) 20.sp else 24.sp,
                    lineHeight = if (isCompact) 26.sp else 30.sp,
                    letterSpacing = (-0.015).em,
                ),
                heading2 = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = if (isCompact) 18.sp else 20.sp,
                    lineHeight = if (isCompact) 24.sp else 26.sp,
                    letterSpacing = (-0.01).em,
                ),
                heading3 = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    letterSpacing = (-0.005).em,
                ),
                bodyPrimary = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Normal,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    letterSpacing = 0.em,
                ),
                bodySecondary = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Normal,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    letterSpacing = 0.005.em,
                    fontFeatureSettings = "ss02, zero",
                ),
                labelAction = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    letterSpacing = 0.01.em,
                ),
                labelMeta = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    letterSpacing = 0.02.em,
                ),
                numericPrice = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    letterSpacing = 0.em,
                    fontFeatureSettings = "tnum",
                ),
                numericTotal = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (isCompact) 20.sp else 24.sp,
                    lineHeight = if (isCompact) 26.sp else 30.sp,
                    letterSpacing = (-0.01).em,
                    fontFeatureSettings = "tnum",
                ),
                numericQuantity = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    letterSpacing = 0.em,
                    fontFeatureSettings = "tnum",
                ),
                monoReference = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Normal,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    letterSpacing = 0.em,
                    fontFeatureSettings = "tnum, zero",
                ),
            )
        }
    }
}

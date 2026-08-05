package co.zw.nissangtr.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import co.zw.nissangtr.ui.R

/**
 * Typography structure adapted from Shopping-By-KMP `theme/Type.kt` (Lato scale),
 * faces replaced with GTR OFL Titillium Web (display) + Source Sans 3 (body).
 */

/** Titillium Web — display / chrome (OFL). Faces vendored under res/font/. */
val GtrDisplayFont = FontFamily(
    Font(R.font.titillium_web_regular, FontWeight.Normal),
    Font(R.font.titillium_web_semibold, FontWeight.SemiBold),
    Font(R.font.titillium_web_bold, FontWeight.Bold),
)

/** Source Sans 3 — body / labels (OFL). Faces vendored under res/font/. */
val GtrBodyFont = FontFamily(
    Font(R.font.source_sans_3_regular, FontWeight.Normal),
    Font(R.font.source_sans_3_semibold, FontWeight.SemiBold),
    Font(R.font.source_sans_3_bold, FontWeight.Bold),
)


/**

 * Material3 type scale mapped to web brand faces

 * (Titillium Web display + Source Sans 3 body).

 * See packages/ui/BRAND_TOKENS.md and packages/ui/fonts/ATTRIBUTION.md.

 */

val GtrTypography = Typography(

    displayLarge = TextStyle(

        fontFamily = GtrDisplayFont,

        fontWeight = FontWeight.Bold,

        fontSize = 36.sp,

        lineHeight = 40.sp,

        letterSpacing = 0.4.sp,

    ),

    displayMedium = TextStyle(

        fontFamily = GtrDisplayFont,

        fontWeight = FontWeight.Bold,

        fontSize = 28.sp,

        lineHeight = 32.sp,

        letterSpacing = 0.4.sp,

    ),

    headlineLarge = TextStyle(

        fontFamily = GtrDisplayFont,

        fontWeight = FontWeight.Bold,

        fontSize = 26.sp,

        lineHeight = 30.sp,

        letterSpacing = 0.3.sp,

    ),

    headlineMedium = TextStyle(

        fontFamily = GtrDisplayFont,

        fontWeight = FontWeight.Bold,

        fontSize = 22.sp,

        lineHeight = 26.sp,

        letterSpacing = 0.3.sp,

    ),

    headlineSmall = TextStyle(

        fontFamily = GtrDisplayFont,

        fontWeight = FontWeight.Bold,

        fontSize = 18.sp,

        lineHeight = 22.sp,

        letterSpacing = 0.25.sp,

    ),

    titleLarge = TextStyle(

        fontFamily = GtrDisplayFont,

        fontWeight = FontWeight.SemiBold,

        fontSize = 18.sp,

        lineHeight = 24.sp,

        letterSpacing = 0.2.sp,

    ),

    titleMedium = TextStyle(

        fontFamily = GtrDisplayFont,

        fontWeight = FontWeight.SemiBold,

        fontSize = 16.sp,

        lineHeight = 22.sp,

        letterSpacing = 0.15.sp,

    ),

    titleSmall = TextStyle(

        fontFamily = GtrDisplayFont,

        fontWeight = FontWeight.SemiBold,

        fontSize = 14.sp,

        lineHeight = 18.sp,

        letterSpacing = 0.2.sp,

    ),

    bodyLarge = TextStyle(

        fontFamily = GtrBodyFont,

        fontWeight = FontWeight.Normal,

        fontSize = 16.sp,

        lineHeight = 22.sp,

        letterSpacing = 0.15.sp,

    ),

    bodyMedium = TextStyle(

        fontFamily = GtrBodyFont,

        fontWeight = FontWeight.Normal,

        fontSize = 14.sp,

        lineHeight = 20.sp,

        letterSpacing = 0.15.sp,

    ),

    bodySmall = TextStyle(

        fontFamily = GtrBodyFont,

        fontWeight = FontWeight.Normal,

        fontSize = 12.sp,

        lineHeight = 16.sp,

        letterSpacing = 0.2.sp,

    ),

    labelLarge = TextStyle(

        fontFamily = GtrBodyFont,

        fontWeight = FontWeight.SemiBold,

        fontSize = 14.sp,

        lineHeight = 18.sp,

        letterSpacing = 0.4.sp,

    ),

    labelMedium = TextStyle(

        fontFamily = GtrBodyFont,

        fontWeight = FontWeight.SemiBold,

        fontSize = 12.sp,

        lineHeight = 16.sp,

        letterSpacing = 0.5.sp,

    ),

    labelSmall = TextStyle(

        fontFamily = GtrBodyFont,

        fontWeight = FontWeight.SemiBold,

        fontSize = 11.sp,

        lineHeight = 14.sp,

        letterSpacing = 0.6.sp,

    ),

)



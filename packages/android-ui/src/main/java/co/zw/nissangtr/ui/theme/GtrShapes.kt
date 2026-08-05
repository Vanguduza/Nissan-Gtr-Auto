package co.zw.nissangtr.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Shape scale forked from Shopping-By-KMP `presentation/theme/Shape.kt`
 * (extraSmall 4 → extraLarge 32). Brand color/type stay GTR; radii follow the
 * KMP design system so ProductBox / Banner / Nav Card match reference density.
 */
val GtrShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

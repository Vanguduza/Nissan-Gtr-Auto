package co.zw.nissangtr.pos.design.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import co.zw.nissangtr.pos.design.tokens.PosTokens

/**
 * Radius scale for Nissan GTR POS (Blueprint §4.4 / SYS-07).
 */
@Immutable
data class PosShape(
    val xs: CornerBasedShape = RoundedCornerShape(PosTokens.RadiusTokens.Radius_xs),
    val sm: CornerBasedShape = RoundedCornerShape(PosTokens.RadiusTokens.Radius_sm),
    val md: CornerBasedShape = RoundedCornerShape(PosTokens.RadiusTokens.Radius_md),
    val lg: CornerBasedShape = RoundedCornerShape(PosTokens.RadiusTokens.Radius_lg),
    val pill: CornerBasedShape = RoundedCornerShape(PosTokens.RadiusTokens.Radius_pill),
)

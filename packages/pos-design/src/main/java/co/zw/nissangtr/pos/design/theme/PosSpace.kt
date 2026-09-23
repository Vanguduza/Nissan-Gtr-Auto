package co.zw.nissangtr.pos.design.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import co.zw.nissangtr.pos.design.tokens.PosTokens

/**
 * 8-point base spacing scale for Nissan GTR POS (Blueprint §4.3 / SYS-06).
 */
@Immutable
data class PosSpace(
    val space0: Dp = PosTokens.SpaceTokens.Space_0,
    val space0_5: Dp = PosTokens.SpaceTokens.Space_0_5,
    val space1: Dp = PosTokens.SpaceTokens.Space_1,
    val space2: Dp = PosTokens.SpaceTokens.Space_2,
    val space3: Dp = PosTokens.SpaceTokens.Space_3,
    val space4: Dp = PosTokens.SpaceTokens.Space_4,
    val space5: Dp = PosTokens.SpaceTokens.Space_5,
    val space6: Dp = PosTokens.SpaceTokens.Space_6,
    val space8: Dp = PosTokens.SpaceTokens.Space_8,
    val space10: Dp = PosTokens.SpaceTokens.Space_10,
    val space12: Dp = PosTokens.SpaceTokens.Space_12,
)

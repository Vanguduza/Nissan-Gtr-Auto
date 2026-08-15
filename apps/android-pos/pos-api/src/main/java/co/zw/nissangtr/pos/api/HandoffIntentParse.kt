package co.zw.nissangtr.pos.api

/**
 * Management → POS session handoff extras.
 * Tokens are parsed in-memory only — never log access/refresh tokens.
 */
object HandoffExtras {
    const val HANDOFF = "co.zw.nissangtr.pos.HANDOFF"
    const val STAFF_DISPLAY_NAME = "co.zw.nissangtr.pos.STAFF_DISPLAY_NAME"
    const val ACCESS_TOKEN = "co.zw.nissangtr.pos.ACCESS_TOKEN"
    const val REFRESH_TOKEN = "co.zw.nissangtr.pos.REFRESH_TOKEN"
    const val BRANCH_ID = "co.zw.nissangtr.pos.BRANCH_ID"
    const val TERMINAL_ID = "co.zw.nissangtr.pos.TERMINAL_ID"
    const val WAREHOUSE_ID = "co.zw.nissangtr.pos.WAREHOUSE_ID"
}

data class PosHandoffPayload(
    val handoff: Boolean,
    val staffDisplayName: String?,
    val accessToken: String?,
    val refreshToken: String?,
    val branchId: String?,
    val terminalId: String?,
    val warehouseId: String?,
) {
    /** Valid Live skip-login when handoff flag + non-blank access token. */
    val canEstablishLiveSession: Boolean
        get() = handoff && !accessToken.isNullOrBlank()

    /** Safe debug summary — never includes tokens. */
    fun safeSummary(): String = buildString {
        append("handoff=").append(handoff)
        append(" staff=").append(staffDisplayName?.isNotBlank() == true)
        append(" token=").append(!accessToken.isNullOrBlank())
        append(" refresh=").append(!refreshToken.isNullOrBlank())
        append(" branch=").append(!branchId.isNullOrBlank())
        append(" terminal=").append(!terminalId.isNullOrBlank())
    }
}

/**
 * Pure parser from string maps (Intent extras / tests).
 * Does not log secrets.
 */
object HandoffIntentParse {
    fun parse(
        extras: Map<String, String?>,
        handoffFlagRaw: String? = extras[HandoffExtras.HANDOFF],
    ): PosHandoffPayload {
        val handoff = handoffFlagRaw == "1" ||
            handoffFlagRaw.equals("true", ignoreCase = true)
        return PosHandoffPayload(
            handoff = handoff,
            staffDisplayName = extras[HandoffExtras.STAFF_DISPLAY_NAME]?.trim()?.takeIf { it.isNotEmpty() },
            accessToken = extras[HandoffExtras.ACCESS_TOKEN]?.trim()?.takeIf { it.isNotEmpty() },
            refreshToken = extras[HandoffExtras.REFRESH_TOKEN]?.trim()?.takeIf { it.isNotEmpty() },
            branchId = extras[HandoffExtras.BRANCH_ID]?.trim()?.takeIf { it.isNotEmpty() },
            terminalId = extras[HandoffExtras.TERMINAL_ID]?.trim()?.takeIf { it.isNotEmpty() },
            warehouseId = extras[HandoffExtras.WAREHOUSE_ID]?.trim()?.takeIf { it.isNotEmpty() },
        )
    }
}

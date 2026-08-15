package com.joker.coolmall.feature.main.model

import androidx.annotation.RawRes
import androidx.annotation.StringRes
import com.joker.coolmall.navigation.main.MainRoutes
import com.joker.coolmall.feature.main.R

/**
 * Staff bottom tabs — Hub / Warehouse / Account.
 * POS cart tab deferred (no staff-pos this phase).
 */
enum class TopLevelDestination(
    @param:StringRes val titleTextId: Int,
    @param:RawRes val animationResId: Int,
    val route: Any,
) {
    HUB(
        titleTextId = R.string.staff_hub_tab,
        animationResId = R.raw.home,
        route = MainRoutes.Home,
    ),
    WAREHOUSE(
        titleTextId = R.string.staff_warehouse_tab,
        animationResId = R.raw.category,
        route = MainRoutes.Category,
    ),
    ACCOUNT(
        titleTextId = R.string.staff_account_tab,
        animationResId = R.raw.me,
        route = MainRoutes.Mine,
    ),
}

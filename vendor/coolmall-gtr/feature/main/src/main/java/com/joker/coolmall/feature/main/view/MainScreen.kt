package com.joker.coolmall.feature.main.view

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.joker.coolmall.core.designsystem.theme.AppTheme
import com.joker.coolmall.feature.main.component.BottomNavigationBar
import com.joker.coolmall.feature.main.model.TopLevelDestination
import com.joker.coolmall.feature.main.viewmodel.MainViewModel
import kotlinx.coroutines.launch

/**
 * Staff main shell — Hub / Warehouse desk / Account.
 * No POS cart tab this phase.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun MainRoute(
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedContentScope: AnimatedContentScope? = null,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val currentPageIndex by viewModel.currentPageIndex.collectAsState()

    MainScreen(
        sharedTransitionScope = sharedTransitionScope,
        animatedContentScope = animatedContentScope,
        currentPageIndex = currentPageIndex,
        onPageChanged = viewModel::updatePageIndex,
        onNavigationItemSelected = viewModel::updateDestination,
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun MainScreen(
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedContentScope: AnimatedContentScope? = null,
    currentPageIndex: Int = 0,
    onPageChanged: (Int) -> Unit = {},
    onNavigationItemSelected: (Int) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var openModuleId by remember { mutableStateOf<String?>(null) }

    val pageState = rememberPagerState(
        initialPage = currentPageIndex,
    ) {
        TopLevelDestination.entries.size
    }

    LaunchedEffect(pageState.currentPage) {
        onPageChanged(pageState.currentPage)
    }

    Scaffold(
        contentWindowInsets = ScaffoldDefaults
            .contentWindowInsets
            .exclude(WindowInsets.statusBars),
        bottomBar = {
            BottomNavigationBar(
                destinations = TopLevelDestination.entries,
                onNavigateToDestination = { index ->
                    openModuleId = null
                    onNavigationItemSelected(index)
                    scope.launch {
                        pageState.scrollToPage(index)
                    }
                },
                currentPageIndex = currentPageIndex,
                modifier = Modifier,
            )
        },
    ) { paddingValues ->
        MainScreenContentView(
            pageState = pageState,
            paddingValues = paddingValues,
            openModuleId = openModuleId,
            onOpenModule = { id ->
                if (id == "warehouse") {
                    openModuleId = null
                    onNavigationItemSelected(TopLevelDestination.WAREHOUSE.ordinal)
                    scope.launch {
                        pageState.scrollToPage(TopLevelDestination.WAREHOUSE.ordinal)
                    }
                } else {
                    openModuleId = id
                    onNavigationItemSelected(TopLevelDestination.HUB.ordinal)
                    scope.launch {
                        pageState.scrollToPage(TopLevelDestination.HUB.ordinal)
                    }
                }
            },
            onCloseModule = { openModuleId = null },
        )
    }
}

@Composable
private fun MainScreenContentView(
    pageState: PagerState,
    paddingValues: PaddingValues,
    openModuleId: String?,
    onOpenModule: (String) -> Unit,
    onCloseModule: () -> Unit,
) {
    HorizontalPager(
        state = pageState,
        modifier = Modifier.padding(paddingValues),
    ) { page: Int ->
        when (page) {
            TopLevelDestination.HUB.ordinal -> {
                val moduleId = openModuleId
                if (moduleId != null) {
                    StaffModuleDeskRoute(moduleId = moduleId, onBack = onCloseModule)
                } else {
                    StaffHubRoute(onOpenModule = onOpenModule)
                }
            }
            TopLevelDestination.WAREHOUSE.ordinal -> WarehouseDeskRoute()
            TopLevelDestination.ACCOUNT.ordinal -> StaffAccountRoute()
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    AppTheme {
        MainScreen()
    }
}

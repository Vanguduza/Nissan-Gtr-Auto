package co.zw.nissangtr.catalogapk

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import co.zw.nissangtr.catalogapk.ui.PermissionBootstrap
import co.zw.nissangtr.catalogapk.ui.bundles.BundlePickerScreen
import co.zw.nissangtr.catalogapk.ui.jobdetail.JobDetailScreen
import co.zw.nissangtr.catalogapk.ui.jobs.JobsScreen
import co.zw.nissangtr.catalogapk.ui.navigation.CatalogRoutes
import co.zw.nissangtr.catalogapk.ui.projects.ProjectsScreen
import co.zw.nissangtr.catalogapk.ui.session.NewSessionScreen
import co.zw.nissangtr.catalogapk.ui.targets.TargetsScreen
import co.zw.nissangtr.catalogapk.ui.theme.CatalogApkTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var permissionBootstrap: PermissionBootstrap

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionBootstrap = PermissionBootstrap(this)
        permissionBootstrap.requestAll()
        handleAuthIntent(intent)
        enableEdgeToEdge()
        setContent {
            CatalogApkTheme {
                val navController = rememberNavController()
                val backStack by navController.currentBackStackEntryAsState()
                val currentRoute = backStack?.destination?.route

                val topLevelRoutes = setOf(
                    CatalogRoutes.TARGETS,
                    CatalogRoutes.NEW_SESSION,
                    CatalogRoutes.JOBS,
                    CatalogRoutes.PROJECTS,
                )

                Scaffold(
                    bottomBar = {
                        if (currentRoute in topLevelRoutes) {
                            NavigationBar {
                                NavigationBarItem(
                                    selected = currentRoute == CatalogRoutes.TARGETS,
                                    onClick = { navController.navigate(CatalogRoutes.TARGETS) },
                                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                    label = { Text(stringResource(R.string.nav_targets)) },
                                )
                                NavigationBarItem(
                                    selected = currentRoute == CatalogRoutes.NEW_SESSION,
                                    onClick = { navController.navigate(CatalogRoutes.NEW_SESSION) },
                                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                                    label = { Text(stringResource(R.string.nav_new_session)) },
                                )
                                NavigationBarItem(
                                    selected = currentRoute == CatalogRoutes.JOBS,
                                    onClick = { navController.navigate(CatalogRoutes.JOBS) },
                                    icon = { Icon(Icons.Default.List, contentDescription = null) },
                                    label = { Text(stringResource(R.string.nav_jobs)) },
                                )
                                NavigationBarItem(
                                    selected = currentRoute == CatalogRoutes.PROJECTS,
                                    onClick = { navController.navigate(CatalogRoutes.PROJECTS) },
                                    icon = { Icon(Icons.Default.Storage, contentDescription = null) },
                                    label = { Text(stringResource(R.string.nav_projects)) },
                                )
                            }
                        }
                    },
                ) { padding ->
                    NavHost(
                        navController = navController,
                        startDestination = CatalogRoutes.TARGETS,
                        modifier = Modifier.padding(padding),
                    ) {
                        composable(CatalogRoutes.TARGETS) { TargetsScreen() }
                        composable(CatalogRoutes.NEW_SESSION) {
                            NewSessionScreen(
                                onJobsStarted = {
                                    navController.navigate(CatalogRoutes.JOBS) {
                                        popUpTo(CatalogRoutes.NEW_SESSION)
                                    }
                                },
                            )
                        }
                        composable(CatalogRoutes.JOBS) {
                            JobsScreen(
                                onOpenJob = { id ->
                                    navController.navigate(CatalogRoutes.jobDetail(id))
                                },
                            )
                        }
                        composable(
                            route = CatalogRoutes.JOB_DETAIL,
                            arguments = listOf(navArgument("jobId") { type = NavType.StringType }),
                        ) {
                            JobDetailScreen(
                                onOpenBundles = { id ->
                                    navController.navigate(CatalogRoutes.bundlePicker(id))
                                },
                            )
                        }
                        composable(
                            route = CatalogRoutes.BUNDLE_PICKER,
                            arguments = listOf(navArgument("jobId") { type = NavType.StringType }),
                        ) {
                            BundlePickerScreen()
                        }
                        composable(CatalogRoutes.PROJECTS) { ProjectsScreen() }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthIntent(intent)
    }

    private fun handleAuthIntent(intent: Intent?) {
        if (intent?.data == null) return
        val app = application as CatalogApkApplication
        val projectId = app.securePrefs.getSessionProject() ?: return
        lifecycleScope.launch {
            val project = app.projectRepository.observeProjects().first()
                .firstOrNull { it.id == projectId }
                ?: return@launch
            val anon = app.securePrefs.getAnonKey(projectId) ?: project.anonKeyEncrypted
            app.supabaseSession.handleAuthIntent(intent, projectId, project.url, anon)
        }
    }
}

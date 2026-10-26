package uk.co.aaronburt.rtsp.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import uk.co.aaronburt.rtsp.R
import uk.co.aaronburt.rtsp.data.AppContainer
import uk.co.aaronburt.rtsp.ui.cameras.CamerasScreen
import uk.co.aaronburt.rtsp.ui.cameras.CamerasViewModel
import uk.co.aaronburt.rtsp.ui.form.CameraFormScreen
import uk.co.aaronburt.rtsp.ui.form.CameraFormViewModel
import uk.co.aaronburt.rtsp.ui.live.LiveScreen
import uk.co.aaronburt.rtsp.ui.live.LiveViewModel
import uk.co.aaronburt.rtsp.ui.live.PlaceholderPlayerSlot
import uk.co.aaronburt.rtsp.ui.live.PlayerSlot
import uk.co.aaronburt.rtsp.ui.live.LocalPictureInPicture
import uk.co.aaronburt.rtsp.ui.multiview.MultiviewScreen
import uk.co.aaronburt.rtsp.ui.recordings.RecordingsScreen
import uk.co.aaronburt.rtsp.ui.recordings.RecordingsViewModel
import uk.co.aaronburt.rtsp.ui.settings.SettingsScreen
import uk.co.aaronburt.rtsp.update.UpdatePrompt

object Routes {
    const val Cameras = "cameras"
    const val Multiview = "multiview"
    const val Recordings = "recordings"
    const val Settings = "settings"
    const val Live = "live/{id}"
    const val Add = "camera/add"
    const val Edit = "camera/{id}/edit"

    fun live(id: String) = "live/$id"

    fun edit(id: String) = "camera/$id/edit"
}

private data class TopLevel(val route: String, val label: Int, val icon: Int)

private val topLevel = listOf(
    TopLevel(Routes.Cameras, R.string.nav_cameras, R.drawable.ic_videocam),
    TopLevel(Routes.Multiview, R.string.nav_multiview, R.drawable.ic_grid_view),
    TopLevel(Routes.Recordings, R.string.nav_recordings, R.drawable.ic_video_library),
    TopLevel(Routes.Settings, R.string.nav_settings, R.drawable.ic_settings),
)

private val barRoutes = setOf(Routes.Cameras, Routes.Recordings, Routes.Settings)

@Composable
fun RtspApp(
    container: AppContainer,
    modifier: Modifier = Modifier,
    playerSlot: PlayerSlot = PlaceholderPlayerSlot,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val showBar = destination?.route in barRoutes
    val showClock by container.displayPreferences.showClock.collectAsStateWithLifecycle()
    val allowRecording by container.displayPreferences.allowRecording.collectAsStateWithLifecycle()
    val pinchToZoom by container.displayPreferences.pinchToZoom.collectAsStateWithLifecycle()
    val multiviewMainStream by container.displayPreferences.multiviewMainStream.collectAsStateWithLifecycle()
    val pipState by LocalPictureInPicture.current.state.collectAsStateWithLifecycle()

    UpdatePrompt()

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = if (pipState.videoOnly) WindowInsets(0) else ScaffoldDefaults.contentWindowInsets,
        bottomBar = {
            if (showBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                    topLevel.filter { it.route != Routes.Recordings || allowRecording }.forEach { item ->
                        val selected = destination?.hierarchy?.any { it.route == item.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = item.route != Routes.Multiview
                                }
                            },
                            icon = {
                                Icon(painter = painterResource(item.icon), contentDescription = null)
                            },
                            label = { Text(stringResource(item.label)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.Cameras,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
            composable(Routes.Cameras) {
                CamerasScreen(
                    viewModel = viewModel(factory = CamerasViewModel.factory(container)),
                    onOpenCamera = { navController.navigate(Routes.live(it)) },
                    onAddCamera = { navController.navigate(Routes.Add) },
                    onEditCamera = { navController.navigate(Routes.edit(it)) },
                )
            }
            composable(Routes.Multiview) {
                MultiviewScreen(
                    viewModel = viewModel(factory = CamerasViewModel.factory(container)),
                    playerSlot = playerSlot,
                    onOpenCamera = { navController.navigate(Routes.live(it)) },
                    onExit = { navController.popBackStack() },
                    showClock = showClock,
                    useMainStream = multiviewMainStream,
                )
            }
            composable(Routes.Recordings) {
                RecordingsScreen(viewModel = viewModel(factory = RecordingsViewModel.factory(container)))
            }
            composable(Routes.Settings) {
                SettingsScreen(preferences = container.displayPreferences, streamDefaults = container.streamDefaults)
            }
            composable(
                route = Routes.Live,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) {
                LiveScreen(
                    viewModel = viewModel(factory = LiveViewModel.factory(container)),
                    playerSlot = playerSlot,
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(Routes.edit(it)) },
                    showClock = showClock,
                    allowRecording = allowRecording,
                    pinchToZoom = pinchToZoom,
                )
            }
            composable(Routes.Add) {
                CameraFormScreen(
                    viewModel = viewModel(factory = CameraFormViewModel.factory(container)),
                    onClose = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                    onDeleted = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.Edit,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) {
                CameraFormScreen(
                    viewModel = viewModel(factory = CameraFormViewModel.factory(container)),
                    onClose = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                    onDeleted = { navController.popBackStack(Routes.Cameras, inclusive = false) },
                )
            }
        }
    }
}

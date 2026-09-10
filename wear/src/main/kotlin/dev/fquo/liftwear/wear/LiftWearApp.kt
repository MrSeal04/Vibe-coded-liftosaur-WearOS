package dev.fquo.liftwear.wear

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.wear.compose.navigation.currentBackStackEntryAsState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import dev.fquo.liftwear.data.LiftWearContainer
import androidx.compose.ui.platform.LocalContext
import dev.fquo.liftwear.datalayer.CredentialIntake
import dev.fquo.liftwear.datalayer.WearableCredentialTransport
import dev.fquo.liftwear.datalayer.WearableNodes
import dev.fquo.liftwear.wear.ambient.AmbientAware
import dev.fquo.liftwear.wear.ambient.AmbientWorkoutSurface
import dev.fquo.liftwear.wear.rest.WorkoutSession
import dev.fquo.liftwear.wear.ui.common.LoadingScreen
import dev.fquo.liftwear.wear.ui.common.MessageScreen
import dev.fquo.liftwear.wear.ui.history.HistoryDetailScreen
import dev.fquo.liftwear.wear.ui.history.HistoryScreen
import dev.fquo.liftwear.wear.ui.history.HistoryViewModel
import dev.fquo.liftwear.wear.ui.home.HomeScreen
import dev.fquo.liftwear.wear.ui.home.HomeViewModel
import dev.fquo.liftwear.wear.ui.settings.SettingsScreen
import dev.fquo.liftwear.wear.ui.settings.SettingsViewModel
import dev.fquo.liftwear.wear.ui.setup.SetupScreen
import dev.fquo.liftwear.wear.ui.setup.SetupViewModel
import dev.fquo.liftwear.wear.ui.workout.FinishScreen
import dev.fquo.liftwear.wear.ui.workout.SetConfirmScreen
import dev.fquo.liftwear.wear.ui.workout.SetListScreen
import dev.fquo.liftwear.wear.ui.workout.WorkoutScreen
import dev.fquo.liftwear.wear.ui.workout.WorkoutViewModel

object Routes {
    const val SETUP = "setup"
    const val HOME = "home"
    const val WORKOUT = "workout"
    const val FINISH = "finish"
    const val SETTINGS = "settings"
    const val HISTORY = "history"

    const val SET_CONFIRM = "setConfirm/{entryId}/{setId}"
    fun setConfirm(entryId: String, setId: String) = "setConfirm/$entryId/$setId"

    const val SET_LIST = "setList/{entryIndex}"
    fun setList(entryIndex: Int) = "setList/$entryIndex"

    const val HISTORY_DETAIL = "history/{recordId}"
    fun historyDetail(recordId: Long) = "history/$recordId"
}

@Composable
fun LiftWearApp(
    container: LiftWearContainer,
    nodes: WearableNodes,
    session: WorkoutSession,
    version: String,
) {
    MaterialTheme {
        // Read at the root rather than inside the workout screen: the wrist can drop on any
        // screen, and what the ambient surface should say depends on the workout, not on
        // which destination the nav host happens to be showing.
        val workout by container.workouts.workout.collectAsStateWithLifecycle(initialValue = null)
        val rest by session.rest.collectAsStateWithLifecycle()

        AmbientAware(
            ambient = { mode -> AmbientWorkoutSurface(mode, workout, rest) },
        ) {
            AppScaffold {
                val pairing by container.pairing.collectAsStateWithLifecycle()
                when (pairing) {
                    // Unknown means the encrypted key has not been read back yet. Showing a
                    // spinner beats flashing the setup screen at an already-paired user.
                    LiftWearContainer.PairingState.Unknown -> LoadingScreen()
                    else -> LiftWearNavHost(
                        container,
                        nodes,
                        session,
                        version,
                        paired = pairing == LiftWearContainer.PairingState.Paired,
                    )
                }
            }
        }
    }
}

@Composable
private fun LiftWearNavHost(
    container: LiftWearContainer,
    nodes: WearableNodes,
    session: WorkoutSession,
    version: String,
    paired: Boolean,
) {
    val navController = rememberSwipeDismissableNavController()
    val factory = rememberContainerFactory(container, nodes, session, version)

    // Pairing state changes from two directions: a key revoked in Settings must land back
    // on setup rather than leave screens up that only produce 401s, and a key delivered by
    // the phone companion must leave setup without the user touching the watch at all.
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    LaunchedEffect(paired, currentRoute) {
        when {
            !paired && currentRoute != null && currentRoute != Routes.SETUP ->
                navController.navigate(Routes.SETUP) {
                    popUpTo(navController.graph.id) { inclusive = true }
                }

            paired && currentRoute == Routes.SETUP ->
                navController.navigate(Routes.HOME) {
                    popUpTo(Routes.SETUP) { inclusive = true }
                }
        }
    }

    SwipeDismissableNavHost(
        navController = navController,
        startDestination = if (paired) Routes.HOME else Routes.SETUP,
    ) {
        composable(Routes.SETUP) {
            val vm: SetupViewModel = viewModel(factory = factory)
            SetupScreen(vm) {
                navController.navigate(Routes.HOME) {
                    popUpTo(Routes.SETUP) { inclusive = true }
                }
            }
        }

        composable(Routes.HOME) {
            val vm: HomeViewModel = viewModel(factory = factory)
            HomeScreen(
                viewModel = vm,
                onOpenWorkout = { navController.navigate(Routes.WORKOUT) },
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(Routes.WORKOUT) {
            val vm = workoutViewModel(navController, factory)
            WorkoutScreen(
                viewModel = vm,
                onConfirmSet = { entryId, setId ->
                    navController.navigate(Routes.setConfirm(entryId, setId))
                },
                onOpenSetList = { index -> navController.navigate(Routes.setList(index)) },
                onFinish = { navController.navigate(Routes.FINISH) },
            )
        }

        composable(Routes.SET_CONFIRM) { backStackEntry ->
            val vm = workoutViewModel(navController, factory)
            val entryId = backStackEntry.arguments?.getString("entryId").orEmpty()
            val setId = backStackEntry.arguments?.getString("setId").orEmpty()
            SetConfirmScreen(vm, entryId, setId, onLogged = { navController.popBackStack() })
        }

        composable(Routes.SET_LIST) { backStackEntry ->
            val vm = workoutViewModel(navController, factory)
            val index = backStackEntry.arguments?.getString("entryIndex")?.toIntOrNull() ?: 0
            SetListScreen(vm, index) { entryId, setId ->
                navController.navigate(Routes.setConfirm(entryId, setId))
            }
        }

        composable(Routes.FINISH) {
            val vm = workoutViewModel(navController, factory)
            FinishScreen(vm) {
                navController.navigate(Routes.HOME) {
                    popUpTo(Routes.HOME) { inclusive = true }
                }
            }
        }

        composable(Routes.SETTINGS) {
            val vm: SettingsViewModel = viewModel(factory = factory)
            SettingsScreen(vm, onUnpaired = { navController.popBackStack() })
        }

        composable(Routes.HISTORY) {
            val vm = historyViewModel(navController, factory)
            HistoryScreen(vm) { id -> navController.navigate(Routes.historyDetail(id)) }
        }

        composable(Routes.HISTORY_DETAIL) { backStackEntry ->
            // Shares the list's ViewModel, so opening a record needs no second fetch and
            // works with whatever the cache already holds.
            val vm = historyViewModel(navController, factory)
            val id = backStackEntry.arguments?.getString("recordId")?.toLongOrNull()
            if (id == null) {
                MessageScreen("Workout not found", "That link is not valid.")
            } else {
                HistoryDetailScreen(vm, id)
            }
        }
    }
}

/**
 * One [WorkoutViewModel] shared by the workout, set-list, set-confirm and finish screens.
 *
 * Scoped to the WORKOUT back stack entry rather than to each destination, so logging a set
 * from the confirm screen updates the focus card behind it without a refetch.
 */
@Composable
private fun workoutViewModel(
    navController: NavHostController,
    factory: ViewModelProvider.Factory,
): WorkoutViewModel {
    val parentEntry = remember(navController.currentBackStackEntry) {
        runCatching { navController.getBackStackEntry(Routes.WORKOUT) }.getOrNull()
    }
    return if (parentEntry != null) {
        viewModel(viewModelStoreOwner = parentEntry, factory = factory)
    } else {
        viewModel(factory = factory)
    }
}

/**
 * One [HistoryViewModel] for the list and the detail screen, scoped to the HISTORY entry.
 *
 * The same shape as [workoutViewModel] and for the same reason: opening a record must not
 * refetch a page the list already has, least of all on a watch that may be offline.
 */
@Composable
private fun historyViewModel(
    navController: NavHostController,
    factory: ViewModelProvider.Factory,
): HistoryViewModel {
    val parentEntry = remember(navController.currentBackStackEntry) {
        runCatching { navController.getBackStackEntry(Routes.HISTORY) }.getOrNull()
    }
    return if (parentEntry != null) {
        viewModel(viewModelStoreOwner = parentEntry, factory = factory)
    } else {
        viewModel(factory = factory)
    }
}

@Composable
private fun rememberContainerFactory(
    container: LiftWearContainer,
    nodes: WearableNodes,
    session: WorkoutSession,
    version: String,
): ViewModelProvider.Factory {
    val context = LocalContext.current.applicationContext
    return remember(container, nodes, session, context) {
        val intake = CredentialIntake(WearableCredentialTransport(context))
        viewModelFactory {
            initializer { SetupViewModel(container, nodes, intake) }
            initializer { HomeViewModel(container) }
            initializer { WorkoutViewModel(container, session) }
            initializer { SettingsViewModel(container, version) }
            initializer { HistoryViewModel(container) }
        }
    }
}

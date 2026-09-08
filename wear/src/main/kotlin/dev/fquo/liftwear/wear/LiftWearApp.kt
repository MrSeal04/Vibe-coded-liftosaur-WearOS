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
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import dev.fquo.liftwear.data.LiftWearContainer
import dev.fquo.liftwear.wear.ui.common.LoadingScreen
import dev.fquo.liftwear.wear.ui.common.MessageScreen
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
}

@Composable
fun LiftWearApp(container: LiftWearContainer, version: String) {
    MaterialTheme {
        AppScaffold {
            val pairing by container.pairing.collectAsStateWithLifecycle()
            when (pairing) {
                // Unknown means the encrypted key has not been read back yet. Showing a
                // spinner beats flashing the setup screen at an already-paired user.
                LiftWearContainer.PairingState.Unknown -> LoadingScreen()
                else -> LiftWearNavHost(container, version, paired = pairing == LiftWearContainer.PairingState.Paired)
            }
        }
    }
}

@Composable
private fun LiftWearNavHost(container: LiftWearContainer, version: String, paired: Boolean) {
    val navController = rememberSwipeDismissableNavController()
    val factory = rememberContainerFactory(container, version)

    // A key can be revoked from Settings mid-session; that must land on setup rather than
    // leaving screens up that will only produce 401s.
    LaunchedEffect(paired) {
        if (!paired) {
            navController.navigate(Routes.SETUP) {
                popUpTo(navController.graph.id) { inclusive = true }
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
            // Phase 8. The Liftoscript history parser already exists in :core:api; wiring
            // it to a paged, offline-readable list is its own piece of work.
            MessageScreen("History", "Coming in a later build.")
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

@Composable
private fun rememberContainerFactory(
    container: LiftWearContainer,
    version: String,
): ViewModelProvider.Factory = remember(container) {
    viewModelFactory {
        initializer { SetupViewModel(container) }
        initializer { HomeViewModel(container) }
        initializer { WorkoutViewModel(container) }
        initializer { SettingsViewModel(container, version) }
    }
}

package org.marshsoft.timora.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.marshsoft.timora.ui.editor.EditorRoute
import org.marshsoft.timora.ui.home.HomeRoute
import org.marshsoft.timora.ui.run.RunRoute
import org.marshsoft.timora.ui.settings.SettingsRoute

object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val ARG_TIMER_ID = "timerId"
    const val EDITOR = "editor/{$ARG_TIMER_ID}"
    const val RUN = "run/{$ARG_TIMER_ID}"

    fun editor(timerId: Long) = "editor/$timerId"
    fun run(timerId: Long) = "run/$timerId"
}

private const val TRANSITION_MS = 320

@Composable
fun TimoraNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val timerIdArgs = listOf(navArgument(Routes.ARG_TIMER_ID) { type = NavType.LongType })
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier,
        enterTransition = { slideInHorizontally(tweenSpec()) { it / 8 } + fadeIn(tweenSpec()) },
        exitTransition = { fadeOut(tweenSpec()) },
        popEnterTransition = { fadeIn(tweenSpec()) },
        popExitTransition = { slideOutHorizontally(tweenSpec()) { it / 8 } + fadeOut(tweenSpec()) },
    ) {
        composable(Routes.HOME) { entry ->
            HomeRoute(
                onCreateTimer = { entry.ifResumed { navController.navigateSingleTop(Routes.editor(0L)) } },
                onEditTimer = { id -> entry.ifResumed { navController.navigateSingleTop(Routes.editor(id)) } },
                onRunTimer = { id -> entry.ifResumed { navController.navigateSingleTop(Routes.run(id)) } },
                onOpenSettings = { entry.ifResumed { navController.navigateSingleTop(Routes.SETTINGS) } },
            )
        }
        composable(Routes.EDITOR, arguments = timerIdArgs) { entry ->
            EditorRoute(
                timerId = entry.arguments?.getLong(Routes.ARG_TIMER_ID) ?: 0L,
                onDone = { entry.ifResumed { navController.popBackStack() } },
            )
        }
        composable(Routes.RUN, arguments = timerIdArgs) { entry ->
            RunRoute(
                timerId = entry.arguments?.getLong(Routes.ARG_TIMER_ID) ?: 0L,
                onClose = { entry.ifResumed { navController.popBackStack() } },
            )
        }
        composable(Routes.SETTINGS) { entry ->
            SettingsRoute(onBack = { entry.ifResumed { navController.popBackStack() } })
        }
    }
}

/** True while this owner (typically a [androidx.navigation.NavBackStackEntry]) is RESUMED, i.e. not mid-transition. */
fun LifecycleOwner.lifecycleIsResumed(): Boolean = lifecycle.currentState == Lifecycle.State.RESUMED

/** Runs [block] only if this entry is RESUMED, swallowing repeated taps fired while a transition is running. */
internal inline fun LifecycleOwner.ifResumed(block: () -> Unit) {
    if (lifecycleIsResumed()) block()
}

internal fun NavHostController.navigateSingleTop(route: String) {
    navigate(route) { launchSingleTop = true }
}

private fun <T> tweenSpec() = androidx.compose.animation.core.tween<T>(TRANSITION_MS)

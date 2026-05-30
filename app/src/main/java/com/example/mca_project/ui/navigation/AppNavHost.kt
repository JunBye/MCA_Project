package com.example.mca_project.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.NavType
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.mca_project.ui.blinddate.BlindDateCalibrationScreen
import com.example.mca_project.ui.blinddate.BlindDateMeasuringScreen
import com.example.mca_project.ui.blinddate.BlindDatePpgLockScreen
import com.example.mca_project.ui.blinddate.BlindDateProcessingScreen
import com.example.mca_project.ui.blinddate.BlindDateSetupScreen
import com.example.mca_project.ui.blinddate.BlindDateViewModel
import com.example.mca_project.ui.home.HomeScreen
import com.example.mca_project.ui.history.HistoryScreen
import com.example.mca_project.ui.interview.InterviewMeasuringScreen
import com.example.mca_project.ui.interview.InterviewProcessingScreen
import com.example.mca_project.ui.interview.InterviewSetupScreen
import com.example.mca_project.ui.interview.InterviewViewModel
import com.example.mca_project.ui.result.ResultScreen
import com.example.mca_project.ui.splash.SplashScreen

// nested graph route (ViewModel 공유 범위)
private const val INTERVIEW_GRAPH = "interview_graph"
private const val BLIND_DATE_GRAPH = "blinddate_graph"

@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH,
        modifier = modifier,
    ) {

        composable(Routes.SPLASH) {
            SplashScreen(onReady = {
                navController.navigate(Routes.HOME) {
                    popUpTo(Routes.SPLASH) { inclusive = true }
                }
            })
        }

        composable(Routes.HOME) {
            HomeScreen(
                onInterview = { navController.navigate(INTERVIEW_GRAPH) },
                onBlindDate = { navController.navigate(BLIND_DATE_GRAPH) },
                onHistory = { navController.navigate(Routes.HISTORY) },
            )
        }

        interviewGraph(navController)
        blindDateGraph(navController)

        composable(
            Routes.RESULT,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId").orEmpty()
            ResultScreen(
                sessionId = sessionId,
                onRestart = { navController.popBackStack(Routes.HOME, inclusive = false) },
                onHome = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.HISTORY) {
            HistoryScreen(
                onOpenSession = { navController.navigate(Routes.result(it)) },
                onHome = { navController.popBackStack(Routes.HOME, inclusive = false) },
            )
        }
    }
}

/** 같은 nested graph 안에서 ViewModel을 공유하기 위한 헬퍼 */
@Composable
private inline fun <reified VM : androidx.lifecycle.ViewModel> NavBackStackEntry.sharedViewModel(
    navController: NavHostController,
    graphRoute: String,
): VM {
    val parentEntry = remember(this) { navController.getBackStackEntry(graphRoute) }
    return hiltViewModel(parentEntry)
}

private fun NavGraphBuilder.interviewGraph(navController: NavHostController) {
    navigation(startDestination = Routes.INTERVIEW_SETUP, route = INTERVIEW_GRAPH) {
        composable(Routes.INTERVIEW_SETUP) {
            InterviewSetupScreen(onStart = { navController.navigate(Routes.INTERVIEW_MEASURING) })
        }
        composable(Routes.INTERVIEW_MEASURING) { entry ->
            val vm = entry.sharedViewModel<InterviewViewModel>(navController, INTERVIEW_GRAPH)
            InterviewMeasuringScreen(
                viewModel = vm,
                onStop = { navController.navigate(Routes.INTERVIEW_PROCESSING) },
            )
        }
        composable(Routes.INTERVIEW_PROCESSING) { entry ->
            val vm = entry.sharedViewModel<InterviewViewModel>(navController, INTERVIEW_GRAPH)
            InterviewProcessingScreen(
                viewModel = vm,
                onDone = { sessionId ->
                    navController.navigate(Routes.result(sessionId)) {
                        popUpTo(INTERVIEW_GRAPH) { inclusive = true }
                    }
                },
            )
        }
    }
}

private fun NavGraphBuilder.blindDateGraph(navController: NavHostController) {
    navigation(startDestination = Routes.BLIND_DATE_SETUP, route = BLIND_DATE_GRAPH) {
        composable(Routes.BLIND_DATE_SETUP) {
            BlindDateSetupScreen(onNext = { navController.navigate(Routes.BLIND_DATE_PPG_LOCK) })
        }
        composable(Routes.BLIND_DATE_PPG_LOCK) { entry ->
            val vm = entry.sharedViewModel<BlindDateViewModel>(navController, BLIND_DATE_GRAPH)
            BlindDatePpgLockScreen(vm, onNext = { navController.navigate(Routes.BLIND_DATE_CALIBRATION) })
        }
        composable(Routes.BLIND_DATE_CALIBRATION) { entry ->
            val vm = entry.sharedViewModel<BlindDateViewModel>(navController, BLIND_DATE_GRAPH)
            BlindDateCalibrationScreen(vm, onDone = { navController.navigate(Routes.BLIND_DATE_MEASURING) })
        }
        composable(Routes.BLIND_DATE_MEASURING) { entry ->
            val vm = entry.sharedViewModel<BlindDateViewModel>(navController, BLIND_DATE_GRAPH)
            BlindDateMeasuringScreen(vm, onStop = { navController.navigate(Routes.BLIND_DATE_PROCESSING) })
        }
        composable(Routes.BLIND_DATE_PROCESSING) { entry ->
            val vm = entry.sharedViewModel<BlindDateViewModel>(navController, BLIND_DATE_GRAPH)
            BlindDateProcessingScreen(
                vm,
                onDone = { sessionId ->
                    navController.navigate(Routes.result(sessionId)) {
                        popUpTo(BLIND_DATE_GRAPH) { inclusive = true }
                    }
                },
            )
        }
    }
}

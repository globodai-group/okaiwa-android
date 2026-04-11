package io.okaiwa.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.okaiwa.features.auth.presentation.screens.LoginScreen
import io.okaiwa.features.chat.presentation.screens.ChatScreen
import io.okaiwa.features.chat.presentation.screens.ConversationListScreen
import io.okaiwa.features.settings.presentation.screens.SettingsScreen
import io.okaiwa.features.wallet.presentation.screens.WalletScreen

/**
 * Sealed class defining all navigation routes in the application.
 *
 * Each screen has a unique [route] string used by the NavHost.
 * Screens requiring arguments define them as constructor parameters.
 */
sealed class Screen(val route: String) {

    data object Login : Screen("login")
    data object Register : Screen("register")
    data object VerifyOtp : Screen("verify_otp/{phone}") {
        fun createRoute(phone: String): String = "verify_otp/$phone"
    }

    data object ConversationList : Screen("conversations")
    data object Chat : Screen("chat/{conversationId}") {
        fun createRoute(conversationId: String): String = "chat/$conversationId"
    }

    data object Wallet : Screen("wallet")
    data object WalletSend : Screen("wallet/send/{chain}") {
        fun createRoute(chain: String): String = "wallet/send/$chain"
    }
    data object WalletReceive : Screen("wallet/receive")

    data object Contacts : Screen("contacts")
    data object Calls : Screen("calls")
    data object Settings : Screen("settings")
    data object Profile : Screen("profile")
    data object SecurityScore : Screen("settings/security_score")
}

/**
 * Root navigation graph for the application.
 *
 * Defines the navigation structure and screen transitions.
 * The start destination depends on authentication state.
 */
@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Login.route,
        modifier = modifier,
    ) {
        // Auth flow
        composable(Screen.Login.route) {
            LoginScreen(
                onNavigateToRegister = {
                    navController.navigate(Screen.Register.route)
                },
                onLoginSuccess = {
                    navController.navigate(Screen.ConversationList.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
            )
        }

        // Main tabs
        composable(Screen.ConversationList.route) {
            ConversationListScreen(
                onNavigateToChat = { conversationId ->
                    navController.navigate(Screen.Chat.createRoute(conversationId))
                },
                onNavigateToWallet = {
                    navController.navigate(Screen.Wallet.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
            )
        }

        composable(
            route = Screen.Chat.route,
            arguments = listOf(
                navArgument("conversationId") { type = NavType.StringType }
            ),
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString("conversationId") ?: return@composable
            ChatScreen(
                conversationId = conversationId,
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(Screen.Wallet.route) {
            WalletScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSend = { chain ->
                    navController.navigate(Screen.WalletSend.createRoute(chain))
                },
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}

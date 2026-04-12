package io.okaiwa.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.okaiwa.features.auth.domain.entities.Countries
import io.okaiwa.features.auth.domain.entities.Country
import io.okaiwa.features.auth.presentation.screens.CountryPickerScreen
import io.okaiwa.features.auth.presentation.screens.OtpVerificationScreen
import io.okaiwa.features.auth.presentation.screens.PhoneEntryMode
import io.okaiwa.features.auth.presentation.screens.PhoneNumberScreen
import io.okaiwa.features.auth.presentation.screens.SplashScreen
import io.okaiwa.features.auth.presentation.screens.WelcomeScreen
import io.okaiwa.features.chat.presentation.screens.ChatScreen
import io.okaiwa.features.chat.presentation.screens.ConversationListScreen
import io.okaiwa.features.settings.presentation.screens.SettingsScreen
import io.okaiwa.features.wallet.presentation.screens.WalletScreen

/**
 * Navigation routes for the entire app.
 *
 * The onboarding graph is:
 *   Splash -> Welcome -> (Register | Login) -> CountryPicker (modal)
 *                          -> Phone entry -> Otp verification -> ConversationList
 *
 * Routes are ephemeral — no backstack restoration across process death
 * yet. The UX flow reboots at Splash every cold start until the
 * persistent auth session lands.
 */
sealed class Screen(val route: String) {
    // Onboarding
    data object Splash : Screen("splash")
    data object Welcome : Screen("welcome")
    data object PhoneEntry : Screen("phone_entry/{mode}") {
        fun createRoute(mode: PhoneEntryMode): String = "phone_entry/${mode.name}"
    }
    data object CountryPicker : Screen("country_picker")
    data object OtpVerification : Screen("otp/{phone}") {
        fun createRoute(phone: String): String = "otp/${java.net.URLEncoder.encode(phone, "UTF-8")}"
    }

    // Main tabs
    data object ConversationList : Screen("conversations")
    data object Chat : Screen("chat/{conversationId}") {
        fun createRoute(conversationId: String): String = "chat/$conversationId"
    }
    data object Wallet : Screen("wallet")
    data object Settings : Screen("settings")
}

/**
 * Root navigation graph for the application.
 */
@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    // Holds the country pick between PhoneNumberScreen ↔ CountryPickerScreen.
    // Saveable keeps the selection across config changes; we store the ISO
    // code (a plain String) and rehydrate the full Country on restore.
    var selectedIso by rememberSaveable { mutableStateOf(Countries.default.isoCode) }
    val selectedCountry: Country = Countries.findByIso(selectedIso) ?: Countries.default

    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route,
        modifier = modifier,
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(onFinished = {
                navController.navigate(Screen.Welcome.route) {
                    popUpTo(Screen.Splash.route) { inclusive = true }
                }
            })
        }

        composable(Screen.Welcome.route) {
            WelcomeScreen(
                onCreateAccount = {
                    navController.navigate(Screen.PhoneEntry.createRoute(PhoneEntryMode.Register))
                },
                onSignIn = {
                    navController.navigate(Screen.PhoneEntry.createRoute(PhoneEntryMode.Login))
                },
            )
        }

        composable(
            route = Screen.PhoneEntry.route,
            arguments = listOf(navArgument("mode") { type = NavType.StringType }),
        ) { backStackEntry ->
            val modeName = backStackEntry.arguments?.getString("mode") ?: PhoneEntryMode.Register.name
            val mode = runCatching { PhoneEntryMode.valueOf(modeName) }.getOrDefault(PhoneEntryMode.Register)

            PhoneNumberScreen(
                mode = mode,
                selectedCountry = selectedCountry,
                onBack = { navController.popBackStack() },
                onPickCountry = {
                    navController.navigate(Screen.CountryPicker.route)
                },
                onContinue = { country, nationalNumber, _ ->
                    val fullNumber = "${country.dialCode}$nationalNumber"
                    navController.navigate(Screen.OtpVerification.createRoute(fullNumber))
                },
            )
        }

        composable(Screen.CountryPicker.route) {
            CountryPickerScreen(
                onBack = { navController.popBackStack() },
                onSelect = { country ->
                    selectedIso = country.isoCode
                    navController.popBackStack()
                },
            )
        }

        composable(
            route = Screen.OtpVerification.route,
            arguments = listOf(navArgument("phone") { type = NavType.StringType }),
        ) { backStackEntry ->
            val phone = java.net.URLDecoder.decode(
                backStackEntry.arguments?.getString("phone") ?: "",
                "UTF-8",
            )
            OtpVerificationScreen(
                phoneNumberDisplay = phone,
                onBack = { navController.popBackStack() },
                onSubmit = {
                    navController.navigate(Screen.ConversationList.route) {
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                    }
                },
                onResend = { /* TODO: call authRepository.requestOtp again */ },
            )
        }

        // Main tabs
        composable(Screen.ConversationList.route) {
            ConversationListScreen(
                onNavigateToChat = { conversationId ->
                    navController.navigate(Screen.Chat.createRoute(conversationId))
                },
                onNavigateToWallet = { navController.navigate(Screen.Wallet.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
            )
        }

        composable(
            route = Screen.Chat.route,
            arguments = listOf(navArgument("conversationId") { type = NavType.StringType }),
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
                onNavigateToSend = { /* chain -> */ },
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}

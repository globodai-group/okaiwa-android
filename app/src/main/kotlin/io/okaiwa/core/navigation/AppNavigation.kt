package io.okaiwa.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import io.okaiwa.features.contacts.presentation.screens.ContactPickerScreen
import io.okaiwa.features.profile.presentation.screens.ProfileScreen
import io.okaiwa.features.settings.presentation.screens.SettingsScreen
import io.okaiwa.features.wallet.presentation.screens.WalletOnboardingScreen

/**
 * Navigation routes for the entire app.
 *
 * The onboarding graph is:
 *   Splash -> Welcome -> PhoneEntry -> CountryPicker (modal) -> Otp -> Main
 *
 * Once through onboarding the user lands on [Screen.Main] which hosts
 * the four-tab bottom navigation. Secondary screens (individual chat,
 * settings sub-pages, wallet send/receive) are pushed on top of the
 * Main scaffold.
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

    // Main bottom-tab host
    data object Main : Screen("main")

    // Stacked destinations pushed on top of the main scaffold.
    data object Chat : Screen("chat/{conversationId}") {
        fun createRoute(conversationId: String): String = "chat/$conversationId"
    }
    data object ContactPicker : Screen("contacts")
}

/**
 * Root navigation graph for the application.
 */
@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
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
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                    }
                },
                onResend = { /* TODO: call authRepository.requestOtp again */ },
            )
        }

        // Main bottom-tab host
        composable(Screen.Main.route) {
            MainScaffold { tab ->
                when (tab) {
                    MainTab.Chats -> ConversationListScreen(
                        onNavigateToChat = { conversationId ->
                            navController.navigate(Screen.Chat.createRoute(conversationId))
                        },
                        // Top-right contacts icon AND the floating FAB
                        // both route to the contact picker. A future
                        // split could open "compose mode" on the FAB vs.
                        // the full address book on the icon, but the
                        // single destination is fine while the flow is
                        // scoped to "pick someone to message".
                        onNavigateToContacts = {
                            navController.navigate(Screen.ContactPicker.route)
                        },
                    )

                    // The full balance overview is only reached once a
                    // wallet exists. Until then, the tab shows the
                    // create/import CTA so we never advertise a "0 ETH"
                    // empty state that could read as a hollow promise.
                    MainTab.Wallet -> WalletOnboardingScreen(
                        onCreateWallet = { /* TODO: push seed-phrase gen flow */ },
                        onImportWallet = { /* TODO: push mnemonic import flow */ },
                    )

                    MainTab.Settings -> SettingsScreen()

                    MainTab.Profile -> ProfileScreen()
                }
            }
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

        composable(Screen.ContactPicker.route) {
            ContactPickerScreen(
                onBack = { navController.popBackStack() },
                onStartConversation = { contact ->
                    // TODO: open or create a conversation with this contact.
                    // For now the picker simply pops — wiring it into
                    // MockChatRepository.createConversation() lands in
                    // the next commit.
                    navController.popBackStack()
                },
            )
        }
    }
}

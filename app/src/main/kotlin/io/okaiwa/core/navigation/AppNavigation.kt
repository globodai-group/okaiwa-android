package io.okaiwa.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
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
import io.okaiwa.features.auth.presentation.viewmodels.OtpVerificationViewModel
import io.okaiwa.features.auth.presentation.viewmodels.PhoneEntryViewModel
import io.okaiwa.features.profile.presentation.LanguagePickerScreen
import io.okaiwa.features.profile.presentation.ProfileSetupScreen
import io.okaiwa.features.chat.presentation.screens.ChatScreen
import io.okaiwa.features.chat.presentation.screens.ConversationListScreen
import io.okaiwa.features.chat.presentation.screens.NewMessageScreen
import io.okaiwa.features.contacts.presentation.screens.ContactPickerScreen
import io.okaiwa.features.profile.presentation.screens.ProfileScreen
import io.okaiwa.features.settings.presentation.screens.SettingsScreen
import io.okaiwa.features.wallet.presentation.onboarding.WalletOnboardingFlow
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

    /** Post-OTP "Pick your username + display name" — required once. */
    data object ProfileSetup : Screen("profile_setup")

    // Stacked destinations pushed on top of the main scaffold.
    data object Chat : Screen("chat/{conversationId}") {
        fun createRoute(conversationId: String): String = "chat/$conversationId"
    }
    /** FAB entry — groups / channels / invite + existing Okaiwa contacts. */
    data object NewMessage : Screen("new_message")
    /** Contacts icon entry — device address book with READ_CONTACTS. */
    data object ContactPicker : Screen("contacts")
    /** Wallet creation onboarding (method → tips → seed → verify → name → ready). */
    data object WalletCreate : Screen("wallet/create")

    /** In-app Français / English switcher surfaced from the Profile tab. */
    data object LanguagePicker : Screen("language_picker")
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
            // Read the persisted session ONCE at splash time so the
            // entry route reflects whether the user is already
            // authenticated. Logic is centralized in SessionGateViewModel
            // so the routing matrix has a single source of truth.
            val gate: SessionGateViewModel = hiltViewModel()
            SplashScreen(onFinished = {
                val target = when {
                    gate.session?.isVerified != true -> Screen.Welcome.route
                    !gate.session.profileSetupDone -> Screen.ProfileSetup.route
                    else -> Screen.Main.route
                }
                navController.navigate(target) {
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

            val phoneVm: PhoneEntryViewModel = hiltViewModel()
            val phoneState by phoneVm.state.collectAsState()
            var pendingPhoneE164 by rememberSaveable { mutableStateOf("") }

            // The repository hits the identity service as soon as the
            // user taps "Continuer" — the OTP screen is only reached
            // once that call succeeds, and the phone is already stashed
            // in the SessionStore so OTP verify can re-submit the
            // matching hash without asking for the number again.
            LaunchedEffect(phoneState.acknowledgedPhoneE164) {
                val number = phoneState.acknowledgedPhoneE164 ?: return@LaunchedEffect
                phoneVm.onNavigated()
                navController.navigate(Screen.OtpVerification.createRoute(number))
            }

            PhoneNumberScreen(
                mode = mode,
                selectedCountry = selectedCountry,
                onBack = { navController.popBackStack() },
                onPickCountry = {
                    navController.navigate(Screen.CountryPicker.route)
                },
                onContinue = { country, nationalNumber, _ ->
                    val fullNumber = "${country.dialCode}$nationalNumber"
                    pendingPhoneE164 = fullNumber
                    phoneVm.submitPhone(fullNumber, mode)
                },
                isLoading = phoneState.isLoading,
                errorMessage = phoneState.error,
                // Login-only: when the backend returns 404 on
                // /v1/auth/login we flip the compose flow to Register
                // with the same number instead of asking the user to
                // re-type it. The phoneVm clears its error state after
                // we consume it.
                accountNotFoundForLogin = phoneState.accountNotFoundForLogin,
                onCreateAccountFromLogin = {
                    phoneVm.clearError()
                    if (pendingPhoneE164.isNotEmpty()) {
                        phoneVm.submitPhone(pendingPhoneE164, PhoneEntryMode.Register)
                    }
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

            val otpVm: OtpVerificationViewModel = hiltViewModel()
            val otpState by otpVm.state.collectAsState()

            LaunchedEffect(otpState.verified, otpState.nextStep) {
                // The VM asked the server whether this account
                // already has a username. New users → ProfileSetup.
                // Returning users (reinstall, multi-device) → Main
                // directly. Without this branch a reinstall on the
                // same phone would force the user to retype their
                // username and immediately hit a 409.
                //
                // nextStep can be null while the bootstrap call is
                // in flight — keep the screen as-is until the VM
                // either resolves a destination or surfaces an error.
                val target = when (otpState.nextStep) {
                    OtpVerificationViewModel.NextStep.Main -> Screen.Main.route
                    OtpVerificationViewModel.NextStep.ProfileSetup -> Screen.ProfileSetup.route
                    null -> null
                }
                if (otpState.verified && target != null) {
                    navController.navigate(target) {
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                    }
                }
            }

            OtpVerificationScreen(
                phoneNumberDisplay = phone,
                onBack = { navController.popBackStack() },
                onSubmit = { code -> otpVm.submit(code) },
                onResend = { /* TODO: call authRepository.requestOtp again */ },
                isLoading = otpState.isLoading,
                errorMessage = otpState.error,
            )
        }

        // Main bottom-tab host
        composable(Screen.ProfileSetup.route) {
            ProfileSetupScreen(onDone = {
                navController.navigate(Screen.Main.route) {
                    popUpTo(Screen.Welcome.route) { inclusive = true }
                }
            })
        }

        composable(Screen.Main.route) {
            MainScaffold { tab ->
                when (tab) {
                    MainTab.Chats -> ConversationListScreen(
                        onNavigateToChat = { conversationId ->
                            navController.navigate(Screen.Chat.createRoute(conversationId))
                        },
                        // Top-right contacts icon → device address book
                        // picker (READ_CONTACTS permission gated) where
                        // you match phone contacts against Okaiwa users.
                        onNavigateToContacts = {
                            navController.navigate(Screen.ContactPicker.route)
                        },
                        // FAB → new-message compose flow: group / channel /
                        // invite actions + list of existing Okaiwa
                        // contacts (people you already have conversations
                        // with). This is purely Okaiwa-side data and does
                        // NOT ask the user for the contacts permission.
                        onNavigateToNewConversation = {
                            navController.navigate(Screen.NewMessage.route)
                        },
                    )

                    // The full balance overview is only reached once a
                    // wallet exists. Until then, the tab shows the
                    // create/import CTA so we never advertise a "0 ETH"
                    // empty state that could read as a hollow promise.
                    MainTab.Wallet -> WalletOnboardingScreen(
                        onCreateWallet = {
                            navController.navigate(Screen.WalletCreate.route)
                        },
                        onImportWallet = { /* TODO: push mnemonic import flow */ },
                    )

                    MainTab.Settings -> SettingsScreen()

                    MainTab.Profile -> ProfileScreen(
                        onSignedOut = {
                            // popUpTo(0) clears the ENTIRE back stack
                            // before navigating to Welcome. The
                            // alternative `popUpTo(Splash, inclusive)`
                            // wouldn't match (Splash was already
                            // dropped from the stack on the initial
                            // splash → main transition) and would
                            // leave Welcome stacked on top of Main —
                            // letting the system back button return
                            // to a wiped-session Profile screen.
                            navController.navigate(Screen.Welcome.route) {
                                popUpTo(0) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                        onOpenLanguagePicker = {
                            navController.navigate(Screen.LanguagePicker.route)
                        },
                    )
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

        composable(Screen.NewMessage.route) {
            NewMessageScreen(
                onBack = { navController.popBackStack() },
                onStartConversation = { conversationId ->
                    // Replace the compose screen with the actual chat —
                    // pressing back returns to the conversation list
                    // rather than stacking NewMessage in the history.
                    navController.popBackStack()
                    navController.navigate(Screen.Chat.createRoute(conversationId))
                },
                onInviteContact = {
                    navController.navigate(Screen.ContactPicker.route)
                },
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

        composable(Screen.WalletCreate.route) {
            WalletOnboardingFlow(
                onFinished = { navController.popBackStack() },
                onCancel = { navController.popBackStack() },
            )
        }

        composable(Screen.LanguagePicker.route) {
            LanguagePickerScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}

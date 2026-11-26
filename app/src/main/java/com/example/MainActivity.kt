package com.example

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.EventNote
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.components.OnboardingDefaultSmsDialog
import com.example.ui.navigation.KmsRoutes
import com.example.ui.screens.BlockedSendersScreen
import com.example.ui.screens.ConversationScreen
import com.example.ui.screens.FinanceLedgerScreen
import com.example.ui.screens.FinanceScreen
import com.example.ui.screens.InboxScreen
import com.example.ui.screens.NewMessageScreen
import com.example.ui.screens.OffersScreen
import com.example.ui.screens.PastArchiveScreen
import com.example.ui.screens.RemindersScreen
import com.example.ui.screens.ScheduledMessagesScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.KmsMsTheme
import com.example.viewmodel.KmsMainViewModel
import kotlinx.coroutines.flow.collectLatest

class MainActivity : FragmentActivity() {

    private var initialSmsRecipient by mutableStateOf<String?>(null)
    private var initialThreadKey by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)

        setContent {
            val viewModel: KmsMainViewModel = viewModel()
            val prefs by viewModel.preferences.collectAsStateWithLifecycle()

            KmsMsTheme(
                themeMode = prefs.themeMode,
                fontScale = prefs.fontScale
            ) {
                KmsMsRootApp(
                    viewModel = viewModel,
                    initialRecipient = initialSmsRecipient,
                    initialThreadKey = initialThreadKey,
                    onConsumedDeepLink = {
                        initialSmsRecipient = null
                        initialThreadKey = null
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val threadKeyExtra = intent.getStringExtra("open_thread_key")
        if (!threadKeyExtra.isNullOrBlank()) {
            initialThreadKey = threadKeyExtra
            return
        }
        if (intent.action == Intent.ACTION_SENDTO || intent.action == Intent.ACTION_SEND) {
            val data: Uri? = intent.data
            if (data != null && data.scheme in listOf("sms", "smsto", "mms", "mmsto")) {
                initialSmsRecipient = data.schemeSpecificPart?.substringBefore("?")?.trim()
            }
        }
    }
}

private data class TopNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@Composable
private fun KmsMsRootApp(
    viewModel: KmsMainViewModel,
    initialRecipient: String?,
    initialThreadKey: String?,
    onConsumedDeepLink: () -> Unit
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val app = context.applicationContext as KmsMsApplication

    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val isDefaultSms by viewModel.isDefaultSmsApp.collectAsStateWithLifecycle()
    val hasSmsPermission by viewModel.hasSmsPermission.collectAsStateWithLifecycle()
    var showOnboardingPrompt by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshDeviceStatusAndSync(silent = true)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val coreSmsPermissions = remember {
        buildList {
            add(Manifest.permission.READ_SMS)
            add(Manifest.permission.RECEIVE_SMS)
            add(Manifest.permission.SEND_SMS)
            add(Manifest.permission.READ_CONTACTS)
            add(Manifest.permission.READ_PHONE_STATE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }

    val runtimePermissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        viewModel.refreshDeviceStatusAndSync(silent = false)
        val readGranted = results[Manifest.permission.READ_SMS] == true
        if (readGranted) {
            viewModel.emitSnackbar("SMS permissions granted! Inbox synced locally on-device.")
        } else if (results.isNotEmpty()) {
            viewModel.emitSnackbar("SMS read permission was not granted. Tap Enable on the banner to retry.")
        }
    }

    val requestCorePermissions: () -> Unit = {
        runtimePermissionsLauncher.launch(coreSmsPermissions)
    }

    val defaultSmsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshDeviceStatusAndSync(silent = true)
        // Follow up by requesting runtime permissions so user has full functionality
        requestCorePermissions()
    }

    val launchDefaultSmsRolePrompt: () -> Unit = {
        val roleIntent = app.deviceSmsSyncManager.buildRequestDefaultSmsIntent()
        if (roleIntent != null) {
            runCatching { defaultSmsLauncher.launch(roleIntent) }.onFailure {
                requestCorePermissions()
            }
        } else {
            requestCorePermissions()
        }
    }

    val launchFullPermissionFlow: () -> Unit = {
        if (!isDefaultSms) {
            launchDefaultSmsRolePrompt()
        } else {
            requestCorePermissions()
        }
    }

    // First-launch prompt to become Android Default SMS App and grant SMS permissions
    LaunchedEffect(prefs.hasPromptedDefaultSms, isDefaultSms, hasSmsPermission) {
        if (!prefs.hasPromptedDefaultSms && (!isDefaultSms || !hasSmsPermission)) {
            showOnboardingPrompt = true
        } else {
            viewModel.refreshDeviceStatusAndSync(silent = true)
        }
    }

    // Deep-link handling (from notification tap or sms:/smsto: intent)
    LaunchedEffect(initialThreadKey, initialRecipient) {
        when {
            !initialThreadKey.isNullOrBlank() -> {
                navController.navigate(KmsRoutes.conversation(initialThreadKey))
                onConsumedDeepLink()
            }
            !initialRecipient.isNullOrBlank() -> {
                navController.navigate(KmsRoutes.newMessage(initialRecipient))
                onConsumedDeepLink()
            }
        }
    }

    // Snackbar with Undo support
    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collectLatest { event ->
            val result = snackbarHostState.showSnackbar(
                message = event.message,
                actionLabel = event.actionLabel,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                event.undoThreadKey?.let { viewModel.undoDeleteThread(it) }
                if (event.undoThreadKeys.isNotEmpty()) {
                    viewModel.undoDeleteThreads(event.undoThreadKeys)
                }
                event.undoMessageId?.let { viewModel.undoDeleteMessage(it) }
            }
        }
    }

    val navItems = remember {
        listOf(
            TopNavItem(KmsRoutes.ROUTE_INBOX, "Inbox", Icons.Rounded.ChatBubble, Icons.Outlined.ChatBubbleOutline),
            TopNavItem(KmsRoutes.ROUTE_FINANCE, "Finance", Icons.Rounded.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet),
            TopNavItem(KmsRoutes.ROUTE_REMINDERS, "Reminders", Icons.Rounded.EventNote, Icons.Outlined.EventNote),
            TopNavItem(KmsRoutes.ROUTE_OFFERS, "Offers", Icons.Rounded.LocalOffer, Icons.Outlined.LocalOffer)
        )
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isTopLevel = currentRoute in navItems.map { it.route }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        val navigateTopLevel: (String) -> Unit = { route ->
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (isTopLevel && !useNavRail) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        navItems.forEach { item ->
                            val selected = currentRoute == item.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopLevel(item.route) },
                                icon = {
                                    Icon(
                                        if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = { Text(item.label) },
                                modifier = Modifier.testTag("nav_${item.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isTopLevel && useNavRail) {
                    NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                        navItems.forEach { item ->
                            val selected = currentRoute == item.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(item.route) },
                                icon = {
                                    Icon(
                                        if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = { Text(item.label) },
                                modifier = Modifier.testTag("rail_${item.route}")
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = KmsRoutes.ROUTE_INBOX,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(KmsRoutes.ROUTE_INBOX) {
                        InboxScreen(
                            viewModel = viewModel,
                            onOpenThread = { key -> navController.navigate(KmsRoutes.conversation(key)) },
                            onNewMessage = { navController.navigate(KmsRoutes.newMessage()) },
                            onOpenBlocked = { navController.navigate(KmsRoutes.ROUTE_BLOCKED) },
                            onOpenScheduled = { navController.navigate(KmsRoutes.ROUTE_SCHEDULED) },
                            onOpenPastOrders = { navController.navigate(KmsRoutes.archive(1)) },
                            onOpenSettings = { navController.navigate(KmsRoutes.ROUTE_SETTINGS) },
                            onRequestDefaultSms = launchFullPermissionFlow
                        )
                    }

                    composable(KmsRoutes.ROUTE_FINANCE) {
                        FinanceScreen(
                            viewModel = viewModel,
                            onOpenAccountLedger = { accountKey ->
                                navController.navigate(KmsRoutes.financeLedger(accountKey))
                            }
                        )
                    }

                    composable(KmsRoutes.ROUTE_REMINDERS) {
                        RemindersScreen(
                            viewModel = viewModel,
                            onOpenPastReminders = { navController.navigate(KmsRoutes.archive(0)) },
                            onOpenPastOrders = { navController.navigate(KmsRoutes.archive(1)) }
                        )
                    }

                    composable(KmsRoutes.ROUTE_OFFERS) {
                        OffersScreen(viewModel = viewModel)
                    }

                    composable(
                        route = KmsRoutes.ROUTE_CONVERSATION_PATTERN,
                        arguments = listOf(navArgument("threadKey") { type = NavType.StringType })
                    ) { entry ->
                        val key = Uri.decode(entry.arguments?.getString("threadKey").orEmpty())
                        ConversationScreen(
                            threadKey = key,
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(
                        route = KmsRoutes.ROUTE_NEW_MESSAGE,
                        arguments = listOf(navArgument("recipient") {
                            type = NavType.StringType
                            defaultValue = ""
                        })
                    ) { entry ->
                        val recipient = Uri.decode(entry.arguments?.getString("recipient").orEmpty())
                        NewMessageScreen(
                            initialRecipient = recipient,
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onOpenThread = { key ->
                                navController.navigate(KmsRoutes.conversation(key)) {
                                    popUpTo(KmsRoutes.ROUTE_INBOX)
                                }
                            }
                        )
                    }

                    composable(
                        route = KmsRoutes.ROUTE_FINANCE_LEDGER_PATTERN,
                        arguments = listOf(navArgument("accountKey") { type = NavType.StringType })
                    ) { entry ->
                        val accountKey = Uri.decode(entry.arguments?.getString("accountKey").orEmpty())
                        FinanceLedgerScreen(
                            accountKey = accountKey,
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(KmsRoutes.ROUTE_BLOCKED) {
                        BlockedSendersScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(KmsRoutes.ROUTE_SCHEDULED) {
                        ScheduledMessagesScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(
                        route = KmsRoutes.ROUTE_ARCHIVE_PATTERN,
                        arguments = listOf(navArgument("initialTab") { type = NavType.IntType })
                    ) { entry ->
                        val initialTab = entry.arguments?.getInt("initialTab") ?: 0
                        PastArchiveScreen(
                            initialTab = initialTab,
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(KmsRoutes.ROUTE_SETTINGS) {
                        SettingsScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }

    if (showOnboardingPrompt) {
        OnboardingDefaultSmsDialog(
            isDefaultSms = isDefaultSms,
            hasSmsPermission = hasSmsPermission,
            onSetDefaultSmsAndPermissions = {
                showOnboardingPrompt = false
                viewModel.markPromptedDefaultSms()
                launchFullPermissionFlow()
            },
            onRequestPermissionsOnly = {
                showOnboardingPrompt = false
                viewModel.markPromptedDefaultSms()
                requestCorePermissions()
            },
            onDismiss = {
                showOnboardingPrompt = false
                viewModel.markPromptedDefaultSms()
            }
        )
    }
}

package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material.icons.rounded.MarkEmailUnread
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.ScheduleSend
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.InboxTab
import com.example.data.model.SmsCategory
import com.example.data.model.SwipeAction
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.MoveToFolderDialog
import com.example.ui.components.SenderMonogram
import com.example.ui.components.formatShortTime
import com.example.ui.theme.LocalKmsSemanticColors
import com.example.ui.theme.MonoFontFamily
import com.example.viewmodel.KmsMainViewModel
import com.example.viewmodel.ThreadSummary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun InboxScreen(
    viewModel: KmsMainViewModel,
    onOpenThread: (String) -> Unit,
    onNewMessage: () -> Unit,
    onOpenBlocked: () -> Unit,
    onOpenScheduled: () -> Unit,
    onOpenPastOrders: () -> Unit,
    onOpenSettings: () -> Unit,
    onRequestDefaultSms: () -> Unit
) {
    val selectedTab by viewModel.selectedInboxTab.collectAsStateWithLifecycle()
    val unreadOnly by viewModel.unreadOnly.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val allThreads by viewModel.allThreads.collectAsStateWithLifecycle()
    val tabUnreadCounts by viewModel.tabUnreadCounts.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val isDefaultSms by viewModel.isDefaultSmsApp.collectAsStateWithLifecycle()
    val hasSmsPermission by viewModel.hasSmsPermission.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()

    var showSearch by remember { mutableStateOf(false) }
    var overflowExpanded by remember { mutableStateOf(false) }
    var selectedThreadForMenu by remember { mutableStateOf<ThreadSummary?>(null) }
    var moveTargetThread by remember { mutableStateOf<ThreadSummary?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tabs = InboxTab.entries
    val pagerState = rememberPagerState(
        initialPage = tabs.indexOf(selectedTab),
        pageCount = { tabs.size }
    )

    LaunchedEffect(selectedTab) {
        val targetIdx = tabs.indexOf(selectedTab)
        if (pagerState.currentPage != targetIdx) {
            pagerState.animateScrollToPage(targetIdx)
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        val pageTab = tabs[pagerState.currentPage]
        if (pageTab != selectedTab) {
            viewModel.selectInboxTab(pageTab)
        }
    }

    val semantic = LocalKmsSemanticColors.current

    Scaffold(
        topBar = {
            if (selectedThreadForMenu != null) {
                val active = selectedThreadForMenu!!
                TopAppBar(
                    title = {
                        Text(
                            text = active.displayTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { selectedThreadForMenu = null }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close selection")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                viewModel.toggleThreadStar(active)
                                selectedThreadForMenu = null
                            },
                            modifier = Modifier.testTag("action_star_thread")
                        ) {
                            Icon(
                                if (active.isStarred) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                                contentDescription = "Star thread"
                            )
                        }
                        IconButton(
                            onClick = {
                                moveTargetThread = active
                                selectedThreadForMenu = null
                            },
                            modifier = Modifier.testTag("action_move_thread")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.DriveFileMove,
                                contentDescription = "Move sender to folder"
                            )
                        }
                        IconButton(
                            onClick = {
                                viewModel.blockSender(active)
                                selectedThreadForMenu = null
                            },
                            modifier = Modifier.testTag("action_block_sender")
                        ) {
                            Icon(Icons.Rounded.Block, contentDescription = "Block sender")
                        }
                        IconButton(
                            onClick = {
                                viewModel.deleteThread(active)
                                selectedThreadForMenu = null
                            },
                            modifier = Modifier.testTag("action_delete_thread")
                        ) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete thread")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "KMS-MS",
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Text(
                                text = "On-device private inbox",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                showSearch = !showSearch
                                if (!showSearch) viewModel.updateSearchQuery("")
                            },
                            modifier = Modifier.testTag("inbox_search_toggle")
                        ) {
                            Icon(
                                if (showSearch) Icons.Rounded.Close else Icons.Rounded.Search,
                                contentDescription = "Search messages"
                            )
                        }
                        IconButton(
                            onClick = { viewModel.refreshDeviceStatusAndSync(silent = false) },
                            modifier = Modifier.testTag("inbox_sync_button")
                        ) {
                            Icon(Icons.Rounded.Sync, contentDescription = "Sync device SMS")
                        }
                        Box {
                            IconButton(
                                onClick = { overflowExpanded = true },
                                modifier = Modifier.testTag("inbox_overflow_menu")
                            ) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(
                                expanded = overflowExpanded,
                                onDismissRequest = { overflowExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Blocked Senders") },
                                    leadingIcon = { Icon(Icons.Rounded.Block, null) },
                                    onClick = { overflowExpanded = false; onOpenBlocked() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Scheduled Messages") },
                                    leadingIcon = { Icon(Icons.Rounded.ScheduleSend, null) },
                                    onClick = { overflowExpanded = false; onOpenScheduled() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Past Orders Archive") },
                                    leadingIcon = { Icon(Icons.Rounded.LocalShipping, null) },
                                    onClick = { overflowExpanded = false; onOpenPastOrders() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Settings") },
                                    leadingIcon = { Icon(Icons.Rounded.Settings, null) },
                                    onClick = { overflowExpanded = false; onOpenSettings() }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewMessage,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("compose_sms_fab")
            ) {
                Icon(Icons.Rounded.Edit, contentDescription = "Compose new message")
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isSyncing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // Default SMS Role & Permission Banner when not yet configured
            AnimatedVisibility(visible = !isDefaultSms || !hasSmsPermission) {
                val bannerText = when {
                    !isDefaultSms && !hasSmsPermission -> "Set KMS-MS as Default SMS app and grant SMS permissions to read and organize your messages."
                    !isDefaultSms -> "Set KMS-MS as Default SMS app for full automatic sorting & OTP cleanup."
                    else -> "SMS permissions required to read and organize your device messages."
                }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = bannerText,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = onRequestDefaultSms,
                            modifier = Modifier.testTag("banner_set_default_sms")
                        ) {
                            Text("Enable")
                        }
                    }
                }
            }

            // Search Input
            AnimatedVisibility(visible = showSearch, enter = fadeIn(), exit = fadeOut()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    placeholder = { Text("Search messages, senders, OTPs…") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("inbox_search_input")
                )
            }

            // 4-Category Segmented Strip (Personal / Transactions / Promotions / Starred)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                tabs.forEachIndexed { idx, tab ->
                    val isSelected = selectedTab == tab
                    val count = tabUnreadCounts[tab] ?: 0
                    Surface(
                        onClick = {
                            viewModel.selectInboxTab(tab)
                            scope.launch { pagerState.animateScrollToPage(idx) }
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainer,
                        border = if (isSelected) null else BorderStroke(
                            1.dp,
                            LocalKmsSemanticColors.current.cardBorder
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("inbox_tab_${tab.name.lowercase()}")
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = when (tab) {
                                    InboxTab.TRANSACTIONS -> "Txns"
                                    InboxTab.PROMOTIONS -> "Promos"
                                    else -> tab.label
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (count > 0) {
                                Text(
                                    text = "$count new",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                                    else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // "Unread Only" Quick Toggle Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FilterChip(
                    selected = unreadOnly,
                    onClick = { viewModel.toggleUnreadOnly() },
                    label = { Text("Unread only") },
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.FilterList,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.testTag("unread_only_toggle")
                )
                Surface(
                    onClick = { viewModel.updateSwipeSettings(tabSwipeEnabled = !prefs.tabSwipeEnabled) },
                    shape = RoundedCornerShape(20.dp),
                    color = if (!prefs.tabSwipeEnabled) semantic.creditGreenContainer else MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, if (!prefs.tabSwipeEnabled) semantic.creditGreen.copy(alpha = 0.3f) else semantic.cardBorder),
                    modifier = Modifier.testTag("toggle_swipe_mode_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SwapHoriz,
                            contentDescription = null,
                            tint = if (!prefs.tabSwipeEnabled) semantic.creditGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (!prefs.tabSwipeEnabled) "Swipe: ➔ Read • 🠔 Delete" else "Swipe: Tabs ↔",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = if (!prefs.tabSwipeEnabled) semantic.creditGreen else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Thread List inside HorizontalPager (userScrollEnabled governed by Settings > Swipe actions)
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = prefs.tabSwipeEnabled,
                modifier = Modifier.weight(1f)
            ) { page ->
                val tab = tabs[page]
                val query = searchQuery.trim().lowercase()
                val pageThreads = remember(allThreads, tab, unreadOnly, query) {
                    allThreads.filter { thread ->
                        val matchesTab = when (tab) {
                            InboxTab.PERSONAL -> thread.category == SmsCategory.PERSONAL
                            InboxTab.TRANSACTIONS -> thread.category == SmsCategory.TRANSACTION
                            InboxTab.PROMOTIONS -> thread.category == SmsCategory.PROMOTION
                            InboxTab.STARRED -> thread.isStarred
                        }
                        val matchesUnread = !unreadOnly || thread.unreadCount > 0
                        val matchesQuery = query.isBlank() ||
                            thread.displayTitle.lowercase().contains(query) ||
                            thread.address.lowercase().contains(query) ||
                            thread.lastSnippet.lowercase().contains(query) ||
                            (thread.latestOtp?.contains(query) == true)
                        matchesTab && matchesUnread && matchesQuery
                    }
                }

                if (pageThreads.isEmpty()) {
                    val emptySubtitle = when {
                        unreadOnly -> "No unread conversations in ${tab.label}."
                        !isDefaultSms || !hasSmsPermission -> "Set KMS-MS as your Default SMS App and grant SMS read/write permissions to organize texts locally on-device."
                        else -> "Incoming texts in ${tab.label} will be sorted automatically right here."
                    }
                    val emptyActionLabel = if (!isDefaultSms || !hasSmsPermission) "Enable Permissions & Default SMS" else null
                    val emptyAction = if (!isDefaultSms || !hasSmsPermission) onRequestDefaultSms else null
                    EmptyStatePanel(
                        icon = Icons.Rounded.Inbox,
                        title = if (unreadOnly) "All caught up!" else "No ${tab.label} messages yet",
                        subtitle = emptySubtitle,
                        actionLabel = emptyActionLabel,
                        onAction = emptyAction
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(pageThreads, key = { it.threadKey }) { thread ->
                            val rowContent: @Composable () -> Unit = {
                                ThreadCardRow(
                                    thread = thread,
                                    isSelected = selectedThreadForMenu?.threadKey == thread.threadKey,
                                    onClick = {
                                        if (selectedThreadForMenu != null) {
                                            selectedThreadForMenu = null
                                        } else {
                                            viewModel.markThreadRead(thread.threadKey, true)
                                            onOpenThread(thread.threadKey)
                                        }
                                    },
                                    onLongClick = { selectedThreadForMenu = thread },
                                    onCopyOtp = { otp ->
                                        val cm = context.getSystemService(ClipboardManager::class.java)
                                        cm?.setPrimaryClip(ClipData.newPlainText("OTP", otp))
                                        viewModel.emitSnackbar("OTP $otp copied to clipboard")
                                    }
                                )
                            }

                            if (!prefs.tabSwipeEnabled &&
                                (prefs.leftSwipeAction != SwipeAction.NONE || prefs.rightSwipeAction != SwipeAction.NONE)
                            ) {
                                SwipeableThreadContainer(
                                    thread = thread,
                                    leftAction = prefs.leftSwipeAction,
                                    rightAction = prefs.rightSwipeAction,
                                    onActionTriggered = { action ->
                                        when (action) {
                                            SwipeAction.MARK_READ -> viewModel.markThreadRead(
                                                threadKey = thread.threadKey,
                                                isRead = thread.unreadCount > 0,
                                                notifyUser = true
                                            )
                                            SwipeAction.STAR -> viewModel.toggleThreadStar(thread)
                                            SwipeAction.MOVE_TO -> moveTargetThread = thread
                                            SwipeAction.DELETE -> viewModel.deleteThread(thread)
                                            SwipeAction.NONE -> Unit
                                        }
                                    },
                                    content = rowContent
                                )
                            } else {
                                rowContent()
                            }
                        }
                    }
                }
            }
        }
    }

    moveTargetThread?.let { target ->
        MoveToFolderDialog(
            senderTitle = target.displayTitle,
            currentCategory = target.category,
            onSelectCategory = { newCat ->
                viewModel.moveThreadToCategory(target, newCat)
                moveTargetThread = null
            },
            onDismiss = { moveTargetThread = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableThreadContainer(
    thread: ThreadSummary,
    leftAction: SwipeAction,
    rightAction: SwipeAction,
    onActionTriggered: (SwipeAction) -> Unit,
    content: @Composable () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> {
                    if (leftAction != SwipeAction.NONE) onActionTriggered(leftAction)
                    false
                }
                SwipeToDismissBoxValue.StartToEnd -> {
                    if (rightAction != SwipeAction.NONE) onActionTriggered(rightAction)
                    false
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = rightAction != SwipeAction.NONE,
        enableDismissFromEndToStart = leftAction != SwipeAction.NONE,
        backgroundContent = {
            val dir = if (dismissState.dismissDirection != SwipeToDismissBoxValue.Settled) {
                dismissState.dismissDirection
            } else {
                dismissState.targetValue
            }
            val isStartToEnd = dir == SwipeToDismissBoxValue.StartToEnd
            val action = if (isStartToEnd) rightAction else leftAction
            val semantic = LocalKmsSemanticColors.current
            val isUnread = thread.unreadCount > 0

            val (bgColor, contentColor) = when (action) {
                SwipeAction.DELETE -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.error
                SwipeAction.STAR -> semantic.offerContainer to semantic.starGold
                SwipeAction.MARK_READ -> semantic.creditGreenContainer to semantic.creditGreen
                SwipeAction.MOVE_TO -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
                SwipeAction.NONE -> MaterialTheme.colorScheme.surfaceContainer to MaterialTheme.colorScheme.onSurfaceVariant
            }

            val icon = when (action) {
                SwipeAction.DELETE -> Icons.Rounded.Delete
                SwipeAction.STAR -> if (thread.isStarred) Icons.Rounded.StarOutline else Icons.Rounded.Star
                SwipeAction.MOVE_TO -> Icons.AutoMirrored.Rounded.DriveFileMove
                SwipeAction.MARK_READ -> if (isUnread) Icons.Rounded.MarkEmailRead else Icons.Rounded.MarkEmailUnread
                SwipeAction.NONE -> Icons.Rounded.Close
            }

            val actionLabel = when (action) {
                SwipeAction.DELETE -> "Delete"
                SwipeAction.STAR -> if (thread.isStarred) "Unstar" else "Star"
                SwipeAction.MOVE_TO -> "Move To"
                SwipeAction.MARK_READ -> if (isUnread) "Mark Read" else "Mark Unread"
                SwipeAction.NONE -> ""
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(bgColor)
                    .padding(horizontal = 20.dp),
                contentAlignment = if (isStartToEnd) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!isStartToEnd) {
                        Text(
                            text = actionLabel,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = contentColor
                        )
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = actionLabel,
                        tint = contentColor
                    )
                    if (isStartToEnd) {
                        Text(
                            text = actionLabel,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = contentColor
                        )
                    }
                }
            }
        },
        content = { content() }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ThreadCardRow(
    thread: ThreadSummary,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onCopyOtp: (String) -> Unit
) {
    val semantic = LocalKmsSemanticColors.current
    val hasUnread = thread.unreadCount > 0

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else semantic.cardBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .testTag("thread_card_${thread.threadKey}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top
        ) {
            SenderMonogram(title = thread.displayTitle, category = thread.category)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = thread.displayTitle,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = if (hasUnread) FontWeight.Bold else FontWeight.SemiBold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (thread.isStarred) {
                        Icon(
                            Icons.Rounded.Star,
                            contentDescription = "Starred",
                            tint = semantic.starGold,
                            modifier = Modifier.size(16.dp).padding(end = 2.dp)
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = formatShortTime(thread.lastTimestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (hasUnread) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = thread.lastSnippet,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (hasUnread) FontWeight.Medium else FontWeight.Normal
                        ),
                        color = if (hasUnread) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (hasUnread) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = thread.unreadCount.coerceAtMost(99).toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }

                // Inline OTP Copy Pill for instant utility
                if (!thread.latestOtp.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        onClick = { onCopyOtp(thread.latestOtp) },
                        shape = RoundedCornerShape(10.dp),
                        color = semantic.otpContainer,
                        modifier = Modifier.testTag("inline_copy_otp_${thread.latestOtp}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Rounded.ContentCopy,
                                contentDescription = "Copy OTP",
                                tint = semantic.otpAccent,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Copy OTP ${thread.latestOtp}",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = MonoFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = semantic.otpAccent
                            )
                        }
                    }
                }
            }
        }
    }
}

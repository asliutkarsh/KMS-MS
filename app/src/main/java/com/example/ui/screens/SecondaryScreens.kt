package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.ScheduleSend
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ScheduledStatus
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.formatFullDateTime
import com.example.ui.theme.LocalKmsSemanticColors
import com.example.viewmodel.KmsMainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedSendersScreen(
    viewModel: KmsMainViewModel,
    onBack: () -> Unit
) {
    val blocked by viewModel.blockedThreads.collectAsStateWithLifecycle()
    val semantic = LocalKmsSemanticColors.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Blocked Senders") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (blocked.isEmpty()) {
            EmptyStatePanel(
                icon = Icons.Rounded.Block,
                title = "No blocked senders",
                subtitle = "Long-press any thread in your Inbox and tap Block to silently route spam here.",
                modifier = Modifier.padding(innerPadding)
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize().padding(innerPadding)
            ) {
                items(blocked, key = { it.threadKey }) { item ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, semantic.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.displayTitle, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    item.lastSnippet,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            OutlinedButton(
                                onClick = { viewModel.unblockSender(item) },
                                modifier = Modifier.testTag("unblock_${item.threadKey}")
                            ) {
                                Text("Unblock")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduledMessagesScreen(
    viewModel: KmsMainViewModel,
    onBack: () -> Unit
) {
    val scheduled by viewModel.scheduledMessages.collectAsStateWithLifecycle()
    val semantic = LocalKmsSemanticColors.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scheduled Messages") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (scheduled.isEmpty()) {
            EmptyStatePanel(
                icon = Icons.Rounded.ScheduleSend,
                title = "No scheduled messages",
                subtitle = "Use the Schedule icon inside any conversation or New Message screen to send texts automatically later.",
                modifier = Modifier.padding(innerPadding)
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize().padding(innerPadding)
            ) {
                items(scheduled, key = { it.id }) { item ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, semantic.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = item.recipientName ?: item.recipientAddress,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = item.status.name,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = when (item.status) {
                                        ScheduledStatus.PENDING -> MaterialTheme.colorScheme.primary
                                        ScheduledStatus.SENT -> semantic.creditGreen
                                        else -> MaterialTheme.colorScheme.error
                                    }
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(item.body, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    formatFullDateTime(item.scheduledTimestamp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                if (item.status == ScheduledStatus.PENDING) {
                                    IconButton(onClick = { viewModel.cancelScheduledSms(item) }) {
                                        Icon(Icons.Rounded.Cancel, contentDescription = "Cancel scheduled message")
                                    }
                                }
                                IconButton(onClick = { viewModel.deleteScheduledSms(item) }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Delete record")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PastArchiveScreen(
    initialTab: Int,
    viewModel: KmsMainViewModel,
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(initialTab.coerceIn(0, 1)) }
    val allReminders by viewModel.reminders.collectAsStateWithLifecycle()
    val orders by viewModel.orders.collectAsStateWithLifecycle()
    val semantic = LocalKmsSemanticColors.current

    val pastReminders = remember(allReminders) {
        val now = System.currentTimeMillis() - 12L * 60L * 60L * 1000L
        allReminders.filter { it.isCompleted || it.dueTimestamp < now }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History & Archives") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Past Reminders (${pastReminders.size})") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Past Orders (${orders.size})") }
                )
            }

            if (selectedTab == 0) {
                if (pastReminders.isEmpty()) {
                    EmptyStatePanel(
                        icon = Icons.Rounded.History,
                        title = "No past reminders",
                        subtitle = "Completed and expired bills, flights, trains, and appointments are archived here."
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(pastReminders, key = { it.id }) { rem ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, semantic.cardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(rem.title, style = MaterialTheme.typography.titleMedium)
                                    Text(rem.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(4.dp))
                                    Text(formatFullDateTime(rem.dueTimestamp), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            } else {
                if (orders.isEmpty()) {
                    EmptyStatePanel(
                        icon = Icons.Rounded.LocalShipping,
                        title = "No tracked orders yet",
                        subtitle = "E-commerce shipping and delivery updates from couriers are automatically indexed here."
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(orders, key = { it.id }) { order ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, semantic.cardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(order.merchant, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                        Text(
                                            order.status,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Text("Order: ${order.orderId}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(4.dp))
                                    Text(order.trackingSummary, style = MaterialTheme.typography.bodySmall)
                                    Spacer(Modifier.height(4.dp))
                                    Text(formatFullDateTime(order.timestamp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

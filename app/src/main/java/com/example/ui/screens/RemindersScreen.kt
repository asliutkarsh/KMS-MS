package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ForwardToInbox
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Hotel
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Payment
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.ReminderEntity
import com.example.data.model.ReminderType
import com.example.intelligence.OfflineRailwayIndex
import com.example.intelligence.OfflineTrainPosition
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.formatCurrency
import com.example.ui.components.formatFullDateTime
import com.example.ui.theme.LocalKmsSemanticColors
import com.example.ui.theme.MonoFontFamily
import com.example.viewmodel.KmsMainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(
    viewModel: KmsMainViewModel,
    onOpenPastReminders: () -> Unit,
    onOpenPastOrders: () -> Unit
) {
    val allReminders by viewModel.reminders.collectAsStateWithLifecycle()
    val upcoming = remember(allReminders) {
        val now = System.currentTimeMillis() - 12L * 60L * 60L * 1000L
        allReminders.filter { !it.isCompleted && it.dueTimestamp >= now }
    }

    var showCreateDialog by remember { mutableStateOf(false) }
    var activeTrainReminder by remember { mutableStateOf<ReminderEntity?>(null) }
    var forwardBillReminder by remember { mutableStateOf<ReminderEntity?>(null) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Smart Reminders", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "Auto-extracted travel, bills & appointments",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_reminder_fab")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add custom reminder")
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Dedicated Archive Shortcuts (Past Reminders & Past Orders + Offline Train Tracker)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenPastReminders,
                        modifier = Modifier.weight(1f).testTag("open_past_reminders_button")
                    ) {
                        Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Past Reminders")
                    }
                    OutlinedButton(
                        onClick = onOpenPastOrders,
                        modifier = Modifier.weight(1f).testTag("open_past_orders_button")
                    ) {
                        Icon(Icons.Rounded.LocalShipping, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Past Orders")
                    }
                }
            }

            // Quick Offline GPS Train Tracker Banner
            item {
                Surface(
                    onClick = {
                        activeTrainReminder = ReminderEntity(
                            type = ReminderType.TRAIN,
                            title = "Indian Railways Offline Tracker",
                            subtitle = "Uses phone GPS only — no internet needed",
                            dueTimestamp = System.currentTimeMillis()
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth().testTag("open_offline_train_tracker_card")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Train, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Offline GPS Live Train Tracker", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Locate nearest Indian Railways junction & share live train progress without data.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.Rounded.GpsFixed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            if (upcoming.isEmpty()) {
                item {
                    EmptyStatePanel(
                        icon = Icons.Rounded.NotificationsActive,
                        title = "No upcoming reminders",
                        subtitle = "Train PNRs, flights, credit card & utility bills, recharges, and appointments are automatically extracted from SMS. Tap + to create your own."
                    )
                }
            } else {
                items(upcoming, key = { it.id }) { rem ->
                    ReminderSmartCard(
                        reminder = rem,
                        onMarkDone = { viewModel.setReminderCompleted(rem.id, true) },
                        onDelete = { viewModel.deleteReminder(rem.id) },
                        onTrackTrain = { activeTrainReminder = rem },
                        onForwardBill = { forwardBillReminder = rem },
                        onOpenWebAction = { url ->
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }.onFailure {
                                viewModel.emitSnackbar("No compatible app found for this shortcut")
                            }
                        }
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateReminderDialog(
            onConfirm = { title, subtitle, type, dueTs, amount ->
                viewModel.addCustomReminder(title, subtitle, type, dueTs, amount)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false }
        )
    }

    activeTrainReminder?.let { trainRem ->
        OfflineTrainTrackerSheet(
            reminder = trainRem,
            onShareText = { text ->
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share Live Train Status"))
            },
            onNotifyStatus = { title, body -> viewModel.postTrainStatusNotification(title, body) },
            onDismiss = { activeTrainReminder = null }
        )
    }

    forwardBillReminder?.let { bill ->
        var phone by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { forwardBillReminder = null },
            title = { Text("Forward Bill Reminder") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Send \"${bill.title}\" to another contact so KMS-MS automatically triggers a bill reminder on their phone.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Recipient phone number") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = phone.isNotBlank(),
                    onClick = {
                        viewModel.forwardBill(
                            recipientAddress = phone.trim(),
                            billTitle = bill.title,
                            amount = bill.amount,
                            dueTimestamp = bill.dueTimestamp,
                            originalSnippet = bill.subtitle
                        )
                        forwardBillReminder = null
                    }
                ) { Text("Forward") }
            },
            dismissButton = {
                TextButton(onClick = { forwardBillReminder = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ReminderSmartCard(
    reminder: ReminderEntity,
    onMarkDone: () -> Unit,
    onDelete: () -> Unit,
    onTrackTrain: () -> Unit,
    onForwardBill: () -> Unit,
    onOpenWebAction: (String) -> Unit
) {
    val semantic = LocalKmsSemanticColors.current
    val icon = when (reminder.type) {
        ReminderType.TRAIN -> Icons.Rounded.Train
        ReminderType.FLIGHT -> Icons.Rounded.Flight
        ReminderType.BUS -> Icons.Rounded.DirectionsBus
        ReminderType.HOTEL -> Icons.Rounded.Hotel
        ReminderType.BILL -> Icons.Rounded.ReceiptLong
        ReminderType.RECHARGE -> Icons.Rounded.PhoneAndroid
        ReminderType.MOVIE -> Icons.Rounded.Movie
        ReminderType.APPOINTMENT, ReminderType.CUSTOM -> Icons.Rounded.Event
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, semantic.cardBorder),
        modifier = Modifier.fillMaxWidth().testTag("reminder_card_${reminder.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = reminder.type.label.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (reminder.isForwardedBill) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "• FORWARDED BILL",
                                style = MaterialTheme.typography.labelSmall,
                                color = semantic.offerAmber
                            )
                        }
                    }
                    Text(reminder.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        reminder.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = formatFullDateTime(reminder.dueTimestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                reminder.amount?.let { amt ->
                    Text(
                        text = formatCurrency(amt, reminder.currencySymbol),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = MonoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = semantic.debitCoral
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Smart Assist Contextual Action Shortcuts
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (reminder.type == ReminderType.TRAIN) {
                    item {
                        AssistChip(
                            onClick = onTrackTrain,
                            label = { Text("Track Offline GPS") },
                            leadingIcon = { Icon(Icons.Rounded.GpsFixed, null, modifier = Modifier.size(16.dp)) }
                        )
                    }
                }
                if (reminder.type == ReminderType.FLIGHT) {
                    item {
                        AssistChip(
                            onClick = { onOpenWebAction("https://www.google.com/search?q=flight+web+check+in+${reminder.pnrNumber ?: ""}") },
                            label = { Text("Web Check-in") },
                            leadingIcon = { Icon(Icons.Rounded.Flight, null, modifier = Modifier.size(16.dp)) }
                        )
                    }
                }
                if (reminder.type == ReminderType.BILL || reminder.type == ReminderType.RECHARGE) {
                    item {
                        AssistChip(
                            onClick = { onOpenWebAction("upi://pay?tn=${Uri.encode(reminder.title)}") },
                            label = { Text("Pay Bill") },
                            leadingIcon = { Icon(Icons.Rounded.Payment, null, modifier = Modifier.size(16.dp)) }
                        )
                    }
                    item {
                        AssistChip(
                            onClick = onForwardBill,
                            label = { Text("Forward Bill") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.ForwardToInbox, null, modifier = Modifier.size(16.dp)) }
                        )
                    }
                }
                item {
                    AssistChip(
                        onClick = onMarkDone,
                        label = { Text("Done") },
                        leadingIcon = { Icon(Icons.Rounded.CheckCircle, null, modifier = Modifier.size(16.dp)) }
                    )
                }
                item {
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Delete reminder", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateReminderDialog(
    onConfirm: (String, String, ReminderType, Long, Double?) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var subtitle by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(ReminderType.BILL) }
    var daysAhead by remember { mutableStateOf("1") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Custom Reminder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title (e.g., Electricity Bill)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("create_reminder_title")
                )
                OutlinedTextField(
                    value = subtitle,
                    onValueChange = { subtitle = it },
                    label = { Text("Notes / PNR / Details") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = daysAhead,
                        onValueChange = { daysAhead = it.filter { ch -> ch.isDigit() } },
                        label = { Text("In days") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("Amount (opt)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf(ReminderType.BILL, ReminderType.TRAIN, ReminderType.FLIGHT, ReminderType.APPOINTMENT, ReminderType.CUSTOM)) { t ->
                        FilterChip(
                            selected = selectedType == t,
                            onClick = { selectedType = t },
                            label = { Text(t.label) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = title.isNotBlank(),
                onClick = {
                    val days = daysAhead.toLongOrNull()?.coerceAtLeast(0L) ?: 1L
                    val dueTs = System.currentTimeMillis() + days * 24L * 60L * 60L * 1000L
                    onConfirm(title, subtitle, selectedType, dueTs, amountText.toDoubleOrNull())
                },
                modifier = Modifier.testTag("confirm_create_reminder")
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
private fun OfflineTrainTrackerSheet(
    reminder: ReminderEntity,
    onShareText: (String) -> Unit,
    onNotifyStatus: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    var position by remember { mutableStateOf<OfflineTrainPosition?>(null) }
    var statusNote by remember { mutableStateOf("Waiting for on-device GPS fix…") }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasLocationPermission = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    DisposableEffect(hasLocationPermission) {
        if (!hasLocationPermission) return@DisposableEffect onDispose {}
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (lm == null) {
            statusNote = "GPS hardware unavailable on this device."
            return@DisposableEffect onDispose {}
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val resolved = OfflineRailwayIndex.resolveOfflinePosition(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    speedMetersPerSecond = location.speed
                )
                position = resolved
                statusNote = "GPS Locked Offline (Zero Internet Used)"
                onNotifyStatus(
                    reminder.title,
                    "Near ${resolved.nearestStation.name} (${resolved.nearestStation.code}) • ${String.format("%.1f", resolved.distanceKm)} km"
                )
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }

        try {
            val lastGps = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (lastGps != null) {
                position = OfflineRailwayIndex.resolveOfflinePosition(
                    lastGps.latitude,
                    lastGps.longitude,
                    lastGps.speed
                )
                statusNote = "Showing last GPS fix — listening for live satellite updates…"
            }
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 4_000L, 50f, listener)
            } else {
                statusNote = "GPS is disabled. Enable device Location for offline train tracking."
            }
        } catch (_: SecurityException) {
            statusNote = "Location permission required for offline GPS tracking."
        }

        onDispose {
            runCatching { lm.removeUpdates(listener) }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Train, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(reminder.title, style = MaterialTheme.typography.titleLarge)
                    Text(
                        reminder.pnrNumber?.let { "PNR: $it • Offline GPS Mode" } ?: "Indian Railways Offline GPS Mode",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (!hasLocationPermission) {
                Text(
                    "Grant foreground GPS location access to determine your nearest Indian Railways junction completely offline without cellular data.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = {
                        permLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Enable Offline GPS Tracking")
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(statusNote, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        position?.let { pos ->
                            Text(
                                text = "${pos.nearestStation.name} (${pos.nearestStation.code})",
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Text(
                                text = "${String.format("%.1f", pos.distanceKm)} km away • Zone ${pos.nearestStation.zone}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Next Major Junction: ${pos.nextNearestStation.name} (${pos.nextNearestStation.code}) — ${String.format("%.1f", pos.distanceToNextKm)} km",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Speed: ${String.format("%.0f", pos.speedKmh)} km/h • GPS: ${String.format("%.4f", pos.latitude)}, ${String.format("%.4f", pos.longitude)}",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily)
                            )
                        }
                    }
                }

                Button(
                    enabled = position != null,
                    onClick = {
                        position?.let { pos ->
                            val text = OfflineRailwayIndex.formatShareText(reminder.title, reminder.pnrNumber, pos)
                            onShareText(text)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("share_train_status_button")
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Share Live Location with Family")
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

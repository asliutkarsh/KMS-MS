package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.BackupReminderCadence
import com.example.data.model.RetentionPeriod
import com.example.data.model.SwipeAction
import com.example.data.model.ThemeMode
import com.example.ui.components.formatFullDateTime
import com.example.ui.theme.LocalKmsSemanticColors
import com.example.viewmodel.KmsMainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: KmsMainViewModel,
    onBack: () -> Unit
) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()

    var showPinDialog by remember { mutableStateOf(false) }
    var showQuickRepliesDialog by remember { mutableStateOf(false) }
    var signatureDraft by remember(prefs.signatureText) { mutableStateOf(prefs.signatureText) }

    // Encrypted Backup Passphrase State
    var pendingExportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) pendingExportUri = uri
    }

    val openBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) pendingImportUri = uri
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back_button")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 1. Appearance & Font Scaling
            item {
                SettingsSectionCard(title = "APPEARANCE & TEXT SCALE") {
                    Text("Theme Mode", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ThemeMode.entries) { mode ->
                            FilterChip(
                                selected = prefs.themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                label = { Text(mode.label) },
                                modifier = Modifier.testTag("theme_mode_${mode.name.lowercase()}")
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Message Font Scaling", style = MaterialTheme.typography.titleSmall)
                        Text("${(prefs.fontScale * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
                    }
                    Slider(
                        value = prefs.fontScale,
                        onValueChange = { viewModel.setFontScale(it) },
                        valueRange = 0.85f..1.4f,
                        steps = 10,
                        modifier = Modifier.testTag("font_scale_slider")
                    )
                }
            }

            // 2. Granular Notification Controls (6 independent toggles)
            item {
                SettingsSectionCard(title = "NOTIFICATIONS") {
                    SettingsSwitchRow(
                        title = "Default & Personal messages",
                        subtitle = "Alerts with Direct Reply from notification shade",
                        checked = prefs.notifyDefault,
                        onCheckedChange = { viewModel.updateNotificationToggle(defaultMsg = it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    SettingsSwitchRow(
                        title = "Promotional SMS",
                        subtitle = "Off by default to keep marketing texts silent",
                        checked = prefs.notifyPromotions,
                        onCheckedChange = { viewModel.updateNotificationToggle(promotions = it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    SettingsSwitchRow(
                        title = "OTP SMS",
                        subtitle = "Shows one-tap Copy OTP & instant Delete buttons",
                        checked = prefs.notifyOtp,
                        onCheckedChange = { viewModel.updateNotificationToggle(otp = it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    SettingsSwitchRow(
                        title = "Smart Reminders",
                        subtitle = "Upcoming bills, flights, trains & appointments",
                        checked = prefs.notifyReminders,
                        onCheckedChange = { viewModel.updateNotificationToggle(reminders = it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    SettingsSwitchRow(
                        title = "Live train status notifications",
                        subtitle = "Offline GPS railway progress updates",
                        checked = prefs.notifyTrainStatus,
                        onCheckedChange = { viewModel.updateNotificationToggle(trainStatus = it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    SettingsSwitchRow(
                        title = "Lock-screen wake-up",
                        subtitle = "Briefly wake screen for high-priority texts & OTPs",
                        checked = prefs.notifyLockScreenWake,
                        onCheckedChange = { viewModel.updateNotificationToggle(lockScreenWake = it) }
                    )
                }
            }

            // 3. Auto-Cleanup & Hygiene Rules
            item {
                SettingsSectionCard(title = "AUTO-CLEANUP (MESSAGE RULES)") {
                    RetentionPickerRow(
                        label = "Delete older OTP messages",
                        selected = prefs.cleanupOtp,
                        onSelect = { viewModel.updateCleanupRule(otp = it) }
                    )
                    Spacer(Modifier.height(10.dp))
                    RetentionPickerRow(
                        label = "Delete older promotional messages",
                        selected = prefs.cleanupPromotions,
                        onSelect = { viewModel.updateCleanupRule(promotions = it) }
                    )
                    Spacer(Modifier.height(10.dp))
                    RetentionPickerRow(
                        label = "Delete older blocked messages",
                        selected = prefs.cleanupBlocked,
                        onSelect = { viewModel.updateCleanupRule(blocked = it) }
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { viewModel.runCleanupNow() },
                        modifier = Modifier.fillMaxWidth().testTag("run_cleanup_now_button")
                    ) {
                        Icon(Icons.Rounded.CleaningServices, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Run Auto-Cleanup Now")
                    }
                }
            }

            // 4. Swipe Actions
            item {
                SettingsSectionCard(title = "SWIPE ACTIONS") {
                    SettingsSwitchRow(
                        title = "Swipe horizontally between Inbox tabs",
                        subtitle = "Turn off to enable custom Left/Right thread swipe actions",
                        checked = prefs.tabSwipeEnabled,
                        onCheckedChange = { viewModel.updateSwipeSettings(tabSwipeEnabled = it) }
                    )
                    if (!prefs.tabSwipeEnabled) {
                        Spacer(Modifier.height(10.dp))
                        SwipeActionPickerRow(
                            label = "Left Swipe Action",
                            selected = prefs.leftSwipeAction,
                            onSelect = { viewModel.updateSwipeSettings(leftAction = it) }
                        )
                        Spacer(Modifier.height(10.dp))
                        SwipeActionPickerRow(
                            label = "Right Swipe Action",
                            selected = prefs.rightSwipeAction,
                            onSelect = { viewModel.updateSwipeSettings(rightAction = it) }
                        )
                    }
                }
            }

            // 5. Finance Vault Security
            item {
                SettingsSectionCard(title = "FINANCE VAULT SECURITY") {
                    SettingsSwitchRow(
                        title = "Lock Finance tab",
                        subtitle = "Require Biometrics or PIN before viewing balances",
                        checked = prefs.financeLockEnabled,
                        onCheckedChange = { viewModel.setFinanceLockEnabled(it) }
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showPinDialog = true },
                        modifier = Modifier.fillMaxWidth().testTag("set_finance_pin_button")
                    ) {
                        Icon(Icons.Rounded.Lock, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (prefs.financePinHash.isBlank()) "Set Fallback Finance PIN" else "Change Finance PIN")
                    }
                }
            }

            // 6. Quick Replies & Signature
            item {
                SettingsSectionCard(title = "MESSAGING UTILITIES") {
                    SettingsSwitchRow(
                        title = "Automatic SMS signature",
                        subtitle = "Appends your custom signature to outgoing texts",
                        checked = prefs.signatureEnabled,
                        onCheckedChange = { viewModel.updateSignature(it, signatureDraft) }
                    )
                    if (prefs.signatureEnabled) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = signatureDraft,
                            onValueChange = {
                                signatureDraft = it
                                viewModel.updateSignature(true, it)
                            },
                            label = { Text("Signature text") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { showQuickRepliesDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Customize Quick Replies (${prefs.quickReplies.size})")
                    }
                }
            }

            // 7. Encrypted Manual File Backup & Restore (SAF / Local / Drive Picker)
            item {
                SettingsSectionCard(title = "ENCRYPTED BACKUP & RESTORE") {
                    Text(
                        text = "Export an AES-256-GCM passphrase-encrypted .kmsbackup file to your device storage or Google Drive using Android's file picker—zero background cloud uploads.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (prefs.lastBackupTimestamp > 0L) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Last backup: ${formatFullDateTime(prefs.lastBackupTimestamp)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(BackupReminderCadence.entries) { cadence ->
                            FilterChip(
                                selected = prefs.backupCadence == cadence,
                                onClick = { viewModel.setBackupCadence(cadence) },
                                label = { Text(cadence.label) }
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                                createBackupLauncher.launch("kms_ms_backup_$stamp.kmsbackup")
                            },
                            modifier = Modifier.weight(1f).testTag("export_backup_button")
                        ) {
                            Icon(Icons.Rounded.Upload, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Export")
                        }
                        OutlinedButton(
                            onClick = { openBackupLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier.weight(1f).testTag("restore_backup_button")
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Restore")
                        }
                    }
                }
            }
        }
    }

    if (showPinDialog) {
        var pin by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text("Set Finance PIN") },
            text = {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter { ch -> ch.isDigit() }.take(6) },
                    label = { Text("Enter 4–6 digit PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    enabled = pin.length >= 4,
                    onClick = {
                        viewModel.setFinancePin(pin)
                        showPinDialog = false
                    }
                ) { Text("Save PIN") }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showQuickRepliesDialog) {
        var textBlock by remember { mutableStateOf(prefs.quickReplies.joinToString("\n")) }
        AlertDialog(
            onDismissRequest = { showQuickRepliesDialog = false },
            title = { Text("Edit Quick Replies") },
            text = {
                OutlinedTextField(
                    value = textBlock,
                    onValueChange = { textBlock = it },
                    label = { Text("One quick reply per line") },
                    minLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    val list = textBlock.lines().map { it.trim() }.filter { it.isNotBlank() }
                    viewModel.updateQuickReplies(list)
                    showQuickRepliesDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showQuickRepliesDialog = false }) { Text("Cancel") }
            }
        )
    }

    pendingExportUri?.let { uri ->
        BackupPassphraseDialog(
            title = "Encrypt Backup File",
            subtitle = "Create a passphrase (min 4 chars) to lock your .kmsbackup archive with AES-256-GCM.",
            confirmLabel = "Encrypt & Save",
            onConfirm = { pass ->
                viewModel.exportEncryptedBackup(uri, pass)
                pendingExportUri = null
            },
            onDismiss = { pendingExportUri = null }
        )
    }

    pendingImportUri?.let { uri ->
        BackupPassphraseDialog(
            title = "Decrypt & Restore Backup",
            subtitle = "Enter the passphrase used when exporting this .kmsbackup archive.",
            confirmLabel = "Decrypt & Restore",
            onConfirm = { pass ->
                viewModel.importEncryptedBackup(uri, pass)
                pendingImportUri = null
            },
            onDismiss = { pendingImportUri = null }
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, LocalKmsSemanticColors.current.cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun RetentionPickerRow(
    label: String,
    selected: RetentionPeriod,
    onSelect: (RetentionPeriod) -> Unit
) {
    Column {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(RetentionPeriod.entries) { period ->
                FilterChip(
                    selected = selected == period,
                    onClick = { onSelect(period) },
                    label = { Text(period.label) }
                )
            }
        }
    }
}

@Composable
private fun SwipeActionPickerRow(
    label: String,
    selected: SwipeAction,
    onSelect: (SwipeAction) -> Unit
) {
    Column {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(SwipeAction.entries) { action ->
                FilterChip(
                    selected = selected == action,
                    onClick = { onSelect(action) },
                    label = { Text(action.label) }
                )
            }
        }
    }
}

@Composable
private fun BackupPassphraseDialog(
    title: String,
    subtitle: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pass by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it },
                    label = { Text("Backup passphrase") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("backup_passphrase_input")
                )
            }
        },
        confirmButton = {
            Button(
                enabled = pass.length >= 4,
                onClick = { onConfirm(pass) },
                modifier = Modifier.testTag("backup_passphrase_confirm")
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

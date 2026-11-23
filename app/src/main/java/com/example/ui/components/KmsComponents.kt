package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.SmsCategory
import com.example.ui.theme.LocalKmsSemanticColors
import com.example.ui.theme.MonoFontFamily
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

fun formatShortTime(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply { timeInMillis = timestamp }
    return when {
        now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR) ->
            SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))
        now.get(Calendar.YEAR) == target.get(Calendar.YEAR) ->
            SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
        else ->
            SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date(timestamp))
    }
}

fun formatFullDateTime(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    return SimpleDateFormat("EEE, MMM d • h:mm a", Locale.getDefault()).format(Date(timestamp))
}

fun formatCurrency(amount: Double?, symbol: String = "₹", masked: Boolean = false): String {
    if (masked) return "$symbol •••••"
    if (amount == null) return "—"
    val formatter = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = if (amount % 1.0 == 0.0) 0 else 2
        maximumFractionDigits = 2
    }
    return "$symbol${formatter.format(amount)}"
}

@Composable
fun SenderMonogram(
    title: String,
    category: SmsCategory,
    modifier: Modifier = Modifier
) {
    val semantic = LocalKmsSemanticColors.current
    val initials = remember(title) {
        val parts = title.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        when {
            parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}".uppercase()
            title.isNotBlank() -> title.filter { it.isLetterOrDigit() }.take(2).uppercase().ifBlank { "?" }
            else -> "?"
        }
    }
    val (bg, fg) = when (category) {
        SmsCategory.PERSONAL -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        SmsCategory.TRANSACTION -> semantic.otpContainer to semantic.otpAccent
        SmsCategory.PROMOTION -> semantic.offerContainer to semantic.offerAmber
    }
    val accentBg = if (category == SmsCategory.PERSONAL) {
        val palette = listOf(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.secondaryContainer,
            semantic.creditGreenContainer,
            semantic.otpContainer
        )
        palette[abs(title.hashCode()) % palette.size]
    } else bg

    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(accentBg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.labelLarge.copy(
                fontFamily = MonoFontFamily,
                fontWeight = FontWeight.Bold
            ),
            color = fg
        )
    }
}

@Composable
fun EmptyStatePanel(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(68.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onAction,
                modifier = Modifier.testTag("empty_state_action_button")
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun OnboardingDefaultSmsDialog(
    isDefaultSms: Boolean = false,
    hasSmsPermission: Boolean = false,
    onSetDefaultSmsAndPermissions: () -> Unit,
    onRequestPermissionsOnly: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onSetDefaultSmsAndPermissions,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("onboarding_set_default_sms_button")
                ) {
                    Icon(
                        Icons.Rounded.Shield,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (!isDefaultSms) "Set Default SMS & Permissions"
                        else "Grant SMS Read/Write Permissions"
                    )
                }

                if (!isDefaultSms && !hasSmsPermission) {
                    OutlinedButton(
                        onClick = onRequestPermissionsOnly,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("onboarding_permissions_only_button")
                    ) {
                        Icon(
                            Icons.Rounded.Sms,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Grant SMS Permissions Only")
                    }
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("onboarding_skip_button")
                ) {
                    Text("Skip for now")
                }
            }
        },
        dismissButton = {},
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Image(
                    painter = painterResource(id = R.drawable.img_onboarding_vault_1795442502721),
                    contentDescription = "KMS-MS on-device privacy vault illustration",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(136.dp)
                        .clip(RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Crop
                )
                Text(
                    text = "Welcome to KMS-MS",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = "Prompt to set as Default SMS & grant permissions",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "To organize incoming texts, track expenses, copy OTPs with one tap, and auto-clean clutter, KMS-MS needs to be your default SMS client and access your SMS inbox.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, LocalKmsSemanticColors.current.cardBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PrivacyBulletRow(
                            icon = Icons.Rounded.Shield,
                            title = "Default SMS App (Recommended)",
                            text = "Enables automatic sorting into Personal, Transactions, Promos, Starred, and instant OTP deletion."
                        )
                        PrivacyBulletRow(
                            icon = Icons.Rounded.Sms,
                            title = "SMS Read & Write Access",
                            text = "Reads your device SMS inbox to categorize texts locally and send replies."
                        )
                        PrivacyBulletRow(
                            icon = Icons.Rounded.Contacts,
                            title = "Contact Names & Alerts",
                            text = "Displays contact names in threads and shows high-priority OTP notification banners."
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "100% on-device processing. No cloud uploads.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    )
}

@Composable
fun OnboardingDefaultSmsDialog(
    onSetDefaultSms: () -> Unit,
    onDismiss: () -> Unit
) {
    OnboardingDefaultSmsDialog(
        isDefaultSms = false,
        hasSmsPermission = false,
        onSetDefaultSmsAndPermissions = onSetDefaultSms,
        onRequestPermissionsOnly = onSetDefaultSms,
        onDismiss = onDismiss
    )
}

@Composable
private fun PrivacyBulletRow(icon: ImageVector, title: String, text: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(18.dp)
                .padding(top = 2.dp)
        )
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun MoveToFolderDialog(
    senderTitle: String,
    currentCategory: SmsCategory,
    onSelectCategory: (SmsCategory) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move \"$senderTitle\" to…") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "KMS-MS will remember this rule for all future messages from this sender.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                val items = listOf(
                    Triple(SmsCategory.PERSONAL, "Personal", Icons.Rounded.Person),
                    Triple(SmsCategory.TRANSACTION, "Transactions", Icons.Rounded.AccountBalance),
                    Triple(SmsCategory.PROMOTION, "Promotions", Icons.Rounded.LocalOffer)
                )
                items.forEach { (cat, label, icon) ->
                    val selected = cat == currentCategory
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectCategory(cat) }
                            .testTag("move_to_${cat.name.lowercase()}"),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainer
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary
                            else LocalKmsSemanticColors.current.cardBorder
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text(label, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ScheduleDateTimeDialog(
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedPresetIndex by remember { mutableIntStateOf(0) }
    var customMinutes by remember { mutableStateOf("30") }

    val presets = remember {
        val now = System.currentTimeMillis()
        listOf(
            "In 15 mins" to (now + 15L * 60_000L),
            "In 1 hour" to (now + 60L * 60_000L),
            "Tonight 8 PM" to Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 20); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
                if (timeInMillis <= now) add(Calendar.DAY_OF_YEAR, 1)
            }.timeInMillis,
            "Tomorrow 9 AM" to Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            }.timeInMillis,
            "Custom mins" to -1L
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Schedule, contentDescription = null) },
        title = { Text("Schedule Message") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Choose when KMS-MS should automatically send this SMS:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                presets.forEachIndexed { idx, (label, ts) ->
                    FilterChip(
                        selected = selectedPresetIndex == idx,
                        onClick = { selectedPresetIndex = idx },
                        label = {
                            Text(
                                if (ts > 0) "$label (${formatShortTime(ts)})" else label
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (selectedPresetIndex == presets.lastIndex) {
                    OutlinedTextField(
                        value = customMinutes,
                        onValueChange = { input -> customMinutes = input.filter { it.isDigit() } },
                        label = { Text("Delay in minutes") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val chosen = presets[selectedPresetIndex].second
                    val targetTs = if (chosen > 0) {
                        chosen
                    } else {
                        val mins = customMinutes.toLongOrNull()?.coerceAtLeast(1L) ?: 15L
                        System.currentTimeMillis() + mins * 60_000L
                    }
                    onConfirm(targetTs)
                },
                modifier = Modifier.testTag("confirm_schedule_button")
            ) {
                Text("Schedule")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

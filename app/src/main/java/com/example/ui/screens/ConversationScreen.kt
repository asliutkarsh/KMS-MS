package com.example.ui.screens

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.automirrored.rounded.ForwardToInbox
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.ScheduleSend
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.local.SmsMessageEntity
import com.example.data.model.SmsCategory
import com.example.ui.components.MoveToFolderDialog
import com.example.ui.components.ScheduleDateTimeDialog
import com.example.ui.components.formatFullDateTime
import com.example.ui.components.formatShortTime
import com.example.ui.theme.LocalKmsSemanticColors
import com.example.ui.theme.MonoFontFamily
import com.example.viewmodel.KmsMainViewModel
import com.example.viewmodel.ThreadSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    threadKey: String,
    viewModel: KmsMainViewModel,
    onBack: () -> Unit
) {
    val messages by remember(threadKey) { viewModel.observeThread(threadKey) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val simSlots by viewModel.simSlots.collectAsStateWithLifecycle()

    val latest = messages.lastOrNull()
    val address = latest?.address ?: threadKey
    val title = latest?.groupName?.takeIf { it.isNotBlank() }
        ?: latest?.contactName?.takeIf { it.isNotBlank() }
        ?: address
    val isGroup = address.contains(",")
    val currentCategory = latest?.category ?: SmsCategory.PERSONAL

    var draft by remember { mutableStateOf("") }
    var selectedSimIndex by remember { mutableIntStateOf(0) }
    var attachedImageUri by remember { mutableStateOf<Uri?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showScheduleDialog by remember { mutableStateOf(false) }
    var forwardBillTarget by remember { mutableStateOf<SmsMessageEntity?>(null) }

    val context = LocalContext.current
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
            viewModel.markThreadRead(threadKey, true)
        }
    }

    // Zero-permission Android Photo Picker for MMS images
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            attachedImageUri = uri
            viewModel.emitSnackbar("Image attached for MMS")
        }
    }

    // Contact vCard attachment picker
    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { contactUri ->
        if (contactUri != null) {
            val contactText = readContactCardSummary(context, contactUri)
            if (contactText != null) {
                draft = if (draft.isBlank()) contactText else "$draft\n$contactText"
            }
        }
    }

    // Voice typing speech-to-text launcher
    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                draft = if (draft.isBlank()) spoken else "$draft $spoken"
            }
        }
    }

    val activeSim = simSlots.getOrNull(selectedSimIndex) ?: simSlots.firstOrNull()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${currentCategory.label} • $address",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("conversation_back_button")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "Thread options")
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("Move sender to folder") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.DriveFileMove, null) },
                                onClick = { menuExpanded = false; showMoveDialog = true }
                            )
                            if (isGroup || messages.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("Rename conversation") },
                                    leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                                    onClick = { menuExpanded = false; showRenameDialog = true }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Block sender") },
                                leadingIcon = { Icon(Icons.Rounded.Block, null) },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.blockSender(
                                        ThreadSummary(
                                            threadKey = threadKey,
                                            displayTitle = title,
                                            address = address,
                                            lastSnippet = latest?.body.orEmpty(),
                                            lastTimestamp = latest?.timestamp ?: 0L,
                                            unreadCount = 0,
                                            isStarred = false,
                                            category = currentCategory,
                                            isBlocked = false,
                                            latestOtp = null,
                                            isGroup = isGroup,
                                            messageCount = messages.size
                                        )
                                    )
                                    onBack()
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    // Quick Replies Strip
                    if (prefs.quickReplies.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            contentPadding = PaddingValues(bottom = 6.dp)
                        ) {
                            items(prefs.quickReplies) { reply ->
                                SuggestionChip(
                                    onClick = { draft = if (draft.isBlank()) reply else "$draft $reply" },
                                    label = { Text(reply, style = MaterialTheme.typography.labelMedium) }
                                )
                            }
                        }
                    }

                    // Attached Image Preview + Active SIM / Signature Pill
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        if (simSlots.isNotEmpty()) {
                            AssistChip(
                                onClick = { selectedSimIndex = (selectedSimIndex + 1) % simSlots.size },
                                label = { Text(activeSim?.displayName ?: "SIM 1") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.SimCard, null, modifier = Modifier.size(16.dp))
                                },
                                modifier = Modifier.testTag("dual_sim_selector")
                            )
                        }
                        if (prefs.signatureEnabled && prefs.signatureText.isNotBlank()) {
                            Text(
                                text = "Sig: ${prefs.signatureText.take(18)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        attachedImageUri?.let { uri ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    AsyncImage(
                                        model = uri,
                                        contentDescription = "Attached MMS photo",
                                        modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    IconButton(
                                        onClick = { attachedImageUri = null },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Remove photo", modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }

                    // Composer Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.testTag("attach_photo_button")
                        ) {
                            Icon(Icons.Rounded.AttachFile, contentDescription = "Attach image")
                        }
                        IconButton(
                            onClick = { contactPickerLauncher.launch(null) },
                            modifier = Modifier.testTag("attach_contact_button")
                        ) {
                            Icon(Icons.Rounded.PersonAdd, contentDescription = "Attach contact card")
                        }
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            placeholder = { Text("Text message…") },
                            shape = RoundedCornerShape(22.dp),
                            maxLines = 4,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("message_composer_input")
                        )
                        if (draft.isBlank()) {
                            IconButton(
                                onClick = {
                                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(
                                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                        )
                                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your message")
                                    }
                                    runCatching { voiceLauncher.launch(intent) }.onFailure {
                                        viewModel.emitSnackbar("Voice typing is unavailable on this device")
                                    }
                                },
                                modifier = Modifier.testTag("voice_typing_button")
                            ) {
                                Icon(Icons.Rounded.Mic, contentDescription = "Voice typing")
                            }
                        } else {
                            IconButton(
                                onClick = { showScheduleDialog = true },
                                modifier = Modifier.testTag("schedule_sms_button")
                            ) {
                                Icon(Icons.Rounded.ScheduleSend, contentDescription = "Schedule send")
                            }
                            IconButton(
                                onClick = {
                                    val textToSend = draft.trim()
                                    val subId = activeSim?.subscriptionId ?: -1
                                    val media = attachedImageUri?.toString()
                                    draft = ""
                                    attachedImageUri = null
                                    viewModel.sendSms(address, textToSend, subId, media)
                                },
                                modifier = Modifier.testTag("send_message_button")
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.Send,
                                    contentDescription = "Send SMS",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            items(messages, key = { it.id }) { msg ->
                MessageBubbleItem(
                    msg = msg,
                    onCopyOtp = { otp ->
                        val cm = context.getSystemService(ClipboardManager::class.java)
                        cm?.setPrimaryClip(ClipData.newPlainText("OTP", otp))
                        viewModel.emitSnackbar("OTP $otp copied")
                    },
                    onDeleteOtp = { viewModel.deleteMessage(msg.id) },
                    onToggleStar = { viewModel.toggleMessageStar(msg) },
                    onForwardBill = { forwardBillTarget = msg }
                )
            }
        }
    }

    if (showMoveDialog) {
        MoveToFolderDialog(
            senderTitle = title,
            currentCategory = currentCategory,
            onSelectCategory = { cat ->
                viewModel.moveThreadToCategory(
                    ThreadSummary(
                        threadKey = threadKey,
                        displayTitle = title,
                        address = address,
                        lastSnippet = latest?.body.orEmpty(),
                        lastTimestamp = latest?.timestamp ?: 0L,
                        unreadCount = 0,
                        isStarred = false,
                        category = currentCategory,
                        isBlocked = false,
                        latestOtp = null,
                        isGroup = isGroup,
                        messageCount = messages.size
                    ),
                    cat
                )
                showMoveDialog = false
            },
            onDismiss = { showMoveDialog = false }
        )
    }

    if (showRenameDialog) {
        var nameInput by remember { mutableStateOf(title) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Conversation") },
            text = {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Custom group / thread name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.renameGroup(threadKey, address, nameInput)
                    showRenameDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showScheduleDialog) {
        ScheduleDateTimeDialog(
            onConfirm = { targetTs ->
                val text = draft.trim()
                if (text.isNotBlank()) {
                    viewModel.scheduleSms(
                        recipientAddress = address,
                        recipientName = title,
                        body = text,
                        scheduledTimestamp = targetTs,
                        simSubscriptionId = activeSim?.subscriptionId ?: -1
                    )
                    draft = ""
                }
                showScheduleDialog = false
            },
            onDismiss = { showScheduleDialog = false }
        )
    }

    forwardBillTarget?.let { billMsg ->
        var recipientInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { forwardBillTarget = null },
            title = { Text("Forward Bill with Auto-Reminder") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Forwarding this bill attaches a KMS-BILL reminder tag so the recipient automatically gets a payment reminder.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = recipientInput,
                        onValueChange = { recipientInput = it },
                        label = { Text("Recipient phone number") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = recipientInput.isNotBlank(),
                    onClick = {
                        viewModel.forwardBill(
                            recipientAddress = recipientInput.trim(),
                            billTitle = "$title Bill",
                            amount = null,
                            dueTimestamp = System.currentTimeMillis() + 3L * 86_400_000L,
                            originalSnippet = billMsg.body
                        )
                        forwardBillTarget = null
                    }
                ) { Text("Forward Bill") }
            },
            dismissButton = {
                TextButton(onClick = { forwardBillTarget = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun MessageBubbleItem(
    msg: SmsMessageEntity,
    onCopyOtp: (String) -> Unit,
    onDeleteOtp: () -> Unit,
    onToggleStar: () -> Unit,
    onForwardBill: () -> Unit
) {
    val semantic = LocalKmsSemanticColors.current
    val alignment = if (msg.isIncoming) Alignment.Start else Alignment.End
    val bubbleColor = if (msg.isIncoming) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val textColor = if (msg.isIncoming) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (msg.isIncoming) 4.dp else 18.dp,
                bottomEnd = if (msg.isIncoming) 18.dp else 4.dp
            ),
            color = bubbleColor,
            border = if (msg.isIncoming) BorderStroke(1.dp, semantic.cardBorder) else null,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (!msg.attachmentUri.isNullOrBlank()) {
                    AsyncImage(
                        model = Uri.parse(msg.attachmentUri),
                        contentDescription = "MMS attachment",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    text = msg.body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor
                )

                // Contextual OTP Actions
                if (!msg.otpCode.isNullOrBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            onClick = { onCopyOtp(msg.otpCode) },
                            shape = RoundedCornerShape(10.dp),
                            color = semantic.otpContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Rounded.ContentCopy, null, tint = semantic.otpAccent, modifier = Modifier.size(14.dp))
                                Text(
                                    text = "Copy ${msg.otpCode}",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontFamily = MonoFontFamily,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = semantic.otpAccent
                                )
                            }
                        }
                        Surface(
                            onClick = onDeleteOtp,
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                                Text(
                                    text = "Delete",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                // Bill Forwarding Shortcut
                if (msg.isBillMessage) {
                    Spacer(Modifier.height(8.dp))
                    AssistChip(
                        onClick = onForwardBill,
                        label = { Text("Forward Bill Reminder") },
                        leadingIcon = {
                            Icon(Icons.AutoMirrored.Rounded.ForwardToInbox, null, modifier = Modifier.size(16.dp))
                        }
                    )
                }

                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = formatShortTime(msg.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (msg.simSubscriptionId >= 0) {
                        Text(
                            text = "SIM",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = onToggleStar,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            if (msg.isStarred) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                            contentDescription = "Star message",
                            tint = if (msg.isStarred) semantic.starGold else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun readContactCardSummary(context: android.content.Context, contactUri: Uri): String? {
    return try {
        var name: String? = null
        var contactId: String? = null
        context.contentResolver.query(
            contactUri,
            arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { c ->
            if (c.moveToFirst()) {
                contactId = c.getString(0)
                name = c.getString(1)
            }
        }
        var phone: String? = null
        if (contactId != null) {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                arrayOf(contactId),
                null
            )?.use { p ->
                if (p.moveToFirst()) phone = p.getString(0)
            }
        }
        if (name != null) "Contact: $name${phone?.let { " ($it)" } ?: ""}" else null
    } catch (_: Exception) {
        null
    }
}

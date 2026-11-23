package com.example.ui.screens

import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.ScheduleSend
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.ui.components.ScheduleDateTimeDialog
import com.example.viewmodel.KmsMainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewMessageScreen(
    initialRecipient: String,
    viewModel: KmsMainViewModel,
    onBack: () -> Unit,
    onOpenThread: (String) -> Unit
) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val simSlots by viewModel.simSlots.collectAsStateWithLifecycle()

    var recipient by remember { mutableStateOf(initialRecipient) }
    var messageBody by remember { mutableStateOf("") }
    var selectedSimIndex by remember { mutableIntStateOf(0) }
    var attachedImage by remember { mutableStateOf<Uri?>(null) }
    var showScheduleDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val activeSim = simSlots.getOrNull(selectedSimIndex) ?: simSlots.firstOrNull()

    val contactPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.query(
                    uri,
                    arrayOf(ContactsContract.Contacts._ID),
                    null,
                    null,
                    null
                )?.use { c ->
                    if (c.moveToFirst()) {
                        val id = c.getString(0)
                        context.contentResolver.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                            arrayOf(id),
                            null
                        )?.use { p ->
                            if (p.moveToFirst()) {
                                val number = p.getString(0).trim()
                                recipient = if (recipient.isBlank()) number else "$recipient, $number"
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Ignore if Contacts permission not granted
            }
        }
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) attachedImage = uri
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Message") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = recipient,
                onValueChange = { recipient = it },
                label = { Text("To (separate multiple numbers with commas for group)") },
                trailingIcon = {
                    IconButton(onClick = { contactPicker.launch(null) }) {
                        Icon(Icons.Rounded.PersonSearch, contentDescription = "Pick contact")
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("new_msg_recipient_input")
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (simSlots.isNotEmpty()) {
                    AssistChip(
                        onClick = { selectedSimIndex = (selectedSimIndex + 1) % simSlots.size },
                        label = { Text(activeSim?.displayName ?: "SIM 1") },
                        leadingIcon = { Icon(Icons.Rounded.SimCard, null, modifier = Modifier.size(16.dp)) }
                    )
                }
                AssistChip(
                    onClick = {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    label = { Text(if (attachedImage == null) "Attach Image" else "Image Attached") },
                    leadingIcon = { Icon(Icons.Rounded.AttachFile, null, modifier = Modifier.size(16.dp)) }
                )
            }

            attachedImage?.let { uri ->
                AsyncImage(
                    model = uri,
                    contentDescription = "Selected attachment",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            if (prefs.quickReplies.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(prefs.quickReplies) { quick ->
                        SuggestionChip(
                            onClick = { messageBody = if (messageBody.isBlank()) quick else "$messageBody $quick" },
                            label = { Text(quick) }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = messageBody,
                onValueChange = { messageBody = it },
                label = { Text("Message") },
                minLines = 4,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth().testTag("new_msg_body_input")
            )

            Spacer(Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { showScheduleDialog = true },
                    enabled = recipient.isNotBlank() && messageBody.isNotBlank(),
                    modifier = Modifier.weight(1f).testTag("new_msg_schedule_button")
                ) {
                    Icon(Icons.Rounded.ScheduleSend, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Schedule")
                }
                Button(
                    onClick = {
                        viewModel.sendSms(
                            recipientAddress = recipient.trim(),
                            body = messageBody.trim(),
                            simSubscriptionId = activeSim?.subscriptionId ?: -1,
                            attachmentUri = attachedImage?.toString(),
                            onSuccess = { threadKey -> onOpenThread(threadKey) }
                        )
                    },
                    enabled = recipient.isNotBlank() && messageBody.isNotBlank(),
                    modifier = Modifier.weight(1f).testTag("new_msg_send_button")
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Send")
                }
            }
        }
    }

    if (showScheduleDialog) {
        ScheduleDateTimeDialog(
            onConfirm = { targetTs ->
                viewModel.scheduleSms(
                    recipientAddress = recipient.trim(),
                    recipientName = null,
                    body = messageBody.trim(),
                    scheduledTimestamp = targetTs,
                    simSubscriptionId = activeSim?.subscriptionId ?: -1
                )
                showScheduleDialog = false
                onBack()
            },
            onDismiss = { showScheduleDialog = false }
        )
    }
}

package org.aprsdroid.app.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.aprsdroid.app.R
import org.aprsdroid.app.StorageDatabase
import org.aprsdroid.app.model.MessageItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageChatScreen(
    targetCall: String,
    myCall: String,
    messages: List<MessageItem>,
    onBack: () -> Unit,
    onSendMessage: (String) -> Unit,
    onDeleteMessage: (Long) -> Unit,
    onRestartMessage: (MessageItem) -> Unit,
    onAbortMessage: (MessageItem) -> Unit,
    onClearAllMessages: () -> Unit,
    onExportLogs: () -> Unit
) {
    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current

    var selectedItemForMenu by remember { mutableStateOf<MessageItem?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showTopMenu by remember { mutableStateOf(false) }

    // Scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .imePadding(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = targetCall,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 18.sp
                        )
                        Text(
                            text = stringResource(R.string.app_messages),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showTopMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options"
                        )
                    }
                    DropdownMenu(
                        expanded = showTopMenu,
                        onDismissRequest = { showTopMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.app_messages_clear)) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            onClick = {
                                showTopMenu = false
                                showClearDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.export_log)) },
                            onClick = {
                                showTopMenu = false
                                onExportLogs()
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        placeholder = { Text(stringResource(R.string.msg_send_hint)) },
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 3,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (inputText.isNotBlank()) {
                                onSendMessage(inputText.trim())
                                inputText = ""
                                focusManager.clearFocus()
                            }
                        }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                    FilledIconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                onSendMessage(inputText.trim())
                                inputText = ""
                            }
                        },
                        enabled = inputText.isNotBlank(),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send"
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            items(messages, key = { it.id }) { item ->
                MessageBubbleItem(
                    item = item,
                    myCall = myCall,
                    targetCall = targetCall,
                    onLongClick = { selectedItemForMenu = item }
                )
            }

            item { Spacer(modifier = Modifier.height(4.dp)) }
        }
    }

    // Message options dialog
    selectedItemForMenu?.let { item ->
        MessageItemActionDialog(
            item = item,
            targetCall = targetCall,
            onDismiss = { selectedItemForMenu = null },
            onCopy = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("APRS message", item.text))
                Toast.makeText(context, R.string.text_copied, Toast.LENGTH_SHORT).show()
                selectedItemForMenu = null
            },
            onDelete = {
                onDeleteMessage(item.id)
                Toast.makeText(context, R.string.message_deleted, Toast.LENGTH_SHORT).show()
                selectedItemForMenu = null
            },
            onRestart = {
                onRestartMessage(item)
                selectedItemForMenu = null
            },
            onAbort = {
                onAbortMessage(item)
                selectedItemForMenu = null
            }
        )
    }

    // Clear all messages confirmation dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.app_messages_clear)) },
            text = { Text(stringResource(R.string.confirm_delete_messages, targetCall)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearAllMessages()
                        showClearDialog = false
                        Toast.makeText(context, R.string.messages_cleared, Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubbleItem(
    item: MessageItem,
    myCall: String,
    targetCall: String,
    onLongClick: () -> Unit
) {
    val isOutgoing = (item.type != StorageDatabase.Companion.Message.TYPE_INCOMING)
    val alignment = if (isOutgoing) Alignment.End else Alignment.Start

    val bubbleShape = if (isOutgoing) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp)
    }

    val bubbleColor = if (isOutgoing) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }

    val contentColor = if (isOutgoing) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val (statusLabel, statusColor) = when (item.type) {
        StorageDatabase.Companion.Message.TYPE_INCOMING -> {
            "📥 来自 $targetCall" to MaterialTheme.colorScheme.secondary
        }
        StorageDatabase.Companion.Message.TYPE_OUT_NEW -> {
            val retryStr = if (item.retryCnt > 0) " (重试 ${item.retryCnt}/7)" else " (待发 0/7)"
            "⏳ 发送中$retryStr" to MaterialTheme.colorScheme.tertiary
        }
        StorageDatabase.Companion.Message.TYPE_OUT_ACKED -> {
            "✓✓ 已送达 (ACK)" to MaterialTheme.colorScheme.primary
        }
        StorageDatabase.Companion.Message.TYPE_OUT_REJECTED -> {
            "✕ 对方拒绝 (REJ)" to MaterialTheme.colorScheme.error
        }
        StorageDatabase.Companion.Message.TYPE_OUT_ABORTED -> {
            "⊘ 发送失败/已中止" to MaterialTheme.colorScheme.error
        }
        else -> {
            myCall to MaterialTheme.colorScheme.onSurfaceVariant
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Card(
            shape = bubbleShape,
            colors = CardDefaults.cardColors(containerColor = bubbleColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(bubbleShape)
                .combinedClickable(
                    onClick = {},
                    onLongClick = onLongClick
                )
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                // Header info: Timestamp & Status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.tss,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = statusLabel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Message body
                Text(
                    text = item.text,
                    fontSize = 15.sp,
                    color = contentColor,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun MessageItemActionDialog(
    item: MessageItem,
    targetCall: String,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onRestart: () -> Unit,
    onAbort: () -> Unit
) {
    val isIncoming = (item.type == StorageDatabase.Companion.Message.TYPE_INCOMING)
    val title = if (isIncoming) {
        stringResource(R.string.msg_from, targetCall)
    } else {
        stringResource(R.string.msg_to, targetCall)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = onCopy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(android.R.string.copy), modifier = Modifier.fillMaxWidth())
                }
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.delete_message), modifier = Modifier.fillMaxWidth())
                }
                if (!isIncoming) {
                    TextButton(
                        onClick = onRestart,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.msg_restart), modifier = Modifier.fillMaxWidth())
                    }
                    if (item.type == StorageDatabase.Companion.Message.TYPE_OUT_NEW) {
                        TextButton(
                            onClick = onAbort,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.msg_abort), modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

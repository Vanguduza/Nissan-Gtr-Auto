package co.zw.nissangtr.customer.chat

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.customer.rpc.ChatMessage
import co.zw.nissangtr.customer.rpc.ChatSenderKind
import co.zw.nissangtr.customer.rpc.ChatThread
import co.zw.nissangtr.customer.rpc.ChatThreadKind
import co.zw.nissangtr.customer.rpc.RpcClient
import co.zw.nissangtr.customer.rpc.RpcNames
import co.zw.nissangtr.customer.rpc.previewLabel
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Thin live-chat scaffold: thread list + bubbles + composer.
 * Mutations via [RpcNames.START_CHAT_THREAD] / [RpcNames.POST_CHAT_MESSAGE];
 * reads via PostgREST + RLS. Poll refresh (no realtime-kt yet).
 */
@Composable
fun ChatScreen(
    rpc: RpcClient,
    onBack: () -> Unit,
    whatsappE164Digits: String = DEFAULT_WHATSAPP_DIGITS,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = viewModel(factory = ChatViewModel.factory(rpc)),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size, state.selectedId) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Live chat", style = MaterialTheme.typography.headlineSmall)
        Text(
            "RPCs: ${RpcNames.START_CHAT_THREAD}, ${RpcNames.POST_CHAT_MESSAGE}, " +
                "${RpcNames.MARK_CHAT_THREAD_READ}, ${RpcNames.CHAT_UNREAD_COUNT}. " +
                if (state.polling) "Poll ${POLL_NOTE}" else "Select a thread to poll.",
            style = MaterialTheme.typography.bodySmall,
        )
        if (state.unread > 0) {
            Text("Unread: ${state.unread}", style = MaterialTheme.typography.bodySmall)
        }

        OutlinedButton(
            onClick = {
                openWhatsApp(context, whatsappE164Digits)
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Ask counter on WhatsApp")
        }

        if (state.selectedId == null) {
            ThreadListPane(
                state = state,
                onKind = viewModel::onKindChange,
                onSubject = viewModel::onSubjectChange,
                onFirstBody = viewModel::onFirstBodyChange,
                onStart = viewModel::startThread,
                onRefresh = viewModel::refreshThreads,
                onOpen = viewModel::openThread,
                modifier = Modifier.weight(1f),
            )
        } else {
            ThreadDetailPane(
                state = state,
                listState = listState,
                onDraft = viewModel::onDraftChange,
                onSend = viewModel::sendMessage,
                onBackToList = viewModel::clearSelection,
                onRefresh = { state.selectedId?.let(viewModel::openThread) },
                modifier = Modifier.weight(1f),
            )
        }

        state.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back")
        }
    }
}

@Composable
private fun ThreadListPane(
    state: ChatUiState,
    onKind: (ChatThreadKind) -> Unit,
    onSubject: (String) -> Unit,
    onFirstBody: (String) -> Unit,
    onStart: () -> Unit,
    onRefresh: () -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("New thread", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.kind == ChatThreadKind.SUPPORT,
                onClick = { onKind(ChatThreadKind.SUPPORT) },
                label = { Text("Support") },
                enabled = !state.busy,
            )
            FilterChip(
                selected = state.kind == ChatThreadKind.PARTS,
                onClick = { onKind(ChatThreadKind.PARTS) },
                label = { Text("Parts") },
                enabled = !state.busy,
            )
        }
        OutlinedTextField(
            value = state.subject,
            onValueChange = onSubject,
            label = { Text("Subject (optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
        )
        OutlinedTextField(
            value = state.firstBody,
            onValueChange = onFirstBody,
            label = { Text("First message (optional)") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.busy,
        )
        Button(
            onClick = onStart,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.busy) "Starting…" else "Start thread")
        }
        OutlinedButton(
            onClick = onRefresh,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Refresh threads")
        }
        HorizontalDivider()
        Text("Your threads", style = MaterialTheme.typography.titleSmall)
        if (state.threads.isEmpty()) {
            Text(
                "No threads yet — start one above.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        state.threads.forEach { thread ->
            ThreadRow(thread = thread, onClick = { onOpen(thread.id) })
            HorizontalDivider()
        }
    }
}

@Composable
private fun ThreadRow(
    thread: ChatThread,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    ) {
        Text(thread.previewLabel(), style = MaterialTheme.typography.bodyMedium)
        Text(
            "${thread.kind.rpcValue} · ${thread.status.rpcValue}" +
                (thread.lastMessageAt?.let { " · $it" } ?: ""),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun ThreadDetailPane(
    state: ChatUiState,
    listState: LazyListState,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
    onBackToList: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = state.selected
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    selected?.previewLabel() ?: "Thread",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    selected?.status?.rpcValue ?: "",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedButton(onClick = onBackToList) { Text("Threads") }
        }
        OutlinedButton(
            onClick = onRefresh,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Refresh messages")
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.messages, key = { it.id }) { msg ->
                MessageBubble(msg)
            }
        }

        if (state.threadClosed) {
            Text("Thread closed", style = MaterialTheme.typography.bodySmall)
        } else {
            OutlinedTextField(
                value = state.draft,
                onValueChange = onDraft,
                label = { Text("Message the counter…") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.sendBusy,
            )
            Button(
                onClick = onSend,
                enabled = !state.sendBusy && state.draft.trim().isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.sendBusy) "Sending…" else "Send")
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: ChatMessage) {
    val mine = msg.senderKind == ChatSenderKind.CUSTOMER
    val align = if (mine) Alignment.CenterEnd else Alignment.CenterStart
    val bg = when (msg.senderKind) {
        ChatSenderKind.CUSTOMER -> MaterialTheme.colorScheme.primaryContainer
        ChatSenderKind.STAFF -> MaterialTheme.colorScheme.secondaryContainer
        ChatSenderKind.SYSTEM -> MaterialTheme.colorScheme.surfaceVariant
    }
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .align(align)
                .widthIn(max = 320.dp)
                .background(bg, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                msg.senderKind.rpcValue,
                style = MaterialTheme.typography.labelSmall,
            )
            Text(msg.body, style = MaterialTheme.typography.bodyMedium)
            Text(msg.createdAt, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private const val DEFAULT_WHATSAPP_DIGITS = "263770000000"
private const val POLL_NOTE = "every 3s (no realtime-kt)"

private fun openWhatsApp(context: android.content.Context, digits: String) {
    val clean = digits.filter { it.isDigit() }.ifEmpty { DEFAULT_WHATSAPP_DIGITS }
    val text = URLEncoder.encode(
        "Hi GTR Auto — I need a parts counter check",
        StandardCharsets.UTF_8.toString(),
    )
    val uri = Uri.parse("https://wa.me/$clean?text=$text")
    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
}

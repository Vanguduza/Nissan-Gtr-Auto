package co.zw.nissangtr.management.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.management.rpc.ChatMessageSummary
import co.zw.nissangtr.management.rpc.ChatThreadSummary
import co.zw.nissangtr.management.rpc.RpcClient
import co.zw.nissangtr.management.rpc.StaffChatFilter
import co.zw.nissangtr.ui.shop.ShopListCard
import co.zw.nissangtr.ui.shop.ShopPrimaryButton
import co.zw.nissangtr.ui.shop.ShopSecondaryButton
import co.zw.nissangtr.ui.shop.ShopStaffScreen

/**
 * Thin staff live-chat scaffold: open / mine / closed list, bubbles, claim / reply / close.
 * Same RPCs as web `/staff/chat`. Polling fallback (no Realtime plugin yet).
 */
@Composable
fun ChatScreen(
    rpc: RpcClient,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = viewModel(factory = ChatViewModel.factory(rpc)),
) {
    val state by viewModel.state.collectAsState()
    val selected = state.threads.find { it.id == state.selectedId }

    ShopStaffScreen(
        title = "Staff chat",
        subtitle = "Live support threads",
        modifier = modifier,
        scrollable = false,
        onBack = onBack,
    ) {
        if (state.unread > 0) {
            Text("Unread: ${state.unread}", style = MaterialTheme.typography.bodyMedium)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ShopSecondaryButton(
                label = "Refresh",
                onClick = viewModel::refreshThreads,
                enabled = !state.busy,
                modifier = Modifier.weight(1f),
            )
        }

        if (state.selectedId == null) {
            FilterRow(
                filter = state.filter,
                onFilter = viewModel::onFilterChange,
                enabled = !state.busy,
            )
            if (state.loading) {
                Text("Loading…", style = MaterialTheme.typography.bodyMedium)
            } else if (state.threads.isEmpty()) {
                Text(
                    "No ${state.filter.name.lowercase()} threads.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(state.threads, key = { it.id }) { thread ->
                        ShopListCard(
                            title = threadPreview(thread),
                            subtitle = "${thread.status} · ${thread.kind}" +
                                (thread.lastMessageAt?.let { " · $it" } ?: ""),
                            onClick = { viewModel.selectThread(thread.id) },
                        )
                    }
                }
            }
        } else {
            ThreadDetail(
                thread = selected,
                messages = state.messages,
                currentUserId = state.currentUserId,
                draft = state.draft,
                busy = state.busy,
                sendBusy = state.sendBusy,
                onDraftChange = viewModel::onDraftChange,
                onBackToList = viewModel::clearSelection,
                onClaim = viewModel::claim,
                onClose = viewModel::close,
                onSend = viewModel::send,
                modifier = Modifier.weight(1f),
            )
        }

        state.message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall)
        }
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun FilterRow(
    filter: StaffChatFilter,
    onFilter: (StaffChatFilter) -> Unit,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StaffChatFilter.entries.forEach { f ->
            FilterChip(
                selected = filter == f,
                onClick = { onFilter(f) },
                enabled = enabled,
                label = {
                    Text(
                        when (f) {
                            StaffChatFilter.OPEN -> "Open"
                            StaffChatFilter.MINE -> "Mine"
                            StaffChatFilter.CLOSED -> "Closed"
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun ThreadDetail(
    thread: ChatThreadSummary?,
    messages: List<ChatMessageSummary>,
    currentUserId: String?,
    draft: String,
    busy: Boolean,
    sendBusy: Boolean,
    onDraftChange: (String) -> Unit,
    onBackToList: () -> Unit,
    onClaim: () -> Unit,
    onClose: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val closed = thread?.status == "closed"
    val canClaim = thread != null &&
        (thread.status == "open" ||
            (thread.status == "assigned" && thread.assignedTo != currentUserId))
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShopSecondaryButton(
                label = "List",
                onClick = onBackToList,
                modifier = Modifier.weight(0.35f),
            )
            Text(
                thread?.let { threadPreview(it) } ?: "Thread",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (canClaim) {
                ShopPrimaryButton(
                    label = "Claim",
                    onClick = onClaim,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                )
            }
            if (!closed) {
                ShopSecondaryButton(
                    label = "Close",
                    onClick = onClose,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(messages, key = { it.id }) { msg ->
                MessageBubble(msg = msg, currentUserId = currentUserId)
            }
        }

        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            label = { Text(if (closed) "Thread closed" else "Reply…") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !closed && !sendBusy,
            minLines = 2,
        )
        ShopPrimaryButton(
            label = if (sendBusy) "Sending…" else "Send",
            onClick = onSend,
            enabled = !closed && !sendBusy && draft.isNotBlank(),
        )
    }
}

@Composable
private fun MessageBubble(
    msg: ChatMessageSummary,
    currentUserId: String?,
) {
    val mine = msg.senderUserId == currentUserId || msg.senderKind == "staff"
    val align = if (mine) Alignment.CenterEnd else Alignment.CenterStart
    val bg = if (mine) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = align) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .background(bg, RoundedCornerShape(10.dp))
                .padding(10.dp),
        ) {
            Text(
                "${msg.senderKind} · ${msg.createdAt}",
                style = MaterialTheme.typography.labelSmall,
            )
            Text(msg.body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun threadPreview(thread: ChatThreadSummary): String {
    val sub = thread.subject?.trim()
    if (!sub.isNullOrEmpty()) return sub
    return if (thread.kind == "parts") "Parts inquiry" else "Support"
}

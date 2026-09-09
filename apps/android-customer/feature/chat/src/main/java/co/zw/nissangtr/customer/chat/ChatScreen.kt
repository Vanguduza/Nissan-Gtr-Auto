package co.zw.nissangtr.customer.chat

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.customer.rpc.ChatMessage
import co.zw.nissangtr.customer.rpc.ChatSenderKind
import co.zw.nissangtr.customer.rpc.ChatThread
import co.zw.nissangtr.customer.rpc.ChatThreadKind
import co.zw.nissangtr.customer.rpc.RpcClient
import co.zw.nissangtr.customer.rpc.previewLabel
import co.zw.nissangtr.customer.visual.GtrPremiumColors
import co.zw.nissangtr.customer.visual.PremiumEmptyState
import co.zw.nissangtr.customer.visual.PremiumMessageBanner
import co.zw.nissangtr.customer.visual.PremiumMessageKind
import co.zw.nissangtr.customer.visual.PremiumPrimaryButton
import co.zw.nissangtr.customer.visual.PremiumScreenHeader
import co.zw.nissangtr.customer.visual.PremiumSecondaryButton
import co.zw.nissangtr.customer.visual.PremiumStatusChip
import co.zw.nissangtr.customer.visual.PremiumStatusTone
import co.zw.nissangtr.customer.visual.PremiumSurfaceCard
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Composable
fun ChatScreen(
    rpc: RpcClient,
    onBack: () -> Unit,
    whatsappE164Digits: String = "",
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = viewModel(factory = ChatViewModel.factory(rpc)),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size, state.selectedId) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }

    Column(
        modifier = modifier.fillMaxSize().background(GtrPremiumColors.Background),
    ) {
        PremiumScreenHeader(
            title = "Support",
            subtitle = if (state.polling) "Checking for replies…" else "Nissan GTR Auto parts counter",
            onBack = onBack,
            trailing = {
                if (state.unread > 0) PremiumStatusChip("${state.unread} new", PremiumStatusTone.Premium)
            },
        )

        if (state.selectedId == null) {
            ThreadListPane(
                state = state,
                onKind = viewModel::onKindChange,
                onSubject = viewModel::onSubjectChange,
                onFirstBody = viewModel::onFirstBodyChange,
                onStart = viewModel::startThread,
                onRefresh = viewModel::refreshThreads,
                onOpen = viewModel::openThread,
                onWhatsApp = { openWhatsApp(context, whatsappE164Digits) },
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

        state.message?.let {
            PremiumMessageBanner(it, PremiumMessageKind.Success, Modifier.padding(12.dp))
        }
        state.error?.let {
            PremiumMessageBanner(it, PremiumMessageKind.Error, Modifier.padding(12.dp))
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
    onWhatsApp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PremiumSurfaceCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Start a conversation",
                    color = GtrPremiumColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
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
                        label = { Text("Parts help") },
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
                    label = { Text("How can we help?") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    enabled = !state.busy,
                )
                PremiumPrimaryButton(
                    text = if (state.busy) "Starting…" else "Start conversation",
                    onClick = onStart,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                PremiumSecondaryButton(
                    text = "Ask the counter on WhatsApp",
                    onClick = onWhatsApp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Text(
            "Your conversations",
            color = GtrPremiumColors.TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (state.threads.isEmpty()) {
            PremiumEmptyState(
                title = "No conversations yet",
                body = "Start a parts or support conversation above.",
            )
        } else {
            state.threads.forEach { thread ->
                ThreadRow(thread) { onOpen(thread.id) }
            }
        }
        PremiumSecondaryButton(
            text = "Refresh conversations",
            onClick = onRefresh,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ThreadRow(thread: ChatThread, onClick: () -> Unit) {
    PremiumSurfaceCard(onClick = onClick) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    thread.previewLabel(),
                    color = GtrPremiumColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    thread.lastMessageAt ?: "No messages yet",
                    color = GtrPremiumColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            PremiumStatusChip(
                thread.status.rpcValue.replaceFirstChar { it.uppercase() },
                PremiumStatusTone.Neutral,
            )
        }
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
    Column(modifier = modifier.padding(horizontal = 12.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    state.selected?.previewLabel() ?: "Conversation",
                    color = GtrPremiumColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    state.selected?.status?.rpcValue.orEmpty(),
                    color = GtrPremiumColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            PremiumSecondaryButton("Threads", onBackToList)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.messages, key = { it.id }) { MessageBubble(it) }
        }

        if (state.threadClosed) {
            PremiumMessageBanner("This conversation is closed.", PremiumMessageKind.Info)
        } else {
            OutlinedTextField(
                value = state.draft,
                onValueChange = onDraft,
                label = { Text("Message the parts counter…") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.sendBusy,
                minLines = 2,
            )
            Row(
                Modifier.fillMaxWidth().padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PremiumSecondaryButton(
                    text = "Refresh",
                    onClick = onRefresh,
                    enabled = !state.busy,
                    modifier = Modifier.weight(1f),
                )
                PremiumPrimaryButton(
                    text = if (state.sendBusy) "Sending…" else "Send",
                    onClick = onSend,
                    enabled = !state.sendBusy && state.draft.trim().isNotEmpty(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: ChatMessage) {
    val mine = msg.senderKind == ChatSenderKind.CUSTOMER
    val align = if (mine) Alignment.CenterEnd else Alignment.CenterStart
    val bg = when (msg.senderKind) {
        ChatSenderKind.CUSTOMER -> GtrPremiumColors.RedDark.copy(alpha = .55f)
        ChatSenderKind.STAFF -> GtrPremiumColors.SurfaceRaised
        ChatSenderKind.SYSTEM -> GtrPremiumColors.SurfaceSoft
    }
    Box(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .align(align)
                .widthIn(max = 320.dp)
                .background(bg, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp),
        ) {
            Text(
                when (msg.senderKind) {
                    ChatSenderKind.CUSTOMER -> "You"
                    ChatSenderKind.STAFF -> "GTR Auto"
                    ChatSenderKind.SYSTEM -> "System"
                },
                color = GtrPremiumColors.TextSecondary,
                style = MaterialTheme.typography.labelSmall,
            )
            Text(msg.body, color = GtrPremiumColors.TextPrimary)
            Text(msg.createdAt, color = GtrPremiumColors.TextDisabled, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun openWhatsApp(context: android.content.Context, digits: String) {
    val clean = digits.filter { it.isDigit() }
    if (clean.isEmpty()) return
    val text = URLEncoder.encode(
        "Hi GTR Auto — I need help finding a part",
        StandardCharsets.UTF_8.toString(),
    )
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$clean?text=$text")))
}

import SwiftUI

/// Customer live chat — thread list + start form (parity with web `/account/chat`).
///
/// Transport: PostgREST RPCs / RLS selects via `StorefrontApi`. No supabase-swift
/// Realtime on this URLSession scaffold — open threads poll every few seconds.
struct ChatScreen: View {
    @EnvironmentObject private var session: StorefrontSession
    @State private var threads: [ChatThread] = []
    @State private var unread = 0
    @State private var kind: ChatThreadKind = .support
    @State private var subject = ""
    @State private var firstBody = ""
    @State private var status: String?
    @State private var busy = false
    @State private var startedThreadId: UUID?

    var body: some View {
        List {
            Section {
                Text("Message the counter for support or parts fitment. WhatsApp remains available.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                Link(destination: whatsappURL) {
                    Label("Ask counter on WhatsApp", systemImage: "message.fill")
                }
            }

            if unread > 0 {
                Section {
                    Text("Unread messages: \(unread)")
                        .font(.subheadline)
                }
            }

            Section("New thread") {
                Picker("Kind", selection: $kind) {
                    ForEach(ChatThreadKind.allCases) { k in
                        Text(k.title).tag(k)
                    }
                }
                .pickerStyle(.segmented)
                TextField("Subject (optional)", text: $subject)
                TextField("First message (optional)", text: $firstBody, axis: .vertical)
                    .lineLimit(2 ... 4)
                Button(busy ? "Starting…" : "New thread") {
                    Task { await startThread() }
                }
                .disabled(busy)
            }

            Section("Threads") {
                if threads.isEmpty {
                    Text("No threads yet — start one above.")
                        .foregroundStyle(.secondary)
                }
                ForEach(threads) { thread in
                    NavigationLink {
                        ChatThreadScreen(threadId: thread.id)
                    } label: {
                        VStack(alignment: .leading, spacing: 4) {
                            HStack {
                                Text(thread.preview).font(.headline)
                                Spacer()
                                Text(thread.status.rawValue)
                                    .font(.caption2)
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 2)
                                    .background(.quaternary, in: Capsule())
                            }
                            Text(thread.kind.title)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                            if let at = thread.lastMessageAt {
                                Text(StorefrontFormat.chatTime(at))
                                    .font(.caption2)
                                    .foregroundStyle(.tertiary)
                            }
                        }
                    }
                }
            }

            Section {
                Text("Updates use short polling (no Realtime client on this scaffold). Pull to refresh anytime.")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                if let status {
                    Text(status)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
        }
        .navigationTitle("Chat")
        .navigationDestination(item: $startedThreadId) { id in
            ChatThreadScreen(threadId: id)
        }
        .task { await refresh() }
        .refreshable { await refresh() }
    }

    private var whatsappURL: URL {
        let text = "Hi GTR Auto — I need a parts counter check"
        let encoded = text.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
        return URL(string: "https://wa.me/\(AppEnv.whatsappE164Digits)?text=\(encoded)")!
    }

    private func refresh() async {
        busy = true
        defer { busy = false }
        do {
            async let list = session.api.listChatThreads()
            async let count = session.api.chatUnreadCount(threadId: nil)
            threads = try await list
            unread = try await count
            status = nil
        } catch {
            status = error.localizedDescription
        }
    }

    private func startThread() async {
        busy = true
        defer { busy = false }
        do {
            let id = try await session.api.startChatThread(
                StartChatThreadInput(
                    kind: kind,
                    subject: subject.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty,
                    body: firstBody.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty
                )
            )
            firstBody = ""
            threads = try await session.api.listChatThreads()
            unread = try await session.api.chatUnreadCount(threadId: nil)
            startedThreadId = id
            status = "Started via start_chat_thread"
        } catch {
            status = error.localizedDescription
        }
    }
}

/// Message bubbles + composer for one thread.
struct ChatThreadScreen: View {
    @EnvironmentObject private var session: StorefrontSession
    let threadId: UUID

    @State private var thread: ChatThread?
    @State private var messages: [ChatMessage] = []
    @State private var draft = ""
    @State private var status: String?
    @State private var busy = false
    @State private var sendBusy = false

    private let pollInterval: Duration = .seconds(4)

    var body: some View {
        VStack(spacing: 0) {
            if let thread {
                HStack {
                    Text(thread.preview).font(.headline)
                    Spacer()
                    Text(thread.status.rawValue)
                        .font(.caption2)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 2)
                        .background(.quaternary, in: Capsule())
                }
                .padding(.horizontal)
                .padding(.vertical, 8)
            }

            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 10) {
                        ForEach(messages) { msg in
                            ChatBubbleView(message: msg)
                                .id(msg.id)
                        }
                    }
                    .padding()
                }
                .onChange(of: messages.count) { _, _ in
                    if let last = messages.last {
                        withAnimation { proxy.scrollTo(last.id, anchor: .bottom) }
                    }
                }
            }

            if thread?.status == .closed {
                Text("Thread closed")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity)
                    .padding(8)
                    .background(.ultraThinMaterial)
            } else {
                HStack(alignment: .bottom, spacing: 8) {
                    TextField("Message the counter…", text: $draft, axis: .vertical)
                        .lineLimit(1 ... 5)
                        .textFieldStyle(.roundedBorder)
                    Button {
                        Task { await send() }
                    } label: {
                        Image(systemName: "paperplane.fill")
                    }
                    .disabled(sendBusy || draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
                .padding()
                .background(.bar)
            }

            if let status {
                Text(status)
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity)
                    .padding(.bottom, 4)
            }
        }
        .navigationTitle("Thread")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            await load(markRead: true)
            await pollLoop()
        }
        .refreshable { await load(markRead: true) }
    }

    private func load(markRead: Bool) async {
        busy = true
        defer { busy = false }
        do {
            let all = try await session.api.listChatThreads()
            thread = all.first { $0.id == threadId }
            messages = try await session.api.listChatMessages(threadId: threadId)
            if markRead {
                try await session.api.markChatThreadRead(threadId: threadId)
            }
            status = nil
        } catch {
            status = error.localizedDescription
        }
    }

    private func send() async {
        let body = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !body.isEmpty else { return }
        sendBusy = true
        defer { sendBusy = false }
        do {
            _ = try await session.api.postChatMessage(threadId: threadId, body: body)
            draft = ""
            messages = try await session.api.listChatMessages(threadId: threadId)
            let all = try await session.api.listChatThreads()
            thread = all.first { $0.id == threadId }
            status = nil
        } catch {
            status = error.localizedDescription
        }
    }

    /// Thin poll fallback — URLSession PostgREST has no supabase-swift Realtime channel.
    private func pollLoop() async {
        while !Task.isCancelled {
            do {
                try await Task.sleep(for: pollInterval)
            } catch {
                return
            }
            guard !Task.isCancelled else { return }
            do {
                let next = try await session.api.listChatMessages(threadId: threadId)
                if next.map(\.id) != messages.map(\.id) {
                    messages = next
                    try? await session.api.markChatThreadRead(threadId: threadId)
                }
                let all = try await session.api.listChatThreads()
                thread = all.first { $0.id == threadId }
            } catch {
                // Keep last good snapshot; surface transient errors lightly.
                status = error.localizedDescription
            }
        }
    }
}

private struct ChatBubbleView: View {
    let message: ChatMessage

    private var isCustomer: Bool { message.senderKind == .customer }
    private var isSystem: Bool { message.senderKind == .system }

    var body: some View {
        HStack {
            if isCustomer { Spacer(minLength: 48) }
            VStack(alignment: isCustomer ? .trailing : .leading, spacing: 4) {
                Text(message.body)
                    .font(.body)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(bubbleColor, in: RoundedRectangle(cornerRadius: 12))
                    .foregroundStyle(isSystem ? .secondary : .primary)
                Text("\(senderLabel) · \(StorefrontFormat.chatTime(message.createdAt))")
                    .font(.caption2)
                    .foregroundStyle(.tertiary)
            }
            if !isCustomer { Spacer(minLength: 48) }
        }
    }

    private var senderLabel: String {
        switch message.senderKind {
        case .customer: return "You"
        case .staff: return "Counter"
        case .system: return "System"
        }
    }

    private var bubbleColor: Color {
        switch message.senderKind {
        case .customer: return Color.accentColor.opacity(0.18)
        case .staff: return Color.secondary.opacity(0.14)
        case .system: return Color.clear
        }
    }
}

private extension String {
    var nilIfEmpty: String? {
        isEmpty ? nil : self
    }
}

extension UUID: @retroactive Identifiable {
    public var id: UUID { self }
}

#Preview {
    NavigationStack {
        ChatScreen()
    }
    .environmentObject(StorefrontSession(api: FakeStorefrontApi()))
}

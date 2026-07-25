import SwiftUI

/// Customer live chat — thread list + start form (parity with web `/account/chat`).
///
/// Transport: PostgREST RPCs / RLS selects via `StorefrontApi`. No supabase-swift
/// Realtime (Phoenix WS is non-trivial without that SDK) — hardened polling instead.
struct ChatScreen: View {
    @EnvironmentObject private var session: StorefrontSession
    @Environment(\.scenePhase) private var scenePhase
    @State private var threads: [ChatThread] = []
    @State private var unread = 0
    @State private var kind: ChatThreadKind = .support
    @State private var subject = ""
    @State private var firstBody = ""
    @State private var status: String?
    @State private var busy = false
    @State private var startedThreadId: UUID?
    @State private var showStartedThread = false

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
                Text("Open threads refresh on a short poll with backoff (no Realtime SDK). Pull to refresh anytime.")
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
        .navigationDestination(isPresented: $showStartedThread) {
            if let startedThreadId {
                ChatThreadScreen(threadId: startedThreadId)
            }
        }
        // Single owner for initial load — avoid `.onAppear` duplicate fetch.
        .task { await refresh() }
        .refreshable { await refresh() }
        .onChange(of: scenePhase) { _, phase in
            guard phase == .active else { return }
            Task { await refresh() }
        }
    }

    private var whatsappURL: URL {
        let text = "Hi GTR Auto — I need a parts counter check"
        var components = URLComponents()
        components.scheme = "https"
        components.host = "wa.me"
        components.path = "/\(AppEnv.whatsappE164Digits)"
        components.queryItems = [URLQueryItem(name: "text", value: text)]
        return components.url ?? URL(string: "https://wa.me/\(AppEnv.whatsappE164Digits)")!
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
            showStartedThread = true
            status = "Started via start_chat_thread"
        } catch {
            status = error.localizedDescription
        }
    }
}

/// Message bubbles + composer for one thread.
struct ChatThreadScreen: View {
    @EnvironmentObject private var session: StorefrontSession
    @Environment(\.scenePhase) private var scenePhase
    let threadId: UUID

    @State private var thread: ChatThread?
    @State private var messages: [ChatMessage] = []
    @State private var draft = ""
    @State private var status: String?
    @State private var pollHint: String?
    @State private var sendBusy = false
    @State private var failureStreak = 0
    /// Serializes poll/load so `.task` sleep + foreground resume cannot overlap.
    @State private var refreshGate = false

    private let basePollSeconds: Double = 4
    private let maxPollSeconds: Double = 30

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

            VStack(spacing: 2) {
                if let status {
                    Text(status)
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
                Text(pollHint ?? livePollCaption)
                    .font(.caption2)
                    .foregroundStyle(pollHint == nil ? .tertiary : .secondary)
            }
            .frame(maxWidth: .infinity)
            .padding(.bottom, 4)
        }
        .navigationTitle("Thread")
        .navigationBarTitleDisplayMode(.inline)
        // One `.task`-owned loop; cancelled on disappear — no second Timer.
        .task {
            await load()
            await pollLoop()
        }
        .refreshable { await load() }
        .onChange(of: scenePhase) { _, phase in
            guard phase == .active else { return }
            Task { await pollOnce(markReadOnChange: true, forceMarkRead: true) }
        }
    }

    private var livePollCaption: String {
        let secs = Int(currentPollSeconds().rounded())
        if thread?.status == .closed {
            return "Closed · slow poll ~\(secs)s"
        }
        return "Live · poll ~\(secs)s"
    }

    private func currentPollSeconds() -> Double {
        if thread?.status == .closed {
            return min(maxPollSeconds, basePollSeconds * 4)
        }
        let factor = pow(2.0, Double(min(failureStreak, 3)))
        return min(maxPollSeconds, basePollSeconds * factor)
    }

    private func load() async {
        await pollOnce(markReadOnChange: true, forceMarkRead: true)
    }

    private func send() async {
        let body = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !body.isEmpty else { return }
        sendBusy = true
        defer { sendBusy = false }
        do {
            _ = try await session.api.postChatMessage(threadId: threadId, body: body)
            draft = ""
            await pollOnce(markReadOnChange: false, forceMarkRead: false)
            status = nil
        } catch {
            status = error.localizedDescription
        }
    }

    /// Hardened poll — exponential backoff on errors, foreground burst, single gate.
    private func pollLoop() async {
        while !Task.isCancelled {
            let interval = Duration.seconds(currentPollSeconds())
            do {
                try await Task.sleep(for: interval)
            } catch {
                return
            }
            guard !Task.isCancelled else { return }
            await pollOnce(markReadOnChange: true, forceMarkRead: false)
        }
    }

    private func pollOnce(markReadOnChange: Bool, forceMarkRead: Bool) async {
        guard !refreshGate else { return }
        refreshGate = true
        defer { refreshGate = false }

        do {
            let next = try await session.api.listChatMessages(threadId: threadId)
            let changed = next.map(\.id) != messages.map(\.id)
            if changed {
                messages = next
            }
            if forceMarkRead || (markReadOnChange && changed) {
                try? await session.api.markChatThreadRead(threadId: threadId)
            }
            let all = try await session.api.listChatThreads()
            thread = all.first { $0.id == threadId }
            failureStreak = 0
            pollHint = nil
            if status?.hasPrefix("Update delayed") == true {
                status = nil
            }
        } catch {
            failureStreak = min(failureStreak + 1, 8)
            let secs = Int(currentPollSeconds().rounded())
            pollHint = "Update delayed — retrying in ~\(secs)s"
            status = "Update delayed: \(error.localizedDescription)"
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

#Preview {
    NavigationStack {
        ChatScreen()
    }
    .environmentObject(StorefrontSession(api: FakeStorefrontApi()))
}

import SwiftUI

/// Customer storefront top strip — menu + GTR logo left; My Account / Cart / Sign-in right.
struct CustomerShellTopBar: View {
    var signedInEmail: String?
    var cartBadgeCount: Int
    var onOpenMenu: () -> Void
    var onOpenAccount: () -> Void
    var onOpenCart: () -> Void
    var onSignIn: () -> Void

    var body: some View {
        HStack(spacing: 8) {
            HStack(spacing: 4) {
                ShopCircleIconButton(systemImage: "line.3.horizontal", onTap: onOpenMenu)
                GTRLogo(height: 28)
                    .frame(maxWidth: 96, alignment: .leading)
            }
            Spacer(minLength: 4)
            Button("My Account", action: onOpenAccount)
                .font(GTRType.label(.caption))
                .foregroundStyle(GTRColors.steel)
            ZStack(alignment: .topTrailing) {
                ShopCircleIconButton(systemImage: "cart.fill", onTap: onOpenCart)
                if cartBadgeCount > 0 {
                    Text(cartBadgeCount > 99 ? "99+" : "\(cartBadgeCount)")
                        .font(GTRType.label(.caption2))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 5)
                        .padding(.vertical, 2)
                        .background(GTRColors.primary, in: Capsule())
                        .offset(x: 4, y: -4)
                }
            }
            if let email = signedInEmail {
                Button(action: onOpenAccount) {
                    Text(String(email.prefix(2)).uppercased())
                        .font(GTRType.label(.caption))
                        .foregroundStyle(GTRColors.primary)
                        .frame(width: 36, height: 36)
                        .background(GTRColors.primary.opacity(0.12), in: Circle())
                }
                .buttonStyle(.plain)
            } else {
                Button(action: onSignIn) {
                    Label("Sign in", systemImage: "person.crop.circle")
                        .font(GTRType.label(.caption))
                }
                .buttonStyle(.plain)
                .foregroundStyle(GTRColors.steel)
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(GTRColors.chalk)
    }
}

enum HamburgerMenuAction: Equatable {
    case close
    case openCatalog(category: String?, subcategory: String?)
}

/// Fallback part categories when browse API returns none.
let gtrPartCategories = [
    "Brakes", "Filters", "Engine", "Suspension", "Electrical", "Cooling", "Body", "Drivetrain",
]

private let defaultSubcategories: [String: [String]] = [
    "Brakes": ["Pads", "Discs", "Calipers", "Fluid"],
    "Filters": ["Oil", "Air", "Cabin", "Fuel"],
    "Engine": ["Belts", "Gaskets", "Sensors", "Mounts"],
    "Suspension": ["Shocks", "Bushings", "Arms", "Springs"],
    "Electrical": ["Batteries", "Lighting", "Ignition", "Sensors"],
    "Cooling": ["Radiators", "Hoses", "Thermostats", "Pumps"],
    "Body": ["Mirrors", "Panels", "Trim", "Glass"],
    "Drivetrain": ["Clutch", "CV joints", "Differentials", "Mounts"],
]

/// Full-screen hamburger — Car Parts → categories → subcategories.
struct HamburgerMenuOverlay: View {
    var categories: [String]
    var onAction: (HamburgerMenuAction) -> Void

    private enum Pane {
        case root, carParts, category(String), deals, about, contact
    }

    @State private var pane: Pane = .root

    private var resolvedCategories: [String] {
        categories.isEmpty ? gtrPartCategories : categories
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                GTRLogo(height: 28)
                Spacer()
                ShopCircleIconButton(systemImage: "xmark", onTap: { onAction(.close) })
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)

            Divider().overlay(GTRColors.mist)

            ScrollView {
                VStack(spacing: 0) {
                    switch pane {
                    case .root:
                        menuRow("Car Parts", "wrench.and.screwdriver") { pane = .carParts }
                        menuRow("Deals", "tag") { pane = .deals }
                        menuRow("Shop catalog", "storefront") {
                            onAction(.openCatalog(category: nil, subcategory: nil))
                        }
                    case .carParts:
                        backButton("Menu") { pane = .root }
                        sectionTitle("Car Parts")
                        menuRow("All car parts", "wrench.and.screwdriver") {
                            onAction(.openCatalog(category: nil, subcategory: nil))
                        }
                        ForEach(resolvedCategories, id: \.self) { cat in
                            menuRow(cat, "wrench.and.screwdriver") { pane = .category(cat) }
                        }
                    case .category(let cat):
                        backButton("Car Parts") { pane = .carParts }
                        sectionTitle(cat)
                        menuRow("All \(cat)", "wrench.and.screwdriver") {
                            onAction(.openCatalog(category: cat, subcategory: nil))
                        }
                        let subs = defaultSubcategories[cat] ?? []
                        if subs.isEmpty {
                            ShopHonestEmpty(
                                title: "No subcategories yet",
                                bodyText: "Browse all \(cat) — deep tree ships when catalog API exposes it."
                            )
                            .padding()
                        } else {
                            ForEach(subs, id: \.self) { sub in
                                menuRow(sub, "wrench.and.screwdriver") {
                                    onAction(.openCatalog(category: cat, subcategory: sub))
                                }
                            }
                        }
                    case .deals:
                        backButton("Menu") { pane = .root }
                        ShopHonestEmpty(
                            title: "No deals feed yet",
                            bodyText: "Promo / deals API is not wired — we never invent sale SKUs."
                        )
                        .padding()
                    case .about:
                        backButton("Menu") { pane = .root }
                        ShopHonestEmpty(
                            title: "About Nissan GTR Auto",
                            bodyText: "Genuine Nissan parts · Harare counter & nationwide dispatch."
                        )
                        .padding()
                    case .contact:
                        backButton("Menu") { pane = .root }
                        ShopHonestEmpty(
                            title: "Contact",
                            bodyText: "Harare counter · WhatsApp via Live chat · nissangtrauto.co.zw/contact"
                        )
                        .padding()
                    }
                }
            }

            Divider().overlay(GTRColors.mist)
            HStack {
                Button { pane = .about } label: {
                    Label("About", systemImage: "info.circle")
                }
                Spacer()
                Button { pane = .contact } label: {
                    Label("Contact", systemImage: "phone")
                }
            }
            .font(GTRType.label(.subheadline))
            .foregroundStyle(GTRColors.steel)
            .padding(16)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(GTRColors.chalk.ignoresSafeArea())
    }

    private func sectionTitle(_ text: String) -> some View {
        Text(text)
            .font(GTRType.displaySemi(.title3))
            .foregroundStyle(GTRColors.steel)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
    }

    private func backButton(_ label: String, action: @escaping () -> Void) -> some View {
        Button("← \(label)", action: action)
            .font(GTRType.label(.subheadline))
            .foregroundStyle(GTRColors.primary)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 16)
            .padding(.top, 8)
    }

    private func menuRow(_ title: String, _ systemImage: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: systemImage)
                    .foregroundStyle(GTRColors.primary)
                    .frame(width: 22)
                Text(title)
                    .font(GTRType.body(.body))
                    .foregroundStyle(GTRColors.steel)
                Spacer()
                Image(systemName: "chevron.right")
                    .foregroundStyle(GTRColors.silverDim)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
        }
        .buttonStyle(.plain)
        Divider().overlay(GTRColors.mist)
    }
}

struct SettingsHubScreen: View {
    @EnvironmentObject private var session: StorefrontSession
    var onOpenAccount: () -> Void
    @State private var deleteMessage: String?
    @State private var showDeleteConfirm = false

    private let legalLinks: [(String, String)] = [
        ("Privacy Policy", "https://nissangtrauto.co.zw/privacy"),
        ("Terms of Service", "https://nissangtrauto.co.zw/terms"),
        ("Cookie Policy", "https://nissangtrauto.co.zw/cookies"),
        ("Returns Policy", "https://nissangtrauto.co.zw/returns"),
        ("Contact", "https://nissangtrauto.co.zw/contact"),
        ("About", "https://nissangtrauto.co.zw/about"),
        ("FAQ", "https://nissangtrauto.co.zw/faq"),
    ]

    var body: some View {
        ShopDefaultScreen(title: "Settings", subtitle: "Prefs · legal · account", onBack: nil) {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text(session.usesFake ? "Fake mode — auth optional" : "Live Supabase session")
                        .font(GTRType.body(.footnote))
                        .foregroundStyle(GTRColors.silverDim)
                    if let email = session.userEmail {
                        Text(email).font(GTRType.body(.body))
                    }

                    Text("Appearance").font(GTRType.displaySemi(.headline))
                    Picker("Theme", selection: $session.themeMode) {
                        ForEach(AppThemeMode.allCases) { mode in
                            Text(mode.label).tag(mode)
                        }
                    }
                    .pickerStyle(.segmented)

                    Text("Notifications").font(GTRType.displaySemi(.headline))
                    Toggle("Receive push notifications", isOn: $session.receivePush)
                    Text("Preference saved on device. Push delivery needs FCM wiring — not enabled yet.")
                        .font(GTRType.body(.caption))
                        .foregroundStyle(GTRColors.silverDim)

                    Text("Legal & help").font(GTRType.displaySemi(.headline))
                    ForEach(legalLinks, id: \.0) { title, url in
                        if let link = URL(string: url) {
                            Link(title, destination: link)
                                .font(GTRType.label(.body))
                        }
                    }

                    Text("Account").font(GTRType.displaySemi(.headline))
                    Button("My Account", action: onOpenAccount)
                        .font(GTRType.label(.body))
                    if session.isSignedIn && (!session.usesFake || session.userEmail != nil) {
                        Button("Sign out", role: .destructive) { session.signOut() }
                    }
                    Button("Delete account", role: .destructive) { showDeleteConfirm = true }
                    if let deleteMessage {
                        Text(deleteMessage)
                            .font(GTRType.body(.caption))
                            .foregroundStyle(GTRColors.silverDim)
                    }

                    let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "?"
                    let build = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "?"
                    Text("App version \(version) (\(build))")
                        .font(GTRType.body(.caption))
                        .foregroundStyle(GTRColors.silverDim)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(16)
            }
        }
        .confirmationDialog(
            "Delete account?",
            isPresented: $showDeleteConfirm,
            titleVisibility: .visible
        ) {
            Button("Contact support") {
                deleteMessage =
                    "Contact support to delete your account — no in-app delete RPC is available."
                if let url = URL(string: "https://nissangtrauto.co.zw/contact") {
                    UIApplication.shared.open(url)
                }
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("There is no customer delete-account endpoint yet. We will not pretend this succeeded.")
        }
    }
}

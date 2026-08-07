import SwiftUI

/// Flat icon-only shell action (no label).
struct ShellIconOnlyButton: View {
    var systemImage: String
    var contentDescription: String
    var onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            Image(systemName: systemImage)
                .font(.system(size: 22))
                .foregroundStyle(GTRColors.primary)
                .padding(.horizontal, 8)
                .padding(.vertical, 8)
                .accessibilityLabel(contentDescription)
        }
        .buttonStyle(.plain)
    }
}

/// Icon-over-label shell action — matches bottom-bar style; no rounded circle chrome.
struct ShellLabeledIconButton: View {
    var systemImage: String
    var label: String
    var onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            VStack(spacing: 2) {
                Image(systemName: systemImage)
                    .font(.system(size: 18))
                    .foregroundStyle(GTRColors.primary)
                Text(label)
                    .font(.system(size: 10))
                    .foregroundStyle(GTRColors.primary.opacity(0.85))
                    .lineLimit(1)
            }
            .frame(minWidth: 52)
            .padding(.horizontal, 6)
            .padding(.vertical, 4)
        }
        .buttonStyle(.plain)
    }
}

/// Customer storefront top strip — large logo LEFT; icon-only Menu / Account / Cart RIGHT.
/// Sign-in lives in Settings / Account (not the top bar).
struct CustomerShellTopBar: View {
    var cartBadgeCount: Int
    var onOpenMenu: () -> Void
    var onOpenAccount: () -> Void
    var onOpenCart: () -> Void

    var body: some View {
        HStack(spacing: 2) {
            GTRLogo(height: 76)
                .frame(maxHeight: 76, alignment: .leading)
                .padding(.leading, 2)
            Spacer(minLength: 4)
            ShellIconOnlyButton(
                systemImage: "line.3.horizontal",
                contentDescription: "Menu",
                onTap: onOpenMenu
            )
            ShellIconOnlyButton(
                systemImage: "person.crop.circle",
                contentDescription: "My Account",
                onTap: onOpenAccount
            )
            ZStack(alignment: .topTrailing) {
                ShellIconOnlyButton(
                    systemImage: "cart.fill",
                    contentDescription: "Cart",
                    onTap: onOpenCart
                )
                if cartBadgeCount > 0 {
                    Text(cartBadgeCount > 99 ? "99+" : "\(cartBadgeCount)")
                        .font(.system(size: 10).bold())
                        .foregroundStyle(.white)
                        .padding(.horizontal, 5)
                        .padding(.vertical, 2)
                        .background(GTRColors.primary, in: Capsule())
                        .offset(x: 6, y: -2)
                }
            }
        }
        .padding(.horizontal, 4)
        .padding(.vertical, 6)
        .background(GTRColors.chalk)
    }
}

enum HamburgerMenuAction: Equatable {
    case close
    case openAllCategories
    case openCategory(String)
}

struct MenuCategoryDef: Identifiable {
    var id: String { label }
    let label: String
    let systemImage: String
    let subs: [(String, String)]
}

let gtrCarPartCategories: [MenuCategoryDef] = [
    .init(label: "Service Parts", systemImage: "wrench.and.screwdriver.fill", subs: [
        ("Oil filters", "line.3.horizontal.decrease.circle"),
        ("Air filters", "wind"),
        ("Belts", "circle.circle"),
        ("Fluids", "drop.fill"),
    ]),
    .init(label: "Braking", systemImage: "brake.signal", subs: [
        ("Brake pads", "square.stack.3d.up"),
        ("Brake discs", "circle.dashed"),
        ("Calipers", "wrench"),
        ("Brake fluid", "drop"),
    ]),
    .init(label: "Steering & Suspension", systemImage: "car.side", subs: [
        ("Shock absorbers", "arrow.up.arrow.down"),
        ("Coil springs", "corkscrew"),
        ("Control arms", "wrench.adjustable"),
    ]),
    .init(label: "Engine Parts", systemImage: "engine.combustion", subs: [
        ("Gaskets", "square.on.square"),
        ("Timing belts", "circle.circle"),
        ("Sensors", "sensor.tag.radiowaves.forward"),
    ]),
    .init(label: "Transmission", systemImage: "gearshape.2", subs: [
        ("Clutch kits", "gearshape"),
        ("Flywheels", "circle"),
    ]),
    .init(label: "Electrical", systemImage: "bolt.fill", subs: [
        ("Batteries", "battery.100"),
        ("Alternators", "bolt.car"),
        ("Starters", "bolt.car"),
        ("Ignition", "flame"),
    ]),
    .init(label: "Lighting", systemImage: "lightbulb.fill", subs: [
        ("Headlamp bulbs", "light.max"),
        ("LED kits", "lightbulb"),
    ]),
    .init(label: "Body & Exhaust", systemImage: "car.fill", subs: [
        ("Exhaust systems", "smoke"),
        ("Body panels", "rectangle.split.3x3"),
    ]),
    .init(label: "Cooling & Heating", systemImage: "thermometer.medium", subs: [
        ("Radiators", "fan"),
        ("Water pumps", "drop.triangle"),
        ("Thermostats", "thermometer"),
    ]),
    .init(label: "Fuel System", systemImage: "fuelpump.fill", subs: [
        ("Fuel injectors", "fuelpump"),
        ("Fuel filters", "line.3.horizontal.decrease"),
    ]),
]

struct RootMenuDef: Identifiable {
    var id: String { label }
    let label: String
    let systemImage: String
    let kind: RootKind
    enum RootKind { case carParts, emptySoon, deals }
}

let gtrRootMenu: [RootMenuDef] = [
    .init(label: "Car Parts", systemImage: "wrench.and.screwdriver", kind: .carParts),
    .init(label: "Accessories", systemImage: "shippingbox", kind: .emptySoon),
    .init(label: "Detailing", systemImage: "sparkles", kind: .emptySoon),
    .init(label: "Tools", systemImage: "hammer", kind: .emptySoon),
    .init(label: "Service Kits", systemImage: "toolbox", kind: .emptySoon),
    .init(label: "Engine Oils", systemImage: "oilcan", kind: .emptySoon),
    .init(label: "Car Batteries", systemImage: "battery.100", kind: .emptySoon),
    .init(label: "Wiper Blades", systemImage: "drop", kind: .emptySoon),
    .init(label: "Deals", systemImage: "tag", kind: .deals),
    .init(label: "Shop By Brand", systemImage: "car", kind: .emptySoon),
]

struct HamburgerMenuOverlay: View {
    var onAction: (HamburgerMenuAction) -> Void

    private enum Pane {
        case root, carParts, category(MenuCategoryDef), deals, about, contact, store
    }

    @State private var pane: Pane = .root

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                GTRLogo(height: 36)
                Spacer()
                Button { onAction(.close) } label: {
                    Image(systemName: "xmark")
                        .foregroundStyle(GTRColors.primary)
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)

            Divider().overlay(GTRColors.mist)

            ScrollView {
                VStack(spacing: 0) {
                    switch pane {
                    case .root:
                        ForEach(gtrRootMenu) { item in
                            menuRow(item.label, item.systemImage) {
                                switch item.kind {
                                case .carParts: pane = .carParts
                                case .deals: pane = .deals
                                case .emptySoon: onAction(.openCategory(item.label))
                                }
                            }
                        }
                    case .carParts:
                        backButton("Menu") { pane = .root }
                        sectionTitle("Car Parts")
                        menuRow("All car parts", "square.grid.2x2") {
                            onAction(.openAllCategories)
                        }
                        ForEach(gtrCarPartCategories) { cat in
                            menuRow(cat.label, cat.systemImage) { pane = .category(cat) }
                        }
                    case .category(let cat):
                        backButton("Car Parts") { pane = .carParts }
                        sectionTitle(cat.label)
                        menuRow("All \(cat.label)", cat.systemImage) { onAction(.openCategory(cat.label)) }
                        ForEach(cat.subs, id: \.0) { sub in
                            menuRow(sub.0, sub.1) { onAction(.openCategory(sub.0)) }
                        }
                    case .deals:
                        backButton("Menu") { pane = .root }
                        ShopHonestEmpty(title: "No deals feed yet", bodyText: "No deals.")
                            .padding()
                    case .about:
                        backButton("Menu") { pane = .root }
                        ShopHonestEmpty(title: "About Nissan GTR Auto", bodyText: "Genuine Nissan parts · Harare counter & nationwide dispatch.")
                            .padding()
                    case .contact:
                        backButton("Menu") { pane = .root }
                        ShopHonestEmpty(title: "Contact", bodyText: "Harare counter · nissangtrauto.co.zw/contact")
                            .padding()
                    case .store:
                        backButton("Menu") { pane = .root }
                        ShopHonestEmpty(title: "Store locator", bodyText: "Harare counter.")
                            .padding()
                    }
                }
            }

            Divider().overlay(GTRColors.mist)
            VStack(alignment: .leading, spacing: 8) {
                Button { pane = .store } label: { Label("Store Locator", systemImage: "mappin.and.ellipse") }
                Button { pane = .about } label: { Label("About Us", systemImage: "info.circle") }
                Button { pane = .contact } label: { Label("Contact Us", systemImage: "phone") }
            }
            .font(GTRType.label(.subheadline))
            .foregroundStyle(GTRColors.steel)
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
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
        Button("â† \(label)", action: action)
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
                    .frame(width: 24)
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
    var onSignIn: (() -> Void)? = nil
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
        // Body-only under TabView — avoid nested ShopDefaultScreen scroll/chrome crashes.
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("Settings")
                    .font(GTRType.displaySemi(.title2))
                    .foregroundStyle(GTRColors.steel)

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

                Text("Legal & help").font(GTRType.displaySemi(.headline))
                ForEach(legalLinks, id: \.0) { title, url in
                    if let link = URL(string: url) {
                        Link(title, destination: link)
                            .font(GTRType.label(.body))
                    }
                }

                Text("Account").font(GTRType.displaySemi(.headline))
                if session.userEmail == nil, let onSignIn {
                    Button("Sign in", action: onSignIn)
                        .font(GTRType.label(.body))
                }
                NavigationLink("Edit profile") { EditProfileScreen() }
                    .font(GTRType.label(.body))
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
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .background(GTRColors.chalk.ignoresSafeArea())
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

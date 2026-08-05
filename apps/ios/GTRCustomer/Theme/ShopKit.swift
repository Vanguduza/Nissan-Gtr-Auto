import SwiftUI

/// Shopping-By-KMP ShopKit — SwiftUI port of Phase A `packages/android-ui/.../shop`
/// (MIT © 2023 Mahdi Razzaghi Ghaleh / Shopping-By-KMP). GTR branded.
///
/// Genuine KMP visual language: chalk body, circle toolbar chrome (`ShopTopBar`),
/// elevated bottom-tab feel, ProductBox cards, sticky PDP/cart CTAs, splash.
/// **Not** steel `GTRBrandBar` / Jetsnack leftovers renamed.

// MARK: - Theme entry

struct ShopTheme<Content: View>: View {
    @ViewBuilder var content: () -> Content

    var body: some View {
        content()
            .tint(GTRColors.primary)
            .background(GTRColors.chalk.ignoresSafeArea())
    }
}

extension View {
    func shopTheme() -> some View {
        ShopTheme { self }
    }
}

// MARK: - Splash (KMP SplashScreen — expanding primary circle + brand reveal)

struct ShopSplash: View {
    var brand: String = "Nissan GTR Auto"
    var tagline: String = "Genuine parts · Harare & nationwide"
    var holdMs: UInt64 = 2_200_000_000
    var onFinished: () -> Void

    @State private var showBrand = false
    @State private var circleScale: CGFloat = 0.2

    var body: some View {
        ZStack {
            GTRColors.chalk.ignoresSafeArea()

            Circle()
                .fill(GTRColors.primary)
                .frame(width: 280, height: 280)
                .scaleEffect(circleScale)
                .offset(y: -180)
                .opacity(0.95)

            VStack(spacing: 16) {
                if showBrand {
                    GTRLogo(height: 48)
                        .frame(maxWidth: 160)
                        .transition(.opacity)
                    Text(brand)
                        .font(GTRType.displaySemi(.title2))
                        .foregroundStyle(GTRColors.steel)
                    Text(tagline)
                        .font(GTRType.body(.subheadline))
                        .foregroundStyle(GTRColors.silverDim)
                        .multilineTextAlignment(.center)
                }
                ProgressView()
                    .tint(GTRColors.primary)
                    .padding(.top, 24)
            }
            .padding(24)
        }
        .onAppear {
            withAnimation(.easeOut(duration: 0.9)) {
                circleScale = 2.4
            }
            withAnimation(.easeIn(duration: 0.45).delay(1.0)) {
                showBrand = true
            }
            Task {
                try? await Task.sleep(nanoseconds: holdMs)
                onFinished()
            }
        }
    }
}

// MARK: - Top bar (KMP DefaultScreenUI — circle actions, chalk — NOT steel brand bar)

struct ShopTopBar: View {
    let title: String
    var subtitle: String? = nil
    var onBack: (() -> Void)? = nil

    var body: some View {
        HStack(alignment: .center, spacing: 8) {
            if let onBack {
                ShopCircleIconButton(systemImage: "chevron.left", onTap: onBack)
            } else {
                Color.clear.frame(width: 50, height: 50)
            }

            VStack(spacing: 2) {
                Text(title)
                    .font(GTRType.displaySemi(.title3))
                    .foregroundStyle(GTRColors.steel)
                    .lineLimit(1)
                if let subtitle {
                    Text(subtitle)
                        .font(GTRType.body(.caption))
                        .foregroundStyle(GTRColors.silverDim)
                        .lineLimit(1)
                }
            }
            .frame(maxWidth: .infinity)

            Color.clear.frame(width: 50, height: 50)
        }
        .padding(16)
        .background(GTRColors.chalk)
    }
}

// MARK: - Screen chrome

/// Full-page shell — KMP DefaultScreenUI: chalk + circle toolbar (no steel bar).
struct ShopDefaultScreen<Content: View>: View {
    let title: String
    var subtitle: String? = nil
    var onBack: (() -> Void)? = nil
    var scrollable: Bool = true
    var loading: Bool = false
    @ViewBuilder var content: () -> Content

    var body: some View {
        VStack(spacing: 0) {
            ShopTopBar(title: title, subtitle: subtitle, onBack: onBack)
            ZStack {
                if scrollable {
                    ScrollView {
                        VStack(alignment: .leading, spacing: 16) {
                            content()
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 8)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                } else {
                    content()
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
                }
                if loading {
                    ProgressView()
                        .tint(GTRColors.primary)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .background(GTRColors.chalk.opacity(0.55))
                }
            }
        }
        .background(GTRColors.chalk.ignoresSafeArea())
    }
}

/// Root tab body — no steel brand bar (KMP MainNav tab content).
struct ShopTabBody<Content: View>: View {
    @ViewBuilder var content: () -> Content

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                content()
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .background(GTRColors.chalk.ignoresSafeArea())
    }
}

struct ShopSectionHeader: View {
    let title: String
    var actionLabel: String? = nil
    var onAction: (() -> Void)? = nil

    var body: some View {
        HStack(spacing: 8) {
            Rectangle()
                .fill(GTRColors.primary)
                .frame(width: 3, height: 16)
            Text(title)
                .font(GTRType.displaySemi(.title3))
                .foregroundStyle(GTRColors.steel)
            Spacer()
            if let actionLabel, let onAction {
                Button(actionLabel, action: onAction)
                    .font(GTRType.label(.caption))
                    .tint(GTRColors.primary)
            }
        }
        .padding(.vertical, 4)
    }
}

struct ShopHonestEmpty: View {
    let title: String
    var bodyText: String? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(GTRType.displaySemi(.headline))
                .foregroundStyle(GTRColors.steel)
            if let bodyText {
                Text(bodyText)
                    .font(GTRType.body(.subheadline))
                    .foregroundStyle(GTRColors.silverDim)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(GTRColors.mist.opacity(0.5), in: RoundedRectangle(cornerRadius: GTRRadius.sharp))
    }
}

struct ShopStatusChip: View {
    let text: String
    var tone: Tone = .neutral

    enum Tone {
        case neutral, success, warning, danger

        var foreground: Color {
            switch self {
            case .neutral: return GTRColors.steel
            case .success: return GTRColors.accent
            case .warning: return GTRColors.warning
            case .danger: return GTRColors.primary
            }
        }

        var background: Color {
            switch self {
            case .neutral: return GTRColors.mist
            case .success: return GTRColors.accent.opacity(0.12)
            case .warning: return GTRColors.warning.opacity(0.12)
            case .danger: return GTRColors.primary.opacity(0.12)
            }
        }
    }

    var body: some View {
        Text(text)
            .font(GTRType.label(.caption2))
            .foregroundStyle(tone.foreground)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(tone.background, in: RoundedRectangle(cornerRadius: GTRRadius.sharp))
    }
}

// MARK: - Circle button (KMP CircleButton — bordered Card circle)

struct ShopCircleIconButton: View {
    let systemImage: String
    var onTap: () -> Void

    private let border = Color(red: 0xDB / 255, green: 0xDB / 255, blue: 0xDC / 255)

    var body: some View {
        Button(action: onTap) {
            Image(systemName: systemImage)
                .font(.system(size: 18, weight: .medium))
                .foregroundStyle(GTRColors.steel)
                .frame(width: 50, height: 50)
                .background(GTRColors.chalk, in: Circle())
                .overlay(Circle().stroke(border, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}

struct ShopHomeHeaderActions: View {
    var onNotifications: () -> Void = {}
    var onSettings: () -> Void = {}

    var body: some View {
        HStack(spacing: 8) {
            ShopCircleIconButton(systemImage: "bell", onTap: onNotifications)
            ShopCircleIconButton(systemImage: "gearshape", onTap: onSettings)
        }
    }
}

struct ShopBannerCarousel: View {
    let banners: [String]
    @State private var page = 0

    var body: some View {
        if banners.isEmpty { EmptyView() }
        else {
            VStack(spacing: 8) {
                TabView(selection: $page) {
                    ForEach(Array(banners.enumerated()), id: \.offset) { index, text in
                        Text(text)
                            .font(GTRType.displaySemi(.subheadline))
                            .foregroundStyle(GTRColors.primaryInk)
                            .multilineTextAlignment(.leading)
                            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
                            .padding(16)
                            .background(GTRColors.steel, in: RoundedRectangle(cornerRadius: GTRRadius.control))
                            .padding(.horizontal, 2)
                            .tag(index)
                    }
                }
                .frame(height: 96)
                .tabViewStyle(.page(indexDisplayMode: .never))

                if banners.count > 1 {
                    HStack(spacing: 6) {
                        ForEach(0..<banners.count, id: \.self) { i in
                            Circle()
                                .fill(i == page ? GTRColors.primary : GTRColors.silver)
                                .frame(width: 6, height: 6)
                        }
                    }
                    .frame(maxWidth: .infinity)
                }
            }
        }
    }
}

struct ShopCategoryBox: View {
    let label: String
    var onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            VStack(spacing: 6) {
                Circle()
                    .fill(GTRColors.primary.opacity(0.12))
                    .frame(width: 56, height: 56)
                    .overlay {
                        Text(String(label.prefix(1)).uppercased())
                            .font(GTRType.displaySemi(.title3))
                            .foregroundStyle(GTRColors.primary)
                    }
                Text(label)
                    .font(GTRType.label(.caption2))
                    .foregroundStyle(GTRColors.steel)
                    .lineLimit(2)
                    .multilineTextAlignment(.center)
                    .frame(width: 72)
            }
        }
        .buttonStyle(.plain)
    }
}

struct ShopMerchTitleRow: View {
    let title: String
    var actionLabel: String? = "See all"
    var onAction: (() -> Void)? = nil

    var body: some View {
        ShopSectionHeader(title: title, actionLabel: actionLabel, onAction: onAction)
    }
}

struct ShopRatingRow: View {
    var ratingLabel: String

    var body: some View {
        HStack(spacing: 4) {
            Image(systemName: "star.fill")
                .font(.caption)
                .foregroundStyle(GTRColors.warning)
            Text(ratingLabel)
                .font(GTRType.body(.caption))
                .foregroundStyle(GTRColors.silverDim)
        }
    }
}

struct ShopExpandableDescription: View {
    let text: String
    @State private var expanded = false

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(text)
                .font(GTRType.body(.subheadline))
                .foregroundStyle(GTRColors.steel)
                .lineLimit(expanded ? nil : 3)
            if text.count > 120 {
                Button(expanded ? "Show less" : "Read more") { expanded.toggle() }
                    .font(GTRType.label(.caption))
                    .tint(GTRColors.primary)
            }
        }
    }
}

// MARK: - Search / location

struct ShopSearchBar: View {
    let placeholder: String
    var onTap: (() -> Void)? = nil
    @Binding var text: String

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass")
                .foregroundStyle(GTRColors.primary)
            if let onTap {
                Text(text.isEmpty ? placeholder : text)
                    .font(GTRType.body(.callout))
                    .foregroundStyle(text.isEmpty ? GTRColors.silverDim : GTRColors.steel)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .contentShape(Rectangle())
                    .onTapGesture(perform: onTap)
            } else {
                TextField(placeholder, text: $text)
                    .font(GTRType.body(.callout))
                    .textInputAutocapitalization(.characters)
            }
        }
        .padding(.horizontal, 12)
        .frame(height: 48)
        .background(
            RoundedRectangle(cornerRadius: GTRRadius.sharp)
                .stroke(GTRColors.silver, lineWidth: 1)
                .background(Color.white, in: RoundedRectangle(cornerRadius: GTRRadius.sharp))
        )
    }
}

struct ShopLocationRow: View {
    let label: String
    let locationText: String
    var onTap: (() -> Void)? = nil

    var body: some View {
        Button {
            onTap?()
        } label: {
            VStack(alignment: .leading, spacing: 4) {
                Text(label)
                    .font(GTRType.label(.caption2))
                    .foregroundStyle(GTRColors.silverDim)
                HStack(spacing: 4) {
                    Image(systemName: "mappin.and.ellipse")
                        .foregroundStyle(GTRColors.primary)
                        .font(.caption)
                    Text(locationText)
                        .font(GTRType.label(.caption))
                        .foregroundStyle(GTRColors.steel)
                        .lineLimit(1)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .buttonStyle(.plain)
        .disabled(onTap == nil)
    }
}

// MARK: - Product card / rails (KMP ProductBox)

struct ShopProductCard: View {
    let title: String
    var subtitle: String? = nil
    let priceLabel: String
    var stockLabel: String? = nil
    var ratingLabel: String? = nil
    var liked: Bool = false
    var onLike: (() -> Void)? = nil
    var onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: 6) {
                ZStack(alignment: .topTrailing) {
                    RoundedRectangle(cornerRadius: GTRRadius.control)
                        .fill(GTRColors.mist)
                        .frame(height: 130)
                        .overlay {
                            Text(title.prefix(14))
                                .font(GTRType.label(.caption))
                                .foregroundStyle(GTRColors.steel)
                                .multilineTextAlignment(.center)
                                .padding(8)
                        }
                    if let onLike {
                        Button(action: onLike) {
                            Image(systemName: liked ? "heart.fill" : "heart")
                                .font(.system(size: 14))
                                .foregroundStyle(GTRColors.primary)
                                .padding(6)
                                .background(GTRColors.chalk, in: Circle())
                        }
                        .buttonStyle(.plain)
                        .padding(8)
                    }
                }
                HStack {
                    Text(title)
                        .font(GTRType.body(.subheadline))
                        .foregroundStyle(GTRColors.steel)
                        .lineLimit(1)
                    Spacer(minLength: 4)
                    if let ratingLabel {
                        ShopRatingRow(ratingLabel: ratingLabel)
                    }
                }
                if let subtitle {
                    Text(subtitle)
                        .font(GTRType.body(.caption))
                        .foregroundStyle(GTRColors.silverDim)
                        .lineLimit(2)
                }
                HStack {
                    Text(priceLabel)
                        .font(GTRType.labelBold(.caption))
                        .foregroundStyle(GTRColors.accent)
                    Spacer(minLength: 4)
                    if let stockLabel {
                        ShopStatusChip(
                            text: stockLabel,
                            tone: stockLabel.lowercased().contains("stock") ? .success : .warning
                        )
                    }
                }
            }
            .frame(width: 168, alignment: .leading)
            .padding(6)
        }
        .buttonStyle(.plain)
    }
}

struct ShopHorizontalRail<Item: Identifiable, Content: View>: View {
    let items: [Item]
    @ViewBuilder var content: (Item) -> Content

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(alignment: .top, spacing: 4) {
                ForEach(items) { item in
                    content(item)
                }
            }
            .padding(.horizontal, 4)
        }
    }
}

// MARK: - Profile / list rows

struct ShopProfileItemBox: View {
    let title: String
    let systemImage: String
    var isLast: Bool = false
    var onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            VStack(spacing: 0) {
                HStack(spacing: 14) {
                    Image(systemName: systemImage)
                        .font(.system(size: 18))
                        .foregroundStyle(GTRColors.primary)
                        .frame(width: 28)
                    Text(title)
                        .font(GTRType.body(.body))
                        .foregroundStyle(GTRColors.steel)
                    Spacer()
                    Image(systemName: "chevron.right")
                        .font(.caption)
                        .foregroundStyle(GTRColors.silverDim)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 14)
                if !isLast {
                    Divider().overlay(GTRColors.mist)
                }
            }
            .background(Color.white)
        }
        .buttonStyle(.plain)
    }
}

struct ShopListCard<Content: View>: View {
    @ViewBuilder var content: () -> Content

    var body: some View {
        content()
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.white, in: RoundedRectangle(cornerRadius: GTRRadius.control))
            .overlay(
                RoundedRectangle(cornerRadius: GTRRadius.control)
                    .stroke(GTRColors.mist, lineWidth: 1)
            )
    }
}

// MARK: - Cart / checkout

struct ShopCartLineRow: View {
    let oem: String
    let title: String
    let priceLabel: String
    let qty: String
    var onTap: (() -> Void)? = nil
    var onAddQty: (() -> Void)? = nil

    var body: some View {
        HStack(alignment: .center, spacing: 12) {
            RoundedRectangle(cornerRadius: GTRRadius.control)
                .fill(GTRColors.mist)
                .frame(width: 72, height: 72)
                .overlay {
                    Text(oem.prefix(8))
                        .font(GTRType.label(.caption2))
                        .foregroundStyle(GTRColors.steel)
                        .multilineTextAlignment(.center)
                        .padding(4)
                }
                .onTapGesture { onTap?() }

            VStack(alignment: .leading, spacing: 4) {
                Text(oem)
                    .font(GTRType.displaySemi(.subheadline))
                    .foregroundStyle(GTRColors.steel)
                    .lineLimit(1)
                Text(title)
                    .font(GTRType.body(.caption))
                    .foregroundStyle(GTRColors.silverDim)
                    .lineLimit(2)
                Text(priceLabel)
                    .font(GTRType.labelBold(.caption))
                    .foregroundStyle(GTRColors.accent)
            }
            Spacer(minLength: 4)
            HStack(spacing: 6) {
                Text(qty)
                    .font(GTRType.label(.caption))
                    .frame(minWidth: 20)
                if let onAddQty {
                    Button(action: onAddQty) {
                        Image(systemName: "plus")
                            .font(.caption.bold())
                            .foregroundStyle(GTRColors.primaryInk)
                            .frame(width: 26, height: 26)
                            .background(GTRColors.primary, in: RoundedRectangle(cornerRadius: GTRRadius.sharp))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .padding(.vertical, 8)
    }
}

struct ShopProceedButtonBox: View {
    let totalLabel: String
    let ctaTitle: String
    var enabled: Bool = true
    var onCta: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            Divider().overlay(GTRColors.mist)
            VStack(spacing: 12) {
                HStack {
                    Text("Total")
                        .font(GTRType.body(.subheadline))
                        .foregroundStyle(GTRColors.steel)
                    Spacer()
                    Text(totalLabel)
                        .font(GTRType.displaySemi(.title3))
                        .foregroundStyle(GTRColors.steel)
                }
                ShopPrimaryButton(title: ctaTitle, enabled: enabled, onTap: onCta)
            }
            .padding(16)
            .background(Color.white)
        }
    }
}

struct ShopPdpBuyBar: View {
    let priceLabel: String
    let ctaTitle: String
    var enabled: Bool = true
    var onCta: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text("Total price")
                    .font(GTRType.label(.caption))
                    .foregroundStyle(GTRColors.silverDim)
                Text(priceLabel)
                    .font(GTRType.displaySemi(.title3))
                    .foregroundStyle(GTRColors.steel)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Button(action: onCta) {
                Text(ctaTitle)
                    .font(GTRType.labelBold(.body))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
            }
            .buttonStyle(.borderedProminent)
            .tint(GTRColors.primary)
            .disabled(!enabled)
            .frame(maxWidth: .infinity)
        }
        .padding(16)
        .background(Color.white)
        .overlay(alignment: .top) { Divider().overlay(GTRColors.mist) }
    }
}

struct ShopImageGalleryStrip: View {
    let items: [String]
    var selected: String
    var onSelect: (String) -> Void

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(items, id: \.self) { item in
                    Button {
                        onSelect(item)
                    } label: {
                        RoundedRectangle(cornerRadius: GTRRadius.sharp)
                            .fill(selected == item ? GTRColors.primary.opacity(0.2) : GTRColors.mist)
                            .frame(width: 56, height: 56)
                            .overlay {
                                Text(item.prefix(4))
                                    .font(GTRType.label(.caption2))
                                    .foregroundStyle(GTRColors.steel)
                            }
                            .overlay {
                                if selected == item {
                                    RoundedRectangle(cornerRadius: GTRRadius.sharp)
                                        .stroke(GTRColors.primary, lineWidth: 2)
                                }
                            }
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(8)
        }
        .background(Color.white, in: RoundedRectangle(cornerRadius: GTRRadius.control))
    }
}

struct ShopStickyCtaBar: View {
    let primaryTitle: String
    var primaryEnabled: Bool = true
    var onPrimary: () -> Void
    var secondaryTitle: String? = nil
    var onSecondary: (() -> Void)? = nil

    var body: some View {
        VStack(spacing: 8) {
            Divider().overlay(GTRColors.mist)
            HStack(spacing: 10) {
                if let secondaryTitle, let onSecondary {
                    Button(secondaryTitle, action: onSecondary)
                        .buttonStyle(.bordered)
                        .tint(GTRColors.steel)
                }
                Button(action: onPrimary) {
                    Text(primaryTitle)
                        .font(GTRType.labelBold(.body))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                }
                .buttonStyle(.borderedProminent)
                .tint(GTRColors.primary)
                .disabled(!primaryEnabled)
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 8)
        }
        .background(GTRColors.chalk)
    }
}

struct ShopAddressPickerRow: View {
    let address: CustomerAddress
    let selected: Bool
    var onSelect: () -> Void

    var body: some View {
        Button(action: onSelect) {
            HStack(alignment: .top, spacing: 12) {
                Image(systemName: selected ? "checkmark.circle.fill" : "circle")
                    .foregroundStyle(selected ? GTRColors.primary : GTRColors.silverDim)
                VStack(alignment: .leading, spacing: 4) {
                    HStack {
                        Text(address.label.isEmpty ? "Address" : address.label)
                            .font(GTRType.labelBold(.subheadline))
                            .foregroundStyle(GTRColors.steel)
                        if address.isDefault {
                            ShopStatusChip(text: "Default", tone: .success)
                        }
                    }
                    Text(address.line1)
                        .font(GTRType.body(.caption))
                        .foregroundStyle(GTRColors.silverDim)
                    if let city = address.city {
                        Text([city, address.province].compactMap { $0 }.joined(separator: ", "))
                            .font(GTRType.body(.caption2))
                            .foregroundStyle(GTRColors.silverDim)
                    }
                }
                Spacer()
            }
            .padding(12)
            .background(
                RoundedRectangle(cornerRadius: GTRRadius.sharp)
                    .stroke(selected ? GTRColors.primary : GTRColors.mist, lineWidth: selected ? 2 : 1)
                    .background(Color.white, in: RoundedRectangle(cornerRadius: GTRRadius.sharp))
            )
        }
        .buttonStyle(.plain)
    }
}

struct ShopPrimaryButton: View {
    let title: String
    var enabled: Bool = true
    var onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            Text(title)
                .font(GTRType.labelBold(.body))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
        }
        .buttonStyle(.borderedProminent)
        .tint(GTRColors.primary)
        .disabled(!enabled)
    }
}

struct ShopProfileAvatar: View {
    var email: String?
    var usesFake: Bool
    var large: Bool = false

    var body: some View {
        VStack(spacing: 12) {
            ZStack {
                Circle()
                    .fill(GTRColors.steelLift)
                    .frame(width: large ? 96 : 56, height: large ? 96 : 56)
                Text(initials)
                    .font(large ? GTRType.displaySemi(.largeTitle) : GTRType.displaySemi(.title3))
                    .foregroundStyle(GTRColors.primaryInk)
            }
            VStack(spacing: 4) {
                Text(email ?? (usesFake ? "Guest (Fake)" : "Customer"))
                    .font(large ? GTRType.displaySemi(.title2) : GTRType.displaySemi(.headline))
                    .foregroundStyle(GTRColors.steel)
                Text(usesFake ? "FakeStorefrontApi" : "Live · ContiPay / Paynow / EcoCash")
                    .font(GTRType.body(.caption))
                    .foregroundStyle(GTRColors.silverDim)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, large ? 24 : 16)
    }

    private var initials: String {
        guard let email, let first = email.first else { return "G" }
        return String(first).uppercased()
    }
}

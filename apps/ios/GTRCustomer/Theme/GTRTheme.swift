import SwiftUI



/// Hex values mirror `packages/ui/brand-tokens.json` / `@gtr/ui`.

enum GTRColors {

    static let primary = Color(red: 0xC8 / 255, green: 0x10 / 255, blue: 0x2E / 255)

    static let primaryHover = Color(red: 0xE0 / 255, green: 0x12 / 255, blue: 0x34 / 255)

    static let primaryInk = Color.white

    static let steel = Color(red: 0x12 / 255, green: 0x15 / 255, blue: 0x1C / 255)

    static let steelLift = Color(red: 0x1E / 255, green: 0x24 / 255, blue: 0x30 / 255)

    static let silver = Color(red: 0xC0 / 255, green: 0xC5 / 255, blue: 0xCE / 255)

    static let silverDim = Color(red: 0x8B / 255, green: 0x92 / 255, blue: 0x9E / 255)

    static let mist = Color(red: 0xE8 / 255, green: 0xEC / 255, blue: 0xF1 / 255)

    static let chalk = Color(red: 0xF4 / 255, green: 0xF5 / 255, blue: 0xF7 / 255)

    static let accent = Color(red: 0x0B / 255, green: 0x6E / 255, blue: 0x4F / 255)

    static let warning = Color(red: 0xB4 / 255, green: 0x53 / 255, blue: 0x09 / 255)

    static let usd = accent

    static let zig = warning

}



enum GTRRadius {

    static let sharp: CGFloat = 2

    static let control: CGFloat = 8

    static let staff: CGFloat = 10

}



/**

 * Typography: bundled OFL Titillium Web (display) + Source Sans 3 (body).

 * Faces registered via `UIAppFonts` in Info.plist — see packages/ui/fonts/ATTRIBUTION.md.

 */

enum GTRType {

    /// PostScript-style names matching bundled TTF files.

    private static let displayRegular = "TitilliumWeb-Regular"

    private static let displaySemiBold = "TitilliumWeb-SemiBold"

    private static let displayBold = "TitilliumWeb-Bold"

    private static let bodyRegular = "SourceSans3-Regular"

    private static let bodySemiBold = "SourceSans3-Semibold"

    private static let bodyBold = "SourceSans3-Bold"



    static func display(_ style: Font.TextStyle = .title2) -> Font {

        .custom(displayBold, size: size(for: style), relativeTo: style)

    }



    static func displaySemi(_ style: Font.TextStyle = .title3) -> Font {

        .custom(displaySemiBold, size: size(for: style), relativeTo: style)

    }



    static func body(_ style: Font.TextStyle = .body) -> Font {

        .custom(bodyRegular, size: size(for: style), relativeTo: style)

    }



    static func label(_ style: Font.TextStyle = .caption) -> Font {

        .custom(bodySemiBold, size: size(for: style), relativeTo: style)

    }



    static func labelBold(_ style: Font.TextStyle = .caption) -> Font {

        .custom(bodyBold, size: size(for: style), relativeTo: style)

    }



    private static func size(for style: Font.TextStyle) -> CGFloat {

        switch style {

        case .largeTitle: return 34

        case .title: return 28

        case .title2: return 22

        case .title3: return 20

        case .headline: return 17

        case .body: return 17

        case .callout: return 16

        case .subheadline: return 15

        case .footnote: return 13

        case .caption: return 12

        case .caption2: return 11

        @unknown default: return 17

        }

    }

}



struct GTRThemeModifier: ViewModifier {

    func body(content: Content) -> some View {

        content

            .tint(GTRColors.primary)

            .background(GTRColors.chalk.ignoresSafeArea())

    }

}



extension View {

    /// Apply GTR brand tint + chalk ground (web parity).

    func gtrTheme() -> some View {

        modifier(GTRThemeModifier())

    }

}



/// Official logo — `Assets.xcassets/BrandLogo` (mirrors web `public/brand/logo.png`).

struct GTRLogo: View {
    var height: CGFloat = 36

    var body: some View {
        Image("BrandLogo")
            .resizable()
            .scaledToFit()
            .frame(height: height, alignment: .leading)
            .accessibilityLabel("Nissan GTR Auto")
    }
}



/// Legacy steel header — **deprecated for customer ShopKit**.
/// Use `ShopTopBar` / `ShopDefaultScreen` (KMP circle chrome). Kept for rare
/// non-ShopKit hosts only; do not add new call sites in GTRCustomer Features.

struct GTRBrandBar: View {

    let title: String

    var subtitle: String? = nil

    var showLogo: Bool = true



    var body: some View {

        VStack(spacing: 0) {

            LinearGradient(

                colors: [GTRColors.primary, GTRColors.steelLift],

                startPoint: .leading,

                endPoint: .trailing

            )

            .frame(height: 3)

            HStack(spacing: 12) {

                if showLogo {

                    GTRLogo(height: 36)

                        .frame(maxWidth: 120, alignment: .leading)

                }

                VStack(alignment: .leading, spacing: 2) {

                    Text(title)

                        .font(GTRType.displaySemi(.title3))

                        .foregroundStyle(GTRColors.primaryInk)

                        .tracking(0.4)

                    if let subtitle {

                        Text(subtitle)

                            .font(GTRType.body(.caption))

                            .foregroundStyle(GTRColors.silver)

                    }

                }

                Spacer()

            }

            .padding(.horizontal, 16)

            .padding(.vertical, 14)

            .background(

                LinearGradient(

                    colors: [GTRColors.steelLift, GTRColors.steel],

                    startPoint: .top,

                    endPoint: .bottom

                )

            )

        }

    }

}



struct GTRSectionLabel: View {

    let text: String



    var body: some View {

        HStack(spacing: 8) {

            Rectangle()

                .fill(GTRColors.primary)

                .frame(width: 3, height: 14)

            Text(text.uppercased())

                .font(GTRType.label(.caption))

                .tracking(0.8)

                .foregroundStyle(GTRColors.steel)

            Spacer()

        }

        Divider().overlay(GTRColors.mist)

    }

}



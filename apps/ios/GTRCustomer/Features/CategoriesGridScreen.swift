import SwiftUI

struct CatalogCategoryCard: Identifiable {
    var id: String { label }
    let label: String
    let systemImage: String
}

let defaultCatalogCategoryCards: [CatalogCategoryCard] = [
    .init(label: "Service Parts", systemImage: "wrench.and.screwdriver.fill"),
    .init(label: "Braking", systemImage: "brake.signal"),
    .init(label: "Steering & Suspension", systemImage: "car.side"),
    .init(label: "Engine Parts", systemImage: "engine.combustion"),
    .init(label: "Transmission", systemImage: "gearshape.2"),
    .init(label: "Electrical", systemImage: "bolt.fill"),
    .init(label: "Lighting", systemImage: "lightbulb.fill"),
    .init(label: "Body & Exhaust", systemImage: "car.fill"),
    .init(label: "Cooling & Heating", systemImage: "thermometer.medium"),
    .init(label: "Fuel System", systemImage: "fuelpump.fill"),
]

/// All Car Parts grid — navigates to live category PLP (no empty-only dialogs).
struct CategoriesGridScreen: View {
    var onBack: () -> Void
    var onSelectCategory: (String) -> Void

    private let columns = [GridItem(.flexible()), GridItem(.flexible())]

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Button(action: onBack) {
                    Image(systemName: "chevron.left")
                        .foregroundStyle(GTRColors.primary)
                }
                Text("All Car Parts")
                    .font(GTRType.displaySemi(.title2))
                Spacer()
            }
            .padding(16)

            ScrollView {
                LazyVGrid(columns: columns, spacing: 12) {
                    ForEach(defaultCatalogCategoryCards) { card in
                        Button {
                            onSelectCategory(card.label)
                        } label: {
                            VStack(spacing: 0) {
                                Rectangle()
                                    .fill(GTRColors.primary)
                                    .frame(height: 4)
                                VStack(spacing: 12) {
                                    Text(card.label)
                                        .font(GTRType.displaySemi(.subheadline))
                                        .foregroundStyle(GTRColors.steel)
                                        .multilineTextAlignment(.center)
                                    Image(systemName: card.systemImage)
                                        .font(.system(size: 40))
                                        .foregroundStyle(GTRColors.silverDim)
                                        .frame(maxWidth: .infinity)
                                        .frame(height: 100)
                                        .background(GTRColors.mist)
                                }
                                .padding(12)
                            }
                            .background(Color.white)
                            .overlay(
                                RoundedRectangle(cornerRadius: 8)
                                    .stroke(GTRColors.mist, lineWidth: 1)
                            )
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(16)
            }
        }
        .background(GTRColors.chalk.ignoresSafeArea())
    }
}

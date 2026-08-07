import SwiftUI

/// Megazip hierarchy browse — Maker → Model → Variant → Section → Diagram.
public struct EpcBrowseScreen: View {
    @EnvironmentObject private var session: StorefrontSession
    @State private var level: Level = .makers
    @State private var makers: [EpcMaker] = []
    @State private var models: [EpcModel] = []
    @State private var variants: [EpcVariant] = []
    @State private var sections: [EpcSection] = []
    @State private var diagram: EpcDiagramResponse?
    @State private var errorText: String?
    @State private var loading = false
    var onOpenOem: (String) -> Void
    var onClose: () -> Void

    private enum Level: Equatable {
        case makers
        case models(EpcMaker)
        case variants(EpcMaker, EpcModel)
        case sections(EpcMaker, EpcModel, EpcVariant)
        case diagram(EpcMaker, EpcModel, EpcVariant, EpcSection)
    }

    public init(onOpenOem: @escaping (String) -> Void, onClose: @escaping () -> Void) {
        self.onOpenOem = onOpenOem
        self.onClose = onClose
    }

    public var body: some View {
        NavigationStack {
            Group {
                if loading {
                    ProgressView("Loading EPC…")
                } else if let errorText {
                    Text(errorText).foregroundStyle(.secondary).padding()
                } else {
                    content
                }
            }
            .navigationTitle(title)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close", action: goBack)
                }
            }
            .task(id: level) { await load() }
        }
    }

    @ViewBuilder
    private var content: some View {
        switch level {
        case .makers:
            List(makers, id: \.slug) { m in
                Button("\(m.name) · \(m.modelCount.map(String.init) ?? "—") models") {
                    level = .models(m)
                }
            }
        case .models:
            List(models.sorted(by: { $0.sortKey < $1.sortKey }), id: \.slug) { m in
                Button(m.displayName) {
                    if case let .models(maker) = level {
                        level = .variants(maker, m)
                    }
                }
            }
        case .variants:
            List(variants, id: \.slug) { v in
                Button("\(v.chassisCode) · \(v.engineCode ?? "")") {
                    if case let .variants(maker, model) = level {
                        level = .sections(maker, model, v)
                    }
                }
            }
        case .sections:
            List(sections.sorted(by: { $0.sortOrder < $1.sortOrder }), id: \.slug) { s in
                Button(s.name) {
                    if case let .sections(maker, model, variant) = level {
                        level = .diagram(maker, model, variant, s)
                    }
                }
            }
        case .diagram:
            if let diagram {
                EpcDiagramView(data: diagram, onOpenOem: onOpenOem)
            } else {
                Text("No diagram").padding()
            }
        }
    }

    private var title: String {
        switch level {
        case .makers: return "EPC catalog"
        case .models(let m): return m.name
        case .variants(_, let m): return m.displayName
        case .sections(_, _, let v): return v.chassisCode
        case .diagram(_, _, _, let s): return s.name
        }
    }

    private func goBack() {
        switch level {
        case .makers: onClose()
        case .models: level = .makers
        case .variants(let maker, _): level = .models(maker)
        case .sections(let maker, let model, _): level = .variants(maker, model)
        case .diagram(let maker, let model, let variant, _):
            level = .sections(maker, model, variant)
        }
    }

    @MainActor
    private func load() async {
        loading = true
        errorText = nil
        defer { loading = false }
        do {
            switch level {
            case .makers:
                makers = try await session.api.listCatalogMakers()
            case .models(let maker):
                models = try await session.api.listCatalogModels(makerSlug: maker.slug)
            case .variants(let maker, let model):
                variants = try await session.api.listCatalogVariants(
                    makerSlug: maker.slug,
                    modelSlug: model.slug
                )
            case .sections(let maker, let model, let variant):
                sections = try await session.api.listCatalogSections(
                    makerSlug: maker.slug,
                    modelSlug: model.slug,
                    variantSlug: variant.slug
                )
            case .diagram(let maker, let model, let variant, let section):
                diagram = try await session.api.getCatalogDiagram(
                    makerSlug: maker.slug,
                    modelSlug: model.slug,
                    variantSlug: variant.slug,
                    sectionSlug: section.slug
                )
            }
        } catch {
            errorText = error.localizedDescription
        }
    }
}

private struct EpcDiagramView: View {
    let data: EpcDiagramResponse
    var onOpenOem: (String) -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if let urlStr = data.imageUrl, let url = URL(string: urlStr) {
                    AsyncImage(url: url) { phase in
                        switch phase {
                        case .success(let img):
                            img.resizable().scaledToFit()
                        case .failure:
                            Text("Diagram unavailable").foregroundStyle(.secondary)
                        default:
                            ProgressView()
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .overlay {
                        GeometryReader { geo in
                            ForEach(Array(data.hotspots.enumerated()), id: \.offset) { _, hs in
                                Rectangle()
                                    .stroke(Color.red, lineWidth: 2)
                                    .frame(
                                        width: geo.size.width * hs.bboxWidth,
                                        height: geo.size.height * hs.bboxHeight
                                    )
                                    .position(
                                        x: geo.size.width * (hs.bboxX + hs.bboxWidth / 2),
                                        y: geo.size.height * (hs.bboxY + hs.bboxHeight / 2)
                                    )
                                    .onTapGesture { onOpenOem(hs.oem) }
                            }
                        }
                    }
                } else {
                    Text("Diagram image unavailable — parts list below.")
                        .foregroundStyle(.secondary)
                        .padding(.horizontal)
                }
                ForEach(data.parts, id: \.oemPartNumber) { part in
                    Button {
                        onOpenOem(part.oemPartNumber)
                    } label: {
                        VStack(alignment: .leading) {
                            Text(part.oemPartNumber).font(.headline)
                            Text(
                                [part.pncCode, part.categoryName, part.stockDescription]
                                    .compactMap { $0 }
                                    .joined(separator: " · ")
                            )
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        }
                    }
                    .padding(.horizontal)
                    Divider()
                }
            }
        }
    }
}

import Foundation

/// Megazip hierarchy browse — mirrors packages/shared catalog-navigation.ts
public struct EpcMaker: Sendable, Hashable {
    public var slug: String
    public var name: String
    public var sortOrder: Int
    public var modelCount: Int?

    public init(slug: String, name: String, sortOrder: Int = 0, modelCount: Int? = nil) {
        self.slug = slug
        self.name = name
        self.sortOrder = sortOrder
        self.modelCount = modelCount
    }
}

public struct EpcModel: Sendable, Hashable {
    public var slug: String
    public var displayName: String
    public var bodyType: String?
    public var sortKey: String
    public var yearStart: Int?
    public var yearEnd: Int?

    public init(
        slug: String,
        displayName: String,
        bodyType: String? = nil,
        sortKey: String,
        yearStart: Int? = nil,
        yearEnd: Int? = nil
    ) {
        self.slug = slug
        self.displayName = displayName
        self.bodyType = bodyType
        self.sortKey = sortKey
        self.yearStart = yearStart
        self.yearEnd = yearEnd
    }
}

public struct EpcVariant: Sendable, Hashable {
    public var slug: String
    public var chassisCode: String
    public var grade: String?
    public var salesRegion: String?
    public var yearLabel: String?
    public var engineCode: String?

    public init(
        slug: String,
        chassisCode: String,
        grade: String? = nil,
        salesRegion: String? = nil,
        yearLabel: String? = nil,
        engineCode: String? = nil
    ) {
        self.slug = slug
        self.chassisCode = chassisCode
        self.grade = grade
        self.salesRegion = salesRegion
        self.yearLabel = yearLabel
        self.engineCode = engineCode
    }
}

public struct EpcSection: Sendable, Hashable {
    public var slug: String
    public var name: String
    public var thumbnailUrl: String?
    public var sortOrder: Int

    public init(slug: String, name: String, thumbnailUrl: String? = nil, sortOrder: Int = 0) {
        self.slug = slug
        self.name = name
        self.thumbnailUrl = thumbnailUrl
        self.sortOrder = sortOrder
    }
}

public struct EpcHotspot: Sendable, Hashable {
    public var oem: String
    public var pncCode: String?
    public var bboxX: Double
    public var bboxY: Double
    public var bboxWidth: Double
    public var bboxHeight: Double
}

public struct EpcDiagramPart: Sendable, Hashable {
    public var oemPartNumber: String
    public var pncCode: String?
    public var categoryName: String?
    public var stockItemId: String?
    public var stockDescription: String?
}

public struct EpcDiagramResponse: Sendable {
    public var diagramSlug: String?
    public var diagramTitle: String?
    public var storagePath: String?
    public var imageUrl: String?
    public var hotspots: [EpcHotspot]
    public var parts: [EpcDiagramPart]

    public init(
        diagramSlug: String? = nil,
        diagramTitle: String? = nil,
        storagePath: String? = nil,
        imageUrl: String? = nil,
        hotspots: [EpcHotspot] = [],
        parts: [EpcDiagramPart] = []
    ) {
        self.diagramSlug = diagramSlug
        self.diagramTitle = diagramTitle
        self.storagePath = storagePath
        self.imageUrl = imageUrl
        self.hotspots = hotspots
        self.parts = parts
    }
}

import Foundation

enum EpcCatalogParser {
    static func parseMakers(_ data: Data) throws -> [EpcMaker] {
        try array(data).compactMap { obj in
            guard let slug = string(obj, "slug") else { return nil }
            return EpcMaker(
                slug: slug,
                name: string(obj, "name") ?? slug,
                sortOrder: int(obj, "sort_order") ?? 0,
                modelCount: int(obj, "model_count")
            )
        }
    }

    static func parseModels(_ data: Data) throws -> [EpcModel] {
        try array(data).compactMap { obj in
            guard let slug = string(obj, "slug") else { return nil }
            return EpcModel(
                slug: slug,
                displayName: string(obj, "display_name") ?? slug,
                bodyType: string(obj, "body_type"),
                sortKey: string(obj, "sort_key") ?? slug,
                yearStart: int(obj, "year_start"),
                yearEnd: int(obj, "year_end")
            )
        }
    }

    static func parseVariants(_ data: Data) throws -> [EpcVariant] {
        try array(data).compactMap { obj in
            guard let slug = string(obj, "slug") else { return nil }
            return EpcVariant(
                slug: slug,
                chassisCode: string(obj, "chassis_code") ?? slug,
                grade: string(obj, "grade"),
                salesRegion: string(obj, "sales_region"),
                yearLabel: string(obj, "year_label"),
                engineCode: string(obj, "engine_code")
            )
        }
    }

    static func parseSections(_ data: Data) throws -> [EpcSection] {
        try array(data).compactMap { obj in
            guard let slug = string(obj, "slug") else { return nil }
            return EpcSection(
                slug: slug,
                name: string(obj, "name") ?? slug,
                thumbnailUrl: string(obj, "thumbnail_url"),
                sortOrder: int(obj, "sort_order") ?? 0
            )
        }
    }

    static func parseDiagram(_ data: Data) throws -> EpcDiagramResponse {
        let root = try jsonObject(data)
        let diagram = root["diagram"] as? [String: Any]
        let hotspots: [EpcHotspot] = ((root["hotspots"] as? [[String: Any]]) ?? []).compactMap { obj in
            guard let oem = string(obj, "oem"),
                  let x = double(obj, "bbox_x"),
                  let y = double(obj, "bbox_y"),
                  let w = double(obj, "bbox_width"),
                  let h = double(obj, "bbox_height")
            else { return nil }
            return EpcHotspot(
                oem: oem,
                pncCode: string(obj, "pnc_code"),
                bboxX: x,
                bboxY: y,
                bboxWidth: w,
                bboxHeight: h
            )
        }
        let parts: [EpcDiagramPart] = ((root["parts"] as? [[String: Any]]) ?? []).compactMap { obj in
            guard let oem = string(obj, "oem_part_number") else { return nil }
            return EpcDiagramPart(
                oemPartNumber: oem,
                pncCode: string(obj, "pnc_code"),
                categoryName: string(obj, "category_name"),
                stockItemId: string(obj, "stock_item_id"),
                stockDescription: string(obj, "stock_description")
            )
        }
        return EpcDiagramResponse(
            diagramSlug: diagram.flatMap { string($0, "slug") },
            diagramTitle: diagram.flatMap { string($0, "title") },
            storagePath: diagram.flatMap { string($0, "storage_path") },
            imageUrl: diagram.flatMap { string($0, "image_url") },
            hotspots: hotspots,
            parts: parts
        )
    }

    private static func array(_ data: Data) throws -> [[String: Any]] {
        let any = try JSONSerialization.jsonObject(with: data)
        if let arr = any as? [[String: Any]] { return arr }
        if let arr = any as? [Any] {
            return arr.compactMap { $0 as? [String: Any] }
        }
        return []
    }

    private static func jsonObject(_ data: Data) throws -> [String: Any] {
        let any = try JSONSerialization.jsonObject(with: data)
        return (any as? [String: Any]) ?? [:]
    }

    private static func string(_ obj: [String: Any], _ key: String) -> String? {
        if let s = obj[key] as? String {
            let t = s.trimmingCharacters(in: .whitespacesAndNewlines)
            return t.isEmpty ? nil : t
        }
        return nil
    }

    private static func int(_ obj: [String: Any], _ key: String) -> Int? {
        if let i = obj[key] as? Int { return i }
        if let n = obj[key] as? NSNumber { return n.intValue }
        return nil
    }

    private static func double(_ obj: [String: Any], _ key: String) -> Double? {
        if let d = obj[key] as? Double { return d }
        if let n = obj[key] as? NSNumber { return n.doubleValue }
        return nil
    }
}

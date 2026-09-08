package co.zw.nissangtr.management.pos

import co.zw.nissangtr.management.rpc.EpcDiagramPart
import co.zw.nissangtr.management.rpc.EpcMaker
import co.zw.nissangtr.management.rpc.EpcModel
import co.zw.nissangtr.management.rpc.EpcSection
import co.zw.nissangtr.management.rpc.EpcVariant
import co.zw.nissangtr.management.rpc.PopularPosSpare
import co.zw.nissangtr.management.rpc.PosPopularItemKind
import co.zw.nissangtr.management.rpc.PosPopularPin

internal sealed interface PosPopularRowItem {
    val stableKey: String

    data class Pinned(val pin: PosPopularPin) : PosPopularRowItem {
        override val stableKey: String = "pin:${pin.stableKey}"
    }

    data class AlgorithmicSpare(val spare: PopularPosSpare) : PosPopularRowItem {
        override val stableKey: String = "algo:${spare.stockItemId}"
    }
}

internal fun buildPopularRowItems(
    pins: List<PosPopularPin>,
    algorithmicSpares: List<PopularPosSpare>,
): List<PosPopularRowItem> {
    val pinnedOems = pins.filter { it.kind == PosPopularItemKind.PART }
        .mapNotNull { it.oemPartNumber?.trim()?.uppercase() }
        .toSet()
    return pins.map(PosPopularRowItem::Pinned) + algorithmicSpares
        .filterNot { it.oemPartNumber.trim().uppercase() in pinnedOems }
        .map(PosPopularRowItem::AlgorithmicSpare)
}

internal fun epcModelPopularPin(maker: EpcMaker, model: EpcModel): PosPopularPin =
    PosPopularPin(
        kind = PosPopularItemKind.MODEL,
        itemKey = "${maker.slug}:${model.slug}",
        label = model.displayName,
        subtitle = listOfNotNull(model.bodyType, model.yearStart?.toString(), model.yearEnd?.toString())
            .filter { it.isNotBlank() }.joinToString(" · ").ifBlank { maker.name },
        searchQuery = model.displayName,
        makerSlug = maker.slug,
        modelSlug = model.slug,
    )

internal fun epcVariantPopularPin(maker: EpcMaker, model: EpcModel, variant: EpcVariant): PosPopularPin =
    PosPopularPin(
        kind = PosPopularItemKind.MODEL,
        itemKey = "${maker.slug}:${model.slug}:${variant.slug}",
        label = "${model.displayName} · ${variant.chassisCode}",
        subtitle = listOfNotNull(variant.yearLabel, variant.engineCode, variant.grade)
            .filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Model variant" },
        searchQuery = listOf(model.displayName, variant.chassisCode, variant.engineCode)
            .filterNotNull().joinToString(" "),
        makerSlug = maker.slug,
        modelSlug = model.slug,
    )

internal fun epcSectionPopularPin(
    maker: EpcMaker,
    model: EpcModel,
    section: EpcSection,
): PosPopularPin =
    PosPopularPin(
        kind = PosPopularItemKind.CATEGORY,
        itemKey = "${maker.slug}:${model.slug}:${section.slug}",
        label = section.name,
        subtitle = model.displayName,
        searchQuery = "${model.displayName} ${section.name}",
        makerSlug = maker.slug,
        modelSlug = model.slug,
        categoryName = section.name,
        imageUrl = section.thumbnailUrl,
    )

internal fun epcPartPopularCandidates(
    maker: EpcMaker,
    model: EpcModel,
    section: EpcSection,
    part: EpcDiagramPart,
): List<PosPopularPin> = buildList {
    add(
        PosPopularPin(
            kind = PosPopularItemKind.PART,
            itemKey = part.oemPartNumber.trim().uppercase(),
            label = part.stockDescription?.takeIf { it.isNotBlank() } ?: part.oemPartNumber,
            subtitle = listOfNotNull(part.oemPartNumber, part.pncCode).joinToString(" · "),
            searchQuery = part.oemPartNumber,
            makerSlug = maker.slug,
            modelSlug = model.slug,
            categoryName = part.categoryName ?: section.name,
            subcategoryName = part.subcategoryName,
            oemPartNumber = part.oemPartNumber,
        ),
    )
    part.categoryName?.trim()?.takeIf { it.isNotEmpty() }?.let { category ->
        add(
            PosPopularPin(
                kind = PosPopularItemKind.CATEGORY,
                itemKey = "${maker.slug}:${model.slug}:category:${slugKey(category)}",
                label = category,
                subtitle = model.displayName,
                searchQuery = "${model.displayName} $category",
                makerSlug = maker.slug,
                modelSlug = model.slug,
                categoryName = category,
            ),
        )
    }
    part.subcategoryName?.trim()?.takeIf { it.isNotEmpty() }?.let { subcategory ->
        add(
            PosPopularPin(
                kind = PosPopularItemKind.SUBCATEGORY,
                itemKey = "${maker.slug}:${model.slug}:subcategory:${slugKey(subcategory)}",
                label = subcategory,
                subtitle = listOfNotNull(part.categoryName, model.displayName).joinToString(" · "),
                searchQuery = "${model.displayName} $subcategory",
                makerSlug = maker.slug,
                modelSlug = model.slug,
                categoryName = part.categoryName,
                subcategoryName = subcategory,
            ),
        )
    }
}.distinctBy { it.stableKey }

private fun slugKey(value: String): String =
    value.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(120)

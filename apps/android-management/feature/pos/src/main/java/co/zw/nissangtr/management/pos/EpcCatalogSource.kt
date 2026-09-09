package co.zw.nissangtr.management.pos

import co.zw.nissangtr.management.rpc.EpcDiagramResponse
import co.zw.nissangtr.management.rpc.EpcDiagramSummary
import co.zw.nissangtr.management.rpc.EpcMaker
import co.zw.nissangtr.management.rpc.EpcModel
import co.zw.nissangtr.management.rpc.EpcSection
import co.zw.nissangtr.management.rpc.EpcVariant
import co.zw.nissangtr.management.rpc.RpcClient

/** Read-only EPC source. POS prefers the encrypted local bundle and falls back to live RPC. */
internal interface EpcCatalogSource {
    suspend fun listMakers(): List<EpcMaker>
    suspend fun listModels(makerSlug: String): List<EpcModel>
    suspend fun listVariants(makerSlug: String, modelSlug: String): List<EpcVariant>
    suspend fun listSections(makerSlug: String, modelSlug: String, variantSlug: String): List<EpcSection>
    suspend fun listDiagrams(makerSlug: String, modelSlug: String, variantSlug: String, sectionSlug: String): List<EpcDiagramSummary>
    suspend fun getDiagram(makerSlug: String, modelSlug: String, variantSlug: String, sectionSlug: String): EpcDiagramResponse
    suspend fun getDiagramBySlug(makerSlug: String, modelSlug: String, variantSlug: String, sectionSlug: String, diagramSlug: String): EpcDiagramResponse
    val offline: Boolean
}

internal class RpcEpcCatalogSource(private val rpc: RpcClient) : EpcCatalogSource {
    override val offline = false
    override suspend fun listMakers() = rpc.listCatalogMakers()
    override suspend fun listModels(makerSlug: String) = rpc.listCatalogModels(makerSlug)
    override suspend fun listVariants(makerSlug: String, modelSlug: String) = rpc.listCatalogVariants(makerSlug, modelSlug)
    override suspend fun listSections(makerSlug: String, modelSlug: String, variantSlug: String) =
        rpc.listCatalogSections(makerSlug, modelSlug, variantSlug)
    override suspend fun listDiagrams(makerSlug: String, modelSlug: String, variantSlug: String, sectionSlug: String) =
        rpc.listCatalogDiagrams(makerSlug, modelSlug, variantSlug, sectionSlug)
    override suspend fun getDiagram(makerSlug: String, modelSlug: String, variantSlug: String, sectionSlug: String) =
        rpc.getCatalogDiagram(makerSlug, modelSlug, variantSlug, sectionSlug)
    override suspend fun getDiagramBySlug(makerSlug: String, modelSlug: String, variantSlug: String, sectionSlug: String, diagramSlug: String) =
        rpc.getCatalogDiagramBySlug(makerSlug, modelSlug, variantSlug, sectionSlug, diagramSlug)
}

internal class OfflineEpcCatalogSource(
    private val engine: co.zw.nissangtr.management.pos.offline.OfflinePosSyncEngine,
) : EpcCatalogSource {
    override val offline = true
    override suspend fun listMakers() = engine.listLocalEpcMakers()
    override suspend fun listModels(makerSlug: String) = engine.listLocalEpcModels(makerSlug)
    override suspend fun listVariants(makerSlug: String, modelSlug: String) =
        engine.listLocalEpcVariants(makerSlug, modelSlug)
    override suspend fun listSections(makerSlug: String, modelSlug: String, variantSlug: String) =
        engine.listLocalEpcSections(makerSlug, modelSlug, variantSlug)
    override suspend fun listDiagrams(makerSlug: String, modelSlug: String, variantSlug: String, sectionSlug: String) =
        engine.listLocalEpcDiagrams(makerSlug, modelSlug, variantSlug, sectionSlug)
    override suspend fun getDiagram(makerSlug: String, modelSlug: String, variantSlug: String, sectionSlug: String) =
        engine.getLocalEpcDiagram(makerSlug, modelSlug, variantSlug, sectionSlug)
    override suspend fun getDiagramBySlug(makerSlug: String, modelSlug: String, variantSlug: String, sectionSlug: String, diagramSlug: String) =
        engine.getLocalEpcDiagramBySlug(makerSlug, modelSlug, variantSlug, sectionSlug, diagramSlug)
}

internal class StoreEpcCatalogSource(
    private val store: co.zw.nissangtr.management.pos.offline.OfflinePosStore,
) : EpcCatalogSource {
    override val offline = true
    override suspend fun listMakers() = store.listEpcMakers()
    override suspend fun listModels(makerSlug: String) = store.listEpcModels(makerSlug)
    override suspend fun listVariants(makerSlug: String, modelSlug: String) = store.listEpcVariants(makerSlug, modelSlug)
    override suspend fun listSections(makerSlug: String, modelSlug: String, variantSlug: String) =
        store.listEpcSections(makerSlug, modelSlug, variantSlug)
    override suspend fun listDiagrams(makerSlug: String, modelSlug: String, variantSlug: String, sectionSlug: String) =
        store.listEpcDiagrams(makerSlug, modelSlug, variantSlug, sectionSlug)
    override suspend fun getDiagram(makerSlug: String, modelSlug: String, variantSlug: String, sectionSlug: String) =
        store.getEpcDiagram(makerSlug, modelSlug, variantSlug, sectionSlug)
    override suspend fun getDiagramBySlug(makerSlug: String, modelSlug: String, variantSlug: String, sectionSlug: String, diagramSlug: String) =
        store.getEpcDiagramBySlug(makerSlug, modelSlug, variantSlug, sectionSlug, diagramSlug)
}

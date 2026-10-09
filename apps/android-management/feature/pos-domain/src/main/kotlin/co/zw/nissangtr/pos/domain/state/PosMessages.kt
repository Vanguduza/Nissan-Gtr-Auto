package co.zw.nissangtr.pos.domain.state

import co.zw.nissangtr.pos.domain.error.PosError
import co.zw.nissangtr.pos.domain.model.CartProjection
import co.zw.nissangtr.pos.domain.model.CatalogPart
import co.zw.nissangtr.pos.domain.model.Category
import co.zw.nissangtr.pos.domain.model.Operator
import co.zw.nissangtr.pos.domain.model.PopularPin
import co.zw.nissangtr.pos.domain.model.VehicleGeneration
import co.zw.nissangtr.pos.domain.model.VehicleModel
import co.zw.nissangtr.pos.domain.model.VehicleSelection

/** Everything that can change [PosState]: operator intents and gateway outcomes. */
sealed interface PosMsg

/** Operator intents (from the UI). */
sealed interface PosIntent : PosMsg {
    data object Start : PosIntent
    data class Navigate(val destination: PosDestination) : PosIntent
    data class EditSearch(val query: String) : PosIntent
    data object SubmitSearch : PosIntent
    data class SearchFor(val query: String) : PosIntent
    data class OpenCategory(val category: Category) : PosIntent
    data object ClearRecentSearches : PosIntent

    data class PickModel(val model: VehicleModel) : PosIntent
    data class PickGeneration(val generation: VehicleGeneration) : PosIntent
    data class PickEngine(val engine: String) : PosIntent
    data object ClearVehicle : PosIntent

    data class AddPart(val part: CatalogPart) : PosIntent
    data class SetQuantity(val lineId: String, val qty: Double) : PosIntent
    data class RemoveLine(val lineId: String) : PosIntent

    data class Pin(val pin: PopularPin) : PosIntent
    data class Unpin(val pin: PopularPin) : PosIntent
    data class HideBestSeller(val part: CatalogPart) : PosIntent
    data class ActivatePin(val pin: PopularPin) : PosIntent

    data object DismissFeedback : PosIntent
    data class ConnectivityChanged(val online: Boolean) : PosIntent
}

/** Gateway outcomes (from effects). */
sealed interface PosEvent : PosMsg {
    data class OperatorLoaded(val operator: Operator) : PosEvent
    data class ModelsLoaded(val models: List<VehicleModel>) : PosEvent
    data class GenerationsLoaded(val model: VehicleModel, val generations: List<VehicleGeneration>) : PosEvent
    data class EnginesLoaded(val generation: VehicleGeneration, val engines: List<String>) : PosEvent
    data class SearchLoaded(val query: String, val results: List<CatalogPart>) : PosEvent
    data class PinsLoaded(val pins: List<PopularPin>) : PosEvent
    data class BestSellersLoaded(val parts: List<CatalogPart>) : PosEvent
    data class HiddenLoaded(val stockItemIds: Set<String>) : PosEvent
    data class CartUpdated(val cart: CartProjection) : PosEvent
    data object CartMutationFinished : PosEvent
    data class PinPersisted(val pin: PopularPin) : PosEvent
    data class UnpinPersisted(val pin: PopularPin) : PosEvent
    data class HidePersisted(val stockItemId: String) : PosEvent

    /** A mutation failed; [rollback] restores optimistic state where one was applied. */
    data class Failed(val error: PosError, val rollback: Rollback? = null) : PosEvent
}

sealed interface Rollback {
    data class RestorePinRemoved(val pin: PopularPin, val index: Int) : Rollback
    data class RemovePinAdded(val pin: PopularPin) : Rollback
    data class UnhideBestSeller(val stockItemId: String) : Rollback
}

/** Declarative side effects; the effect handler performs I/O and dispatches events back. */
sealed interface PosEffect {
    data object LoadOperator : PosEffect
    data object LoadModels : PosEffect
    data class LoadGenerations(val model: VehicleModel) : PosEffect
    data class LoadEngines(val model: VehicleModel, val generation: VehicleGeneration) : PosEffect
    data class Search(val query: String, val vehicle: VehicleSelection?) : PosEffect
    data object LoadPopular : PosEffect
    data class AddToCart(val cartId: String, val part: CatalogPart, val qty: Double) : PosEffect
    data class SetQuantity(val cartId: String, val lineId: String, val qty: Double) : PosEffect
    data class RemoveLine(val cartId: String, val lineId: String) : PosEffect
    data class SetCartVehicle(val cartId: String, val vehicle: VehicleSelection?) : PosEffect
    data class PersistPin(val pin: PopularPin) : PosEffect
    data class PersistUnpin(val pin: PopularPin, val index: Int) : PosEffect
    data class PersistHide(val stockItemId: String) : PosEffect
}

data class Reduction(val state: PosState, val effects: List<PosEffect> = emptyList())

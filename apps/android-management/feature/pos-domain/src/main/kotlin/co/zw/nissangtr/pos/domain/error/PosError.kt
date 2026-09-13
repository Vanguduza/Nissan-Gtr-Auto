package co.zw.nissangtr.pos.domain.error

sealed interface PosError {
    data class Transient(val retryable: Boolean, val message: String = "") : PosError
    data class Input(val field: String, val reason: String) : PosError
    data class BusinessRule(val rule: String, val detail: String) : PosError
    data class PaymentUnknown(val correlation: String) : PosError
    data class HardwareUnavailable(val device: String) : PosError
    data class OfflineRestricted(val blocked: Set<String>) : PosError
}

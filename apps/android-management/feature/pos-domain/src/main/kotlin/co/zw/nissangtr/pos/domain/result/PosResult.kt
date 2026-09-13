package co.zw.nissangtr.pos.domain.result

import co.zw.nissangtr.pos.domain.error.PosError

sealed interface PosResult<out T> {
    data class Ok<out T>(val value: T) : PosResult<T>
    data class Err(val error: PosError) : PosResult<Nothing>

    val isOk: Boolean get() = this is Ok
    val isErr: Boolean get() = this is Err

    fun getOrNull(): T? = when (this) {
        is Ok -> value
        is Err -> null
    }

    fun errorOrNull(): PosError? = when (this) {
        is Ok -> null
        is Err -> error
    }
}

inline fun <T, R> PosResult<T>.map(transform: (T) -> R): PosResult<R> = when (this) {
    is PosResult.Ok -> PosResult.Ok(transform(value))
    is PosResult.Err -> this
}

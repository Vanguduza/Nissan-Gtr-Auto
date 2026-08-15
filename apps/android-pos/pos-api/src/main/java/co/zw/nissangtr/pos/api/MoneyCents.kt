package co.zw.nissangtr.pos.api

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Integer minor-unit helpers at the `:pos-api` edge (§16.5).
 * [TenderAllocator](co.zw.nissangtr.pos.pay.TenderAllocator) stays in Long cents —
 * convert server `numeric` / display majors here only (half-up to 2 dp).
 */
object MoneyCents {
    fun majorToCents(major: Double): Long {
        return BigDecimal.valueOf(major)
            .setScale(2, RoundingMode.HALF_UP)
            .movePointRight(2)
            .longValueExact()
    }

    fun majorStringToCents(major: String): Long {
        return BigDecimal(major.trim())
            .setScale(2, RoundingMode.HALF_UP)
            .movePointRight(2)
            .longValueExact()
    }

    /** RPC / display major at exactly 2 dp (e.g. `"212.00"`). */
    fun centsToMajorString(cents: Long): String {
        val bd = BigDecimal.valueOf(cents).movePointLeft(2).setScale(2, RoundingMode.UNNECESSARY)
        return bd.toPlainString()
    }
}

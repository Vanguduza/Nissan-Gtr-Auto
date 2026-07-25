package co.zw.nissangtr.delivery.jobs

import co.zw.nissangtr.delivery.rpc.DeliveryFailureReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JobsViewModelHelpersTest {

    @Test
    fun failureReasonsMatchRpcEnum() {
        val values = DeliveryFailureReason.entries.map { it.rpcValue }
        assertTrue(values.contains("customer_absent"))
        assertTrue(values.contains("refused"))
        assertTrue(values.contains("wrong_address"))
        assertTrue(values.contains("damaged"))
        assertTrue(values.contains("other"))
        assertEquals(5, values.size)
    }
}

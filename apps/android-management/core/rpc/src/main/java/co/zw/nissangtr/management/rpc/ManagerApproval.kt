package co.zw.nissangtr.management.rpc

/**
 * Runs one action under a manager's own sign-in, then restores the attendant's session (owner
 * decision D4, Blueprint §10.10: re-authenticated per action, never cached). Clients that wrap
 * another client must pass this through, or approvals would run as the attendant.
 */
interface ManagerApproval {
    suspend fun <T> withManagerApproval(managerIdentifier: String, managerPassword: String, block: suspend () -> T): T
}

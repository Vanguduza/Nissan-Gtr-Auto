package co.zw.nissangtr.customer.rpc

/** Mirrors web `profiles` row subset. */
data class UserProfile(
    val id: String,
    val fullName: String? = null,
)

/** Mirrors web `customers` row subset for edit-profile. */
data class CustomerProfile(
    val id: String,
    val displayName: String? = null,
    val email: String? = null,
    val phoneE164: String? = null,
    val whatsappE164: String? = null,
    val smsReceipts: Boolean = false,
    val emailReceipts: Boolean = false,
    val whatsappReceipts: Boolean = false,
    val marketingOptIn: Boolean = false,
    val lastPromoAt: String? = null,
)

enum class PreferredReceiptChannel {
    Whatsapp,
    Sms,
    Email,
}

data class CustomerContactPatch(
    val displayName: String? = null,
    val email: String? = null,
    val phoneE164: String? = null,
    val whatsappE164: String? = null,
    val smsReceipts: Boolean? = null,
    val emailReceipts: Boolean? = null,
    val whatsappReceipts: Boolean? = null,
)

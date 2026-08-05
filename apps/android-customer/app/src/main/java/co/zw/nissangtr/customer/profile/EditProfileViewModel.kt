package co.zw.nissangtr.customer.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.customer.rpc.CustomerContactPatch
import co.zw.nissangtr.customer.rpc.CustomerProfile
import co.zw.nissangtr.customer.rpc.PreferredReceiptChannel
import co.zw.nissangtr.customer.rpc.RpcClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditProfileUiState(
    val firstName: String = "",
    val lastName: String = "",
    val company: String = "",
    val email: String = "",
    val phone: String = "",
    val preferredContact: PreferredReceiptChannel = PreferredReceiptChannel.Whatsapp,
    val marketingOptIn: Boolean = false,
    val customerId: String? = null,
    val lastPromoAt: String? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

class EditProfileViewModel(
    private val rpc: RpcClient,
) : ViewModel() {
    private val _state = MutableStateFlow(EditProfileUiState())
    val state: StateFlow<EditProfileUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun onFirstName(v: String) = _state.update { it.copy(firstName = v, message = null) }
    fun onLastName(v: String) = _state.update { it.copy(lastName = v, message = null) }
    fun onCompany(v: String) = _state.update { it.copy(company = v, message = null) }
    fun onEmail(v: String) = _state.update { it.copy(email = v, message = null) }
    fun onPhone(v: String) = _state.update { it.copy(phone = v, message = null) }
    fun onPreferred(v: PreferredReceiptChannel) =
        _state.update { it.copy(preferredContact = v, message = null) }
    fun onMarketing(v: Boolean) = _state.update { it.copy(marketingOptIn = v, message = null) }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val profile = rpc.loadOwnProfile()
                val customer = rpc.loadOwnCustomer()
                val names = splitName(profile?.fullName)
                _state.update {
                    it.copy(
                        busy = false,
                        firstName = names.first,
                        lastName = names.second,
                        company = customer?.displayName.orEmpty(),
                        email = customer?.email.orEmpty(),
                        phone = customer?.phoneE164 ?: customer?.whatsappE164.orEmpty(),
                        preferredContact = preferredFromCustomer(customer),
                        marketingOptIn = customer?.marketingOptIn == true,
                        customerId = customer?.id,
                        lastPromoAt = customer?.lastPromoAt,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "load failed") }
            }
        }
    }

    fun save() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val s = _state.value
                val fullName = listOf(s.firstName, s.lastName)
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .joinToString(" ")
                rpc.updateOwnFullName(fullName)
                val notes = mutableListOf("Name saved to profiles.")
                if (s.customerId != null) {
                    val pref = s.preferredContact
                    rpc.updateOwnCustomerContact(
                        CustomerContactPatch(
                            displayName = s.company.trim().ifEmpty { fullName.ifEmpty { "Customer" } },
                            email = s.email.trim().ifEmpty { null },
                            phoneE164 = s.phone.trim().ifEmpty { null },
                            whatsappE164 = s.phone.trim().ifEmpty { null },
                            smsReceipts = pref == PreferredReceiptChannel.Sms,
                            emailReceipts = pref == PreferredReceiptChannel.Email,
                            whatsappReceipts = pref == PreferredReceiptChannel.Whatsapp,
                        ),
                    )
                    notes.add("Contact + receipt prefs updated.")
                    rpc.setOwnMarketingOptIn(s.marketingOptIn)
                    notes.add(
                        if (s.marketingOptIn) {
                            "Marketing opt-in enabled."
                        } else {
                            "Marketing opt-in disabled."
                        },
                    )
                } else {
                    notes.add("No linked customer row — contact fields display-only until linked.")
                }
                refresh()
                _state.update { it.copy(busy = false, message = notes.joinToString(" ")) }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "save failed") }
            }
        }
    }

    companion object {
        fun factory(rpc: RpcClient): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    EditProfileViewModel(rpc) as T
            }

        private fun splitName(full: String?): Pair<String, String> {
            val parts = full?.trim()?.split(Regex("\\s+"))?.filter { it.isNotEmpty() }.orEmpty()
            if (parts.isEmpty()) return "" to ""
            if (parts.size == 1) return parts[0] to ""
            return parts[0] to parts.drop(1).joinToString(" ")
        }

        private fun preferredFromCustomer(c: CustomerProfile?): PreferredReceiptChannel {
            if (c == null) return PreferredReceiptChannel.Whatsapp
            return when {
                c.whatsappReceipts -> PreferredReceiptChannel.Whatsapp
                c.smsReceipts -> PreferredReceiptChannel.Sms
                c.emailReceipts -> PreferredReceiptChannel.Email
                else -> PreferredReceiptChannel.Whatsapp
            }
        }
    }
}

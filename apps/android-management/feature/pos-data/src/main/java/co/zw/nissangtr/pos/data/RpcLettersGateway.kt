package co.zw.nissangtr.pos.data

import co.zw.nissangtr.management.rpc.BusinessProfileRow
import co.zw.nissangtr.management.rpc.PaymentLetterRow
import co.zw.nissangtr.management.rpc.RpcClient
import co.zw.nissangtr.pos.domain.error.PosError
import co.zw.nissangtr.pos.domain.gateway.LettersGateway
import co.zw.nissangtr.pos.domain.model.BusinessProfile
import co.zw.nissangtr.pos.domain.model.LetterSource
import co.zw.nissangtr.pos.domain.model.Money
import co.zw.nissangtr.pos.domain.model.MySignature
import co.zw.nissangtr.pos.domain.model.PaymentLetter
import co.zw.nissangtr.pos.domain.model.PaymentLetterSummary

/** Payment letters, the signer's own signature and the business details, over the phase 8 RPCs. */
class RpcLettersGateway(private val rpc: RpcClient) : LettersGateway {
    override suspend fun list(source: LetterSource) = call { rpc.listPaymentLetters(source.kind, source.id).map { it.toDomain() } }

    override suspend fun issue(source: LetterSource, notes: String?) = call {
        try {
            rpc.createPaymentLetter(source.kind, source.id, notes)
        } catch (e: Exception) {
            // The issuer must have their own signature on file first (Settings → My signature).
            if ("signature required" in e.message.orEmpty().lowercase()) throw PosFailure(PosError.BusinessRule("letter_needs_signature", ""))
            throw e
        }
    }

    override suspend fun letter(letterId: String) = call {
        val d = rpc.getPaymentLetter(letterId)
        PaymentLetter(
            summary = d.row.toDomain(),
            externalReference = d.fields["external_reference"],
            providerReference = d.fields["provider_reference"],
            terminalTransactionId = d.fields["terminal_transaction_id"],
            rrn = d.fields["rrn"],
            authorizationCode = d.fields["authorization_code"],
            cardLast4 = d.fields["card_last4"],
            cardScheme = d.fields["card_scheme"],
            failureDetail = d.fields["failure_detail"],
            managerEmployeeCode = d.fields["manager_employee_code"],
            issueNotes = d.fields["issue_notes"],
            business = d.business?.toDomain(),
            signature = d.signature,
            signatureSha256 = d.signatureSha256,
        )
    }

    override suspend fun mySignature() = call { rpc.getMyManagerSignature().let { MySignature(it.fullName, it.employeeCode, it.hasSignature, it.image) } }

    override suspend fun saveSignature(png: ByteArray) = call {
        rpc.saveMyManagerSignature(png).let { MySignature(it.fullName, it.employeeCode, it.hasSignature, it.image) }
    }

    override suspend fun profile() = call { rpc.getBusinessDocumentProfile().toDomain() }

    override suspend fun saveProfile(profile: BusinessProfile) = call {
        rpc.setBusinessDocumentProfile(
            BusinessProfileRow(
                profile.legalName, profile.tradingName, profile.domain, profile.city, profile.country,
                profile.addressLine1, profile.addressLine2, profile.phone, profile.email, profile.registrationNumber,
            ),
        ).toDomain()
    }
}

private fun PaymentLetterRow.toDomain() = PaymentLetterSummary(
    id, documentNumber, provider, observedStatus, Money.ofMajor(amount, currency.toDomain()), customerName, invoiceNumber, managerName, managerTitle, issuedAt,
)

private fun BusinessProfileRow.toDomain() = BusinessProfile(legalName, tradingName, domain, city, country, addressLine1, addressLine2, phoneE164, email, registrationNumber)

package co.zw.nissangtr.pos.ui.sale

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.pos.design.theme.PosTheme
import co.zw.nissangtr.pos.domain.model.BusinessProfile
import co.zw.nissangtr.pos.domain.model.LetterSource
import co.zw.nissangtr.pos.domain.model.PaymentLetter
import co.zw.nissangtr.pos.domain.state.LetterIntent
import co.zw.nissangtr.pos.domain.state.PosIntent
import co.zw.nissangtr.pos.domain.state.PosState
import co.zw.nissangtr.pos.ui.R
import co.zw.nissangtr.pos.ui.common.PosField
import co.zw.nissangtr.pos.ui.common.PosModal
import co.zw.nissangtr.pos.ui.common.PosPrimaryButton
import co.zw.nissangtr.pos.ui.common.PosRowEnd
import co.zw.nissangtr.pos.ui.common.PosText
import co.zw.nissangtr.pos.ui.common.formatMoney
import co.zw.nissangtr.pos.ui.home.SoftButton
import java.io.ByteArrayOutputStream

/*
 * Payment letters (phase 8): the block on each payment in recovery, the letter itself with Print,
 * and Settings → My signature / Business details on documents.
 */

/** Prints a signed A4 document (job name, lines, signature image, lines under the signature). */
typealias SignedDocumentPrinter = (String, List<String>, ByteArray?, List<String>) -> Unit

private fun providerLabel(p: String?): String = when (p) {
    "card_terminal" -> "Card machine"
    "ecocash" -> "EcoCash"
    "paynow" -> "Paynow"
    "contipay" -> "ContiPay"
    "bank" -> "Card / bank transfer"
    null -> "—"
    else -> p.replace('_', ' ')
}

private fun issued(iso: String) = iso.take(16).replace('T', ' ')

/** Letters already issued for one payment, and issuing one (signed-in manager, finance or admin). */
@Composable
internal fun LettersBlock(state: PosState, source: LetterSource, dispatch: (PosIntent) -> Unit) {
    val palette = PosTheme.palette
    var open by rememberSaveable(source.key) { mutableStateOf(false) }
    var notes by rememberSaveable(source.key) { mutableStateOf("") }
    LaunchedEffect(source.key) { if (state.online) dispatch(LetterIntent.Load(source)) }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp).clip(PosTheme.shape.sm).background(palette.canvas).padding(12.dp)) {
        Row {
            Column(Modifier.weight(1f)) {
                PosText(stringResource(R.string.pos_letter_title), PosTheme.type.bodyPrimary.copy(fontWeight = FontWeight.SemiBold), palette.textPrimary)
                PosText(stringResource(R.string.pos_letter_hint), PosTheme.type.bodySecondary, palette.textMuted)
            }
            if (!open) SoftButton(stringResource(R.string.pos_letter_issue), null, enabled = state.online, onClick = { open = true }, modifier = Modifier.width(150.dp))
        }
        if (open) {
            Spacer(Modifier.height(8.dp))
            PosField(stringResource(R.string.pos_letter_note), notes, { notes = it }, placeholder = stringResource(R.string.pos_letter_note_hint))
            if (!state.selfApprover) PosText(stringResource(R.string.pos_letter_who), PosTheme.type.bodySecondary, palette.textMuted, modifier = Modifier.padding(top = 6.dp))
            PosRowEnd {
                SoftButton(stringResource(R.string.pos_cancel), null, enabled = true, onClick = { open = false }, modifier = Modifier.widthIn(max = 140.dp))
                PosPrimaryButton(
                    stringResource(if (state.lettersBusy) R.string.pos_loading else R.string.pos_letter_issue_open),
                    enabled = state.online && !state.lettersBusy,
                    onClick = {
                        dispatch(LetterIntent.Issue(source, notes))
                        open = false
                        notes = ""
                    },
                )
            }
        }
        state.letters[source.key].orEmpty().forEach { l ->
            ListRow(
                title = l.documentNumber ?: l.id.take(8),
                subtitle = listOfNotNull(l.observedStatus?.replace('_', ' '), l.managerName, issued(l.issuedAtIso)).joinToString(" · "),
            ) {
                SoftButton(stringResource(R.string.pos_open), null, enabled = state.online, onClick = { dispatch(LetterIntent.Open(l.id)) }, modifier = Modifier.width(110.dp))
            }
        }
    }
}

/** The plain-text body of a letter for the A4 printer (the signature prints as an image under it). */
private fun letterLines(l: PaymentLetter, labels: Map<String, String>): List<String> {
    val b = l.business
    val s = l.summary
    fun row(k: String, v: String?) = v?.takeIf { it.isNotBlank() }?.let { "${labels.getValue(k).padEnd(28)}$it" }
    return listOfNotNull(
        b?.tradingName ?: "Nissan GTR Auto",
        b?.legalName?.takeIf { it != b.tradingName },
        listOfNotNull(b?.addressLine1, b?.addressLine2, b?.city, b?.country).joinToString(", ").ifBlank { null },
        listOfNotNull(b?.phone, b?.email, b?.domain).joinToString(" · ").ifBlank { null },
        b?.registrationNumber?.let { "Reg. $it" },
        "",
        labels.getValue("title"),
        "${s.documentNumber.orEmpty()} · ${issued(s.issuedAtIso)}",
        "",
        labels.getValue("intro"),
        "",
        row("customer", s.customerName),
        row("invoice", s.invoiceNumber),
        row("paid_with", providerLabel(s.provider)),
        row("amount", formatMoney(s.amount)),
        row("status", s.observedStatus?.replace('_', ' ')),
        row("our_ref", l.externalReference),
        row("provider_ref", l.providerReference),
        row("terminal_txn", l.terminalTransactionId),
        row("rrn", l.rrn),
        row("auth", l.authorizationCode),
        row("card", l.cardLast4?.let { "${l.cardScheme ?: "Card"} ending $it" }),
        row("detail", l.failureDetail),
        l.issueNotes?.let { "" },
        l.issueNotes,
    )
}

@Composable
private fun letterLabels(): Map<String, String> = mapOf(
    "title" to stringResource(R.string.pos_letter_doc_title),
    "intro" to stringResource(R.string.pos_letter_doc_intro),
    "customer" to stringResource(R.string.pos_letter_customer),
    "invoice" to stringResource(R.string.pos_letter_invoice),
    "paid_with" to stringResource(R.string.pos_letter_paid_with),
    "amount" to stringResource(R.string.pos_letter_amount),
    "status" to stringResource(R.string.pos_letter_status),
    "our_ref" to stringResource(R.string.pos_letter_our_ref),
    "provider_ref" to stringResource(R.string.pos_letter_provider_ref),
    "terminal_txn" to stringResource(R.string.pos_letter_terminal_txn),
    "rrn" to stringResource(R.string.pos_letter_rrn),
    "auth" to stringResource(R.string.pos_letter_auth),
    "card" to stringResource(R.string.pos_letter_card),
    "detail" to stringResource(R.string.pos_letter_detail),
)

/** The issued letter on screen, with Print (A4 through the document printer bridge). */
@Composable
fun LetterDialog(state: PosState, dispatch: (PosIntent) -> Unit, print: SignedDocumentPrinter?) {
    val letter = state.openLetter ?: return
    val palette = PosTheme.palette
    val labels = letterLabels()
    val lines = remember(letter, labels) { letterLines(letter, labels) }
    val image = remember(letter) { letter.signature?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
    val signer = listOfNotNull(letter.summary.managerName, listOfNotNull(letter.summary.managerTitle, letter.managerEmployeeCode).joinToString(" · ").ifBlank { null })
    PosModal(stringResource(R.string.pos_letter_title), onDismiss = { dispatch(LetterIntent.Close) }, width = 620.dp) {
        Column(Modifier.fillMaxWidth().clip(PosTheme.shape.sm).background(Color.White).border(1.dp, palette.borderSubtle, PosTheme.shape.sm).padding(18.dp)) {
            val factLabels = labels.filterKeys { it != "title" && it != "intro" }.values
            lines.forEachIndexed { i, line ->
                // Detail rows are padded for the printer's fixed-width text; on screen they are two columns.
                val label = factLabels.firstOrNull { line.length > 28 && line.startsWith(it) && line.substring(it.length, 28).isBlank() }
                if (label != null) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                        PosText(label, PosTheme.type.bodySecondary, Color(0xFF555555), modifier = Modifier.width(200.dp))
                        PosText(line.substring(28), PosTheme.type.bodySecondary.copy(fontWeight = FontWeight.SemiBold), Color(0xFF111111), modifier = Modifier.weight(1f))
                    }
                } else {
                    PosText(
                        line,
                        if (i == 0 || line == labels["title"]) PosTheme.type.bodyPrimary.copy(fontWeight = FontWeight.Bold) else PosTheme.type.bodySecondary,
                        Color(0xFF111111),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            if (image != null) {
                Image(image, contentDescription = stringResource(R.string.pos_letter_signature), modifier = Modifier.height(56.dp).widthIn(max = 220.dp), contentScale = ContentScale.Fit)
            } else {
                PosText(stringResource(R.string.pos_letter_signature_on_file, letter.signatureSha256?.take(12).orEmpty()), PosTheme.type.bodySecondary, Color(0xFF777777))
            }
            Spacer(Modifier.height(2.dp).width(220.dp).background(Color(0xFF999999)))
            signer.forEach { PosText(it, PosTheme.type.bodySecondary, Color(0xFF111111)) }
        }
        PosRowEnd {
            SoftButton(stringResource(R.string.pos_close), null, enabled = true, onClick = { dispatch(LetterIntent.Close) }, modifier = Modifier.widthIn(max = 140.dp))
            PosPrimaryButton(
                stringResource(R.string.pos_print),
                enabled = print != null,
                onClick = { print?.invoke(letter.summary.documentNumber ?: "Payment letter", lines, letter.signature, signer) },
            )
        }
    }
}

/** Settings → My signature: drawn on the screen with a finger or stylus; letters use the one on file when issued. */
@Composable
internal fun SignatureSettingsRow(state: PosState, dispatch: (PosIntent) -> Unit) {
    val palette = PosTheme.palette
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val sig = state.mySignature
    val onFile = remember(sig) { sig?.image?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
    LaunchedEffect(Unit) { if (state.online) dispatch(LetterIntent.LoadSettings) }
    ListRow(
        stringResource(R.string.pos_sig_title),
        stringResource(if (sig == null) R.string.pos_sig_not_manager else if (sig.hasSignature) R.string.pos_sig_on_file else R.string.pos_sig_none),
    )
    if (sig == null) return
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        onFile?.let { Image(it, contentDescription = stringResource(R.string.pos_letter_signature), modifier = Modifier.height(64.dp).widthIn(max = 260.dp).background(Color.White), contentScale = ContentScale.Fit) }
        Canvas(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 140.dp)
                .height(140.dp)
                .clip(PosTheme.shape.sm)
                .background(Color.White)
                .border(1.dp, palette.borderStrong, PosTheme.shape.sm)
                .onSizeChanged { size = it }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { current = listOf(it) },
                        onDrag = { change, _ -> current = current + change.position },
                        onDragEnd = {
                            if (current.size > 1) strokes.add(current)
                            current = emptyList()
                        },
                    )
                },
        ) {
            (strokes + listOf(current)).forEach { pts ->
                if (pts.size < 2) return@forEach
                val path = Path().apply {
                    moveTo(pts.first().x, pts.first().y)
                    pts.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(path, Color(0xFF0B1D4A), style = Stroke(width = 4f, cap = StrokeCap.Round))
            }
        }
        PosText(stringResource(R.string.pos_sig_hint), PosTheme.type.bodySecondary, palette.textMuted)
        PosRowEnd {
            SoftButton(stringResource(R.string.pos_sig_clear), null, enabled = strokes.isNotEmpty(), onClick = { strokes.clear() }, modifier = Modifier.widthIn(max = 140.dp))
            PosPrimaryButton(
                stringResource(if (state.lettersBusy) R.string.pos_loading else R.string.pos_sig_save),
                enabled = strokes.isNotEmpty() && size.width > 0 && state.online && !state.lettersBusy,
                onClick = {
                    dispatch(LetterIntent.SaveSignature(renderSignature(strokes.toList(), size)))
                    strokes.clear()
                },
            )
        }
    }
}

/** The drawn strokes as a transparent-background PNG (what the server stores and letters print). */
private fun renderSignature(strokes: List<List<Offset>>, size: IntSize): ByteArray {
    val bmp = Bitmap.createBitmap(size.width.coerceAtLeast(1), size.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF0B1D4A.toInt()
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
    }
    strokes.forEach { pts ->
        val path = android.graphics.Path().apply {
            moveTo(pts.first().x, pts.first().y)
            pts.drop(1).forEach { lineTo(it.x, it.y) }
        }
        canvas.drawPath(path, paint)
    }
    return ByteArrayOutputStream().use { out ->
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        bmp.recycle()
        out.toByteArray()
    }
}

/** Settings → Business details on documents (admin): the header of payment letters. */
@Composable
internal fun BusinessProfileSettingsRow(state: PosState, dispatch: (PosIntent) -> Unit) {
    val p = state.businessProfile ?: return
    var open by rememberSaveable { mutableStateOf(false) }
    var draft by remember(p) { mutableStateOf(p) }
    ListRow(stringResource(R.string.pos_bp_title), listOf(p.tradingName, p.city, p.country).filterNotNull().joinToString(" · ")) {
        if (!open) SoftButton(stringResource(R.string.pos_bp_edit), null, enabled = state.online, onClick = { open = true }, modifier = Modifier.width(120.dp))
    }
    if (!open) return
    val field = @Composable { label: Int, value: String?, set: (String) -> BusinessProfile ->
        PosField(stringResource(label), value.orEmpty(), { draft = set(it) })
    }
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        field(R.string.pos_bp_trading, draft.tradingName) { draft.copy(tradingName = it) }
        field(R.string.pos_bp_legal, draft.legalName) { draft.copy(legalName = it) }
        field(R.string.pos_bp_domain, draft.domain) { draft.copy(domain = it) }
        field(R.string.pos_bp_reg, draft.registrationNumber) { draft.copy(registrationNumber = it.ifBlank { null }) }
        field(R.string.pos_bp_address, draft.addressLine1) { draft.copy(addressLine1 = it.ifBlank { null }) }
        field(R.string.pos_bp_address2, draft.addressLine2) { draft.copy(addressLine2 = it.ifBlank { null }) }
        field(R.string.pos_bp_city, draft.city) { draft.copy(city = it.ifBlank { null }) }
        field(R.string.pos_bp_country, draft.country) { draft.copy(country = it.ifBlank { null }) }
        field(R.string.pos_bp_phone, draft.phone) { draft.copy(phone = it.ifBlank { null }) }
        field(R.string.pos_bp_email, draft.email) { draft.copy(email = it.ifBlank { null }) }
        PosText(stringResource(R.string.pos_bp_admin_only), PosTheme.type.bodySecondary, PosTheme.palette.textMuted)
        PosRowEnd {
            SoftButton(stringResource(R.string.pos_cancel), null, enabled = true, onClick = { open = false; draft = p }, modifier = Modifier.widthIn(max = 140.dp))
            PosPrimaryButton(
                stringResource(R.string.pos_bp_save),
                enabled = state.online && !state.lettersBusy && draft.tradingName.isNotBlank() && draft.legalName.isNotBlank() && draft.domain.isNotBlank(),
                onClick = {
                    dispatch(LetterIntent.SaveProfile(draft))
                    open = false
                },
            )
        }
    }
}

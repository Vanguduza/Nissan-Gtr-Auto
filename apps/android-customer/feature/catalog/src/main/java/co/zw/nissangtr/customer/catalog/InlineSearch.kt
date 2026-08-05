package co.zw.nissangtr.customer.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import co.zw.nissangtr.customer.rpc.CatalogPartHit
import co.zw.nissangtr.customer.rpc.RpcClient
import co.zw.nissangtr.customer.rpc.SearchMode
import co.zw.nissangtr.ui.theme.GtrColors
import kotlinx.coroutines.delay

enum class SuggestKind { Category, Model, Part }

data class SearchSuggestion(
    val kind: SuggestKind,
    val title: String,
    val subtitle: String? = null,
    /** When set, open PDP; otherwise apply as category/model filter. */
    val oem: String? = null,
    val filterQuery: String? = null,
)

/**
 * Editable inline search under the shell top bar.
 * Debounced (~300ms) live suggestions via [RpcClient.searchCatalog] — not Meili.
 * Focus/type stays on Home; never opens the discarded SearchResults page.
 */
@Composable
fun InlineCatalogSearch(
    rpc: RpcClient,
    onOpenProduct: (oem: String) -> Unit,
    onApplyFilter: (query: String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search parts, VIN, model…",
) {
    var query by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<List<SearchSuggestion>>(emptyList()) }

    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) {
            suggestions = emptyList()
            busy = false
            return@LaunchedEffect
        }
        busy = true
        delay(300)
        suggestions = runCatching { fetchSuggestions(rpc, q) }.getOrElse { emptyList() }
        busy = false
    }

    val showPopup = focused && query.trim().length >= 2

    Column(modifier = modifier.fillMaxWidth().zIndex(2f)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, GtrColors.Silver, RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focused = it.isFocused },
                )
            }
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
            }
        }

        if (showPopup) {
            SuggestionPopup(
                suggestions = suggestions,
                busy = busy,
                onSelect = { hit ->
                    focused = false
                    query = hit.title
                    when {
                        hit.oem != null -> onOpenProduct(hit.oem)
                        else -> onApplyFilter(hit.filterQuery ?: hit.title)
                    }
                    suggestions = emptyList()
                },
            )
        }
    }
}

@Composable
private fun SuggestionPopup(
    suggestions: List<SearchSuggestion>,
    busy: Boolean,
    onSelect: (SearchSuggestion) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .heightIn(max = 320.dp)
            .border(1.dp, GtrColors.Mist, RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
            .verticalScroll(rememberScrollState()),
    ) {
        if (suggestions.isEmpty() && !busy) {
            Text(
                "No matches",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        val groups = listOf(
            SuggestKind.Category to "Category / type",
            SuggestKind.Model to "Model",
            SuggestKind.Part to "Part number",
        )
        for ((kind, label) in groups) {
            val rows = suggestions.filter { it.kind == kind }
            if (rows.isEmpty()) continue
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
            rows.forEach { row ->
                SuggestionRow(row, onClick = { onSelect(row) })
                HorizontalDivider(color = GtrColors.Mist)
            }
        }
    }
}

@Composable
private fun SuggestionRow(row: SearchSuggestion, onClick: () -> Unit) {
    val icon: ImageVector = when (row.kind) {
        SuggestKind.Category -> Icons.Filled.Category
        SuggestKind.Model -> Icons.Filled.DirectionsCar
        SuggestKind.Part -> Icons.Filled.Tag
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(row.title, style = MaterialTheme.typography.bodyMedium)
            row.subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private suspend fun fetchSuggestions(rpc: RpcClient, query: String): List<SearchSuggestion> {
    val out = LinkedHashMap<String, SearchSuggestion>()

    fun put(s: SearchSuggestion) {
        val key = "${s.kind}:${s.title.uppercase()}:${s.oem.orEmpty()}"
        out.putIfAbsent(key, s)
    }

    fun absorbParts(hits: List<CatalogPartHit>, asParts: Boolean) {
        for (hit in hits.take(8)) {
            if (asParts) {
                put(
                    SearchSuggestion(
                        kind = SuggestKind.Part,
                        title = hit.oemPartNumber,
                        subtitle = listOfNotNull(hit.categoryName, hit.pncCode).joinToString(" · ").ifBlank { null },
                        oem = hit.oemPartNumber,
                    ),
                )
            }
            hit.categoryName?.trim()?.takeIf { it.isNotEmpty() }?.let { cat ->
                put(
                    SearchSuggestion(
                        kind = SuggestKind.Category,
                        title = cat,
                        subtitle = hit.subcategoryName,
                        filterQuery = cat,
                    ),
                )
            }
            hit.pncCode?.trim()?.takeIf { it.isNotEmpty() }?.let { pnc ->
                put(
                    SearchSuggestion(
                        kind = SuggestKind.Category,
                        title = pnc,
                        subtitle = "PNC",
                        filterQuery = pnc,
                    ),
                )
            }
            listOfNotNull(hit.chassisCode, hit.engineCode).forEach { model ->
                val m = model.trim()
                if (m.isNotEmpty()) {
                    put(
                        SearchSuggestion(
                            kind = SuggestKind.Model,
                            title = m,
                            subtitle = hit.oemPartNumber,
                            filterQuery = m,
                        ),
                    )
                }
            }
        }
    }

    val partRes = runCatching { rpc.searchCatalog(SearchMode.PART, query) }.getOrNull()
    absorbParts(partRes?.parts.orEmpty(), asParts = true)

    val modelRes = runCatching { rpc.searchCatalog(SearchMode.MODEL, query) }.getOrNull()
    absorbParts(modelRes?.parts.orEmpty(), asParts = false)
    modelRes?.parts.orEmpty().take(6).forEach { hit ->
        put(
            SearchSuggestion(
                kind = SuggestKind.Model,
                title = hit.chassisCode ?: hit.engineCode ?: hit.oemPartNumber,
                subtitle = hit.categoryName,
                oem = hit.oemPartNumber.takeIf { hit.chassisCode.isNullOrBlank() && hit.engineCode.isNullOrBlank() },
                filterQuery = hit.chassisCode ?: hit.engineCode ?: query,
            ),
        )
    }

    val pncRes = runCatching { rpc.searchCatalog(SearchMode.PNC, query) }.getOrNull()
    absorbParts(pncRes?.parts.orEmpty(), asParts = false)

    val vinLike = query.length in 11..17 && query.all { it.isLetterOrDigit() }
    if (vinLike) {
        val vinRes = runCatching { rpc.searchCatalog(SearchMode.VIN, query) }.getOrNull()
        absorbParts(vinRes?.parts.orEmpty(), asParts = true)
    }

    return out.values.toList().take(24)
}

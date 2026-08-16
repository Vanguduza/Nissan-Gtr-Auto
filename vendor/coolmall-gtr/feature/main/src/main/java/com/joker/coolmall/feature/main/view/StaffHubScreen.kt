package com.joker.coolmall.feature.main.view

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.zw.nissangtr.management.gtradapter.StaffNavModule
import com.joker.coolmall.core.designsystem.theme.SpacePaddingMedium
import com.joker.coolmall.core.ui.component.appbar.CenterTopAppBar
import com.joker.coolmall.feature.main.R
import com.joker.coolmall.feature.main.component.CommonScaffold
import com.joker.coolmall.feature.main.viewmodel.StaffHubViewModel

@Composable
internal fun StaffHubRoute(
    viewModel: StaffHubViewModel = hiltViewModel(),
    onOpenModule: (String) -> Unit = {},
) {
    val modules by viewModel.modules.collectAsStateWithLifecycle()
    val roles by viewModel.rolesLabel.collectAsStateWithLifecycle()
    val fake by viewModel.fakeMode.collectAsStateWithLifecycle()

    StaffHubScreen(
        modules = modules,
        rolesLabel = roles,
        fakeMode = fake,
        onModuleClick = { mod -> onOpenModule(mod.id) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StaffHubScreen(
    modules: List<StaffNavModule>,
    rolesLabel: String,
    fakeMode: Boolean,
    onModuleClick: (StaffNavModule) -> Unit,
) {
    CommonScaffold(
        topBar = {
            CenterTopAppBar(R.string.staff_hub_title, showBackIcon = false)
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(SpacePaddingMedium),
        ) {
            Text(
                text = stringResource(R.string.staff_hub_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (rolesLabel.isNotBlank()) {
                Text(
                    text = stringResource(R.string.staff_roles_fmt, rolesLabel),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (fakeMode) {
                Text(
                    text = stringResource(R.string.staff_fake_mode),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )
            }
            LazyColumn(
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(modules, key = { it.id }) { mod ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onModuleClick(mod) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(mod.label, style = MaterialTheme.typography.titleMedium)
                            Text(
                                mod.href,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                stringResource(R.string.staff_hub_open_module),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

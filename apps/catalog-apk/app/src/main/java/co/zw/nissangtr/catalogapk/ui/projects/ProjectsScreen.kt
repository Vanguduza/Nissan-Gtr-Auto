package co.zw.nissangtr.catalogapk.ui.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ProjectsScreen(
    viewModel: ProjectsViewModel = viewModel(),
) {
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val signedInId by viewModel.signedInProjectId.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var anonKey by remember { mutableStateOf("") }
    var signInProjectId by remember { mutableStateOf<String?>(null) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var maxPagesText by remember(settings.maxPagesDebug) {
        mutableStateOf(settings.maxPagesDebug.toString())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Supabase Projects", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Anon key only. JWT Edge import uses your session — no service role in the APK.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Crawl gates / slots", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = settings.requireCharging,
                        onCheckedChange = { viewModel.setRequireCharging(it) },
                    )
                    Text("Require charging")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = settings.requireWifi,
                        onCheckedChange = { viewModel.setRequireWifi(it) },
                    )
                    Text("Require unmetered Wi‑Fi")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = settings.debugBypass,
                        onCheckedChange = { viewModel.setDebugBypass(it) },
                    )
                    Text("Debug bypass gates")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { viewModel.setMaxSlots(1) }) {
                        Text(if (settings.maxSlots == 1) "Slots: 1 ✓" else "Slots: 1")
                    }
                    TextButton(onClick = { viewModel.setMaxSlots(2) }) {
                        Text(if (settings.maxSlots == 2) "Slots: 2 ✓" else "Slots: 2")
                    }
                }
                OutlinedTextField(
                    value = maxPagesText,
                    onValueChange = { maxPagesText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Debug max pages (0 = unlimited)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    onClick = {
                        viewModel.setMaxPagesDebug(maxPagesText.toIntOrNull() ?: 0)
                    },
                ) { Text("Save max pages") }
            }
        }

        Button(onClick = { showAdd = true }) { Text("Add project") }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(projects, key = { it.id }) { project ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(project.name, style = MaterialTheme.typography.titleMedium)
                        Text(project.url, style = MaterialTheme.typography.bodySmall)
                        Text(
                            if (signedInId == project.id) "Signed in" else "Not signed in",
                            style = MaterialTheme.typography.labelMedium,
                        )
                        TextButton(onClick = { signInProjectId = project.id }) {
                            Text("Sign in")
                        }
                        if (signedInId == project.id) {
                            TextButton(onClick = { viewModel.signOut(project.id) }) {
                                Text("Sign out")
                            }
                        }
                        TextButton(onClick = { viewModel.deleteProject(project.id) }) {
                            Text("Remove")
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Add Supabase project") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("Project URL") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = anonKey,
                        onValueChange = { anonKey = it },
                        label = { Text("Anon key") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.addProject(name, url, anonKey)
                        showAdd = false
                        name = ""
                        url = ""
                        anonKey = ""
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showAdd = false }) { Text("Cancel") }
            },
        )
    }

    signInProjectId?.let { projectId ->
        AlertDialog(
            onDismissRequest = { signInProjectId = null },
            title = { Text("Sign in") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.signIn(projectId, email, password)
                        signInProjectId = null
                        email = ""
                        password = ""
                    },
                ) { Text("Sign in") }
            },
            dismissButton = {
                TextButton(onClick = { signInProjectId = null }) { Text("Cancel") }
            },
        )
    }
}

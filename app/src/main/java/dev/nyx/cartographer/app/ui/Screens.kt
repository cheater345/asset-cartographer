package dev.nyx.cartographer.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.nyx.cartographer.app.vm.CartographerViewModel
import dev.nyx.cartographer.core.JournalEntry

private const val DEFAULT_DEVICE_PATH = "/storage/emulated/0/Android/data/com.mobile.legends/files"

@Composable
fun BundleScreen(state: CartographerViewModel.UiState, onImportApk: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onImportApk, enabled = !state.busy) {
                Text("Import APK")
            }
            Spacer(Modifier.width(12.dp))
            Text("${state.bundles.size} archive entries", style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(state.bundles) { bundle ->
                ListItem(
                    headlineContent = {
                        Text(
                            bundle.archivePath,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    supportingContent = {
                        val engine = bundle.engineVersion ?: "-"
                        val mode = bundle.compressionMode?.toString() ?: "-"
                        Text("${bundle.kind} · ${bundle.uncompressedSize} B · engine $engine · mode $mode")
                    },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun DeviceScreen(
    state: CartographerViewModel.UiState,
    onBrowse: (String) -> Unit,
    onPickFolder: () -> Unit,
    onRequestShizuku: () -> Unit,
) {
    var path by rememberSaveable { mutableStateOf(DEFAULT_DEVICE_PATH) }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        OutlinedTextField(
            value = path,
            onValueChange = { path = it },
            label = { Text("Directory (read-only listing)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = { onBrowse(path) },
                enabled = !state.busy && state.shizukuGranted,
            ) {
                Text("Browse via Shizuku")
            }
            if (!state.shizukuGranted) {
                TextButton(onClick = onRequestShizuku) {
                    Text(if (state.shizukuAlive) "Grant Shizuku" else "Shizuku not running")
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        OutlinedButton(onClick = onPickFolder, enabled = !state.busy) {
            Text("Index SAF folder instead")
        }
        Spacer(Modifier.height(8.dp))
        Text("${state.deviceEntries.size} entries", style = MaterialTheme.typography.bodyMedium)
        LazyColumn(Modifier.weight(1f)) {
            items(state.deviceEntries) { entry ->
                ListItem(
                    headlineContent = {
                        Text(
                            entry.path,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    supportingContent = {
                        val kind = if (entry.isDirectory) "dir" else "file"
                        Text("$kind · ${entry.sizeBytes} B · ${entry.modifiedAt}")
                    },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun ReportsScreen(
    state: CartographerViewModel.UiState,
    onExport: () -> Unit,
    onRollback: (JournalEntry) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = onExport, enabled = !state.busy) {
                Text("Export reports")
            }
            Text("${state.journal.size} journal entries", style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(state.journal.asReversed()) { entry ->
                ListItem(
                    headlineContent = {
                        Text(
                            "${entry.op} — ${entry.outcome}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    supportingContent = {
                        Text(
                            "${entry.target}\n${entry.detail}",
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    trailingContent = {
                        if (entry.reversible) {
                            TextButton(onClick = { onRollback(entry) }) {
                                Text("Roll back")
                            }
                        }
                    },
                )
                HorizontalDivider()
            }
        }
    }
}

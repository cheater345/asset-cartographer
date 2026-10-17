package dev.nyx.cartographer.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nyx.cartographer.app.vm.CartographerViewModel
import dev.nyx.cartographer.app.vm.CartographerViewModel.Tab

@Composable
fun CartographerTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme(),
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartographerApp(vm: CartographerViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()

    val apkPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importApk)
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(vm::indexSafFolder)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Asset Cartographer") },
                actions = {
                    Text(
                        state.status,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = state.tab == Tab.Bundles,
                    onClick = { vm.selectTab(Tab.Bundles) },
                    icon = { Text("APK") },
                    label = { Text("Bundles") },
                )
                NavigationBarItem(
                    selected = state.tab == Tab.Device,
                    onClick = { vm.selectTab(Tab.Device) },
                    icon = { Text("DEV") },
                    label = { Text("Device") },
                )
                NavigationBarItem(
                    selected = state.tab == Tab.Reports,
                    onClick = { vm.selectTab(Tab.Reports) },
                    icon = { Text("LOG") },
                    label = { Text("Reports") },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state.tab) {
                Tab.Bundles -> BundleScreen(
                    state = state,
                    onImportApk = { apkPicker.launch(arrayOf("*/*")) },
                )

                Tab.Device -> DeviceScreen(
                    state = state,
                    onBrowse = vm::listDeviceDir,
                    onPickFolder = { folderPicker.launch(null) },
                    onRequestShizuku = vm::requestShizukuPermission,
                )

                Tab.Reports -> ReportsScreen(
                    state = state,
                    onExport = vm::exportReports,
                    onRollback = vm::rollback,
                )
            }
        }
    }
}

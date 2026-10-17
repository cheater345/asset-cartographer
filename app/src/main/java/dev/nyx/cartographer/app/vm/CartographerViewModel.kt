package dev.nyx.cartographer.app.vm

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.nyx.cartographer.core.BundleRecord
import dev.nyx.cartographer.core.FileRecord
import dev.nyx.cartographer.core.JournalEntry
import dev.nyx.cartographer.device.DeviceInspector
import dev.nyx.cartographer.device.ReadOnlyShell
import dev.nyx.cartographer.device.SafWalker
import dev.nyx.cartographer.report.InvestigationJournal
import dev.nyx.cartographer.report.ReportWriter
import dev.nyx.cartographer.unityfs.ApkScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class CartographerViewModel(app: Application) : AndroidViewModel(app) {

    enum class Tab { Bundles, Device, Reports }

    data class UiState(
        val tab: Tab = Tab.Bundles,
        val bundles: List<BundleRecord> = emptyList(),
        val deviceEntries: List<FileRecord> = emptyList(),
        val journal: List<JournalEntry> = emptyList(),
        val busy: Boolean = false,
        val status: String = "idle",
        val shizukuAlive: Boolean = false,
        val shizukuGranted: Boolean = false,
    )

    private data class OpResult(val detail: String, val artifacts: List<File> = emptyList())

    private val shell = ReadOnlyShell()
    private val inspector = DeviceInspector(shell)
    private val safWalker = SafWalker(app)
    private val scanner = ApkScanner()
    private val journal = InvestigationJournal(File(app.filesDir, "journal"))
    private val reports = ReportWriter(File(app.filesDir, "reports"))

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refreshJournal()
    }

    fun selectTab(tab: Tab) {
        _state.update { it.copy(tab = tab) }
    }

    fun setShizukuState(alive: Boolean, granted: Boolean) {
        _state.update { it.copy(shizukuAlive = alive, shizukuGranted = granted) }
    }

    fun requestShizukuPermission() {
        ReadOnlyShell.requestIfNeeded()
    }

    fun importApk(uri: Uri) = runOperation("apk.import", uri.toString()) {
        val local = copyToCache(uri)
        val records = scanner.scan(local)
        _state.update { it.copy(bundles = records) }
        OpResult("imported ${records.size} archive entries", listOf(local))
    }

    fun listDeviceDir(path: String) = runOperation("device.listDir", path) {
        val entries = inspector.list(path)
        _state.update { it.copy(deviceEntries = entries) }
        OpResult("listed ${entries.size} entries")
    }

    fun indexSafFolder(uri: Uri) = runOperation("saf.walk", uri.toString()) {
        try {
            getApplication<Application>().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            // grant is transient; the walk below still works for this session
        }
        val snapshot = safWalker.walk(uri)
        _state.update { it.copy(deviceEntries = snapshot.records) }
        val suffix = if (snapshot.truncated) " (truncated at cap)" else ""
        OpResult("indexed ${snapshot.records.size} entries$suffix")
    }

    fun exportReports() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, status = "report.export…") }
            val started = System.currentTimeMillis()
            try {
                val stamp = started.toString()
                val current = _state.value
                val artifacts = withContext(Dispatchers.IO) {
                    reports.writeBundleReport(current.bundles, stamp) +
                        reports.writeDeviceReport(current.deviceEntries, stamp) +
                        reports.writeJournal(current.journal, stamp)
                }
                journal.record(
                    JournalEntry(
                        id = UUID.randomUUID().toString(),
                        wallClock = started,
                        op = "report.export",
                        target = reports.directory.absolutePath,
                        outcome = "ok",
                        detail = "${artifacts.size} files",
                        artifacts = artifacts.map { it.absolutePath },
                        reversible = true,
                    )
                )
                refreshJournal()
                _state.update { it.copy(busy = false, status = "exported ${artifacts.size} files") }
            } catch (t: Throwable) {
                journal.record(errorEntry("report.export", reports.directory.absolutePath, started, t))
                refreshJournal()
                _state.update {
                    it.copy(busy = false, status = "export failed — ${t.message ?: t::class.java.simpleName}")
                }
            }
        }
    }

    fun rollback(entry: JournalEntry) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { journal.rollback(entry) }
            refreshJournal()
            _state.update {
                it.copy(status = if (ok) "rolled back ${entry.op}" else "rollback partial — check journal")
            }
        }
    }

    private fun runOperation(op: String, target: String, block: suspend () -> OpResult) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, status = "$op…") }
            val started = System.currentTimeMillis()
            try {
                val result = withContext(Dispatchers.IO) { block() }
                journal.record(
                    JournalEntry(
                        id = UUID.randomUUID().toString(),
                        wallClock = started,
                        op = op,
                        target = target,
                        outcome = "ok",
                        detail = result.detail,
                        artifacts = result.artifacts.map { it.absolutePath },
                        reversible = result.artifacts.isNotEmpty(),
                    )
                )
                refreshJournal()
                _state.update { it.copy(busy = false, status = "$op ok — ${result.detail}") }
            } catch (t: Throwable) {
                journal.record(errorEntry(op, target, started, t))
                refreshJournal()
                _state.update {
                    it.copy(busy = false, status = "$op failed — ${t.message ?: t::class.java.simpleName}")
                }
            }
        }
    }

    private fun errorEntry(op: String, target: String, started: Long, t: Throwable) = JournalEntry(
        id = UUID.randomUUID().toString(),
        wallClock = started,
        op = op,
        target = target,
        outcome = "error",
        detail = t.message ?: t::class.java.simpleName,
    )

    private fun refreshJournal() {
        val entries = journal.read()
        _state.update { it.copy(journal = entries) }
    }

    private fun copyToCache(uri: Uri): File {
        val app = getApplication<Application>()
        val out = File(app.cacheDir, "imported-${System.currentTimeMillis()}.apk")
        app.contentResolver.openInputStream(uri)?.use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("cannot open input stream for $uri")
        return out
    }
}

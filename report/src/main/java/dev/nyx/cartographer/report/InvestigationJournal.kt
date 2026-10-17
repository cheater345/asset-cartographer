package dev.nyx.cartographer.report

import dev.nyx.cartographer.core.JournalEntry
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Append-only JSONL journal of every operation. Rollback is a compensating
 * action that only ever deletes artifacts whose paths this tool itself
 * recorded — nothing outside the app sandbox is touched.
 */
class InvestigationJournal(private val dir: File) {

    private val json = Json { encodeDefaults = true }
    private val file: File get() = File(dir, "journal.jsonl")

    init {
        dir.mkdirs()
    }

    fun record(entry: JournalEntry): JournalEntry {
        file.appendText(json.encodeToString(JournalEntry.serializer(), entry) + "\n")
        return entry
    }

    fun read(): List<JournalEntry> =
        if (!file.exists()) emptyList()
        else file.readLines()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                try {
                    json.decodeFromString(JournalEntry.serializer(), line)
                } catch (_: Exception) {
                    null
                }
            }

    fun rollback(entry: JournalEntry): Boolean {
        val ok = entry.artifacts.all { path ->
            val f = File(path)
            !f.exists() || f.delete()
        }
        record(
            JournalEntry(
                id = UUID.randomUUID().toString(),
                wallClock = System.currentTimeMillis(),
                op = "journal.rollback",
                target = entry.id,
                outcome = if (ok) "ok" else "partial",
                detail = "compensates ${entry.op}",
                reversible = false,
            )
        )
        return ok
    }
}

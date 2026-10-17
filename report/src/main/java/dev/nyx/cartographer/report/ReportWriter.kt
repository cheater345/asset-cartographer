package dev.nyx.cartographer.report

import dev.nyx.cartographer.core.BundleRecord
import dev.nyx.cartographer.core.FileRecord
import dev.nyx.cartographer.core.JournalEntry
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/** Writes documentation artifacts as JSON + CSV. All paths land in [directory]. */
class ReportWriter(val directory: File) {

    private val json = Json { prettyPrint = true }

    init {
        directory.mkdirs()
    }

    fun writeBundleReport(records: List<BundleRecord>, stamp: String): List<File> {
        val jsonFile = File(directory, "bundles-$stamp.json")
        val csvFile = File(directory, "bundles-$stamp.csv")

        jsonFile.writeText(json.encodeToString(ListSerializer(BundleRecord.serializer()), records))

        csvFile.writeText(buildString {
            appendLine(
                "archive_path,kind,compressed_size,uncompressed_size,crc32," +
                    "format_version,engine_version,engine_revision,compression_mode"
            )
            for (r in records) {
                appendLine(
                    listOf(
                        r.archivePath,
                        r.kind,
                        r.compressedSize.toString(),
                        r.uncompressedSize.toString(),
                        r.crc32.toString(),
                        r.formatVersion?.toString() ?: "",
                        r.engineVersion ?: "",
                        r.engineRevision ?: "",
                        r.compressionMode?.toString() ?: "",
                    ).joinToString(",") { csvEscape(it) }
                )
            }
        })

        return listOf(jsonFile, csvFile)
    }

    fun writeDeviceReport(records: List<FileRecord>, stamp: String): File {
        val jsonFile = File(directory, "device-$stamp.json")
        jsonFile.writeText(json.encodeToString(ListSerializer(FileRecord.serializer()), records))
        return jsonFile
    }

    fun writeJournal(entries: List<JournalEntry>, stamp: String): File {
        val jsonFile = File(directory, "journal-$stamp.json")
        jsonFile.writeText(json.encodeToString(ListSerializer(JournalEntry.serializer()), entries))
        return jsonFile
    }

    private fun csvEscape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
}

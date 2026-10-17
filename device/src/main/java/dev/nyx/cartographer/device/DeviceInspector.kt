package dev.nyx.cartographer.device

import dev.nyx.cartographer.core.FileRecord
import java.time.Instant

/**
 * Turns [CartographerUserService]'s record format into structured entries:
 * `name|sizeBytes|modifiedAtMillis|d|f`, one per line, with names escaped
 * (`%7C`, `%0A`) on the service side.
 */
class DeviceInspector(private val shell: ReadOnlyShell) {

    suspend fun list(dir: String): List<FileRecord> {
        val result = shell.exec(ReadOnlyShell.Op.ListDir(dir))
        if (result.exitCode != 0) return emptyList()

        return result.stdout.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != CartographerUserService.TRUNCATED }
            .mapNotNull { line ->
                val parts = line.split('|')
                if (parts.size < 4) return@mapNotNull null

                val name = unescape(parts[0])
                val millis = parts[2].toLongOrNull() ?: 0L

                FileRecord(
                    path = "$dir/$name",
                    sizeBytes = parts[1].toLongOrNull() ?: -1L,
                    isDirectory = parts[3] == "d",
                    modifiedAt = Instant.ofEpochMilli(millis).toString(),
                )
            }
            .toList()
    }

    private fun unescape(value: String): String =
        value.replace("%0A", "\n").replace("%7C", "|")
}

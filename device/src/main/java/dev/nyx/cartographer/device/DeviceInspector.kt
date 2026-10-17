package dev.nyx.cartographer.device

import dev.nyx.cartographer.core.FileRecord

/**
 * Turns read-only shell output into structured records. Parses toybox
 * `ls -la` output (ISO timestamps, Android 10+).
 */
class DeviceInspector(private val shell: ReadOnlyShell) {

    private val lsLine = Regex(
        """([\-dlcbps][rwxstST\-]{9}[.+]?)\s+\d+\s+(\S+)\s+(\S+)\s+(\d+)\s+(\d{4}-\d{2}-\d{2})\s+(\d{2}:\d{2})\s+(.+)"""
    )

    suspend fun list(dir: String): List<FileRecord> {
        val result = shell.exec(ReadOnlyShell.Op.ListDir(dir))
        if (result.exitCode != 0) return emptyList()

        return result.stdout.lineSequence().mapNotNull { line ->
            val match = lsLine.matchEntire(line.trim()) ?: return@mapNotNull null
            val (perms, owner, group, size, date, time, name) = match.destructured
            if (name == "." || name == "..") return@mapNotNull null

            FileRecord(
                path = "$dir/$name",
                sizeBytes = size.toLongOrNull() ?: -1L,
                owner = "$owner:$group",
                isDirectory = perms.startsWith("d"),
                modifiedAt = "$date $time",
            )
        }.toList()
    }
}

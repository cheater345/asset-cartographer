package dev.nyx.cartographer.unityfs

import dev.nyx.cartographer.core.BundleRecord
import java.io.File
import java.util.zip.ZipFile

/**
 * Statically scans an APK file's `assets/` tree. The APK is supplied by the
 * user (imported via SAF and copied to app cache, since ZipFile needs a real
 * path). Nothing here touches a running process.
 */
class ApkScanner {

    fun scan(apk: File): List<BundleRecord> {
        val records = mutableListOf<BundleRecord>()

        ZipFile(apk).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory) continue
                if (!entry.name.startsWith("assets/")) continue
                if (entry.size < MIN_CANDIDATE_SIZE) continue

                val head = zip.getInputStream(entry).use { stream ->
                    val buf = ByteArray(HEADER_PROBE_BYTES)
                    val read = stream.read(buf)
                    if (read >= 32) buf.copyOf(read) else null
                } ?: continue

                val header = UnityFsHeader.parse(head, head.size)

                records += when {
                    header != null -> BundleRecord(
                        archivePath = entry.name,
                        compressedSize = entry.compressedSize,
                        uncompressedSize = entry.size,
                        crc32 = entry.crc,
                        kind = KIND_UNITYFS,
                        formatVersion = header.formatVersion,
                        engineVersion = header.engineVersion,
                        engineRevision = header.engineRevision,
                        compressionMode = header.compressionMode,
                    )

                    looksLikeBundle(entry.name) -> BundleRecord(
                        archivePath = entry.name,
                        compressedSize = entry.compressedSize,
                        uncompressedSize = entry.size,
                        crc32 = entry.crc,
                        kind = KIND_UNRECOGNIZED,
                    )

                    else -> BundleRecord(
                        archivePath = entry.name,
                        compressedSize = entry.compressedSize,
                        uncompressedSize = entry.size,
                        crc32 = entry.crc,
                        kind = KIND_OTHER,
                    )
                }
            }
        }

        return records
    }

    private fun looksLikeBundle(name: String): Boolean =
        name.endsWith(".unity3d") || name.endsWith(".bundle") || name.contains(".assets")

    companion object {
        const val KIND_UNITYFS = "unityfs"
        const val KIND_UNRECOGNIZED = "unrecognized"
        const val KIND_OTHER = "other"

        private const val MIN_CANDIDATE_SIZE = 128L
        private const val HEADER_PROBE_BYTES = 512
    }
}

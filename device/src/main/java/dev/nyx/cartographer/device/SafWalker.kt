package dev.nyx.cartographer.device

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dev.nyx.cartographer.core.FileRecord

/**
 * SAF fallback: walks a user-granted document tree and snapshots file
 * metadata. Caps depth and entry count so a giant tree can't blow memory.
 */
class SafWalker(private val context: Context) {

    data class Snapshot(val records: List<FileRecord>, val truncated: Boolean)

    fun walk(treeUri: Uri, maxDepth: Int = 8, maxEntries: Int = 20_000): Snapshot {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: return Snapshot(emptyList(), false)

        val records = mutableListOf<FileRecord>()
        var truncated = false

        fun visit(doc: DocumentFile, depth: Int) {
            if (truncated || depth > maxDepth) return
            for (child in doc.listFiles()) {
                if (records.size >= maxEntries) {
                    truncated = true
                    return
                }
                records += FileRecord(
                    path = relativePath(root, child),
                    sizeBytes = child.length(),
                    isDirectory = child.isDirectory,
                    modifiedAt = child.lastModified().toString(),
                )
                if (child.isDirectory) visit(child, depth + 1)
            }
        }

        visit(root, 0)
        return Snapshot(records, truncated)
    }

    private fun relativePath(root: DocumentFile, doc: DocumentFile): String =
        doc.uri.toString().removePrefix(root.uri.toString())
}

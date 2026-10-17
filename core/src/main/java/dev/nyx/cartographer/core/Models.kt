package dev.nyx.cartographer.core

import kotlinx.serialization.Serializable

/** A single directory entry observed through a read-only listing. */
@Serializable
data class FileRecord(
    val path: String,
    val sizeBytes: Long,
    val owner: String = "",
    val isDirectory: Boolean = false,
    val modifiedAt: String = "",
)

/** A container found inside an APK's `assets/` tree, classified by header parse. */
@Serializable
data class BundleRecord(
    val archivePath: String,
    val compressedSize: Long,
    val uncompressedSize: Long,
    val crc32: Long,
    val kind: String,
    val formatVersion: Int? = null,
    val engineVersion: String? = null,
    val engineRevision: String? = null,
    val compressionMode: Int? = null,
)

/**
 * One operation, one receipt. Reads are reversible = false (nothing to undo).
 * Tool-created artifacts are reversible = true and list their paths.
 */
@Serializable
data class JournalEntry(
    val id: String,
    val wallClock: Long,
    val op: String,
    val target: String,
    val outcome: String,
    val detail: String = "",
    val artifacts: List<String> = emptyList(),
    val reversible: Boolean = false,
)

package dev.nyx.cartographer.unityfs

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Preamble of a UnityFS container.
 *
 * Field layout follows the community-documented format (AssetStudio / UnityPy):
 * magic "UnityFS\0", format version, engine version string, engine revision
 * string, declared archive size, compressed blocksInfo size, uncompressed
 * blocksInfo size, flags. All integers are big-endian.
 */
class UnityFsHeader(
    val formatVersion: Int,
    val engineVersion: String,
    val engineRevision: String,
    val declaredSize: Long,
    val compressedBlocksInfoSize: Int,
    val uncompressedBlocksInfoSize: Int,
    val flags: Int,
) {
    /** 0 = none, 1 = LZ4, 2 = LZ4HC, 3 = LZMA. */
    val compressionMode: Int get() = flags and 0x3F

    val blocksInfoAtEnd: Boolean get() = flags and 0x40 != 0

    companion object {
        private val MAGIC = byteArrayOf(
            0x55, 0x6E, 0x69, 0x74, 0x79, 0x46, 0x53, 0x00, // "UnityFS\0"
        )

        /**
         * Returns null when [data] is not a plain UnityFS container — which is
         * itself a useful finding for the report (possibly encrypted content).
         */
        fun parse(data: ByteArray, length: Int): UnityFsHeader? = runCatching {
            require(length >= 32 && data.size >= length)

            for (i in MAGIC.indices) {
                require(data[i] == MAGIC[i]) { "magic mismatch" }
            }

            val buf = ByteBuffer.wrap(data, 0, length).order(ByteOrder.BIG_ENDIAN)
            buf.position(MAGIC.size)

            val formatVersion = buf.int
            val engineVersion = buf.readCString()
            val engineRevision = buf.readCString()
            val declaredSize = buf.long
            val compressed = buf.int
            val uncompressed = buf.int
            val flags = buf.int

            UnityFsHeader(
                formatVersion = formatVersion,
                engineVersion = engineVersion,
                engineRevision = engineRevision,
                declaredSize = declaredSize,
                compressedBlocksInfoSize = compressed,
                uncompressedBlocksInfoSize = uncompressed,
                flags = flags,
            )
        }.getOrNull()

        private fun ByteBuffer.readCString(): String {
            val sb = StringBuilder(16)
            while (remaining() > 0) {
                val b = get().toInt()
                if (b == 0) break
                sb.append(b.toChar())
            }
            return sb.toString()
        }
    }
}

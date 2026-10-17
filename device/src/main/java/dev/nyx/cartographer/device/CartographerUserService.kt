package dev.nyx.cartographer.device

import java.io.File

/**
 * Runs as the shell user in its own process, started by Shizuku
 * (see read-only shell's bindUserService). Exposes exactly two read
 * operations over binder — directory listing and single-path metadata.
 *
 * Note: user services are not declared in the manifest; the Shizuku process
 * loads this class from the app APK by name at runtime. If this project ever
 * enables R8 minification, keep this class and its default constructor.
 */
class CartographerUserService : IRemoteFs.Stub() {

    override fun listDir(path: String): String {
        val children = File(path).listFiles() ?: return MISSING

        val out = StringBuilder(4096)
        var lines = 0
        for (child in children) {
            if (lines >= MAX_LINES || out.length >= MAX_CHARS) {
                out.append(TRUNCATED).append('\n')
                break
            }
            out.append(escape(child.name)).append('|')
                .append(child.length()).append('|')
                .append(child.lastModified()).append('|')
                .append(if (child.isDirectory) 'd' else 'f')
                .append('\n')
            lines++
        }
        return out.toString()
    }

    override fun statPath(path: String): String {
        val f = File(path)
        if (!f.exists()) return MISSING
        return "${escape(f.name)}|${f.length()}|${f.lastModified()}|${if (f.isDirectory) 'd' else 'f'}"
    }

    override fun destroy() {
        System.exit(0)
    }

    private fun escape(value: String): String =
        value.replace("|", "%7C").replace("\n", "%0A")

    companion object {
        const val MISSING = "__MISSING__"
        const val TRUNCATED = "__TRUNCATED__"

        // Binder transactions are capped around 1 MiB; stay well below.
        private const val MAX_LINES = 4000
        private const val MAX_CHARS = 600_000
    }
}

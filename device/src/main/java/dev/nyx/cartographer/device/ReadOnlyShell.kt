package dev.nyx.cartographer.device

import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/**
 * The only surface this app has onto another app's files, by design.
 *
 * Every capability is a typed, read-only operation assembled as argv (no
 * shell interpreter, no generic exec(String)). There is no write op anywhere
 * in [Op] — adding one is a visible, reviewable change, not a quiet one-liner.
 */
class ReadOnlyShell {

    sealed interface Op {
        data class ListDir(val path: String) : Op
        data class Stat(val path: String) : Op
    }

    data class ShellResult(val exitCode: Int, val stdout: String, val stderr: String)

    companion object {
        const val REQUEST_CODE = 0x5A17

        fun binderAlive(): Boolean = try {
            Shizuku.pingBinder()
        } catch (_: Throwable) {
            false
        }

        fun permissionState(): Int =
            if (!binderAlive()) PackageManager.PERMISSION_DENIED
            else Shizuku.checkSelfPermission()

        fun permissionGranted(): Boolean =
            permissionState() == PackageManager.PERMISSION_GRANTED

        /** Safe to call anytime; no-ops when Shizuku is absent or already granted. */
        fun requestIfNeeded() {
            if (!binderAlive()) return
            if (permissionGranted()) return
            if (Shizuku.shouldShowRequestPermissionRationale()) return
            Shizuku.requestPermission(REQUEST_CODE)
        }

        private fun argv(op: Op): Array<String> {
            val path = when (op) {
                is Op.ListDir -> op.path
                is Op.Stat -> op.path
            }
            // Absolute-path invariant: an argv item starting with '/' can never
            // be parsed as an option, so flag injection is structurally impossible.
            require(path.startsWith("/")) { "paths must be absolute" }

            return when (op) {
                is Op.ListDir -> arrayOf("ls", "-la", path)
                is Op.Stat -> arrayOf("stat", "-c", "%n|%s|%Y", path)
            }
        }
    }

    suspend fun exec(op: Op): ShellResult = withContext(Dispatchers.IO) {
        check(binderAlive()) { "Shizuku is not running" }
        check(permissionGranted()) { "Shizuku permission not granted" }

        val process = Shizuku.newProcess(argv(op), null, null)
        val stdout = process.inputStream.bufferedReader().use { it.readText() }
        val stderr = process.errorStream.bufferedReader().use { it.readText() }
        val exit = process.waitFor()

        ShellResult(exit, stdout, stderr)
    }
}

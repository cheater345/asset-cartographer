package dev.nyx.cartographer.device

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.RemoteException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

/**
 * The only surface this app has onto another app's files, by design.
 *
 * Shizuku API 13 removed the old `Shizuku.newProcess`; the supported path is
 * a user service. [CartographerUserService] runs as the shell user in its own
 * process and exposes exactly two read operations over binder ([IRemoteFs]).
 * There is no write op anywhere — adding one is a visible, reviewable change
 * to the AIDL surface, not a quiet one-liner.
 */
class ReadOnlyShell(private val context: Context) {

    sealed interface Op {
        data class ListDir(val path: String) : Op
        data class Stat(val path: String) : Op
    }

    data class ShellResult(val exitCode: Int, val stdout: String, val stderr: String)

    @Volatile
    private var remote: IRemoteFs? = null

    @Volatile
    private var pendingBind: CompletableDeferred<IRemoteFs>? = null

    private val bindLock = Mutex()

    private val serviceArgs = Shizuku.UserServiceArgs(
        // Resolved at runtime, so the name stays correct even if the class is
        // renamed by minification (the `tag` below keeps the identity stable).
        ComponentName(context.packageName, CartographerUserService::class.java.name),
    )
        .daemon(false)
        .tag("cartographer-readonly-fs")
        .processNameSuffix("readonly")
        .debuggable((context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0)
        .version(1)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val pending = pendingBind
            pendingBind = null
            val svc = binder?.let { IRemoteFs.Stub.asInterface(it) }
            if (svc != null) {
                pending?.complete(svc)
            } else {
                pending?.completeExceptionally(IllegalStateException("user service returned a null binder"))
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            remote = null
        }

        override fun onNullBinding(name: ComponentName?) {
            val pending = pendingBind
            pendingBind = null
            pending?.completeExceptionally(
                IllegalStateException("user service returned a null binding — check the debuggable flag"),
            )
        }
    }

    suspend fun exec(op: Op): ShellResult = withContext(Dispatchers.IO) {
        val path = when (op) {
            is Op.ListDir -> op.path
            is Op.Stat -> op.path
        }
        // Absolute-path invariant: resolved against the remote File API only.
        require(path.startsWith("/")) { "paths must be absolute" }

        val svc = bind()
        try {
            when (op) {
                is Op.ListDir -> {
                    val out = svc.listDir(op.path)
                    if (out == CartographerUserService.MISSING) {
                        ShellResult(1, "", "not a readable directory: ${op.path}")
                    } else {
                        ShellResult(0, out, "")
                    }
                }

                is Op.Stat -> {
                    val out = svc.statPath(op.path)
                    if (out == CartographerUserService.MISSING) {
                        ShellResult(1, "", "not found: ${op.path}")
                    } else {
                        ShellResult(0, out, "")
                    }
                }
            }
        } catch (e: RemoteException) {
            remote = null
            ShellResult(1, "", "remote exception: ${e.message}")
        }
    }

    /** Stops the user service process. Safe to call anytime. */
    fun shutdown() {
        val svc = remote ?: return
        remote = null
        try {
            svc.destroy()
        } catch (_: Throwable) {
            // the process exits mid-transaction; failing here is expected
        }
        try {
            Shizuku.unbindUserService(serviceArgs, connection, true)
        } catch (_: Throwable) {
            // already unbound
        }
    }

    private suspend fun bind(): IRemoteFs {
        remote?.let { return it }
        return bindLock.withLock {
            remote ?: run {
                check(Shizuku.pingBinder()) { "Shizuku is not running" }
                check(permissionGranted()) { "Shizuku permission not granted" }

                val deferred = CompletableDeferred<IRemoteFs>()
                pendingBind = deferred
                try {
                    Shizuku.bindUserService(serviceArgs, connection)
                } catch (t: Throwable) {
                    pendingBind = null
                    throw IllegalStateException("could not bind user service: ${t.message}", t)
                }

                val svc = withTimeoutOrNull(BIND_TIMEOUT_MS) { deferred.await() }
                if (svc == null) {
                    pendingBind = null
                    throw IllegalStateException(
                        "timed out waiting for user service — is Shizuku running and permission granted?",
                    )
                }
                remote = svc
                svc
            }
        }
    }

    companion object {
        const val REQUEST_CODE = 0x5A17
        private const val BIND_TIMEOUT_MS = 15_000L

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
    }
}

package dev.nyx.cartographer.app

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import dev.nyx.cartographer.app.ui.CartographerApp
import dev.nyx.cartographer.app.ui.CartographerTheme
import dev.nyx.cartographer.app.vm.CartographerViewModel
import dev.nyx.cartographer.device.ReadOnlyShell
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private val vm: CartographerViewModel by viewModels()

    private val binderReceived = Shizuku.OnBinderReceivedListener { refreshShizuku() }
    private val binderDead = Shizuku.OnBinderDeadListener { refreshShizuku() }
    private val permissionResult = Shizuku.OnRequestPermissionResultListener { _, _ -> refreshShizuku() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionResult)
        refreshShizuku()

        setContent {
            CartographerTheme {
                CartographerApp(vm)
            }
        }
    }

    override fun onDestroy() {
        Shizuku.removeBinderReceivedListener(binderReceived)
        Shizuku.removeBinderDeadListener(binderDead)
        Shizuku.removeRequestPermissionResultListener(permissionResult)
        super.onDestroy()
    }

    private fun refreshShizuku() {
        val alive = ReadOnlyShell.binderAlive()
        val granted = alive && ReadOnlyShell.permissionState() == PackageManager.PERMISSION_GRANTED
        vm.setShizukuState(alive, granted)
    }
}

package com.parcha.spoilnot.core.firebase

import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.parcha.spoilnot.BuildConfig

/** Android 17 (API 37) gates private-network addresses, including the emulator host 10.0.2.2. */
const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
private const val API_LOCAL_NETWORK_PERMISSION = 37

/**
 * Dev debug builds only: asks once for local network access so the app can reach the Firebase emulators.
 * Does nothing in builds that use real Firebase; those never declare the permission.
 */
@Composable
fun RequestEmulatorNetworkAccess() {
    if (BuildConfig.FIREBASE_EMULATOR_HOST.isBlank() || Build.VERSION.SDK_INT < API_LOCAL_NETWORK_PERMISSION) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, ACCESS_LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED) {
            launcher.launch(ACCESS_LOCAL_NETWORK)
        }
    }
}

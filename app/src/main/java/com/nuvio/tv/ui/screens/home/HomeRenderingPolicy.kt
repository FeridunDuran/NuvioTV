package com.nuvio.tv.ui.screens.home

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.nuvio.tv.ui.screens.player.NuvioExoPlayerPerformanceHelper

private const val REDUCED_EFFECTS_RAM_LIMIT = 1536L * 1024 * 1024

@Composable
internal fun rememberReducedHomeEffects(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val ram = NuvioExoPlayerPerformanceHelper.getDevicePhysicalRamBytes(context)
        ram in 1 until REDUCED_EFFECTS_RAM_LIMIT && !Log.isLoggable("NuvioHomeLegacy", Log.DEBUG)
    }
}

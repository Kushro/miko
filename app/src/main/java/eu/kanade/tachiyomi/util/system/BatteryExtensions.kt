package eu.kanade.tachiyomi.util.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/*
 * MIKO — charging state helpers, mirroring NetworkStateTracker so the downloader can react to the
 * charger the same way it reacts to the network.
 */

/**
 * Whether the device is currently plugged in, read from the sticky `ACTION_BATTERY_CHANGED`
 * broadcast. A full battery still counts as charging (the charger is attached).
 */
fun Context.isCharging(): Boolean {
    val intent = ContextCompat.registerReceiver(
        this,
        null,
        IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        ContextCompat.RECEIVER_NOT_EXPORTED,
    ) ?: return false
    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
}

/**
 * Emits the charging state, starting with the current one and then on every plug/unplug.
 */
fun Context.chargingStateFlow(): Flow<Boolean> = callbackFlow {
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            trySend(intent.action == Intent.ACTION_POWER_CONNECTED)
        }
    }
    val filter = IntentFilter().apply {
        addAction(Intent.ACTION_POWER_CONNECTED)
        addAction(Intent.ACTION_POWER_DISCONNECTED)
    }

    ContextCompat.registerReceiver(
        this@chargingStateFlow,
        receiver,
        filter,
        ContextCompat.RECEIVER_NOT_EXPORTED,
    )
    trySend(isCharging())

    awaitClose {
        unregisterReceiver(receiver)
    }
}.distinctUntilChanged()

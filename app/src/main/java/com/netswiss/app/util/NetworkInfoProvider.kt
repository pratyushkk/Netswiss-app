package com.netswiss.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.*
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NetworkState(
    val networkType: String = "Unknown",
    val signalStrengthDbm: Int = -999,
    val isConnected: Boolean = false
)

class NetworkInfoProvider(private val context: Context) {

    private val _networkState = MutableStateFlow(NetworkState())
    val networkState: StateFlow<NetworkState> = _networkState.asStateFlow()

    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private var telephonyCallback: Any? = null
    private var phoneStateListener: PhoneStateListener? = null

    fun startListening() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            _networkState.value = NetworkState(networkType = "No Permission")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            startTelephonyCallback()
        } else {
            startPhoneStateListener()
        }

        // Initial read
        updateNetworkType()
    }

    @Suppress("DEPRECATION")
    private fun startPhoneStateListener() {
        phoneStateListener = object : PhoneStateListener() {
            @Deprecated("Deprecated in Java")
            override fun onSignalStrengthsChanged(signalStrength: SignalStrength?) {
                super.onSignalStrengthsChanged(signalStrength)
                val dbm = extractSignalDbm(signalStrength)
                _networkState.value = _networkState.value.copy(
                    signalStrengthDbm = dbm,
                    isConnected = dbm > -140
                )
            }

            @Deprecated("Deprecated in Java")
            override fun onDataConnectionStateChanged(state: Int, networkType: Int) {
                super.onDataConnectionStateChanged(state, networkType)
                updateNetworkType()
            }
        }

        try {
            telephonyManager.listen(
                phoneStateListener!!,
                PhoneStateListener.LISTEN_SIGNAL_STRENGTHS or PhoneStateListener.LISTEN_DATA_CONNECTION_STATE
            )
        } catch (_: Exception) {}
    }

    private fun startTelephonyCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val callback = object : TelephonyCallback(),
                TelephonyCallback.SignalStrengthsListener,
                TelephonyCallback.DataConnectionStateListener {

                override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                    val dbm = extractSignalDbm(signalStrength)
                    _networkState.value = _networkState.value.copy(
                        signalStrengthDbm = dbm,
                        isConnected = dbm > -140
                    )
                }

                override fun onDataConnectionStateChanged(state: Int, networkType: Int) {
                    updateNetworkType()
                }
            }

            try {
                telephonyManager.registerTelephonyCallback(
                    context.mainExecutor, callback
                )
                telephonyCallback = callback
            } catch (_: Exception) {}
        }
    }

    @Suppress("DEPRECATION")
    private fun updateNetworkType() {
        val type = try {
            when (telephonyManager.dataNetworkType) {
                TelephonyManager.NETWORK_TYPE_NR -> "5G NR"
                TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
                TelephonyManager.NETWORK_TYPE_HSPAP,
                TelephonyManager.NETWORK_TYPE_HSPA,
                TelephonyManager.NETWORK_TYPE_HSDPA,
                TelephonyManager.NETWORK_TYPE_HSUPA -> "3G HSPA+"
                TelephonyManager.NETWORK_TYPE_UMTS -> "3G UMTS"
                TelephonyManager.NETWORK_TYPE_EDGE -> "2G EDGE"
                TelephonyManager.NETWORK_TYPE_GPRS -> "2G GPRS"
                TelephonyManager.NETWORK_TYPE_CDMA,
                TelephonyManager.NETWORK_TYPE_EVDO_0,
                TelephonyManager.NETWORK_TYPE_EVDO_A,
                TelephonyManager.NETWORK_TYPE_EVDO_B -> "3G CDMA"
                TelephonyManager.NETWORK_TYPE_1xRTT -> "2G CDMA"
                TelephonyManager.NETWORK_TYPE_IWLAN -> "WiFi"
                else -> "Unknown"
            }
        } catch (_: SecurityException) {
            "No Permission"
        }

        _networkState.value = _networkState.value.copy(networkType = type)
    }

    private fun extractSignalDbm(signalStrength: SignalStrength?): Int {
        if (signalStrength == null) return -999

        // Try NR (5G) first
        val cellSignalStrengths = signalStrength.cellSignalStrengths
        for (css in cellSignalStrengths) {
            when (css) {
                is CellSignalStrengthNr -> return css.dbm
                is CellSignalStrengthLte -> return css.dbm
                is CellSignalStrengthWcdma -> return css.dbm
                is CellSignalStrengthGsm -> return css.dbm
                is CellSignalStrengthCdma -> return css.dbm
            }
        }

        return -999
    }

    @Suppress("DEPRECATION")
    fun stopListening() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (telephonyCallback as? TelephonyCallback)?.let {
                try {
                    telephonyManager.unregisterTelephonyCallback(it)
                } catch (_: Exception) {}
            }
        } else {
            phoneStateListener?.let {
                try {
                    telephonyManager.listen(it, PhoneStateListener.LISTEN_NONE)
                } catch (_: Exception) {}
            }
        }
    }
}

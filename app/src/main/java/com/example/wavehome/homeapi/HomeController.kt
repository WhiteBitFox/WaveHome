package com.example.wavehome.homeapi

import android.content.Context
import android.util.Log
import androidx.activity.ComponentActivity
import com.example.wavehome.SmartDeviceType
import com.example.wavehome.SmartHomeAction
import com.example.wavehome.SmartHomeDeviceSnapshot
import com.google.home.FactoryRegistry
import com.google.home.Home
import com.google.home.HomeClient
import com.google.home.HomeDevice
import com.google.home.HomeConfig
import com.google.home.google.GoogleTVDevice
import com.google.home.matter.standard.BasicVideoPlayerDevice
import com.google.home.matter.standard.CastingVideoPlayerDevice
import com.google.home.matter.standard.ColorTemperatureLightDevice
import com.google.home.matter.standard.DimmableLightDevice
import com.google.home.matter.standard.ExtendedColorLightDevice
import com.google.home.matter.standard.OnOff
import com.google.home.matter.standard.OnOffLightDevice
import com.google.home.matter.standard.OnOffPluginUnitDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class HomeController private constructor(context: Context) {
    private val appContext = context.applicationContext

    private val registry = FactoryRegistry(
        traits = listOf(OnOff),
        types = listOf(
            OnOffLightDevice,
            DimmableLightDevice,
            ColorTemperatureLightDevice,
            ExtendedColorLightDevice,
            OnOffPluginUnitDevice,
            BasicVideoPlayerDevice,
            CastingVideoPlayerDevice,
            GoogleTVDevice
        )
    )

    private val homeConfig = HomeConfig(
        coroutineContext = Dispatchers.IO,
        factoryRegistry = registry
    )

    private val homeClient: HomeClient = Home.getClient(appContext, homeConfig)
    private val _devices = MutableStateFlow<List<HomeDevice>>(emptyList())
    val devices: StateFlow<List<HomeDevice>> = _devices.asStateFlow()

    private var observationJob: Job? = null

    fun registerPermissions(activity: ComponentActivity) {
        homeClient.registerActivityResultCallerForPermissions(activity)
    }

    suspend fun requestPermissions(forceLaunch: Boolean = false) {
        homeClient.requestPermissions(forceLaunch = forceLaunch)
    }

    fun startDeviceObservation(scope: CoroutineScope, onError: (Throwable) -> Unit = {}) {
        if (observationJob?.isActive == true) return

        observationJob = scope.launch {
            try {
                homeClient.devices().collect { deviceSet ->
                    Log.d("WaveHome_Debug", "Pobrano urządzeń: ${deviceSet.size}")
                    _devices.value = deviceSet.toList()
                }
            } catch (e: Exception) {
                Log.e("HomeController", "Błąd pobierania urządzeń", e)
                onError(e)
            }
        }
    }

    suspend fun getCurrentDeviceSnapshots(refresh: Boolean = true): List<SmartHomeDeviceSnapshot> {
        val currentDevices = if (refresh || _devices.value.isEmpty()) {
            fetchDevicesOnce()
        } else {
            _devices.value
        }
        val capturedAt = System.currentTimeMillis()
        return currentDevices.mapNotNull { device ->
            runCatching {
                val handle = resolveOnOffHandle(device)
                SmartHomeDeviceSnapshot(
                    deviceId = device.id.toString(),
                    name = device.name ?: "Nieznane urządzenie",
                    type = handle?.type ?: SmartDeviceType.UNKNOWN,
                    isOn = handle?.onOff?.onOff,
                    supportsOnOff = handle?.onOff != null,
                    capturedAt = capturedAt
                )
            }.onFailure { error ->
                Log.e("HomeController", "Pominięto urządzenie z niepoprawnym stanem", error)
            }.getOrNull()
        }
    }

    suspend fun setDevicePower(deviceId: String, desiredOn: Boolean): Boolean {
        val device = fetchDevicesOnce().firstOrNull { it.id.toString() == deviceId }
            ?: return false
        val onOff = resolveOnOffHandle(device)?.onOff ?: return false

        return try {
            if (desiredOn) {
                onOff.on()
            } else {
                onOff.off()
            }
            true
        } catch (e: Exception) {
            Log.e("HomeController", "Nie udało się ustawić stanu urządzenia $deviceId", e)
            false
        }
    }

    suspend fun applyActions(
        actions: List<SmartHomeAction>,
        currentStates: List<SmartHomeDeviceSnapshot>,
        isBlocked: (SmartHomeAction, SmartHomeDeviceSnapshot) -> Boolean
    ) {
        actions.forEach { action ->
            currentStates
                .asSequence()
                .filter { it.supportsOnOff }
                .filter { it.isOn != null }
                .filter { it.type == action.deviceType }
                .filter { action.targetDeviceId == null || action.targetDeviceId == it.deviceId }
                .filter { it.isOn != action.desiredOn }
                .filterNot { isBlocked(action, it) }
                .forEach { setDevicePower(it.deviceId, action.desiredOn) }
        }
    }

    private suspend fun fetchDevicesOnce(): List<HomeDevice> {
        return try {
            val fetched = withTimeoutOrNull(12_000L) {
                homeClient.devices().firstOrNull()?.toList()
            }
            if (fetched != null) {
                _devices.value = fetched
                fetched
            } else {
                _devices.value
            }
        } catch (e: Exception) {
            Log.e("HomeController", "Nie udało się odświeżyć listy urządzeń", e)
            _devices.value
        }
    }

    private suspend fun resolveOnOffHandle(device: HomeDevice): OnOffHandle? {
        try {
            val light = device.typeOrNull(OnOffLightDevice).firstOrNull()
            val onOff = light?.standardTraits?.onOff
            if (onOff != null) {
                return OnOffHandle(SmartDeviceType.LIGHT, onOff)
            }
        } catch (e: Exception) {
            Log.d("HomeController", "Urządzenie nie jest OnOffLightDevice: ${device.id}", e)
        }

        try {
            val dimmableLight = device.typeOrNull(DimmableLightDevice).firstOrNull()
            val onOff = dimmableLight?.standardTraits?.onOff
            if (onOff != null) {
                return OnOffHandle(SmartDeviceType.LIGHT, onOff)
            }
        } catch (e: Exception) {
            Log.d("HomeController", "Urządzenie nie jest DimmableLightDevice: ${device.id}", e)
        }

        try {
            val colorTemperatureLight = device.typeOrNull(ColorTemperatureLightDevice).firstOrNull()
            val onOff = colorTemperatureLight?.standardTraits?.onOff
            if (onOff != null) {
                return OnOffHandle(SmartDeviceType.LIGHT, onOff)
            }
        } catch (e: Exception) {
            Log.d("HomeController", "Urządzenie nie jest ColorTemperatureLightDevice: ${device.id}", e)
        }

        try {
            val extendedColorLight = device.typeOrNull(ExtendedColorLightDevice).firstOrNull()
            val onOff = extendedColorLight?.standardTraits?.onOff
            if (onOff != null) {
                return OnOffHandle(SmartDeviceType.LIGHT, onOff)
            }
        } catch (e: Exception) {
            Log.d("HomeController", "Urządzenie nie jest ExtendedColorLightDevice: ${device.id}", e)
        }

        try {
            val plug = device.typeOrNull(OnOffPluginUnitDevice).firstOrNull()
            val onOff = plug?.standardTraits?.onOff
            if (onOff != null) {
                return OnOffHandle(SmartDeviceType.PLUG, onOff)
            }
        } catch (e: Exception) {
            Log.d("HomeController", "Urządzenie nie jest OnOffPluginUnitDevice: ${device.id}", e)
        }

        try {
            val basicVideoPlayer = device.typeOrNull(BasicVideoPlayerDevice).firstOrNull()
            val onOff = basicVideoPlayer?.standardTraits?.onOff
            if (onOff != null) {
                return OnOffHandle(SmartDeviceType.TV, onOff)
            }
        } catch (e: Exception) {
            Log.d("HomeController", "Urządzenie nie jest BasicVideoPlayerDevice: ${device.id}", e)
        }

        try {
            val castingVideoPlayer = device.typeOrNull(CastingVideoPlayerDevice).firstOrNull()
            val onOff = castingVideoPlayer?.standardTraits?.onOff
            if (onOff != null) {
                return OnOffHandle(SmartDeviceType.TV, onOff)
            }
        } catch (e: Exception) {
            Log.d("HomeController", "Urządzenie nie jest CastingVideoPlayerDevice: ${device.id}", e)
        }

        try {
            val googleTv = device.typeOrNull(GoogleTVDevice).firstOrNull()
            val onOff = googleTv?.standardTraits?.onOff
            if (onOff != null) {
                return OnOffHandle(SmartDeviceType.TV, onOff)
            }
        } catch (e: Exception) {
            Log.d("HomeController", "Urządzenie nie jest GoogleTVDevice: ${device.id}", e)
        }

        return null
    }

    private data class OnOffHandle(
        val type: SmartDeviceType,
        val onOff: OnOff
    )

    companion object {
        @Volatile
        private var INSTANCE: HomeController? = null

        fun getInstance(context: Context): HomeController {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: HomeController(context).also { INSTANCE = it }
            }
        }
    }
}

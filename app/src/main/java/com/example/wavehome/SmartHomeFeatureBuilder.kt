package com.example.wavehome

object SmartHomeFeatureBuilder {
    fun build(
        snapshot: SmartHomeContextSnapshot,
        targetSize: Int = SENSOR_FEATURE_COUNT
    ): FloatArray {
        return buildNamedFeatures(snapshot).toFixedVector(targetSize)
    }

    fun buildDynamicFeatures(snapshot: SmartHomeContextSnapshot): Map<String, Float> {
        return buildNamedFeatures(snapshot).associate { it.name to it.value }
    }

    private fun buildNamedFeatures(snapshot: SmartHomeContextSnapshot): List<NamedFeature> {
        val weather = snapshot.weather
        val devices = snapshot.deviceStates
        val controllableDevices = devices.filter { it.supportsOnOff }

        return buildList {
            add(NamedFeature("context.is_home", if (snapshot.isHome == true) 1f else 0f))
            add(NamedFeature("context.room", encodeCategory(snapshot.roomName)))
            add(NamedFeature("context.user_status", encodeCategory(snapshot.userStatus ?: snapshot.activityType)))
            add(NamedFeature("sleep.minutes_last_night", normalize((snapshot.sleep?.minutes ?: 0) / 720f)))
            add(NamedFeature("weather.temperature", normalize(((weather?.temperature ?: 20.0) + 30.0) / 70.0)))
            add(NamedFeature("weather.humidity", normalize((weather?.humidity ?: 0) / 100f)))
            add(NamedFeature("weather.wind_speed", normalize((weather?.windSpeed ?: 0.0) / 30.0)))
            add(NamedFeature("weather.precipitation", normalize((weather?.precipitation ?: 0.0) / 20.0)))

            add(NamedFeature("devices.total", normalize(devices.size / 20f)))
            add(NamedFeature("devices.controllable_ratio", ratio(controllableDevices.size, devices.size)))
            add(NamedFeature("devices.on_ratio", ratio(controllableDevices.count { it.isOn == true }, controllableDevices.size)))
            add(NamedFeature("sleep.is_sleeping_now", if (snapshot.sleep?.isSleepingNow == true) 1f else 0f))

            devices
                .groupBy { it.type.name }
                .toSortedMap()
                .forEach { (typeName, typeDevices) ->
                    val controllableByType = typeDevices.filter { it.supportsOnOff }
                    add(NamedFeature("devices.type.$typeName.count", normalize(typeDevices.size / 10f)))
                    add(
                        NamedFeature(
                            "devices.type.$typeName.on_ratio",
                            ratio(controllableByType.count { it.isOn == true }, controllableByType.size)
                        )
                    )
                }

            devices
                .sortedBy { it.deviceId }
                .forEach { device ->
                    val stableId = device.deviceId.ifBlank { device.name }
                    add(NamedFeature("device.$stableId.supports_on_off", if (device.supportsOnOff) 1f else 0f))
                    device.isOn?.let { isOn ->
                        add(NamedFeature("device.$stableId.is_on", if (isOn) 1f else 0f))
                    }
                }
        }
    }

    private fun encodeCategory(value: String?): Float {
        if (value.isNullOrBlank()) return 0f
        val positiveHash = value.trim().lowercase().hashCode().toLong() and 0x7fffffff
        return (positiveHash % 10_000).toFloat() / 10_000f
    }

    private fun List<NamedFeature>.toFixedVector(targetSize: Int): FloatArray {
        if (targetSize <= 0) return FloatArray(0)

        val result = FloatArray(targetSize)
        val fixedFeatureCount = minOf(CORE_FEATURE_COUNT, targetSize, size)

        for (index in 0 until fixedFeatureCount) {
            result[index] = this[index].value
        }

        val bucketCount = targetSize - fixedFeatureCount
        if (bucketCount <= 0) return result

        drop(fixedFeatureCount).forEach { feature ->
            val bucket = fixedFeatureCount + stableBucket(feature.name, bucketCount)
            result[bucket] = normalize(result[bucket] + feature.value)
        }

        return result
    }

    private fun stableBucket(name: String, bucketCount: Int): Int {
        val positiveHash = name.hashCode().toLong() and 0x7fffffff
        return (positiveHash % bucketCount).toInt()
    }

    private fun ratio(numerator: Int, denominator: Int): Float {
        if (denominator <= 0) return 0f
        return normalize(numerator.toFloat() / denominator.toFloat())
    }

    private fun normalize(value: Double): Float = normalize(value.toFloat())

    private fun normalize(value: Float): Float = value.coerceIn(0f, 1f)

    private data class NamedFeature(
        val name: String,
        val value: Float
    )

    private const val CORE_FEATURE_COUNT = 8
}

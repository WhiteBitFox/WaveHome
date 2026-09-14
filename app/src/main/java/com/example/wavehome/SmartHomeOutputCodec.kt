package com.example.wavehome

object SmartHomeOutputCodec {
    private const val LIGHT_BIT = 1
    private const val PLUG_BIT = 1 shl 1
    private const val TV_BIT = 1 shl 2

    fun decode(outputClass: Int): List<SmartHomeAction> {
        val boundedOutput = outputClass.coerceIn(0, SMART_HOME_OUTPUT_CLASSES - 1)
        return listOf(
            SmartHomeAction(
                deviceType = SmartDeviceType.LIGHT,
                desiredOn = (boundedOutput and LIGHT_BIT) != 0
            ),
            SmartHomeAction(
                deviceType = SmartDeviceType.PLUG,
                desiredOn = (boundedOutput and PLUG_BIT) != 0
            ),
            SmartHomeAction(
                deviceType = SmartDeviceType.TV,
                desiredOn = (boundedOutput and TV_BIT) != 0
            )
        )
    }

    fun encodeDeviceStates(deviceStates: List<SmartHomeDeviceSnapshot>): Int {
        var output = 0
        if (deviceStates.any { it.type == SmartDeviceType.LIGHT && it.isOn == true }) {
            output = output or LIGHT_BIT
        }
        if (deviceStates.any { it.type == SmartDeviceType.PLUG && it.isOn == true }) {
            output = output or PLUG_BIT
        }
        if (deviceStates.any { it.type == SmartDeviceType.TV && it.isOn == true }) {
            output = output or TV_BIT
        }
        return output.coerceIn(0, SMART_HOME_OUTPUT_CLASSES - 1)
    }
}

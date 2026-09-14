package com.example.wavehome

import android.bluetooth.BluetoothDevice

// Wszystkie modele danych w jednym miejscu
data class BluetoothDeviceInfo(val device: BluetoothDevice, var rssi: Short?)
data class RoomFingerprint(val roomName: String, val signalMap: Map<String, Int>)
data class CombinedScan(val signals: Map<String, Int>)
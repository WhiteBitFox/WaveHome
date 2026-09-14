package com.example.wavehome

import android.content.Context
import android.os.SystemClock
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.nnapi.NnApiDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class SmartHomeMLBenchmark(private val context: Context) {
    private val SEQ_LEN = 12
    private val FEATURES = 11
    private val OUTPUT_DEVICES = 16

    fun runBenchmark(onProgress: (String) -> Unit): String {
        val sb = StringBuilder()
        try {
            onProgress("Wczytywanie modelu z pamięci...")
            val modelBuffer = loadModelFile("smarthome_lstm.tflite")

            // Generowanie losowych danych (symulacja danych z czujników)
            val inputBuffer = ByteBuffer.allocateDirect(1 * SEQ_LEN * FEATURES * 4).apply {
                order(ByteOrder.nativeOrder())
                for (i in 0 until SEQ_LEN * FEATURES) putFloat(Math.random().toFloat())
            }
            val outputBuffer = ByteBuffer.allocateDirect(1 * OUTPUT_DEVICES * 4).apply {
                order(ByteOrder.nativeOrder())
            }

            // 1. TEST CPU
            onProgress("Testowanie na CPU (Domyślnie)...")
            val cpuOptions = Interpreter.Options()
            val cpuTime = testHardware(modelBuffer, inputBuffer, outputBuffer, cpuOptions)
            sb.appendLine("Średni czas CPU: $cpuTime") // Usunięte "ms" z tego miejsca

            // 2. TEST GPU
            onProgress("Testowanie na GPU (Adreno)...")
            val compatList = CompatibilityList()
            val gpuOptions = Interpreter.Options()
            if (compatList.isDelegateSupportedOnThisDevice) {
                gpuOptions.addDelegate(GpuDelegate(compatList.bestOptionsForThisDevice))
            } else {
                gpuOptions.addDelegate(GpuDelegate())
            }
            val gpuTime = testHardware(modelBuffer, inputBuffer, outputBuffer, gpuOptions)

            // Jeśli GPU zwróci błąd o braku wsparcia dla operacji (np. WHILE w LSTM)
            if (gpuTime.contains("Failed to apply delegate")) {
                sb.appendLine("Średni czas GPU: Brak wsparcia sprzętowego dla tego typu sieci (LSTM)")
            } else {
                sb.appendLine("Średni czas GPU: $gpuTime")
            }

            // 3. TEST NPU (NNAPI)
            onProgress("Testowanie na NPU (Snapdragon Hexagon)...")
            val npuOptions = Interpreter.Options()
            npuOptions.addDelegate(NnApiDelegate())
            val npuTime = testHardware(modelBuffer, inputBuffer, outputBuffer, npuOptions)
            sb.appendLine("Średni czas NPU: $npuTime")

        } catch (e: Exception) {
            return "Wystąpił błąd krytyczny: ${e.message}"
        }
        return sb.toString()
    }

    private fun testHardware(
        model: ByteBuffer, input: ByteBuffer, output: ByteBuffer, options: Interpreter.Options
    ): String {
        var interpreter: Interpreter? = null
        return try {
            interpreter = Interpreter(model, options)

            // Rozgrzewka
            for (i in 0..50) {
                input.rewind(); output.rewind()
                interpreter.run(input, output)
            }

            val iterations = 1000
            val startTime = SystemClock.elapsedRealtime()

            // Płaściwy pomiar
            for (i in 0 until iterations) {
                input.rewind(); output.rewind()
                interpreter.run(input, output)
            }

            val totalTimeMs = SystemClock.elapsedRealtime() - startTime
            val avgTime = totalTimeMs.toFloat() / iterations

            String.format("%.4f ms", avgTime) // Zwraca ładnie sformatowany wynik, np. "0.0450 ms"

        } catch (e: Exception) {
            e.message ?: "Nieznany błąd"
        } finally {
            interpreter?.close()
        }
    }

    private fun loadModelFile(fileName: String): ByteBuffer {
        val fileDescriptor = context.assets.openFd(fileName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
    }
}
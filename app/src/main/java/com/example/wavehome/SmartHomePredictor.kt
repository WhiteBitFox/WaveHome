package com.example.wavehome

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.sqrt

class SmartHomePredictor(private val context: Context, private val db: AppDatabase) {
    private val sequenceLength = 12
    private val localModelStore = LocalSequenceModelStore(context)

    suspend fun predict(currentSensors: FloatArray): Int {
        // 1. Pobierz wszystkie poprawki z bazy danych
        val overrides = db.overrideDao().getByKind(OVERRIDE_KIND_MODEL_CORRECTION)

        // 2. Znajdź najbliższą poprawkę (k-NN)
        val nearest = overrides.find { it.isSimilarTo(currentSensors, threshold = 0.05f) }

        if (nearest != null) {
            return nearest.correctOutput
        }

        localModelStore.load()?.predict(buildInputSequence(currentSensors))?.let {
            return it.outputClass
        }

        // 3. Jeśli nie ma lokalnego modelu, uruchom standardowy model
        return runTFLiteInference(currentSensors)
    }

    suspend fun predictActions(currentSensors: FloatArray): SmartHomePrediction {
        val overrides = db.overrideDao().getByKind(OVERRIDE_KIND_MODEL_CORRECTION)

        val nearest = overrides.find { it.isSimilarTo(currentSensors, threshold = 0.05f) }

        val localPrediction = if (nearest == null) {
            localModelStore.load()?.predict(buildInputSequence(currentSensors))
        } else {
            null
        }
        val outputClass = nearest?.correctOutput
            ?: localPrediction?.outputClass
            ?: runTFLiteInference(currentSensors)

        return SmartHomePrediction(
            outputClass = outputClass,
            actions = decodeOutput(outputClass),
            source = when {
                nearest != null -> PredictionSource.USER_OVERRIDE
                localPrediction != null -> PredictionSource.LOCAL_TRAINING
                else -> PredictionSource.MODEL
            }
        )
    }

    private suspend fun runTFLiteInference(sensors: FloatArray): Int {
        if (sensors.size != SENSOR_FEATURE_COUNT) return 0

        var interpreter: Interpreter? = null
        return try {
            interpreter = Interpreter(loadModelFile("smarthome_lstm.tflite"))
            val sequence = buildInputSequence(sensors)
            val input = Array(1) {
                sequence
            }
            val output = Array(1) { FloatArray(SMART_HOME_OUTPUT_CLASSES) }

            interpreter.run(input, output)
            output[0].indices.maxByOrNull { output[0][it] } ?: 0
        } catch (e: Exception) {
            e.printStackTrace()
            0
        } finally {
            interpreter?.close()
        }
    }

    private suspend fun buildInputSequence(currentSensors: FloatArray): Array<FloatArray> {
        val history = db.timeLogDao()
            .getLatestEntries(sequenceLength - 1)
            .asReversed()
            .mapNotNull { entry ->
                entry.mlInputVector?.takeIf { it.size == SENSOR_FEATURE_COUNT }
            }

        val paddingSize = (sequenceLength - history.size - 1).coerceAtLeast(0)
        return buildList {
            repeat(paddingSize) { add(FloatArray(SENSOR_FEATURE_COUNT)) }
            addAll(history.takeLast(sequenceLength - 1))
            add(currentSensors.copyOf())
        }.toTypedArray()
    }

    private fun decodeOutput(outputClass: Int): List<SmartHomeAction> {
        return SmartHomeOutputCodec.decode(outputClass)
    }

    private fun loadModelFile(fileName: String): ByteBuffer {
        val fileDescriptor = context.assets.openFd(fileName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            fileDescriptor.startOffset,
            fileDescriptor.declaredLength
        )
    }

    private fun UserOverride.isSimilarTo(current: FloatArray, threshold: Float): Boolean {
        if (this.sensorSnapshot.size != current.size) return false

        var distanceSum = 0f
        for (i in current.indices) {
            val diff = this.sensorSnapshot[i] - current[i]
            // Obliczenie odległości euklidesowej bez użycia pow(2).
            distanceSum += diff * diff
        }

        return sqrt(distanceSum) < threshold
    }
}

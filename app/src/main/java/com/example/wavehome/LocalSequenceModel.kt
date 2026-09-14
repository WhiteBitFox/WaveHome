package com.example.wavehome

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.sqrt

data class LocalTrainingSummary(
    val trainedAt: Long,
    val sampleCount: Int,
    val classCount: Int,
    val featureCount: Int,
    val accuracy: Float
)

data class LocalSequencePrediction(
    val outputClass: Int,
    val distance: Float
)

data class LocalSequenceModel(
    val trainedAt: Long,
    val sequenceLength: Int,
    val sensorFeatureCount: Int,
    val centroids: Map<Int, FloatArray>,
    val sampleCount: Int,
    val accuracy: Float
) {
    val featureCount: Int = sequenceLength * sensorFeatureCount

    fun predict(sequence: Array<FloatArray>): LocalSequencePrediction? {
        if (centroids.isEmpty() || sequence.size != sequenceLength) return null

        val flattened = flatten(sequence)
        return centroids
            .map { (outputClass, centroid) ->
                LocalSequencePrediction(
                    outputClass = outputClass,
                    distance = euclideanDistance(flattened, centroid)
                )
            }
            .minByOrNull { it.distance }
    }

    fun summary(): LocalTrainingSummary {
        return LocalTrainingSummary(
            trainedAt = trainedAt,
            sampleCount = sampleCount,
            classCount = centroids.size,
            featureCount = featureCount,
            accuracy = accuracy
        )
    }

    companion object {
        fun flatten(sequence: Array<FloatArray>): FloatArray {
            val result = FloatArray(sequence.sumOf { it.size })
            var offset = 0
            sequence.forEach { row ->
                row.copyInto(result, destinationOffset = offset)
                offset += row.size
            }
            return result
        }

        fun euclideanDistance(left: FloatArray, right: FloatArray): Float {
            if (left.size != right.size) return Float.MAX_VALUE

            var sum = 0f
            for (index in left.indices) {
                val diff = left[index] - right[index]
                sum += diff * diff
            }
            return sqrt(sum)
        }
    }
}

class LocalSequenceModelStore(context: Context) {
    private val modelFile = File(context.filesDir, MODEL_FILE_NAME)

    suspend fun load(): LocalSequenceModel? = withContext(Dispatchers.IO) {
        if (!modelFile.exists()) return@withContext null

        runCatching {
            val json = JSONObject(modelFile.readText())
            val centroidsJson = json.getJSONArray("centroids")
            val centroids = buildMap {
                for (index in 0 until centroidsJson.length()) {
                    val item = centroidsJson.getJSONObject(index)
                    put(item.getInt("outputClass"), item.getJSONArray("values").toFloatArray())
                }
            }

            LocalSequenceModel(
                trainedAt = json.getLong("trainedAt"),
                sequenceLength = json.getInt("sequenceLength"),
                sensorFeatureCount = json.getInt("sensorFeatureCount"),
                centroids = centroids,
                sampleCount = json.getInt("sampleCount"),
                accuracy = json.optDouble("accuracy", 0.0).toFloat()
            )
        }.getOrNull()
    }

    suspend fun save(model: LocalSequenceModel) = withContext(Dispatchers.IO) {
        val centroids = JSONArray()
        model.centroids.forEach { (outputClass, values) ->
            centroids.put(
                JSONObject()
                    .put("outputClass", outputClass)
                    .put("values", values.toJsonArray())
            )
        }

        val json = JSONObject()
            .put("trainedAt", model.trainedAt)
            .put("sequenceLength", model.sequenceLength)
            .put("sensorFeatureCount", model.sensorFeatureCount)
            .put("sampleCount", model.sampleCount)
            .put("accuracy", model.accuracy)
            .put("centroids", centroids)

        modelFile.writeText(json.toString())
    }

    suspend fun loadSummary(): LocalTrainingSummary? {
        return load()?.summary()
    }

    private fun JSONArray.toFloatArray(): FloatArray {
        return FloatArray(length()) { index -> getDouble(index).toFloat() }
    }

    private fun FloatArray.toJsonArray(): JSONArray {
        val json = JSONArray()
        forEach { json.put(it.toDouble()) }
        return json
    }

    private companion object {
        const val MODEL_FILE_NAME = "personalized_sequence_model.json"
    }
}

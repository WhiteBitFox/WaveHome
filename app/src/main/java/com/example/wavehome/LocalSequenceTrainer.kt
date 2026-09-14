package com.example.wavehome

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalSequenceTrainer(
    private val db: AppDatabase,
    private val modelStore: LocalSequenceModelStore,
    private val sequenceLength: Int = DEFAULT_SEQUENCE_LENGTH
) {
    suspend fun train(): Result<LocalTrainingSummary> = withContext(Dispatchers.Default) {
        runCatching {
            val samples = loadTrainingSamples()
            require(samples.size >= MIN_SAMPLES) {
                "Za mało danych do uczenia. Potrzeba minimum $MIN_SAMPLES pełnych sekwencji."
            }

            val centroids = buildCentroids(samples)
            val accuracy = calculateTrainingAccuracy(samples, centroids)
            val model = LocalSequenceModel(
                trainedAt = System.currentTimeMillis(),
                sequenceLength = sequenceLength,
                sensorFeatureCount = SENSOR_FEATURE_COUNT,
                centroids = centroids,
                sampleCount = samples.size,
                accuracy = accuracy
            )

            modelStore.save(model)
            model.summary()
        }
    }

    private suspend fun loadTrainingSamples(): List<TrainingSample> {
        val logs = db.timeLogDao()
            .getAll()
            .asReversed()
            .filter { it.mlInputVector?.size == SENSOR_FEATURE_COUNT }

        if (logs.size < sequenceLength) return emptyList()

        val statesByLogId = logs.associate { log ->
            val deviceStates = db.timeLogDao()
                .getDeviceStatesForTimeLog(log.id)
                .map { it.toSnapshot() }
            log.id to deviceStates
        }

        return buildList {
            for (index in sequenceLength - 1 until logs.size) {
                val sequence = logs
                    .subList(index - sequenceLength + 1, index + 1)
                    .mapNotNull { it.mlInputVector }
                    .map { it.copyOf() }
                    .toTypedArray()
                if (sequence.size != sequenceLength) continue

                val targetStates = statesByLogId[logs[index].id].orEmpty()
                if (targetStates.none { it.supportsOnOff && it.isOn != null }) continue

                add(
                    TrainingSample(
                        features = LocalSequenceModel.flatten(sequence),
                        outputClass = SmartHomeOutputCodec.encodeDeviceStates(targetStates)
                    )
                )
            }
        }
    }

    private fun buildCentroids(samples: List<TrainingSample>): Map<Int, FloatArray> {
        return samples
            .groupBy { it.outputClass }
            .mapValues { (_, classSamples) ->
                val centroid = FloatArray(classSamples.first().features.size)
                classSamples.forEach { sample ->
                    sample.features.forEachIndexed { index, value ->
                        centroid[index] += value
                    }
                }
                for (index in centroid.indices) {
                    centroid[index] /= classSamples.size
                }
                centroid
            }
    }

    private fun calculateTrainingAccuracy(
        samples: List<TrainingSample>,
        centroids: Map<Int, FloatArray>
    ): Float {
        if (samples.isEmpty() || centroids.isEmpty()) return 0f

        val correct = samples.count { sample ->
            val predicted = centroids
                .minByOrNull { (_, centroid) ->
                    LocalSequenceModel.euclideanDistance(sample.features, centroid)
                }
                ?.key
            predicted == sample.outputClass
        }
        return correct.toFloat() / samples.size
    }

    private fun TimeLogDeviceState.toSnapshot(): SmartHomeDeviceSnapshot {
        return SmartHomeDeviceSnapshot(
            deviceId = deviceId,
            name = deviceName,
            type = runCatching { SmartDeviceType.valueOf(deviceType) }
                .getOrDefault(SmartDeviceType.UNKNOWN),
            isOn = isOn,
            supportsOnOff = supportsOnOff,
            capturedAt = capturedAt
        )
    }

    private data class TrainingSample(
        val features: FloatArray,
        val outputClass: Int
    )

    private companion object {
        const val DEFAULT_SEQUENCE_LENGTH = 12
        const val MIN_SAMPLES = 8
    }
}

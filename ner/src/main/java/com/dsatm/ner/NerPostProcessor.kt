package com.dsatm.ner

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.InputStream
import kotlin.math.exp

class NerPostProcessor(private val context: Context) {

    private val TAG = "NerPostProcessor"
    private lateinit var idToLabel: Map<Int, String>

    fun initialize() {
        Log.d(TAG, "Initializing post-processor...")
        try {
            val configStream = context.assets.open("config.json")
            idToLabel = loadLabelsFromConfig(configStream)
            Log.d(TAG, "ID-to-label mapping loaded. Total labels: ${idToLabel.size}")
            Log.d(TAG, "Labels: $idToLabel")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize post-processor.", e)
            throw e
        }
    }

    fun process(tokens: List<String>, logits: FloatArray): List<PiiEntity> {
        if (!::idToLabel.isInitialized) {
            throw IllegalStateException("PostProcessor has not been initialized. Call initialize() first.")
        }
        Log.d(TAG, "Starting post-processing of logits.")

        val predictedLabelIds = getPredictions(logits, tokens.size)

        val entities = mutableListOf<PiiEntity>()
        var currentLabel = ""
        var currentEntity = StringBuilder()

        for (i in 1 until tokens.size) {
            val token = tokens[i]
            val labelId = predictedLabelIds[i]
            val label = idToLabel[labelId] ?: "O"

            if (label.startsWith("B-")) {
                if (currentEntity.isNotEmpty()) {
                    entities.add(PiiEntity(currentLabel, currentEntity.toString().trim()))
                }
                currentLabel = label.substring(2)
                currentEntity = StringBuilder(cleanToken(token))
            } else if (label.startsWith("I-") && label.substring(2) == currentLabel) {
                currentEntity.append(" ").append(cleanToken(token))
            } else {
                if (currentEntity.isNotEmpty()) {
                    entities.add(PiiEntity(currentLabel, currentEntity.toString().trim()))
                }
                currentLabel = ""
                currentEntity = StringBuilder()
            }
        }

        if (currentEntity.isNotEmpty()) {
            entities.add(PiiEntity(currentLabel, currentEntity.toString().trim()))
        }

        Log.d(TAG, "Post-processing complete. Found ${entities.size} entities.")
        return entities
    }

    /**
     * Replaces the detected entities in the original text with their labels in angle brackets.
     * This method iterates through the entities from the end of the text to the beginning
     * to avoid issues with string replacement shifting indices.
     */
    fun redactTextWithLabels(originalText: String, entities: List<PiiEntity>): String {
        var redactedText = StringBuilder(originalText)
        val sortedEntities = entities.sortedByDescending { originalText.indexOf(it.text) }

        for (entity in sortedEntities) {
            val startIndex = redactedText.indexOf(entity.text, ignoreCase = true)
            if (startIndex != -1) {
                val endIndex = startIndex + entity.text.length
                val replacement = "<${entity.label}>"
                redactedText.replace(startIndex, endIndex, replacement)
            }
        }
        return redactedText.toString()
    }

    private fun loadLabelsFromConfig(inputStream: InputStream): Map<Int, String> {
        val json = JSONObject(inputStream.bufferedReader().readText())
        val idToLabelJson = json.getJSONObject("id2label")
        val map = mutableMapOf<Int, String>()
        val keys = idToLabelJson.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            map[key.toInt()] = idToLabelJson.getString(key)
        }
        return map
    }

    private fun getPredictions(logits: FloatArray, seqLength: Int): List<Int> {
        val predictions = mutableListOf<Int>()
        val numLabels = idToLabel.size
        for (i in 0 until seqLength) {
            var maxIndex = 0
            var maxValue = Float.MIN_VALUE
            for (j in 0 until numLabels) {
                val value = logits[i * numLabels + j]
                if (value > maxValue) {
                    maxValue = value
                    maxIndex = j
                }
            }
            predictions.add(maxIndex)
        }
        return predictions
    }

    private fun cleanToken(token: String): String {
        return if (token.startsWith("##")) {
            token.substring(2)
        } else {
            token
        }
    }
}
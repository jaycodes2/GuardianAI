package com.dsatm.ner

import android.content.Context
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.LongBuffer

/**
 * Manages the entire NER model pipeline.
 *
 * This class orchestrates the model loading, tokenization, inference, and post-processing
 * to identify PII entities in text. It provides a simple API for your app to use.
 *
 * @param context The Android context used to access assets.
 */
class BertNerOnnxManager(private val context: Context) {

    // Tag for logging in Logcat
    private val TAG = "BertNerOnnxManager"

    // ONNX Runtime components
    private lateinit var ortEnv: OrtEnvironment
    private lateinit var ortSession: OrtSession

    // Pre-processing and post-processing modules
    private lateinit var tokenizer: BertTokenizer
    lateinit var postProcessor: NerPostProcessor

    /**
     * Initializes all components of the NER pipeline.
     * This method is a one-time setup and should be called on a background thread.
     */
    fun initialize() {
        Log.d(TAG, "Starting initialization of NER manager...")
        try {
            // Initialize ONNX Runtime environment and session
            ortEnv = OrtEnvironment.getEnvironment()
            ortSession = ortEnv.createSession(context.assets.open("mobilebert_ner.onnx").readBytes(), OrtSession.SessionOptions())
            Log.d(TAG, "ONNX session created successfully.")

            // Initialize our custom modules
            tokenizer = BertTokenizer(context)
            tokenizer.initialize()
            Log.d(TAG, "Tokenizer initialized.")

            postProcessor = NerPostProcessor(context)
            postProcessor.initialize()
            Log.d(TAG, "Post-processor initialized.")

            Log.d(TAG, "NER manager initialization complete.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize NER manager.", e)
            // It's crucial to handle this gracefully in your app (e.g., disable the NER feature)
            close()
            throw e
        }
    }

    /**
     * Runs the PII detection pipeline on the given text.
     *
     * This method performs the following steps:
     * 1. Tokenizes the text using the BertTokenizer.
     * 2. Creates ONNX tensors from the numerical input IDs.
     * 3. Runs the inference session.
     * 4. Calls the NerPostProcessor to get the final PII entities.
     *
     * @param text The input string to analyze.
     * @return A list of identified [PiiEntity] objects.
     */
    fun detectPii(text: String): List<PiiEntity> {
        if (!::ortSession.isInitialized) {
            throw IllegalStateException("BertNerOnnxManager is not initialized. Call initialize() first.")
        }
        Log.d(TAG, "Starting PII detection for text: '$text'")

        // 1. Tokenize the input text
        val tokenizedInput = tokenizer.tokenize(text)

        // 2. Prepare the input tensors for the ONNX model
        val inputs = createOnnxTensors(tokenizedInput)

        // 3. Run inference
        var outputTensor: OnnxTensor? = null
        try {
            val results = ortSession.run(inputs)
            outputTensor = results[0] as OnnxTensor
            Log.d(TAG, "Inference successful. Starting post-processing.")

            // 4. Get the final entities from the post-processor
            val entities = postProcessor.process(
                tokens = tokenizedInput.tokens,
                logits = outputTensor.floatBuffer.array()
            )

            Log.d(TAG, "PII detection complete. Found ${entities.size} entities.")
            return entities
        } finally {
            // Clean up all resources
            inputs.values.forEach { it.close() }
            outputTensor?.close()
        }
    }

    /**
     * Creates the ONNX tensors from the tokenized input arrays.
     */
    private fun createOnnxTensors(tokenizedInput: TokenizedInput): Map<String, OnnxTensor> {
        val inputs = mutableMapOf<String, OnnxTensor>()

        inputs["input_ids"] = createTensor(tokenizedInput.inputIds, longArrayOf(1, tokenizedInput.inputIds.size.toLong()))
        inputs["attention_mask"] = createTensor(tokenizedInput.attentionMask, longArrayOf(1, tokenizedInput.attentionMask.size.toLong()))
        inputs["token_type_ids"] = createTensor(tokenizedInput.tokenTypeIds, longArrayOf(1, tokenizedInput.tokenTypeIds.size.toLong()))

        return inputs
    }

    /**
     * Helper function to create an OnnxTensor from a LongArray.
     * This is the correct, supported approach using a LongBuffer.
     */
    private fun createTensor(data: LongArray, shape: LongArray): OnnxTensor {
        val longBuffer = LongBuffer.allocate(data.size)
        longBuffer.put(data)
        longBuffer.flip() // Flips the buffer from writing to reading
        return OnnxTensor.createTensor(ortEnv, longBuffer, shape)
    }

    /**
     * Cleans up all ONNX-related resources.
     * This method must be called when the NER manager is no longer needed (e.g., in `onDestroy`).
     */
    fun close() {
        Log.d(TAG, "Closing ONNX session and environment.")
        if (::ortSession.isInitialized) {
            ortSession.close()
        }
        if (::ortEnv.isInitialized) {
            ortEnv.close()
        }
    }
}

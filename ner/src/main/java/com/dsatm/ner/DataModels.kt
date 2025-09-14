package com.dsatm.ner

/**
 * Data class to hold the tokenized output.
 *
 * This mirrors the output of the Hugging Face tokenizer in your Python script.
 * We use `LongArray` because ONNX models often expect 64-bit integers.
 * The `tokens` list holds the string representations of the tokens for post-processing.
 */
data class TokenizedInput(
    val tokens: List<String>,
    val inputIds: LongArray,
    val attentionMask: LongArray,
    val tokenTypeIds: LongArray
)

/**
 * Data class to represent a single identified PII entity.
 *
 * @param label The type of PII (e.g., "GIVENNAME", "STREET", "CITY").
 * @param text The actual text of the entity found in the original sentence.
 */
data class PiiEntity(
    val label: String,
    val text: String
)
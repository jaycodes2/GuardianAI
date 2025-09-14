package com.dsatm.ner

import android.content.Context
import android.util.Log
import java.io.InputStream
import java.util.Locale

/**
 * Handles the pre-processing (tokenization) for the MobileBERT model.
 *
 * This class is responsible for loading the vocabulary and tokenizer configuration,
 * and converting raw text into the numerical tensors required by the ONNX model.
 *
 * @param context The Android context used to access the assets directory.
 */
class BertTokenizer(private val context: Context) {

    // Tag for logging in Logcat
    private val TAG = "BertTokenizer"

    // The vocabulary map, mapping a token string to its ID
    private lateinit var vocab: Map<String, Int>

    // Special token IDs, which are necessary for the model to understand
    private var clsTokenId: Int = -1
    private var sepTokenId: Int = -1
    private var unkTokenId: Int = -1
    private var padTokenId: Int = -1

    // The maximum sequence length the model can handle
    private val maxSequenceLength = 128

    /**
     * Initializes the tokenizer by loading the vocabulary and special tokens.
     * This method should be called once before performing any tokenization.
     */
    fun initialize() {
        Log.d(TAG, "Initializing tokenizer...")
        try {
            // Load the vocabulary from the assets file
            val vocabInputStream = context.assets.open("vocab.txt")
            vocab = loadVocab(vocabInputStream)
            Log.d(TAG, "Vocab loaded. Total tokens: ${vocab.size}")

            // Get the IDs for the special tokens
            clsTokenId = vocab["[CLS]"] ?: throw IllegalStateException("Missing [CLS] token in vocab.txt")
            sepTokenId = vocab["[SEP]"] ?: throw IllegalStateException("Missing [SEP] token in vocab.txt")
            unkTokenId = vocab["[UNK]"] ?: throw IllegalStateException("Missing [UNK] token in vocab.txt")
            padTokenId = vocab["[PAD]"] ?: 0 // Use 0 as a default if not found
            Log.d(TAG, "Special token IDs found: CLS=$clsTokenId, SEP=$sepTokenId, UNK=$unkTokenId, PAD=$padTokenId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize tokenizer.", e)
            throw e // Re-throw to indicate a critical failure
        }
    }

    /**
     * Converts a raw text string into a numerical format for the model.
     *
     * This function performs the following steps:
     * 1. Tokenizes the text into sub-words.
     * 2. Maps each sub-word to its vocabulary ID.
     * 3. Adds special tokens like [CLS] and [SEP].
     * 4. Pads or truncates the sequence to the maximum length (128).
     * 5. Creates the attention mask and token type IDs.
     *
     * @param text The input string to tokenize.
     * @return A [TokenizedInput] data class containing the numerical arrays.
     */
    fun tokenize(text: String): TokenizedInput {
        if (!::vocab.isInitialized) {
            throw IllegalStateException("Tokenizer has not been initialized. Call initialize() first.")
        }
        Log.d(TAG, "Tokenizing text: '$text'")

        val tokens = mutableListOf<String>()
        val inputIds = mutableListOf<Long>()
        val attentionMask = mutableListOf<Long>()
        val tokenTypeIds = mutableListOf<Long>()

        // 1. Add the CLS token at the beginning
        tokens.add("[CLS]")
        inputIds.add(clsTokenId.toLong())
        attentionMask.add(1L)
        tokenTypeIds.add(0L)

        // 2. Tokenize the input text, splitting by spaces and handling punctuation
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        for (word in words) {
            // A simple sub-word tokenization approach that works for this model.
            // A more complex implementation would use the tokenizer.json file.
            val subwords = splitIntoSubwords(word.toLowerCase(Locale.ROOT))
            for (subword in subwords) {
                // Check if the current list of input IDs is at the max length minus 1 for SEP token.
                if (inputIds.size >= maxSequenceLength - 1) {
                    Log.w(TAG, "Max sequence length reached. Truncating text.")
                    break
                }

                // Get the ID for the subword, or use the UNK token ID if not found
                val subwordId = vocab[subword] ?: unkTokenId

                tokens.add(subword)
                inputIds.add(subwordId.toLong())
                attentionMask.add(1L)
                tokenTypeIds.add(0L)
            }
            if (inputIds.size >= maxSequenceLength - 1) break
        }

        // 3. Add the SEP token at the end
        if (inputIds.size < maxSequenceLength) {
            tokens.add("[SEP]")
            inputIds.add(sepTokenId.toLong())
            attentionMask.add(1L)
            tokenTypeIds.add(0L)
        }

        // 4. Pad the sequence to the max length with 0s
        while (inputIds.size < maxSequenceLength) {
            inputIds.add(padTokenId.toLong())
            attentionMask.add(0L)
            tokenTypeIds.add(0L)
        }

        Log.d(TAG, "Tokenization complete. Sequence length: ${inputIds.size}")
        Log.d(TAG, "Input IDs: ${inputIds.take(10)}...")
        Log.d(TAG, "Tokens: ${tokens.take(10)}...")

        return TokenizedInput(
            tokens = tokens.toList(),
            inputIds = inputIds.toLongArray(),
            attentionMask = attentionMask.toLongArray(),
            tokenTypeIds = tokenTypeIds.toLongArray()
        )
    }

    /**
     * Loads the vocabulary file from assets into a map.
     */
    private fun loadVocab(inputStream: InputStream): Map<String, Int> {
        val vocab = mutableMapOf<String, Int>()
        inputStream.bufferedReader().useLines { lines ->
            lines.forEachIndexed { index, line ->
                vocab[line] = index
            }
        }
        return vocab
    }

    /**
     * A simple function to split words into sub-words based on a pre-trained vocabulary.
     * This is a simplified approach to replicate the MobileBertTokenizer.
     * For example, "tokenization" might become "token" and "##ization".
     *
     * In a production app, a dedicated tokenizer library would be used to parse tokenizer.json.
     * For this guide, this logic is sufficient to demonstrate the concept.
     */
    private fun splitIntoSubwords(word: String): List<String> {
        val subwords = mutableListOf<String>()
        var remainingWord = word
        while (remainingWord.isNotEmpty()) {
            var found = false
            for (i in remainingWord.length downTo 1) {
                val candidate = remainingWord.substring(0, i)
                if (vocab.containsKey(candidate)) {
                    subwords.add(candidate)
                    remainingWord = remainingWord.substring(i)
                    found = true
                    break
                }
            }
            // If we didn't find a valid subword, treat the first character as an unknown token
            if (!found) {
                val firstChar = remainingWord.substring(0, 1)
                subwords.add(firstChar)
                remainingWord = remainingWord.substring(1)
            }
        }
        return subwords
    }
}

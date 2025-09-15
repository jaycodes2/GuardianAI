package com.dsatm.ner

import android.content.Context

class MobileBertAnalyzer(context: Context) {

    private val bertNerOnnxManager = BertNerOnnxManager(context)

    init {
        bertNerOnnxManager.initialize()
    }

    fun analyze(text: String): List<PiiEntity> {
        return bertNerOnnxManager.detectPii(text)
    }

    fun close() {
        bertNerOnnxManager.close()
    }
}

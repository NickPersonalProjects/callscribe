package com.nicholaston.callscribe.transcription

import com.k2fsa.sherpa.onnx.OfflineRecognizer

object SherpaRuntime {
    const val VERSION = "1.13.8"

    val recognizerClass: Class<OfflineRecognizer>
        get() = OfflineRecognizer::class.java
}

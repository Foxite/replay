package com.example.replay

enum class BoolPreference(val value: String) {
    USE_MIC_CHANNEL("USE_MIC_CHANNEL"),
    USE_OUTPUT_CHANNEL("USE_OUTPUT_CHANNEL"),
    SAVE_SEPERATELY_BY_CHANNEL("SAVE_SEPERATELY_BY_CHANNEL")
}

enum class IntPreference(val value: String) {
    RECORD_LENGTH("RECORD_LENGTH")
}
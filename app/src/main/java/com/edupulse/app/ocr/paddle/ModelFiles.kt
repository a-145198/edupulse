package com.edupulse.app.ocr.paddle

import java.io.File

data class ModelFiles(
    val version: String,
    val baseDir: File,
    val detectionModel: File,
    val recognitionModel: File,
    val classificationModel: File,
    val dictionaryFile: File
)

data class DetectionModelFiles(
    val version: String,
    val baseDir: File,
    val detectionModel: File
)

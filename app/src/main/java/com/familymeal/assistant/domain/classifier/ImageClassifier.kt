package com.familymeal.assistant.domain.classifier

import android.net.Uri
import com.familymeal.assistant.domain.model.ClassificationResult
import kotlinx.coroutines.flow.Flow

interface ImageClassifier {
    fun classify(photoUri: Uri): Flow<ClassificationResult>
}

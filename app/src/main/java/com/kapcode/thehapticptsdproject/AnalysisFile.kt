package com.kapcode.thehapticptsdproject

import android.net.Uri

data class AnalysisFile(val uri: Uri, val name: String, val parentUri: Uri, val durationMs: Long = 0)

package org.javacs.kt

interface IServerSettings {
    fun completionsEnabled(): Boolean
    fun diagnosticsEnabled(): Boolean { return true }
    fun codeActionsEnabled(): Boolean
    fun smartSelectionsEnabled(): Boolean
    fun signatureHelpEnabled(): Boolean
    fun referencesEnabled(): Boolean
    fun definitionsEnabled(): Boolean
    fun codeAnalysisEnabled(): Boolean
    fun shouldMatchAllLowerCase(): Boolean
    fun completionFuzzyMatchMinRatio(): Int { return 80 }
} 
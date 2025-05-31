package me.rerere.rikkahub.ui.context

import androidx.compose.runtime.staticCompositionLocalOf
import com.google.firebase.analytics.FirebaseAnalytics

val LocalFirebaseAnalytics = staticCompositionLocalOf<FirebaseAnalytics> {
    error("LocalFirebaseAnalytics not provided")
}

object AnalyticsEvents {
    const val CHAT_SEND = "chat_send"
    const val CHAT_EDIT = "chat_edit"
    const val REGENERATE = "regenerate"
}
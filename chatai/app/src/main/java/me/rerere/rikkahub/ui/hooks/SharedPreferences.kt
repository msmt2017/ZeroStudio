package me.rerere.rikkahub.ui.hooks

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow

@Composable
fun rememberSharedPreferenceString(
    keyForString: String,
    defaultValue: String? = null
): MutableState<String?> {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("rikkahub.preferences", Context.MODE_PRIVATE)
    }
    val stateFlow = remember(keyForString, defaultValue) { prefs.getStringFlowForKey(keyForString, defaultValue) }
    val state by stateFlow.collectAsStateWithLifecycle(prefs.getString(keyForString, defaultValue))
    return remember {
        object : MutableState<String?> {
            override var value: String?
                get() = state
                set(value) {
                    prefs.edit { putString(keyForString, value) }
                }

            override fun component1(): String? = value
            override fun component2(): (String?) -> Unit = { value = it }
        }
    }
}

fun SharedPreferences.getStringFlowForKey(keyForString: String, defaultValue: String? = null) =
    callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (keyForString == key) {
                trySend(getString(key, defaultValue))
            }
        }
        registerOnSharedPreferenceChangeListener(listener)
        if (contains(keyForString)) {
            send(
                getString(
                    keyForString,
                    defaultValue
                )
            ) // if you want to emit an initial pre-existing value
        }
        awaitClose { unregisterOnSharedPreferenceChangeListener(listener) }
    }.buffer(Channel.UNLIMITED) // so trySend never fails
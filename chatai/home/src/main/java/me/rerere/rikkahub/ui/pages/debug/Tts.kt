package me.rerere.rikkahub.ui.pages.debug

import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.ui.hooks.tts.rememberTtsState
import java.util.Locale

@Composable
fun DebugTtsDemoComponent() {
    // 1. 记住 TTS 状态
    var initErrorMsg by remember { mutableStateOf<String?>(null) }
    val ttsState = rememberTtsState(
        // 可以设置一个偏好的默认语言，如果支持的话
        defaultLanguage = Locale.US,
        onInitError = { error ->
            initErrorMsg = "TTS Init Error: $error"
            Log.e("TtsDemoComponent", "TTS Init Error: $error")
        }
    )
    // 2. 收集状态以驱动 UI
    val isInitialized by ttsState.isInitialized.collectAsState()
    val isSpeaking by ttsState.isSpeaking.collectAsState()
    val currentLanguage by ttsState.currentLanguage.collectAsState()
    val availableLanguages by ttsState.availableLanguages.collectAsState()
    // initError 也可以直接从 state 收集，但 callback 对于一次性日志/通知可能更好
    // val initErrorDirect by ttsState.initError.collectAsState()
    // 3. 管理要朗读的文本
    var textToSpeak by remember { mutableStateOf("Hello! This is a Text-to-Speech demo.") }
    // 4. UI 布局
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("TTS Demo", style = MaterialTheme.typography.headlineSmall)
        // --- 状态显示 ---
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Initialized: $isInitialized", color = if (isInitialized) Color.Green else Color.Gray)
                Text("Speaking: $isSpeaking", color = if (isSpeaking) Color.Blue else Color.Gray)
                Text("Current Language: ${currentLanguage?.displayLanguage ?: "None"}")
                initErrorMsg?.let {
                    Text(it, color = Color.Red, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        // --- 语言选择 ---
        LanguageSelector(
            availableLanguages = availableLanguages,
            currentLanguage = currentLanguage,
            onLanguageSelected = { locale ->
                val success = ttsState.setLanguage(locale)
                if (!success) {
                    Log.w("TtsDemoComponent", "Failed to set language to ${locale.displayLanguage}")
                    // 可以在这里显示一个短暂的消息提示用户
                }
            },
            enabled = isInitialized // 只有初始化后才能选择语言
        )
        // --- 文本输入 ---
        OutlinedTextField(
            value = textToSpeak,
            onValueChange = { textToSpeak = it },
            label = { Text("Text to Speak") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )
        // --- 控制按钮 ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = {
                    // 使用 QUEUE_FLUSH 会打断当前朗读并立即开始新的
                    ttsState.speak(textToSpeak, queueMode = TextToSpeech.QUEUE_FLUSH)
                },
                enabled = isInitialized && !isSpeaking && textToSpeak.isNotBlank()
            ) {
                Text("Speak")
            }
            Button(
                onClick = { ttsState.stop() },
                enabled = isInitialized && isSpeaking
            ) {
                Text("Stop")
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text("Note: TTS engine initialization and language availability depend on the device.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun LanguageSelector(
    availableLanguages: Set<Locale>,
    currentLanguage: Locale?,
    onLanguageSelected: (Locale) -> Unit,
    enabled: Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    val sortedLanguages = remember(availableLanguages) {
        availableLanguages.toList().sortedBy { it.displayName }
    }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = currentLanguage?.displayName ?: "Select Language",
            onValueChange = {}, // Read-only
            readOnly = true,
            label = { Text("TTS Language") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor() // Important for linking TextField to Dropdown
                .fillMaxWidth(),
            enabled = enabled
        )
        ExposedDropdownMenu(
            expanded = enabled && expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth()
        ) {
            if (sortedLanguages.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No languages available") },
                    onClick = { expanded = false },
                    enabled = false
                )
            } else {
                sortedLanguages.forEach { locale ->
                    DropdownMenuItem(
                        text = { Text(locale.displayName) },
                        onClick = {
                            onLanguageSelected(locale)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
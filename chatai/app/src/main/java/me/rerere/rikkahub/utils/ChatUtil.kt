package me.rerere.rikkahub.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import java.io.ByteArrayOutputStream
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.uuid.Uuid

private const val TAG = "ChatUtil"


fun navigateToChatPage(
    navController: NavController,
    chatId: Uuid = Uuid.random()
) {
    Log.i(TAG, "navigateToChatPage: navigate to $chatId")
    navController.navigate("chat/${chatId}") {
        popUpTo(0) {
            inclusive = true
        }
        launchSingleTop = true
    }
}

fun Context.copyMessageToClipboard(message: UIMessage) {
    this.writeClipboardText(message.toText())
}

@OptIn(ExperimentalEncodingApi::class)
suspend fun Context.saveMessageImage(image: String) = withContext(Dispatchers.IO) {
    when {
        image.startsWith("data:image") -> {
            val byteArray = Base64.decode(image.substringAfter("base64,").toByteArray())
            val bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
            exportImage(this@saveMessageImage.getActivity()!!, bitmap)
        }
        image.startsWith("file:") -> {
            val file = image.toUri().toFile()
            exportImageFile(this@saveMessageImage.getActivity()!!, file)
        }
        image.startsWith("http") -> {
            kotlin.runCatching { // Use runCatching to handle potential network exceptions
                val url = java.net.URL(image)
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.connect()

                if (connection.responseCode == java.net.HttpURLConnection.HTTP_OK) {
                    val bitmap = BitmapFactory.decodeStream(connection.inputStream)
                    exportImage(this@saveMessageImage.getActivity()!!, bitmap)
                } else {
                    Log.e(TAG, "saveMessageImage: Failed to download image from $image, response code: ${connection.responseCode}")
                    null // Return null on failure
                }
            }.getOrNull() // Return null if any exception occurs during download
        }
        else -> error("Invalid image format")
    }
}

fun Context.createChatFilesByContents(uris: List<Uri>): List<Uri> {
    val newUris = mutableListOf<Uri>()
    val dir = this.filesDir.resolve("upload")
    if (!dir.exists()) {
        dir.mkdirs()
    }
    uris.forEach { uri ->
        val fileName = Uuid.random()
        val newUri = dir
            .resolve("$fileName")
            .toUri()
        this.contentResolver.openInputStream(uri)?.use { inputStream ->
            this.contentResolver.openOutputStream(newUri)?.use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        newUris.add(newUri)
    }
    return newUris
}

fun Context.createChatFilesByByteArrays(byteArrays: List<ByteArray>): List<Uri> {
    val newUris = mutableListOf<Uri>()
    val dir = this.filesDir.resolve("upload")
    if (!dir.exists()) {
        dir.mkdirs()
    }
    byteArrays.forEach { byteArray ->
        val fileName = Uuid.random()
        val newUri = dir
            .resolve("$fileName")
            .toUri()
        this.contentResolver.openOutputStream(newUri)?.use { outputStream ->
            outputStream.write(byteArray)
        }
        newUris.add(newUri)
    }
    return newUris
}

@OptIn(ExperimentalEncodingApi::class)
suspend fun Context.convertBase64ImagePartToLocalFile(message: UIMessage): UIMessage = withContext(Dispatchers.IO) {
    message.copy(
        parts = message.parts.map { part ->
            when(part) {
                is UIMessagePart.Image -> {
                    if (part.url.startsWith("data:image")) {
                        // base64 image
                        val sourceByteArray = Base64.decode(part.url.substringAfter("base64,").toByteArray())
                        val bitmap = BitmapFactory.decodeByteArray(sourceByteArray, 0, sourceByteArray.size)
                        val byteArray = bitmap.compress()
                        val urls = createChatFilesByByteArrays(listOf(byteArray))
                        Log.i(TAG, "convertBase64ImagePartToLocalFile: convert base64 img to ${urls.joinToString(", ")}")
                        part.copy(
                            url = urls.first().toString(),
                        )
                    } else {
                        part
                    }
                }
                else -> part
            }
        }
    )
}

fun Bitmap.compress(): ByteArray = ByteArrayOutputStream().use {
    compress(Bitmap.CompressFormat.PNG, 100, it)
    it.toByteArray()
}

fun Context.deleteChatFiles(uris: List<Uri>) {
    uris.filter { it.toString().startsWith("file:") }.forEach { uri ->
        val file = uri.toFile()
        if (file.exists()) {
            file.delete()
        }
    }
}

fun Context.deleteAllChatFiles() {
    val dir = this.filesDir.resolve("upload")
    if (dir.exists()) {
        dir.deleteRecursively()
    }
}

suspend fun Context.countChatFiles(): Pair<Int, Long> = withContext(Dispatchers.IO) {
    val dir = filesDir.resolve("upload")
    if (!dir.exists()) {
        return@withContext Pair(0, 0)
    }
    val files = dir.listFiles() ?: return@withContext Pair(0, 0)
    val count = files.size
    val size = files.sumOf { it.length() }
    Pair(count, size)
}
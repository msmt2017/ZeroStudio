package me.rerere.rikkahub.utils

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

private const val TAG = "ContextUtil"

/**
 * Read clipboard data as text
 */
fun Context.readClipboardText(): String {
    val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    val clip = clipboardManager.primaryClip ?: return ""
    val item = clip.getItemAt(0) ?: return ""
    return item.text.toString()
}

/**
 * Write text into clipboard
 */
fun Context.writeClipboardText(text: String) {
    val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    clipboardManager.setPrimaryClip(android.content.ClipData.newPlainText("text", text))
    Log.i(TAG, "writeClipboardText: $text")
}

/**
 * Open a url
 */
fun Context.openUrl(url: String) {
    Log.i(TAG, "openUrl: $url")
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
    intent.data = url.toUri()
    startActivity(intent)
}

fun Context.getActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) {
            return context
        }
        context = context.baseContext
    }
    return null
}

fun Context.getComponentActivity(): ComponentActivity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is ComponentActivity) {
            return context
        }
        context = context.baseContext
    }
    return null
}

fun Context.exportImage(activity: Activity, bitmap: Bitmap, fileName: String = "RikkaHub_${System.currentTimeMillis()}.png") {
    // 检查存储权限（Android 9及以下需要）
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
            return
        }
    }

    // 保存到相册
    var outputStream: OutputStream? = null
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10及以上使用MediaStore API
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                outputStream = contentResolver.openOutputStream(it)
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream!!)
            }
        } else {
            // Android 9及以下直接写入文件
            val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val image = File(imagesDir, fileName)
            outputStream = FileOutputStream(image)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)

            // 通知图库更新
            val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            mediaScanIntent.data = Uri.fromFile(image)
            sendBroadcast(mediaScanIntent)
        }
        Log.i(TAG, "Image saved successfully: $fileName")
    } catch (e: Exception) {
        Log.e(TAG, "Failed to save image", e)
    } finally {
        outputStream?.close()
    }
}

fun Context.exportImageFile(activity: Activity, file: File, fileName: String = "RikkaHub_${System.currentTimeMillis()}.png") {
    // 检查存储权限（Android 9及以下需要）
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
            return
        }
    }

    // 保存到相册
    var outputStream: OutputStream? = null
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10及以上使用MediaStore API
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                outputStream = contentResolver.openOutputStream(it)
                file.inputStream().copyTo(outputStream!!)
            }
        } else {
            // Android 9及以下直接写入文件
            val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val image = File(imagesDir, fileName)
            file.copyTo(image, overwrite = true)

            // 通知图库更新
            val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            mediaScanIntent.data = Uri.fromFile(image)
            sendBroadcast(mediaScanIntent)
        }
        Log.i(TAG, "Image file saved successfully: $fileName")
    } catch (e: Exception) {
        Log.e(TAG, "Failed to save image file", e)
    } finally {
        outputStream?.close()
    }
}

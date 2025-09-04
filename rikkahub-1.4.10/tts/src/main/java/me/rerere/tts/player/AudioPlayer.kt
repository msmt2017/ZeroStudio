package me.rerere.tts.player

import android.content.Context
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import me.rerere.tts.model.AudioFormat
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "AudioPlayer"

/**
 * 协程化的音频播放器，支持复用播放器实例
 * 支持 MediaPlayer 和 AudioTrack 的复用，提供资源管理
 *
 * ## 使用示例
 * ```kotlin
 * // 创建播放器实例
 * val audioPlayer = AudioPlayer(context)
 *
 * try {
 *     // 播放音频文件
 *     audioPlayer.playSound(audioData, AudioFormat.MP3)
 *
 *     // 播放 PCM 数据
 *     audioPlayer.playPcmSound(pcmData, 24000)
 *
 *     // 手动停止播放
 *     audioPlayer.stop()
 * } finally {
 *     // 释放资源
 *     audioPlayer.dispose()
 * }
 * ```
 *
 * ## 特性
 * - ✅ 复用 MediaPlayer 和 AudioTrack 实例，避免频繁创建
 * - ✅ 支持协程取消和错误处理
 * - ✅ 自动清理临时文件
 * - ✅ 线程安全的资源管理
 * - ✅ 支持多种音频格式 (MP3, WAV, OGG, AAC, OPUS, PCM)
 */
class AudioPlayer(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private var audioTrack: AudioTrack? = null
    private val isDisposed = AtomicBoolean(false)
    private var currentTempFile: File? = null

    /**
     * 使用指定格式播放音频数据
     */
    suspend fun playSound(
        sound: ByteArray,
        format: AudioFormat
    ): Unit = withContext(Dispatchers.IO) {
        if (isDisposed.get()) {
            throw IllegalStateException("AudioPlayer has been disposed")
        }

        suspendCancellableCoroutine { continuation ->
            try {
                // 停止当前播放
                stopCurrentPlayback()

                // 创建临时文件使用与 RikkaHubApp 相同的临时文件夹
                val tempDir = context.filesDir.resolve("temp")
                if (!tempDir.exists()) {
                    tempDir.mkdirs()
                }
                val tempFile = File.createTempFile("audio_temp", getFileExtension(format), tempDir)
                currentTempFile = tempFile
                tempFile.writeBytes(sound)

                // 获取或创建 MediaPlayer 实例
                val player = getOrCreateMediaPlayer()

                player.apply {
                    reset()
                    setDataSource(tempFile.absolutePath)

                    setOnCompletionListener {
                        Log.d(TAG, "Audio playback completed")
                        cleanupTempFile()
                        if (continuation.isActive) {
                            continuation.resume(Unit)
                        }
                    }

                    setOnErrorListener { _, what, extra ->
                        val errorMsg = "MediaPlayer error occurred $what $extra"
                        Log.e(TAG, errorMsg)
                        cleanupTempFile()
                        if (continuation.isActive) {
                            continuation.resumeWithException(Exception(errorMsg))
                        }
                        true
                    }

                    setOnPreparedListener {
                        it.start()
                        Log.d(TAG, "Audio playback started")
                    }

                    prepareAsync()
                }

                // 处理取消
                continuation.invokeOnCancellation {
                    Log.d(TAG, "Audio playback cancelled")
                    try {
                        stopCurrentPlayback()
                        cleanupTempFile()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error during cancellation cleanup", e)
                    }
                }

                Log.i(TAG, "Playing audio with format: $format")
            } catch (e: Exception) {
                val errorMsg = "Failed to play sound: ${e.message}"
                Log.e(TAG, errorMsg, e)
                cleanupTempFile()
                if (continuation.isActive) {
                    continuation.resumeWithException(Exception(errorMsg))
                }
            }
        }
    }

    /**
     * 播放 PCM 音频数据
     */
    suspend fun playPcmSound(
        pcmData: ByteArray,
        sampleRate: Int = 24000
    ): Unit = withContext(Dispatchers.IO) {
        if (isDisposed.get()) {
            throw IllegalStateException("AudioPlayer has been disposed")
        }

        suspendCancellableCoroutine { continuation ->
            try {
                // 停止当前播放
                stopCurrentPlayback()

                val minBufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    android.media.AudioFormat.CHANNEL_OUT_MONO,
                    android.media.AudioFormat.ENCODING_PCM_16BIT
                )

                // 获取或创建 AudioTrack 实例
                val track = getOrCreateAudioTrack(sampleRate, minBufferSize)

                // 处理取消
                continuation.invokeOnCancellation {
                    Log.d(TAG, "PCM playback cancelled")
                    try {
                        stopPcmPlayback()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error during PCM cancellation cleanup", e)
                    }
                }

                track.play()
                Log.d(TAG, "PCM playback started")

                val bytesWritten = track.write(pcmData, 0, pcmData.size)
                if (bytesWritten != pcmData.size) {
                    Log.w(TAG, "Not all PCM data was written: $bytesWritten/${pcmData.size}")
                }

                // 等待播放完成
                track.stop()
                Log.i(TAG, "PCM playback completed")

                if (continuation.isActive) {
                    continuation.resume(Unit)
                }
            } catch (e: Exception) {
                val errorMsg = "PCM playback failed: ${e.message}"
                Log.e(TAG, errorMsg, e)
                stopPcmPlayback()
                if (continuation.isActive) {
                    continuation.resumeWithException(Exception(errorMsg))
                }
            }
        }
    }

    /**
     * 停止当前播放
     */
    fun stop() {
        if (isDisposed.get()) return

        try {
            stopCurrentPlayback()
            cleanupTempFile()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping playback", e)
        }
    }

    /**
     * 释放所有资源
     */
    fun dispose() {
        if (isDisposed.compareAndSet(false, true)) {
            Log.d(TAG, "Disposing AudioPlayer")

            try {
                stopCurrentPlayback()
                cleanupTempFile()

                mediaPlayer?.release()
                mediaPlayer = null

                audioTrack?.release()
                audioTrack = null
            } catch (e: Exception) {
                Log.e(TAG, "Error during disposal", e)
            }
        }
    }

    /**
     * 获取或创建 MediaPlayer 实例
     */
    private fun getOrCreateMediaPlayer(): MediaPlayer {
        return mediaPlayer ?: run {
            val newPlayer = MediaPlayer()
            mediaPlayer = newPlayer
            newPlayer
        }
    }

    /**
     * 获取或创建 AudioTrack 实例
     */
    private fun getOrCreateAudioTrack(sampleRate: Int, bufferSize: Int): AudioTrack {
        // 如果当前 AudioTrack 配置不匹配，则重新创建
        audioTrack?.let { track ->
            if (track.sampleRate != sampleRate || track.state != AudioTrack.STATE_INITIALIZED) {
                track.release()
                audioTrack = null
            }
        }

        return audioTrack ?: run {
            val newTrack = AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                android.media.AudioFormat.CHANNEL_OUT_MONO,
                android.media.AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
                AudioTrack.MODE_STREAM
            )
            audioTrack = newTrack
            newTrack
        }
    }

    /**
     * 停止当前播放
     */
    private fun stopCurrentPlayback() {
        try {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.stop()
                }
                player.reset()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping MediaPlayer", e)
        }

        stopPcmPlayback()
    }

    /**
     * 停止 PCM 播放
     */
    private fun stopPcmPlayback() {
        try {
            audioTrack?.let { track ->
                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        track.stop()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioTrack", e)
        }
    }

    /**
     * 清理临时文件
     */
    private fun cleanupTempFile() {
        currentTempFile?.let { file ->
            try {
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting temp file", e)
            }
            currentTempFile = null
        }
    }

    private fun getFileExtension(format: AudioFormat): String {
        return when (format) {
            AudioFormat.MP3 -> ".mp3"
            AudioFormat.WAV -> ".wav"
            AudioFormat.OGG -> ".ogg"
            AudioFormat.AAC -> ".aac"
            AudioFormat.OPUS -> ".opus"
            AudioFormat.PCM -> ".pcm"
        }
    }
}

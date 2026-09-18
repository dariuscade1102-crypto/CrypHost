package com.cryptmc.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

sealed class DownloadProgress {
    data class InProgress(val bytesRead: Long, val totalBytes: Long?) : DownloadProgress()
    data class Done(val file: File) : DownloadProgress()
    data class Failed(val message: String) : DownloadProgress()
}

/**
 * Streams an arbitrary URL to disk with progress — used for the on-device
 * AI model `.task` file (ai/OnDeviceAiEngine.kt) but generic enough to
 * reuse for anything else this app needs to pull down as a raw file
 * (same shape as network/ServerJarDownloader.kt for server jars).
 */
class ModelDownloader {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // model files can be gigabytes; no fixed read timeout
        .build()

    fun download(url: String, destination: File): Flow<DownloadProgress> = flow {
        destination.parentFile?.mkdirs()
        val tmp = File(destination.parentFile, "${destination.name}.part")
        val request = Request.Builder().url(url).build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                emit(DownloadProgress.Failed("HTTP ${response.code} downloading model"))
                return@flow
            }
            val body = response.body ?: run {
                emit(DownloadProgress.Failed("Empty response body"))
                return@flow
            }
            val total = body.contentLength().takeIf { it > 0 }
            var readTotal = 0L
            body.byteStream().use { input ->
                tmp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        readTotal += read
                        emit(DownloadProgress.InProgress(readTotal, total))
                    }
                }
            }
            tmp.renameTo(destination)
            emit(DownloadProgress.Done(destination))
        }
    }.flowOn(Dispatchers.IO)
}

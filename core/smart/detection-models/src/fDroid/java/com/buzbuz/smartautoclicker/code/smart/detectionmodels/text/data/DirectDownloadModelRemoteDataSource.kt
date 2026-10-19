// Modified for Yuklick on 2026-10-20. See CHANGELOG.md.
/*
 * Copyright (C) 2026 Kevin Buzeau
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.buzbuz.smartautoclicker.code.smart.detectionmodels.text.data

import android.content.Context
import android.util.Log

import com.buzbuz.smartautoclicker.core.base.di.Dispatcher
import com.buzbuz.smartautoclicker.core.base.di.HiltCoroutineDispatchers.IO
import com.buzbuz.smartautoclicker.code.smart.detectionmodels.text.domain.OCRAlphabet

import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class DirectDownloadModelRemoteDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    @param:Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
): RecognitionModelsRemoteDataSource {
    private val _currentlyDownloading = MutableStateFlow<Map<OCRAlphabet, Int>>(emptyMap())
    override val currentlyDownloading: StateFlow<Map<OCRAlphabet, Int>> = _currentlyDownloading
    private val recognitionModelsDataDir = context.recognitionModelsDataDir()

    override suspend fun downloadRecognitionModel(alphabet: OCRAlphabet, onSuccess: () -> Unit) {
        synchronized(this) {
            if (_currentlyDownloading.value.containsKey(alphabet)) return
            _currentlyDownloading.update { it + (alphabet to 0) }
        }
        try {
            withContext(ioDispatcher) {
                val target = recognitionModelsDataDir.recognitionModelDataDir(alphabet)
                val staging = File(recognitionModelsDataDir, ".${alphabet.name.lowercase()}.download")
                staging.mkdirs()
                try {
                    val files = listOf("rec.ncnn.param", "rec.ncnn.bin", "dict.txt")
                    files.forEachIndexed { index, name ->
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        val path = "core/smart/detection-models/models/text/${alphabet.name.lowercase()}/src/main/assets/models/text/recognize/${alphabet.name.lowercase()}/$name"
                        var downloaded = false
                        for (base in MODEL_BASE_URLS) {
                            if (downloadFile(base + path, File(staging, name)) { progress ->
                                _currentlyDownloading.update { it + (alphabet to ((index * 100 + progress) / 3)) }
                            }) { downloaded = true; break }
                        }
                        check(downloaded) { "Model file download failed: $name" }
                    }
                    check(File(staging, "rec.ncnn.param").bufferedReader().use { it.readLine() } == "7767517") { "Invalid NCNN model" }
                    target.mkdirs()
                    files.forEach { name -> File(staging, name).copyTo(File(target, name), overwrite = true) }
                    onSuccess()
                } finally { staging.deleteRecursively() }
            }
        } catch (ex: kotlinx.coroutines.CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Log.e(TAG, "Cannot install $alphabet", ex)
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                android.widget.Toast.makeText(context, "模型下载失败，请检查网络后重试", android.widget.Toast.LENGTH_LONG).show()
            }
        } finally { _currentlyDownloading.update { it - alphabet } }
    }

    private suspend fun downloadFile(url: String, dest: File, onProgress: (Int) -> Unit): Boolean {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 20_000
        connection.instanceFollowRedirects = true
        try {
            check(connection.responseCode == HttpURLConnection.HTTP_OK) { "HTTP ${connection.responseCode}" }
            val total = connection.contentLengthLong
            var read = 0L
            connection.inputStream.use { input -> dest.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    read += count
                    if (total > 0) onProgress((read * 100 / total).toInt().coerceIn(0, 100))
                }
            } }
            check(read > 0 && (total <= 0 || read == total)) { "Incomplete model" }
            return true
        } catch (ex: kotlinx.coroutines.CancellationException) { throw ex
        } catch (ex: Exception) {
            Log.w(TAG, "Download failed: $url", ex)
            return false
        } finally { connection.disconnect() }
    }
}

private val MODEL_BASE_URLS = listOf(
    "https://raw.githubusercontent.com/Nain57/Smart-AutoClicker/master/",
    "https://github.com/Nain57/Smart-AutoClicker/raw/refs/heads/master/",
)
private const val TAG = "DirectDownloadModelRemoteDataSource"

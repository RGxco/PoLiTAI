package com.example.politai

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SyncRepository {
    private const val FILE_PREFIX = "synced_"
    private val gson = Gson()
    private val listType = object : TypeToken<List<Map<String, Any?>>>() {}.type

    data class SyncSummary(
        val sourceCount: Int,
        val updatedFiles: List<String>,
        val recordCount: Int,
        val details: List<String>
    )

    suspend fun syncUrls(context: Context, rawInput: String): SyncSummary = withContext(Dispatchers.IO) {
        val urls = parseUrls(rawInput)
        require(urls.isNotEmpty()) { "Please enter at least one valid http(s) URL." }

        val updatedFiles = mutableListOf<String>()
        val details = mutableListOf<String>()
        val failures = mutableListOf<String>()
        var totalRecords = 0
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        urls.forEach { urlString ->
            try {
                val payload = downloadPage(urlString)
                val cleanText = DocumentProcessor.stripHtmlTags(payload.html).take(20000)
                require(cleanText.length >= 120) { "Could not extract enough readable text from $urlString" }

                val url = URL(urlString)
                val host = url.host.removePrefix("www.")
                val fileName = buildFileName(host)
                val syncFile = File(context.filesDir, fileName)
                val title = payload.title.ifBlank { host }
                val language = AppLanguages.detect("$title\n$cleanText")
                val chunks = splitIntoChunks(cleanText)
                val existingRecords = readRecords(syncFile)
                    .filterNot { record -> record["url"]?.toString() == urlString }

                val newRecords = chunks.mapIndexed { index, chunk ->
                    linkedMapOf<String, Any>(
                        "id" to "${host.hashCode()}-${urlString.hashCode()}-$index",
                        "type" to "synced_web",
                        "source" to host,
                        "title" to title,
                        "language" to language.code,
                        "synced_at" to timestamp,
                        "url" to urlString,
                        "text" to buildString {
                            append("Source: ")
                            append(host)
                            append(". Title: ")
                            append(title)
                            append(". Language: ")
                            append(language.responseName)
                            append(". ")
                            append(chunk)
                        }
                    )
                }

                syncFile.writeText(gson.toJson(existingRecords + newRecords))
                updatedFiles += fileName
                totalRecords += newRecords.size
                details += "$host (${language.responseName}) -> ${newRecords.size} sections"
            } catch (error: Exception) {
                failures += "$urlString -> ${error.message ?: "Unknown error"}"
            }
        }

        if (updatedFiles.isEmpty()) {
            throw IllegalStateException(failures.joinToString("\n").ifBlank { "Sync failed for all URLs." })
        }

        if (failures.isNotEmpty()) {
            details += failures.map { "Failed: $it" }
        }

        SyncSummary(
            sourceCount = urls.size,
            updatedFiles = updatedFiles.distinct(),
            recordCount = totalRecords,
            details = details
        )
    }

    fun listSyncedDatabaseNames(context: Context): List<String> {
        return context.filesDir.listFiles()
            ?.filter { it.isFile && it.name.startsWith(FILE_PREFIX) && it.extension.equals("json", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            ?.map { it.name }
            .orEmpty()
    }

    private fun parseUrls(rawInput: String): List<String> {
        return rawInput.split(Regex("[\\r\\n,]+"))
            .map { it.trim() }
            .filter { it.startsWith("http://") || it.startsWith("https://") }
            .distinct()
    }

    private fun buildFileName(host: String): String {
        val slug = host.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "source" }
        return "${FILE_PREFIX}${slug}.json"
    }

    private fun readRecords(file: File): List<Map<String, Any?>> {
        if (!file.exists()) return emptyList()
        return runCatching {
            gson.fromJson<List<Map<String, Any?>>>(file.readText(), listType) ?: emptyList()
        }.getOrDefault(emptyList())
    }

    private fun splitIntoChunks(text: String): List<String> {
        val paragraphs = text.split(Regex("\\n\\s*\\n"))
            .map { it.trim() }
            .filter { it.length >= 40 }

        if (paragraphs.isNotEmpty()) {
            return paragraphs.chunked(2).map { block -> block.joinToString("\n\n") }
        }

        return text.chunked(1200).map { it.trim() }.filter { it.isNotBlank() }
    }

    private fun downloadPage(urlString: String): PagePayload {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.setRequestProperty("User-Agent", "PoLiTAI/3.2")

        return try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${connection.responseCode}")
            }

            val charset = parseCharset(connection.contentType)
            val html = connection.inputStream.bufferedReader(charset ?: Charsets.UTF_8).use { it.readText() }
            PagePayload(
                title = extractTitle(html),
                html = html
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun parseCharset(contentType: String?): Charset? {
        val charsetName = Regex("charset=([^;]+)", RegexOption.IGNORE_CASE)
            .find(contentType.orEmpty())
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.trim('"', '\'')
            ?: return null

        return runCatching { Charset.forName(charsetName) }.getOrNull()
    }

    private fun extractTitle(html: String): String {
        return Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            .find(html)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            .orEmpty()
    }

    private data class PagePayload(
        val title: String,
        val html: String
    )
}

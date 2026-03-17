package com.example.politai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.min
import kotlin.math.roundToInt

object DocumentProcessor {
    private const val MAX_TEXT_CHARS = 12000
    private const val MAX_PDF_PAGES = 5
    private const val PDF_RENDER_LONG_SIDE = 1600

    data class ParsedDocument(
        val type: String,
        val content: String
    )

    suspend fun parse(
        context: Context,
        uri: Uri,
        displayName: String,
        mimeType: String?
    ): ParsedDocument {
        val type = guessType(displayName, mimeType)
        val extractedText = when (type) {
            "pdf" -> extractPdfText(context, uri)
            "docx" -> withContext(Dispatchers.IO) { extractDocxText(context, uri) }
            "html" -> withContext(Dispatchers.IO) { stripHtmlTags(readText(context, uri)) }
            "doc" -> withContext(Dispatchers.IO) { readBinaryFallback(context, uri) }
            else -> withContext(Dispatchers.IO) { readText(context, uri) }
        }

        return ParsedDocument(
            type = type,
            content = normalizeText(extractedText).take(MAX_TEXT_CHARS)
        )
    }

    fun stripHtmlTags(html: String): String {
        var temp = html.replace(
            Regex("<(style|script|noscript|header|footer|nav)[^>]*>.*?</\\1>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)),
            " "
        )

        val paragraphRegex = Regex("<(p|article|div|li|h1|h2|h3)[^>]*>(.*?)</\\1>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val extracted = paragraphRegex.findAll(temp)
            .map { it.groupValues[2] }
            .joinToString("\n\n")
            .ifBlank { temp }

        temp = extracted
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</(p|div|article|li|h1|h2|h3|tr)>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]*>"), " ")

        return decodeEntities(temp)
    }

    private fun guessType(displayName: String, mimeType: String?): String {
        val extension = displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when {
            mimeType == "application/pdf" || extension == "pdf" -> "pdf"
            mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" || extension == "docx" -> "docx"
            mimeType == "application/msword" || extension == "doc" -> "doc"
            mimeType?.contains("html", ignoreCase = true) == true || extension in setOf("html", "htm") -> "html"
            extension in setOf("txt", "md", "csv", "json", "xml", "rtf") -> extension
            else -> extension.ifBlank { "text" }
        }
    }

    private fun readText(context: Context, uri: Uri): String {
        return context.contentResolver.openInputStream(uri)?.use { input ->
            input.bufferedReader(Charsets.UTF_8).readText()
        }.orEmpty()
    }

    private fun extractDocxText(context: Context, uri: Uri): String {
        return context.contentResolver.openInputStream(uri)?.use { input ->
            val parts = mutableListOf<String>()
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name.lowercase(Locale.ROOT)
                    if (name == "word/document.xml" || name.startsWith("word/header") || name.startsWith("word/footer")) {
                        val xml = ByteArrayOutputStream().also { output ->
                            zip.copyTo(output)
                        }.toString(Charsets.UTF_8.name())

                        val text = decodeEntities(
                            xml.replace(Regex("</w:p>"), "\n")
                                .replace(Regex("<w:br[^>]*/>"), "\n")
                                .replace(Regex("<w:tab[^>]*/>"), "\t")
                                .replace(Regex("</w:tr>"), "\n")
                                .replace(Regex("</w:tc>"), "\t")
                                .replace(Regex("<[^>]+>"), " ")
                        )

                        if (text.isNotBlank()) {
                            parts += text
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            parts.joinToString("\n\n")
        }.orEmpty()
    }

    private fun readBinaryFallback(context: Context, uri: Uri): String {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return ""
        val asciiText = String(bytes, Charsets.ISO_8859_1)
        val unicodeText = String(bytes, Charsets.UTF_16LE)

        return listOf(asciiText, unicodeText)
            .flatMap { candidate ->
                Regex("[\\p{L}\\p{N}][\\p{L}\\p{N}\\p{Punct}\\s]{20,}")
                    .findAll(candidate)
                    .map { it.value.trim() }
                    .toList()
            }
            .distinct()
            .joinToString("\n")
    }

    private suspend fun extractPdfText(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return@withContext ""
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        try {
            val renderer = PdfRenderer(descriptor)
            try {
                val builder = StringBuilder()
                val pagesToRead = min(renderer.pageCount, MAX_PDF_PAGES)

                for (pageIndex in 0 until pagesToRead) {
                    val page = renderer.openPage(pageIndex)
                    try {
                        val longSide = maxOf(page.width, page.height).coerceAtLeast(1)
                        val scale = min(2f, PDF_RENDER_LONG_SIDE / longSide.toFloat())
                        val width = maxOf(1, (page.width * scale).roundToInt())
                        val height = maxOf(1, (page.height * scale).roundToInt())
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

                        try {
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val pageText = recognizer.process(InputImage.fromBitmap(bitmap, 0)).awaitResult().text
                            if (pageText.isNotBlank()) {
                                if (builder.isNotEmpty()) builder.append("\n\n")
                                builder.append(pageText)
                            }
                        } finally {
                            bitmap.recycle()
                        }
                    } finally {
                        page.close()
                    }
                }

                builder.toString()
            } finally {
                renderer.close()
            }
        } finally {
            recognizer.close()
            descriptor.close()
        }
    }

    private fun normalizeText(text: String): String {
        return decodeEntities(text)
            .replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]"), " ")
            .replace(Regex("[ \t]+"), " ")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    private fun decodeEntities(text: String): String {
        return text
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
    }

    private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result ->
            continuation.resume(result)
        }.addOnFailureListener { error ->
            continuation.resumeWithException(error)
        }
    }
}

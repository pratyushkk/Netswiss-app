package com.netswiss.app.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

object BundleExtractor {

    data class ExtractionResult(
        val apkFiles: List<File>,
        val error: String? = null
    )

    /**
     * Supported bundle extensions.
     */
    val SUPPORTED_EXTENSIONS = listOf("apk", "xapk", "apkm", "apks")

    /** Check if the file name has a bundle extension (other than apk) */
    fun isBundleFile(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in SUPPORTED_EXTENSIONS && ext != "apk"
    }

    /**
     * Checks if a file is actually a bundle, even if it has an .apk extension.
     * Bundles (XAPK, APKS, APKM) contain other .apk files inside them.
     */
    fun isActuallyBundle(file: File): Boolean {
        if (isBundleFile(file.name)) return true
        
        // Inspect the ZIP contents to see if it contains other .apk files
        try {
            ZipFile(file).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (!entry.isDirectory && entry.name.lowercase().endsWith(".apk") && entry.name.lowercase() != "base.apk" && !entry.name.lowercase().contains("config.")) {
                        // If it contains embedded APK files in directories or multiple apks, it's a bundle.
                        // Wait, a standard AAB might contain base.apk but we don't install AABs.
                        // A standard APK does not contain .apk files inside it.
                        return true
                    }
                }
            }
        } catch (_: Exception) {}
        
        return false
    }

    fun isApkFile(fileName: String): Boolean {
        return fileName.lowercase().endsWith(".apk")
    }

    fun isAabFile(fileName: String): Boolean {
        return fileName.lowercase().endsWith(".aab")
    }

    fun getFileExtension(fileName: String): String {
        return fileName.substringAfterLast('.', "").lowercase()
    }

    /**
     * Extract APK files from a bundle (XAPK/APKM/APKS) URI.
     * Returns extracted APK file paths in the app's cache directory.
     */
    fun extractBundle(context: Context, uri: Uri): ExtractionResult {
        // Copy URI to a temporary file first because ZipFile needs a File
        val tempFile = File(context.cacheDir, "temp_bundle_${System.currentTimeMillis()}.zip")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            return extractBundle(context, tempFile).also {
                tempFile.delete()
            }
        } catch (e: Exception) {
            tempFile.delete()
            return ExtractionResult(emptyList(), "Failed to read bundle: ${e.message}")
        }
    }

    /**
     * Extract APK files from a bundle File using ZipFile (more reliable than ZipInputStream).
     */
    fun extractBundle(context: Context, file: File): ExtractionResult {
        val cacheDir = File(context.cacheDir, "bundle_extract_${System.currentTimeMillis()}")
        cacheDir.mkdirs()

        return try {
            val apkFiles = mutableListOf<File>()

            ZipFile(file).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    // Extract all .apk files from the bundle
                    if (!entry.isDirectory && entry.name.lowercase().endsWith(".apk")) {
                        val outFile = File(cacheDir, entry.name.substringAfterLast('/'))
                        zip.getInputStream(entry).use { input ->
                            outFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        apkFiles.add(outFile)
                    }
                }
            }

            if (apkFiles.isEmpty()) {
                ExtractionResult(emptyList(), "No APK files found inside bundle")
            } else {
                ExtractionResult(apkFiles)
            }
        } catch (e: Exception) {
            cacheDir.deleteRecursively()
            ExtractionResult(emptyList(), e.message ?: "Extraction failed")
        }
    }
}

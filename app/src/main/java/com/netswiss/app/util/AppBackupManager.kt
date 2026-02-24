package com.netswiss.app.util

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class BackupInfo(
    val file: File,
    val appName: String,
    val packageName: String,
    val versionName: String,
    val backupDate: Long,
    val fileSize: Long,
    val isSplit: Boolean
)

object AppBackupManager {

    private const val BACKUP_DIR_NAME = "NetSwiss/Backups"

    fun getBackupDir(): File {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            BACKUP_DIR_NAME
        )
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Backup an installed app. For single APKs, copies the file directly.
     * For split APKs, creates a .apks ZIP bundle.
     */
    fun backupApp(context: Context, packageName: String): Result<BackupInfo> {
        return try {
            val appInfo = InstalledAppsProvider.getAppInfo(context, packageName)
                ?: return Result.failure(Exception("App not found: $packageName"))

            val backupDir = getBackupDir()
            val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val safeName = appInfo.name.replace(Regex("[^a-zA-Z0-9._-]"), "_")

            if (appInfo.isSplit) {
                // Create .apks ZIP bundle for split APKs
                val backupFile = File(backupDir, "${safeName}_${appInfo.versionName}_$dateStr.apks")
                ZipOutputStream(FileOutputStream(backupFile)).use { zos ->
                    // Add base APK
                    addFileToZip(zos, File(appInfo.apkPath), "base.apk")

                    // Add split APKs
                    appInfo.splitPaths.forEachIndexed { index, path ->
                        val splitFile = File(path)
                        addFileToZip(zos, splitFile, "split_$index.apk")
                    }
                }

                Result.success(
                    BackupInfo(
                        file = backupFile,
                        appName = appInfo.name,
                        packageName = packageName,
                        versionName = appInfo.versionName,
                        backupDate = System.currentTimeMillis(),
                        fileSize = backupFile.length(),
                        isSplit = true
                    )
                )
            } else {
                // Single APK - copy directly
                val backupFile = File(backupDir, "${safeName}_${appInfo.versionName}_$dateStr.apk")
                File(appInfo.apkPath).copyTo(backupFile, overwrite = true)

                Result.success(
                    BackupInfo(
                        file = backupFile,
                        appName = appInfo.name,
                        packageName = packageName,
                        versionName = appInfo.versionName,
                        backupDate = System.currentTimeMillis(),
                        fileSize = backupFile.length(),
                        isSplit = false
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * List all existing backup files.
     */
    fun getBackups(): List<BackupInfo> {
        val backupDir = getBackupDir()
        return backupDir.listFiles()
            ?.filter { it.extension in listOf("apk", "apks") }
            ?.map { file ->
                val parts = file.nameWithoutExtension.split("_")
                val appName = parts.firstOrNull() ?: "Unknown"
                val versionName = if (parts.size > 1) parts[1] else "?"
                BackupInfo(
                    file = file,
                    appName = appName.replace("_", " "),
                    packageName = "",
                    versionName = versionName,
                    backupDate = file.lastModified(),
                    fileSize = file.length(),
                    isSplit = file.extension == "apks"
                )
            }
            ?.sortedByDescending { it.backupDate }
            ?: emptyList()
    }

    /**
     * Delete a backup file.
     */
    fun deleteBackup(backupInfo: BackupInfo): Boolean {
        return backupInfo.file.delete()
    }

    private fun addFileToZip(zos: ZipOutputStream, file: File, entryName: String) {
        zos.putNextEntry(ZipEntry(entryName))
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(65536)
            var len: Int
            while (fis.read(buffer).also { len = it } != -1) {
                zos.write(buffer, 0, len)
            }
        }
        zos.closeEntry()
    }

    /**
     * Format file size for display.
     */
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
            else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
}

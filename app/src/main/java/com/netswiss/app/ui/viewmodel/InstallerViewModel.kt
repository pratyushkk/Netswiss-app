package com.netswiss.app.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.netswiss.app.util.ApkInstaller
import com.netswiss.app.util.BundleExtractor
import com.netswiss.app.util.InstallStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class SelectedFile(
    val uri: Uri,
    val name: String,
    val extension: String,
    val size: Long,
    val cachedFile: File? = null
)

data class InstallItem(
    val file: SelectedFile,
    val status: InstallStatus = InstallStatus.Queued
)

data class InstallerUiState(
    val selectedFiles: List<SelectedFile> = emptyList(),
    val installQueue: List<InstallItem> = emptyList(),
    val isInstalling: Boolean = false,
    val currentInstallIndex: Int = -1,
    val overallProgress: Float = 0f,
    val needsInstallPermission: Boolean = false,
    val message: String? = null
)

class InstallerViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(InstallerUiState())
    val uiState: StateFlow<InstallerUiState> = _uiState.asStateFlow()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun addFiles(uris: List<Uri>) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            val newFiles = mutableListOf<SelectedFile>()

            for (uri in uris) {
                try {
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: Exception) {}

                    val docFile = DocumentFile.fromSingleUri(context, uri)
                    val name = docFile?.name ?: uri.lastPathSegment ?: "unknown.apk"
                    val ext = BundleExtractor.getFileExtension(name)
                    val resolvedExt = if (ext in BundleExtractor.SUPPORTED_EXTENSIONS || ext == "aab") {
                        ext
                    } else {
                        "apk"
                    }

                    // Copy to cache immediately while URI permission is valid
                    val cacheDir = File(context.cacheDir, "install_cache")
                    cacheDir.mkdirs()
                    val cachedFile = File(cacheDir, "${System.currentTimeMillis()}_${name}")

                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            cachedFile.outputStream().buffered().use { output ->
                                input.copyTo(output)
                                output.flush()
                            }
                        }
                    }

                    if (cachedFile.exists() && cachedFile.length() > 0) {
                        newFiles.add(
                            SelectedFile(
                                uri = uri,
                                name = name,
                                extension = resolvedExt,
                                size = cachedFile.length(),
                                cachedFile = cachedFile
                            )
                        )
                    }
                } catch (_: Exception) {}
            }

            _uiState.value = _uiState.value.copy(
                selectedFiles = _uiState.value.selectedFiles + newFiles,
                message = if (newFiles.isEmpty() && uris.isNotEmpty()) {
                    "Could not read the selected files."
                } else null
            )
        }
    }

    fun removeFile(file: SelectedFile) {
        file.cachedFile?.delete()
        _uiState.value = _uiState.value.copy(
            selectedFiles = _uiState.value.selectedFiles - file
        )
    }

    fun clearQueue() {
        _uiState.value.selectedFiles.forEach { it.cachedFile?.delete() }
        _uiState.value = InstallerUiState()
    }

    fun checkInstallPermission(): Boolean {
        val context = getApplication<Application>()
        val canInstall = ApkInstaller.canInstallPackages(context)
        if (!canInstall) {
            _uiState.value = _uiState.value.copy(needsInstallPermission = true)
        }
        return canInstall
    }

    fun dismissPermissionPrompt() {
        _uiState.value = _uiState.value.copy(needsInstallPermission = false)
    }

    fun startInstall() {
        val context = getApplication<Application>()
        val files = _uiState.value.selectedFiles
        if (files.isEmpty()) return

        if (!ApkInstaller.canInstallPackages(context)) {
            _uiState.value = _uiState.value.copy(needsInstallPermission = true)
            return
        }

        val queue = files.map { InstallItem(it) }
        _uiState.value = _uiState.value.copy(
            installQueue = queue,
            isInstalling = true,
            currentInstallIndex = 0
        )

        viewModelScope.launch(Dispatchers.IO) {
            for (i in files.indices) {
                _uiState.value = _uiState.value.copy(currentInstallIndex = i)
                val selectedFile = files[i]

                try {
                    if (selectedFile.extension == "aab") {
                        updateItemStatus(i, InstallStatus.Failure("AAB files cannot be installed directly."))
                        continue
                    }

                    val cachedFile = selectedFile.cachedFile
                    if (cachedFile == null || !cachedFile.exists() || cachedFile.length() == 0L) {
                        updateItemStatus(i, InstallStatus.Failure("File not available: ${selectedFile.name}"))
                        continue
                    }

                    if (BundleExtractor.isActuallyBundle(cachedFile)) {
                        val result = BundleExtractor.extractBundle(context, cachedFile)
                        if (result.error != null) {
                            updateItemStatus(i, InstallStatus.Failure(result.error))
                            continue
                        }

                        if (result.apkFiles.isEmpty()) {
                            updateItemStatus(i, InstallStatus.Failure("No APK files found in bundle"))
                            continue
                        }

                        // Use PackageInstaller session API for bundle APKs
                        installViaSession(i, result.apkFiles)
                    } else {
                        // Use PackageInstaller session API for single APK too
                        installViaSession(i, listOf(cachedFile))
                    }
                } catch (e: Exception) {
                    updateItemStatus(i, InstallStatus.Failure("Error: ${e.message}"))
                }
            }

            _uiState.value = _uiState.value.copy(
                isInstalling = false,
                message = if (files.size > 1) "Initial launch complete. Please respond to confirmation dialogs." else null
            )
        }
    }

    private fun installViaSession(index: Int, apkFiles: List<File>) {
        val context = getApplication<Application>()
        // Must run installApks on main thread because SessionCallback handler
        // needs the main looper
        mainHandler.post {
            ApkInstaller.installApks(context, apkFiles) { status ->
                updateItemStatus(index, status)
            }
        }
        // Give time for the session to be created and committed
        Thread.sleep(2000)
    }

    private fun updateItemStatus(index: Int, status: InstallStatus) {
        val queue = _uiState.value.installQueue.toMutableList()
        if (index < queue.size) {
            queue[index] = queue[index].copy(status = status)
            
            // Show a prompt to the user when install completes
            val globalMessage = when (status) {
                is InstallStatus.Success -> "Successfully installed ${queue[index].file.name}"
                is InstallStatus.Failure -> "Failed to install ${queue[index].file.name}: ${status.message}"
                else -> _uiState.value.message
            }
            
            _uiState.value = _uiState.value.copy(
                installQueue = queue,
                message = globalMessage
            )
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    override fun onCleared() {
        super.onCleared()
        val context = getApplication<Application>()
        File(context.cacheDir, "install_cache").deleteRecursively()
        File(context.cacheDir, "share").deleteRecursively()
        val cacheDirs = context.cacheDir.listFiles { file -> 
            file.isDirectory && file.name.startsWith("bundle_extract_")
        }
        cacheDirs?.forEach { it.deleteRecursively() }
        
        val tempZips = context.cacheDir.listFiles { file ->
            file.isFile && file.name.startsWith("temp_bundle_")
        }
        tempZips?.forEach { it.delete() }
    }
}

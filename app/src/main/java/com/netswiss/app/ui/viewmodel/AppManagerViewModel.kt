package com.netswiss.app.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.netswiss.app.util.AppBackupManager
import com.netswiss.app.util.AppInfo
import com.netswiss.app.util.InstalledAppsProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class AppManagerUiState(
    val apps: List<AppInfo> = emptyList(),
    val filteredApps: List<AppInfo> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val selectedApp: AppInfo? = null,
    val sortOrder: SortOrder = SortOrder.NAME_ASC,
    val message: String? = null
)

enum class SortOrder {
    NAME_ASC, NAME_DESC, SIZE_ASC, SIZE_DESC, INSTALL_DATE, UPDATE_DATE
}

class AppManagerViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AppManagerUiState())
    val uiState: StateFlow<AppManagerUiState> = _uiState.asStateFlow()

    init {
        loadApps()
    }

    fun loadApps() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val apps = InstalledAppsProvider.getInstalledUserApps(getApplication())
            _uiState.value = _uiState.value.copy(
                apps = apps,
                filteredApps = filterAndSort(apps, _uiState.value.searchQuery, _uiState.value.sortOrder),
                isLoading = false
            )
        }
    }

    fun updateSearch(query: String) {
        _uiState.value = _uiState.value.copy(
            searchQuery = query,
            filteredApps = filterAndSort(_uiState.value.apps, query, _uiState.value.sortOrder)
        )
    }

    fun updateSortOrder(order: SortOrder) {
        _uiState.value = _uiState.value.copy(
            sortOrder = order,
            filteredApps = filterAndSort(_uiState.value.apps, _uiState.value.searchQuery, order)
        )
    }

    fun selectApp(app: AppInfo?) {
        _uiState.value = _uiState.value.copy(selectedApp = app)
    }

    fun uninstallApp(packageName: String) {
        val context = getApplication<Application>()
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun shareApk(app: AppInfo) {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sourceFile = File(app.apkPath)
                val cacheDir = File(context.cacheDir, "share")
                cacheDir.mkdirs()
                val shareFile = File(cacheDir, "${app.name.replace(" ", "_")}.apk")
                sourceFile.copyTo(shareFile, overwrite = true)

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    shareFile
                )

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/vnd.android.package-archive"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share ${app.name}").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    message = "Failed to share: ${e.message}"
                )
            }
        }
    }

    fun backupApp(app: AppInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = AppBackupManager.backupApp(getApplication(), app.packageName)
            result.fold(
                onSuccess = { backup ->
                    _uiState.value = _uiState.value.copy(
                        message = "Backed up ${app.name} (${AppBackupManager.formatFileSize(backup.fileSize)})"
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        message = "Backup failed: ${e.message}"
                    )
                }
            )
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    private fun filterAndSort(
        apps: List<AppInfo>,
        query: String,
        order: SortOrder
    ): List<AppInfo> {
        val filtered = if (query.isBlank()) apps
        else apps.filter {
            it.name.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
        }

        return when (order) {
            SortOrder.NAME_ASC -> filtered.sortedBy { it.name.lowercase() }
            SortOrder.NAME_DESC -> filtered.sortedByDescending { it.name.lowercase() }
            SortOrder.SIZE_ASC -> filtered.sortedBy { it.apkSize }
            SortOrder.SIZE_DESC -> filtered.sortedByDescending { it.apkSize }
            SortOrder.INSTALL_DATE -> filtered.sortedByDescending { it.installTime }
            SortOrder.UPDATE_DATE -> filtered.sortedByDescending { it.updateTime }
        }
    }
}

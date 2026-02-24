package com.netswiss.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.netswiss.app.util.AppBackupManager
import com.netswiss.app.util.AppInfo
import com.netswiss.app.util.ApkInstaller
import com.netswiss.app.util.BackupInfo
import com.netswiss.app.util.BundleExtractor
import com.netswiss.app.util.InstallStatus
import com.netswiss.app.util.InstalledAppsProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BackupRestoreUiState(
    val installedApps: List<AppInfo> = emptyList(),
    val selectedForBackup: Set<String> = emptySet(), // package names
    val backups: List<BackupInfo> = emptyList(),
    val isLoadingApps: Boolean = false,
    val isBackingUp: Boolean = false,
    val isRestoring: Boolean = false,
    val backupProgress: String? = null,
    val restoreStatus: InstallStatus? = null,
    val message: String? = null,
    val activeTab: Int = 0 // 0 = Backup, 1 = Restore
)

class BackupRestoreViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(BackupRestoreUiState())
    val uiState: StateFlow<BackupRestoreUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoadingApps = true)
            val apps = InstalledAppsProvider.getInstalledUserApps(getApplication())
            val backups = AppBackupManager.getBackups()
            _uiState.value = _uiState.value.copy(
                installedApps = apps,
                backups = backups,
                isLoadingApps = false
            )
        }
    }

    fun setActiveTab(tab: Int) {
        _uiState.value = _uiState.value.copy(activeTab = tab)
    }

    fun toggleAppSelection(packageName: String) {
        val current = _uiState.value.selectedForBackup
        _uiState.value = _uiState.value.copy(
            selectedForBackup = if (packageName in current) {
                current - packageName
            } else {
                current + packageName
            }
        )
    }

    fun selectAllApps() {
        _uiState.value = _uiState.value.copy(
            selectedForBackup = _uiState.value.installedApps.map { it.packageName }.toSet()
        )
    }

    fun deselectAllApps() {
        _uiState.value = _uiState.value.copy(selectedForBackup = emptySet())
    }

    fun backupSelectedApps() {
        val selected = _uiState.value.selectedForBackup
        if (selected.isEmpty()) {
            _uiState.value = _uiState.value.copy(message = "No apps selected for backup")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isBackingUp = true)
            var successCount = 0
            var failCount = 0

            selected.forEachIndexed { index, packageName ->
                val appName = _uiState.value.installedApps.find { it.packageName == packageName }?.name ?: packageName
                _uiState.value = _uiState.value.copy(
                    backupProgress = "Backing up $appName (${index + 1}/${selected.size})"
                )

                val result = AppBackupManager.backupApp(getApplication(), packageName)
                if (result.isSuccess) successCount++ else failCount++
            }

            val backups = AppBackupManager.getBackups()
            _uiState.value = _uiState.value.copy(
                isBackingUp = false,
                backupProgress = null,
                backups = backups,
                selectedForBackup = emptySet(),
                message = "Backup complete: $successCount succeeded, $failCount failed"
            )
        }
    }

    fun restoreBackup(backup: BackupInfo) {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isRestoring = true)

            try {
                val apkFiles = if (backup.isSplit) {
                    val result = BundleExtractor.extractBundle(context, backup.file)
                    if (result.error != null) {
                        _uiState.value = _uiState.value.copy(
                            isRestoring = false,
                            message = "Restore failed: ${result.error}"
                        )
                        return@launch
                    }
                    result.apkFiles
                } else {
                    listOf(backup.file)
                }

                // Use PackageInstaller session API via callback
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    ApkInstaller.installApks(context, apkFiles) { status ->
                        _uiState.value = _uiState.value.copy(restoreStatus = status)
                        if (status is InstallStatus.Success || status is InstallStatus.Failure) {
                            _uiState.value = _uiState.value.copy(
                                isRestoring = false,
                                message = if (status is InstallStatus.Success) "Restore launched — confirm on screen"
                                else "Restore failed: ${(status as InstallStatus.Failure).message}"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRestoring = false,
                    message = "Restore failed: ${e.message}"
                )
            }
        }
    }

    fun deleteBackup(backup: BackupInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            val deleted = AppBackupManager.deleteBackup(backup)
            if (deleted) {
                val backups = AppBackupManager.getBackups()
                _uiState.value = _uiState.value.copy(
                    backups = backups,
                    message = "Backup deleted"
                )
            } else {
                _uiState.value = _uiState.value.copy(message = "Failed to delete backup")
            }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}

package com.netswiss.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import com.netswiss.app.ui.components.AppCard
import com.netswiss.app.ui.viewmodel.BackupRestoreViewModel
import com.netswiss.app.util.AppBackupManager
import com.netswiss.app.util.AppInfo
import com.netswiss.app.util.BackupInfo
import com.netswiss.app.ui.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    paddingValues: PaddingValues,
    viewModel: BackupRestoreViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(scaffoldPadding)
                .padding(horizontal = Spacing.lg, vertical = Spacing.md)
        ) {
            // Header
            Text(
                text = "Backup & Restore",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Export and restore your apps",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(Spacing.md))

            // Tab Row
            TabRow(
                selectedTabIndex = uiState.activeTab,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = uiState.activeTab == 0,
                    onClick = { viewModel.setActiveTab(0) },
                    text = { Text("Backup") },
                    icon = { Icon(Icons.Default.Backup, contentDescription = null) }
                )
                Tab(
                    selected = uiState.activeTab == 1,
                    onClick = { viewModel.setActiveTab(1) },
                    text = { Text("Restore") },
                    icon = { Icon(Icons.Default.Restore, contentDescription = null) }
                )
            }

            Spacer(modifier = Modifier.height(Spacing.sm))

            when (uiState.activeTab) {
                0 -> BackupTab(
                    apps = uiState.installedApps,
                    selectedApps = uiState.selectedForBackup,
                    isLoading = uiState.isLoadingApps,
                    isBackingUp = uiState.isBackingUp,
                    backupProgress = uiState.backupProgress,
                    onToggleApp = { viewModel.toggleAppSelection(it) },
                    onSelectAll = { viewModel.selectAllApps() },
                    onDeselectAll = { viewModel.deselectAllApps() },
                    onBackup = { viewModel.backupSelectedApps() }
                )
                1 -> RestoreTab(
                    backups = uiState.backups,
                    isRestoring = uiState.isRestoring,
                    onRestore = { viewModel.restoreBackup(it) },
                    onDelete = { viewModel.deleteBackup(it) },
                    onRefresh = { viewModel.loadData() }
                )
            }
        }
    }
}

@Composable
private fun BackupTab(
    apps: List<AppInfo>,
    selectedApps: Set<String>,
    isLoading: Boolean,
    isBackingUp: Boolean,
    backupProgress: String?,
    onToggleApp: (String) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onBackup: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Backup progress
        AnimatedVisibility(visible = isBackingUp) {
            AppCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text(
                        text = backupProgress ?: "Backing up...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Spacer(modifier = Modifier.height(Spacing.xs))
        }

        // Select all / Deselect all
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${selectedApps.size} selected",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onSelectAll, enabled = !isBackingUp) {
                Text("All")
            }
            TextButton(onClick = onDeselectAll, enabled = !isBackingUp) {
                Text("None")
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(apps, key = { it.packageName }) { app ->
                    BackupAppItem(
                        app = app,
                        isSelected = app.packageName in selectedApps,
                        onToggle = { onToggleApp(app.packageName) },
                        enabled = !isBackingUp
                    )
                }
            }
        }

        // Backup button
        if (selectedApps.isNotEmpty() && !isBackingUp) {
            Button(
                onClick = onBackup,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Default.Backup, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Backup ${selectedApps.size} App(s)",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun BackupAppItem(
    app: AppInfo,
    isSelected: Boolean,
    onToggle: () -> Unit,
    enabled: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onToggle,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else MaterialTheme.colorScheme.surface
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs)
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() },
                enabled = enabled
            )
            Spacer(modifier = Modifier.width(Spacing.xs))

            app.icon?.let { drawable ->
                Image(
                    bitmap = drawable.toBitmap(36, 36).asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                )
            } ?: Icon(Icons.Default.Android, contentDescription = null, modifier = Modifier.size(32.dp))

            Spacer(modifier = Modifier.width(Spacing.xs))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "v${app.versionName} • ${AppBackupManager.formatFileSize(app.apkSize)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (app.isSplit) {
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Text(
                        text = "SPLIT",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RestoreTab(
    backups: List<BackupInfo>,
    isRestoring: Boolean,
    onRestore: (BackupInfo) -> Unit,
    onDelete: (BackupInfo) -> Unit,
    onRefresh: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${backups.size} backup(s) found",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        }

        AnimatedVisibility(visible = isRestoring) {
            AppCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text("Restoring...", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(modifier = Modifier.height(Spacing.xs))
        }

        if (backups.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.FolderOff,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    Text(
                        "No backups found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Backed up apps will appear here",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                contentPadding = PaddingValues(bottom = Spacing.xxxl)
            ) {
                items(backups) { backup ->
                    BackupFileItem(
                        backup = backup,
                        isRestoring = isRestoring,
                        onRestore = { onRestore(backup) },
                        onDelete = { onDelete(backup) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BackupFileItem(
    backup: BackupInfo,
    isRestoring: Boolean,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    AppCard {
        Column(modifier = Modifier.animateContentSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = if (backup.isSplit) Icons.Default.ViewInAr else Icons.Default.InsertDriveFile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(Spacing.sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = backup.appName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "v${backup.versionName} • ${AppBackupManager.formatFileSize(backup.fileSize)}" +
                                if (backup.isSplit) " • Split" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                            .format(Date(backup.backupDate)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.xs))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = { showDeleteConfirm = true },
                    enabled = !isRestoring,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete")
                }
                Spacer(modifier = Modifier.width(Spacing.xs))
                FilledTonalButton(
                    onClick = onRestore,
                    enabled = !isRestoring
                ) {
                    Icon(Icons.Default.InstallMobile, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Install")
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Backup?") },
            text = { Text("Delete backup of ${backup.appName}? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

package com.netswiss.app.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.netswiss.app.ui.components.AppCard
import com.netswiss.app.ui.viewmodel.InstallItem
import com.netswiss.app.ui.viewmodel.InstallerViewModel
import com.netswiss.app.ui.viewmodel.SelectedFile
import com.netswiss.app.util.ApkInstaller
import com.netswiss.app.util.InstallStatus
import com.netswiss.app.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallerScreen(
    paddingValues: PaddingValues,
    viewModel: InstallerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        viewModel.addFiles(uris)
    }

    // Launcher for install permission settings
    val installPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        // Check again after returning from settings
        if (ApkInstaller.canInstallPackages(context)) {
            viewModel.dismissPermissionPrompt()
            viewModel.startInstall()
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    // Permission dialog
    if (uiState.needsInstallPermission) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissPermissionPrompt() },
            icon = { Icon(Icons.Default.Security, contentDescription = null) },
            title = { Text("Permission Required") },
            text = { Text("To install APKs, you need to allow this app to install unknown apps. Tap 'Open Settings' and enable the toggle.") },
            confirmButton = {
                FilledTonalButton(
                    onClick = {
                        viewModel.dismissPermissionPrompt()
                        installPermissionLauncher.launch(
                            ApkInstaller.getInstallPermissionIntent(context)
                        )
                    }
                ) { Text("Open Settings") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissPermissionPrompt() }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = paddingValues.calculateBottomPadding())
            )
        },
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
                text = "Package Installer",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Install APK, XAPK, APKM, APKS bundles",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(Spacing.md))

            // File Picker Button
            AppCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Select Files",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = ".apk .xapk .apkm .apks supported",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilledTonalButton(
                        onClick = {
                            filePickerLauncher.launch(arrayOf(
                                "application/vnd.android.package-archive",
                                "application/octet-stream",
                                "*/*"
                            ))
                        },
                        enabled = !uiState.isInstalling
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Browse")
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.sm))

            // Selected files count & actions
            if (uiState.selectedFiles.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${uiState.selectedFiles.size} file(s) selected",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f)
                    )
                    if (!uiState.isInstalling) {
                        TextButton(onClick = { viewModel.clearQueue() }) {
                            Text("Clear")
                        }
                    }
                }
            }

            // Overall progress
            AnimatedVisibility(visible = uiState.isInstalling) {
                Column {
                    LinearProgressIndicator(
                        progress = { uiState.overallProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Spacing.xs)
                    )
                    Text(
                        text = "Installing... ${(uiState.overallProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.xs))

            // File list
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                if (uiState.installQueue.isNotEmpty()) {
                    itemsIndexed(uiState.installQueue) { index, item ->
                        InstallQueueItem(
                            item = item,
                            isCurrent = index == uiState.currentInstallIndex && uiState.isInstalling
                        )
                    }
                } else {
                    itemsIndexed(uiState.selectedFiles) { _, file ->
                        SelectedFileItem(
                            file = file,
                            onRemove = { viewModel.removeFile(file) },
                            isInstalling = uiState.isInstalling
                        )
                    }
                }
            }

            // Install button
            if (uiState.selectedFiles.isNotEmpty() && !uiState.isInstalling) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                Button(
                    onClick = { viewModel.startInstall() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Default.InstallMobile, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Install All (${uiState.selectedFiles.size})",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectedFileItem(
    file: SelectedFile,
    onRemove: () -> Unit,
    isInstalling: Boolean
) {
    AppCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = when (file.extension) {
                    "apk" -> MaterialTheme.colorScheme.primaryContainer
                    "xapk" -> MaterialTheme.colorScheme.secondaryContainer
                    "apkm" -> MaterialTheme.colorScheme.tertiaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = file.extension.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.width(Spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatSize(file.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!isInstalling) {
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun InstallQueueItem(
    item: InstallItem,
    isCurrent: Boolean
) {
    AppCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
        ) {
            Icon(
                imageVector = when (item.status) {
                    is InstallStatus.Queued -> Icons.Default.Schedule
                    is InstallStatus.Installing -> Icons.Default.Downloading
                    is InstallStatus.PendingUserAction -> Icons.Default.TouchApp
                    is InstallStatus.Success -> Icons.Default.CheckCircle
                    is InstallStatus.Failure -> Icons.Default.Error
                },
                contentDescription = null,
                tint = when (item.status) {
                    is InstallStatus.Queued -> MaterialTheme.colorScheme.onSurfaceVariant
                    is InstallStatus.Installing -> MaterialTheme.colorScheme.primary
                    is InstallStatus.PendingUserAction -> MaterialTheme.colorScheme.tertiary
                    is InstallStatus.Success -> MaterialTheme.colorScheme.primary
                    is InstallStatus.Failure -> MaterialTheme.colorScheme.error
                },
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(Spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = when (item.status) {
                        is InstallStatus.Queued -> "Queued"
                        is InstallStatus.Installing -> "Installing ${(item.status.progress * 100).toInt()}%"
                        is InstallStatus.PendingUserAction -> "Confirm install on screen"
                        is InstallStatus.Success -> "Launched installer"
                        is InstallStatus.Failure -> item.status.message
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = when (item.status) {
                        is InstallStatus.Failure -> MaterialTheme.colorScheme.error
                        is InstallStatus.Success -> MaterialTheme.colorScheme.primary
                        is InstallStatus.PendingUserAction -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                if (item.status is InstallStatus.Installing && isCurrent) {
                    LinearProgressIndicator(
                        progress = { item.status.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    }
}

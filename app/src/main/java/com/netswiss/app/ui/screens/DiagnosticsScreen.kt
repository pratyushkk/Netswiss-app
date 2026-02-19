package com.netswiss.app.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.netswiss.app.ui.components.AppCard
import com.netswiss.app.ui.components.PrimaryButton
import com.netswiss.app.ui.components.StatusBadge
import com.netswiss.app.ui.theme.Spacing
import com.netswiss.app.ui.viewmodel.DiagnosticsTask
import com.netswiss.app.ui.viewmodel.DiagnosticsViewModel
import com.netswiss.app.ui.viewmodel.TaskState
import com.netswiss.app.ui.viewmodel.TaskStatus

@Composable
fun DiagnosticsScreen(
    paddingValues: PaddingValues = PaddingValues(),
    viewModel: DiagnosticsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md)
            .padding(paddingValues)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Diagnostics",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "Ping, traceroute, DNS, public IP and port scan tools",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xxs, bottom = Spacing.sm)
        )

        AppCard {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text("Status Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text("Network: ${uiState.networkType}", style = MaterialTheme.typography.bodyMedium)
                Text("Local IP: ${uiState.localIp}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Public IP: ${uiState.tasks[DiagnosticsTask.PublicIp]?.preview ?: "--"}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.sm))

        OutlinedTextField(
            value = uiState.targetHost,
            onValueChange = viewModel::updateTargetHost,
            label = { Text("Target Host") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        OutlinedTextField(
            value = uiState.ports,
            onValueChange = viewModel::updatePorts,
            label = { Text("Ports (for scan)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        )

        Spacer(modifier = Modifier.height(Spacing.sm))

        DiagnosticsGrid(
            taskState = uiState.tasks,
            onRun = { viewModel.runTask(it) },
            onExpand = { viewModel.expand(it) },
            expandedTask = uiState.expandedTask
        )

        AnimatedVisibility(visible = uiState.expandedTask != null) {
            val task = uiState.expandedTask
            val details = uiState.tasks[task]?.details.orEmpty()
            AppCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.sm),
                contentPadding = PaddingValues(Spacing.md)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "${task?.name ?: "Result"} Output",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(details))
                                Toast.makeText(context, "Copied diagnostics logs", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy logs")
                        }
                    }
                    Text(
                        text = details,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun DiagnosticsGrid(
    taskState: Map<DiagnosticsTask, TaskState>,
    onRun: (DiagnosticsTask) -> Unit,
    onExpand: (DiagnosticsTask) -> Unit,
    expandedTask: DiagnosticsTask?
) {
    val tiles = listOf(
        Triple(DiagnosticsTask.Ping, "Ping", Icons.Default.Speed),
        Triple(DiagnosticsTask.Traceroute, "Traceroute", Icons.Default.NetworkCheck),
        Triple(DiagnosticsTask.DnsLookup, "DNS Lookup", Icons.Default.Language),
        Triple(DiagnosticsTask.PublicIp, "Public IP", Icons.Default.Info),
        Triple(DiagnosticsTask.PortScan, "Port Scan", Icons.Default.Settings)
    )

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        maxItemsInEachRow = 2,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        tiles.forEach { (task, label, icon) ->
            val state = taskState[task] ?: TaskState()
            val selected = expandedTask == task
            val statusColor = statusColor(state.status)

            AppCard(
                modifier = Modifier.fillMaxWidth(0.48f),
                onClick = {
                    onRun(task)
                    onExpand(task)
                },
                contentPadding = PaddingValues(Spacing.sm)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = icon, contentDescription = label, tint = statusColor)
                        Spacer(modifier = Modifier.weight(1f))
                        if (selected) {
                            StatusBadge(text = "Active", color = statusColor)
                        }
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = state.preview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                    PrimaryButton(
                        text = if (state.status == TaskStatus.Loading) "Running..." else "Run",
                        onClick = { onRun(task); onExpand(task) },
                        enabled = state.status != TaskStatus.Loading
                    )
                }
            }
        }
    }
}

@Composable
private fun statusColor(status: TaskStatus): Color {
    return when (status) {
        TaskStatus.Idle -> MaterialTheme.colorScheme.onSurfaceVariant
        TaskStatus.Loading -> MaterialTheme.colorScheme.primary
        TaskStatus.Success -> Color(0xFF2E7D32)
        TaskStatus.Error -> MaterialTheme.colorScheme.error
    }
}

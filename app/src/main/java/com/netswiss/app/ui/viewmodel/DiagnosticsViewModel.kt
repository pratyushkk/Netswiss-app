package com.netswiss.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.netswiss.app.util.NetworkDiagnostics
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DiagnosticsTask {
    Ping,
    Traceroute,
    DnsLookup,
    PublicIp,
    PortScan
}

enum class TaskStatus {
    Idle,
    Loading,
    Success,
    Error
}

data class TaskState(
    val status: TaskStatus = TaskStatus.Idle,
    val preview: String = "Tap to run",
    val details: String = "No data yet.",
    val updatedAt: Long = 0L
)

data class DiagnosticsUiState(
    val targetHost: String = "google.com",
    val ports: String = "53,80,443,8080",
    val localIp: String = "--",
    val networkType: String = "--",
    val tasks: Map<DiagnosticsTask, TaskState> = DiagnosticsTask.entries.associateWith { TaskState() },
    val expandedTask: DiagnosticsTask? = null
)

class DiagnosticsViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(DiagnosticsUiState())
    val uiState: StateFlow<DiagnosticsUiState> = _uiState.asStateFlow()

    private val jobs = mutableMapOf<DiagnosticsTask, Job>()

    init {
        refreshSummary()
    }

    fun refreshSummary() {
        val summary = NetworkDiagnostics.getNetworkSummary(getApplication())
        _uiState.update {
            it.copy(
                localIp = summary.localIp,
                networkType = summary.networkType
            )
        }
    }

    fun updateTargetHost(value: String) {
        _uiState.update { it.copy(targetHost = value) }
    }

    fun updatePorts(value: String) {
        _uiState.update { it.copy(ports = value) }
    }

    fun expand(task: DiagnosticsTask?) {
        _uiState.update { it.copy(expandedTask = task) }
    }

    fun runTask(task: DiagnosticsTask) {
        jobs[task]?.cancel()
        jobs[task] = viewModelScope.launch {
            setLoading(task)
            val host = _uiState.value.targetHost
            val ports = _uiState.value.ports
            val output = when (task) {
                DiagnosticsTask.Ping -> NetworkDiagnostics.ping(host)
                DiagnosticsTask.Traceroute -> NetworkDiagnostics.traceroute(host)
                DiagnosticsTask.DnsLookup -> NetworkDiagnostics.dnsLookup(host)
                DiagnosticsTask.PublicIp -> NetworkDiagnostics.publicIp()
                DiagnosticsTask.PortScan -> NetworkDiagnostics.portScan(host, ports)
            }
            val isError = output.startsWith("Ping error", true) ||
                output.startsWith("Traceroute error", true) ||
                output.startsWith("DNS lookup error", true) ||
                output.startsWith("Public IP error", true) ||
                output.startsWith("Port scan failed", true) ||
                output.startsWith("No valid ports", true)

            setResult(task, output, if (isError) TaskStatus.Error else TaskStatus.Success)
            if (task == DiagnosticsTask.PublicIp && !isError) {
                refreshSummary()
            }
        }
    }

    private fun setLoading(task: DiagnosticsTask) {
        _uiState.update { current ->
            val updated = current.tasks.toMutableMap()
            updated[task] = TaskState(
                status = TaskStatus.Loading,
                preview = "Running...",
                details = "Running $task...",
                updatedAt = System.currentTimeMillis()
            )
            current.copy(tasks = updated, expandedTask = task)
        }
    }

    private fun setResult(task: DiagnosticsTask, output: String, status: TaskStatus) {
        _uiState.update { current ->
            val previewLine = output.lineSequence().firstOrNull()?.take(64).orEmpty().ifBlank { "Completed" }
            val updated = current.tasks.toMutableMap()
            updated[task] = TaskState(
                status = status,
                preview = previewLine,
                details = output,
                updatedAt = System.currentTimeMillis()
            )
            current.copy(tasks = updated, expandedTask = task)
        }
    }
}

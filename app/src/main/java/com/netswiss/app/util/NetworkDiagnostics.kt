package com.netswiss.app.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.URL
import java.util.Collections

data class NetworkSummary(
    val localIp: String,
    val networkType: String
)

object NetworkDiagnostics {

    fun getNetworkSummary(context: Context): NetworkSummary {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(network)
        val networkType = when {
            caps == null -> "Disconnected"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "Unknown"
        }
        return NetworkSummary(localIp = getLocalIpv4Address(), networkType = networkType)
    }

    suspend fun ping(host: String, timeoutMs: Int = 2000): String = withContext(Dispatchers.IO) {
        val target = host.trim()
        if (target.isBlank()) return@withContext "Ping failed: host is empty"
        return@withContext try {
            val start = System.currentTimeMillis()
            val reachable = InetAddress.getByName(target).isReachable(timeoutMs)
            val elapsed = System.currentTimeMillis() - start
            if (reachable) "Reachable in ${elapsed}ms" else "Not reachable within ${timeoutMs}ms"
        } catch (e: Exception) {
            "Ping error: ${e.message ?: "unknown"}"
        }
    }

    suspend fun dnsLookup(host: String): String = withContext(Dispatchers.IO) {
        val target = host.trim()
        if (target.isBlank()) return@withContext "DNS lookup failed: host is empty"
        return@withContext try {
            val addresses = InetAddress.getAllByName(target)
            if (addresses.isEmpty()) {
                "No DNS records found"
            } else {
                buildString {
                    appendLine("Resolved ${addresses.size} record(s):")
                    addresses.forEachIndexed { index, inetAddress ->
                        appendLine("${index + 1}. ${inetAddress.hostAddress}")
                    }
                }.trim()
            }
        } catch (e: Exception) {
            "DNS lookup error: ${e.message ?: "unknown"}"
        }
    }

    suspend fun publicIp(timeoutMs: Int = 5000): String = withContext(Dispatchers.IO) {
        return@withContext try {
            val connection = (URL("https://api.ipify.org").openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                requestMethod = "GET"
            }
            val ip = connection.inputStream.bufferedReader().use { it.readText() }.trim()
            connection.disconnect()
            if (ip.isBlank()) "Unable to fetch public IP" else "Public IP: $ip"
        } catch (e: Exception) {
            "Public IP error: ${e.message ?: "unknown"}"
        }
    }

    suspend fun traceroute(host: String, maxHops: Int = 15): String = withContext(Dispatchers.IO) {
        val target = host.trim()
        if (target.isBlank()) return@withContext "Traceroute failed: host is empty"

        return@withContext try {
            val lines = mutableListOf<String>()
            val destIp = try {
                InetAddress.getByName(target).hostAddress
            } catch (_: Exception) {
                null
            }
            for (ttl in 1..maxHops) {
                val hop = runPingWithTtl(target, ttl)
                lines.add(String.format("%2d  %s", ttl, hop))
                if (destIp != null && hop.contains(destIp)) break
            }
            if (lines.isEmpty()) {
                "Traceroute returned no hops"
            } else {
                buildString {
                    appendLine("Traceroute to $target")
                    lines.forEach { appendLine(it) }
                }.trim()
            }
        } catch (e: Exception) {
            "Traceroute error: ${e.message ?: "unknown"}"
        }
    }

    suspend fun portScan(host: String, portInput: String): String = withContext(Dispatchers.IO) {
        val target = host.trim()
        if (target.isBlank()) return@withContext "Port scan failed: host is empty"

        val ports = parsePorts(portInput)
        if (ports.isEmpty()) return@withContext "No valid ports. Example: 53,80,443,8080"

        val openPorts = mutableListOf<Int>()
        val closedPorts = mutableListOf<Int>()

        ports.forEach { port ->
            val isOpen = try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(target, port), 350)
                    true
                }
            } catch (_: Exception) {
                false
            }
            if (isOpen) openPorts.add(port) else closedPorts.add(port)
        }

        buildString {
            appendLine("Scanned ${ports.size} port(s) on $target")
            appendLine("Open: ${if (openPorts.isEmpty()) "none" else openPorts.joinToString(", ")}")
            appendLine("Closed/Filtered: ${if (closedPorts.isEmpty()) "none" else closedPorts.joinToString(", ")}")
        }.trim()
    }

    private fun getLocalIpv4Address(): String {
        return try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            interfaces.forEach { networkInterface ->
                val addresses = Collections.list(networkInterface.inetAddresses)
                addresses.forEach { address ->
                    if (!address.isLoopbackAddress && address.hostAddress?.contains(':') == false) {
                        return address.hostAddress ?: "--"
                    }
                }
            }
            "--"
        } catch (_: Exception) {
            "--"
        }
    }

    private fun runPingWithTtl(host: String, ttl: Int): String {
        val process = ProcessBuilder("ping", "-c", "1", "-W", "1", "-t", ttl.toString(), host)
            .redirectErrorStream(true)
            .start()
        val output = BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
            reader.readLines().joinToString("\n")
        }
        process.waitFor()

        val lower = output.lowercase()
        val fromKeyword = "from "
        val fromIdx = lower.indexOf(fromKeyword)
        if (fromIdx >= 0) {
            val end = output.indexOf(' ', fromIdx + fromKeyword.length)
            val value = if (end > fromIdx) output.substring(fromIdx + fromKeyword.length, end) else output.substring(fromIdx + fromKeyword.length)
            return value.trim().trim(':')
        }
        if (lower.contains("time to live exceeded")) return "TTL exceeded"
        if (lower.contains("100% packet loss")) return "timeout"
        return "unresolved hop"
    }

    private fun parsePorts(portInput: String): List<Int> {
        return portInput
            .split(",", " ", ";", "\n", "\t")
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 1..65535 }
            .distinct()
            .take(64)
    }
}

package com.hightemp.proxy_switcher.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.hightemp.proxy_switcher.proxy.ProxyStats
import com.hightemp.proxy_switcher.proxy.ProxyStatsSnapshot
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(navController: NavController) {
    val stats by ProxyStats.snapshot.collectAsState()
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(stats.isRunning, stats.startedAtMillis) {
        nowMillis = System.currentTimeMillis()
        while (stats.isRunning) {
            delay(1000)
            nowMillis = System.currentTimeMillis()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statistics") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SessionSummaryCard(stats = stats, nowMillis = nowMillis)
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        title = "Downloaded",
                        value = formatBytes(stats.downloadedBytes),
                        subtitle = "from network",
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "Uploaded",
                        value = formatBytes(stats.uploadedBytes),
                        subtitle = "to network",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        title = "Average down",
                        value = "${formatBytes(averageBytesPerSecond(stats.downloadedBytes, stats, nowMillis))}/s",
                        subtitle = "session rate",
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "Average up",
                        value = "${formatBytes(averageBytesPerSecond(stats.uploadedBytes, stats, nowMillis))}/s",
                        subtitle = "session rate",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        title = "Active",
                        value = stats.activeConnections.toString(),
                        subtitle = "open tunnels",
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "Peak",
                        value = stats.peakActiveConnections.toString(),
                        subtitle = "max active",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        title = "Total tunnels",
                        value = stats.totalConnections.toString(),
                        subtitle = "opened",
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "Failures",
                        value = stats.failedConnections.toString(),
                        subtitle = "rejected or failed",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionSummaryCard(stats: ProxyStatsSnapshot, nowMillis: Long) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (stats.isRunning) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (stats.isRunning) "RUNNING" else "STOPPED",
                style = MaterialTheme.typography.labelLarge,
                color = if (stats.isRunning) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Text(
                text = formatBytes(stats.totalBytes),
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "Total traffic, ${formatDuration(sessionDurationMillis(stats, nowMillis))}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun sessionDurationMillis(stats: ProxyStatsSnapshot, nowMillis: Long): Long {
    if (stats.startedAtMillis <= 0L) return 0L
    val endMillis = if (stats.isRunning) nowMillis else stats.stoppedAtMillis
    return max(0L, endMillis - stats.startedAtMillis)
}

private fun averageBytesPerSecond(bytes: Long, stats: ProxyStatsSnapshot, nowMillis: Long): Long {
    val seconds = max(1L, sessionDurationMillis(stats, nowMillis) / 1000L)
    return bytes / seconds
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"

    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024.0
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    return String.format(Locale.ROOT, "%.2f %s", value, units[unitIndex])
}

private fun formatDuration(durationMillis: Long): String {
    val totalSeconds = durationMillis / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L

    return if (hours > 0L) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
    }
}

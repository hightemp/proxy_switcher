package com.hightemp.proxy_switcher.ui.screens

import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemProxyScreen(navController: NavController) {
    val context = LocalContext.current

    val hasPermission = remember {
        context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
                PackageManager.PERMISSION_GRANTED
    }

    fun readCurrentProxy(): String {
        val globalHost = Settings.Global.getString(context.contentResolver, "global_http_proxy_host") ?: ""
        val globalPort = Settings.Global.getString(context.contentResolver, "global_http_proxy_port") ?: ""
        if (globalHost.isNotEmpty()) return "$globalHost:$globalPort"
        return Settings.Global.getString(context.contentResolver, Settings.Global.HTTP_PROXY) ?: ""
    }

    // Read the ACTUAL proxy applied to the active network (what Chrome uses)
    fun readLinkProxy(): String {
        return try {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            val network = cm.activeNetwork ?: return ""
            val lp = cm.getLinkProperties(network) ?: return ""
            val proxy = lp.httpProxy ?: return ""
            val h = proxy.host ?: ""
            val p = proxy.port
            if (h.isEmpty()) "" else "$h:$p"
        } catch (e: Exception) { "" }
    }

    var currentValue by remember { mutableStateOf(readCurrentProxy()) }
    var linkProxyValue by remember { mutableStateOf(readLinkProxy()) }
    var editValue by remember { mutableStateOf(currentValue) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            snackbarMessage = null
        }
    }

    fun refresh() {
        currentValue = readCurrentProxy()
        linkProxyValue = readLinkProxy()
        editValue = currentValue
    }

    fun apply() {
        try {
            val value = editValue.trim()
            val host = value.substringBefore(":")
            val port = value.substringAfter(":", "")
            // Set both key families
            Settings.Global.putString(context.contentResolver, Settings.Global.HTTP_PROXY, value)
            Settings.Global.putString(context.contentResolver, "global_http_proxy_host", host)
            Settings.Global.putString(context.contentResolver, "global_http_proxy_port", port)
            Settings.Global.putString(context.contentResolver, "global_http_proxy_exclusion_list", "")
            currentValue = value
            snackbarMessage = "Proxy set to: $value"
        } catch (e: Exception) {
            snackbarMessage = "Error: ${e.message}"
        }
    }

    fun clear() {
        try {
            // Delete all proxy key families via ContentResolver (putString("") is unreliable on Android 13+)
            context.contentResolver.delete(Settings.Global.getUriFor(Settings.Global.HTTP_PROXY), null, null)
            context.contentResolver.delete(Settings.Global.getUriFor("global_http_proxy_host"), null, null)
            context.contentResolver.delete(Settings.Global.getUriFor("global_http_proxy_port"), null, null)
            context.contentResolver.delete(Settings.Global.getUriFor("global_http_proxy_exclusion_list"), null, null)
            context.contentResolver.delete(Settings.Global.getUriFor("global_proxy_pac_url"), null, null)
            currentValue = ""
            editValue = ""
            snackbarMessage = "Proxy cleared"
        } catch (e: Exception) {
            snackbarMessage = "Error: ${e.message}"
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("System Proxy") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // Permission banner
            if (!hasPermission) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 2.dp, end = 8.dp).size(18.dp)
                        )
                        Column {
                            Text(
                                "WRITE_SECURE_SETTINGS not granted — read-only mode",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS",
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // Current value card (Settings.Global)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Settings.Global proxy", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = currentValue.ifEmpty { "(not set)" },
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (currentValue.isEmpty())
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else
                            MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Active network proxy (what Chrome actually reads via LinkProperties)
            val linkProxyIsStale = linkProxyValue.isNotEmpty() && currentValue.isEmpty()
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = if (linkProxyIsStale)
                    CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                else
                    CardDefaults.cardColors()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Active network proxy (LinkProperties)", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = linkProxyValue.ifEmpty { "(none)" },
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (linkProxyValue.isEmpty())
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else if (linkProxyIsStale)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.primary
                    )
                    if (linkProxyIsStale) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Per-network WiFi proxy is set — Chrome will fail even with no global proxy.\n" +
                            "Fix: WiFi Settings → long-press network → Modify → Proxy → None",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                })
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Open WiFi Settings")
                        }
                    }
                }
            }

            // Edit field
            OutlinedTextField(
                value = editValue,
                onValueChange = { editValue = it },
                label = { Text("Proxy value") },
                placeholder = { Text("host:port  e.g. 127.0.0.1:8080") },
                singleLine = true,
                enabled = hasPermission,
                modifier = Modifier.fillMaxWidth()
            )

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { apply() },
                    enabled = hasPermission && editValue.trim().isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Apply")
                }
                OutlinedButton(
                    onClick = { clear() },
                    enabled = hasPermission,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Clear")
                }
            }

            HorizontalDivider()

            // Quick presets
            Text("Quick presets", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SuggestionChip(
                    onClick = { editValue = "127.0.0.1:8080" },
                    label = { Text("127.0.0.1:8080") }
                )
                SuggestionChip(
                    onClick = { editValue = "10.0.2.2:8080" },
                    label = { Text("10.0.2.2:8080") }
                )
            }
        }
    }
}

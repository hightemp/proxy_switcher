package com.hightemp.proxy_switcher.ui.screens

import android.content.pm.PackageManager
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

    fun readCurrentProxy(): String =
        Settings.Global.getString(context.contentResolver, Settings.Global.HTTP_PROXY) ?: ""

    var currentValue by remember { mutableStateOf(readCurrentProxy()) }
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
        editValue = currentValue
    }

    fun apply() {
        try {
            Settings.Global.putString(context.contentResolver, Settings.Global.HTTP_PROXY, editValue.trim())
            currentValue = editValue.trim()
            snackbarMessage = "Proxy set to: ${editValue.trim().ifEmpty { "(empty)" }}"
        } catch (e: Exception) {
            snackbarMessage = "Error: ${e.message}"
        }
    }

    fun clear() {
        try {
            Settings.Global.putString(context.contentResolver, Settings.Global.HTTP_PROXY, "")
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

            // Current value card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Current value", style = MaterialTheme.typography.labelMedium)
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

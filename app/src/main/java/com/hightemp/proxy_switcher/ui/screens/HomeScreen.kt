package com.hightemp.proxy_switcher.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.hightemp.proxy_switcher.data.local.ProxyEntity
import com.hightemp.proxy_switcher.service.ProxyService
import com.hightemp.proxy_switcher.ui.viewmodel.ProxyViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: ProxyViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val proxyList by viewModel.proxyList.collectAsState()
    val isRunning by viewModel.isProxyRunning.collectAsState()
    val selectedProxy by viewModel.selectedProxy.collectAsState()
    var expanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Proxy Switcher") },
                actions = {
                    IconButton(onClick = { navController.navigate("logs") }) {
                        Icon(Icons.Default.Info, contentDescription = "Logs")
                    }
                    IconButton(onClick = { navController.navigate("proxy_list") }) {
                        Icon(Icons.Default.Settings, contentDescription = "Manage Proxies")
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Status Indicator
            Text(
                text = if (isRunning) "RUNNING" else "STOPPED",
                style = MaterialTheme.typography.displayMedium,
                color = if (isRunning) Color.Green else Color.Red
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(text = "Local Proxy: 127.0.0.1:8080")

            Spacer(modifier = Modifier.height(32.dp))

            // Proxy Selector
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = selectedProxy?.let { "${it.label ?: it.host} (${it.type})" } ?: "Direct Connection",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Direct Connection") },
                        onClick = {
                            viewModel.setSelectedProxy(null)
                            expanded = false
                        }
                    )
                    proxyList.forEach { proxy ->
                        DropdownMenuItem(
                            text = { Text("${proxy.label ?: proxy.host} (${proxy.type})") },
                            onClick = {
                                viewModel.setSelectedProxy(proxy)
                                expanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    if (isRunning) {
                        val intent = Intent(context, ProxyService::class.java).apply {
                            action = ProxyService.ACTION_STOP
                        }
                        context.startService(intent)
                        viewModel.setProxyRunning(false)
                    } else {
                        val intent = Intent(context, ProxyService::class.java).apply {
                            action = ProxyService.ACTION_START
                            if (selectedProxy != null) {
                                putExtra(ProxyService.EXTRA_PROXY_ID, selectedProxy!!.id)
                            }
                        }
                        context.startForegroundService(intent)
                        viewModel.setProxyRunning(true)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            ) {
                Text(if (isRunning) "STOP PROXY" else "START PROXY")
            }
        }
    }
}
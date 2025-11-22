package com.hightemp.proxy_switcher.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.hightemp.proxy_switcher.data.local.ProxyEntity
import com.hightemp.proxy_switcher.data.local.ProxyType
import com.hightemp.proxy_switcher.ui.viewmodel.ProxyViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditProxyScreen(
    navController: NavController,
    proxyId: Long? = null,
    viewModel: ProxyViewModel = hiltViewModel()
) {
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(ProxyType.HTTP) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()

    LaunchedEffect(proxyId) {
        if (proxyId != null && proxyId != -1L) {
            val proxy = viewModel.getProxyById(proxyId)
            proxy?.let {
                host = it.host
                port = it.port.toString()
                type = it.type
                username = it.username ?: ""
                password = it.password ?: ""
                label = it.label ?: ""
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (proxyId == null || proxyId == -1L) "Add Proxy" else "Edit Proxy") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Label (Optional)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("Host") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = port,
                onValueChange = { port = it },
                label = { Text("Port") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            
            // Proxy Type Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ProxyType.values().forEach { proxyType ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(
                            selected = type == proxyType,
                            onClick = { type = proxyType }
                        )
                        Text(text = proxyType.name)
                    }
                }
            }

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username (Optional)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password (Optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    if (host.isNotBlank() && port.isNotBlank()) {
                        val newProxy = ProxyEntity(
                            id = if (proxyId != null && proxyId != -1L) proxyId else 0,
                            host = host,
                            port = port.toIntOrNull() ?: 8080,
                            type = type,
                            username = username.ifBlank { null },
                            password = password.ifBlank { null },
                            label = label.ifBlank { null }
                        )
                        if (proxyId != null && proxyId != -1L) {
                            viewModel.updateProxy(newProxy)
                        } else {
                            viewModel.addProxy(newProxy)
                        }
                        navController.popBackStack()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save")
            }
        }
    }
}
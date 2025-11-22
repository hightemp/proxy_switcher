package com.hightemp.proxy_switcher.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.hightemp.proxy_switcher.data.local.ProxyEntity
import com.hightemp.proxy_switcher.ui.viewmodel.ProxyViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxyListScreen(
    navController: NavController,
    viewModel: ProxyViewModel = hiltViewModel()
) {
    val proxyList by viewModel.proxyList.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Proxy List") }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate("add_proxy") }) {
                Icon(Icons.Default.Add, contentDescription = "Add Proxy")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            items(proxyList) { proxy ->
                ProxyItem(
                    proxy = proxy,
                    onDelete = { viewModel.deleteProxy(proxy) },
                    onClick = { navController.navigate("edit_proxy/${proxy.id}") }
                )
            }
        }
    }
}

@Composable
fun ProxyItem(
    proxy: ProxyEntity,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = proxy.label ?: "${proxy.host}:${proxy.port}",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${proxy.type} - ${proxy.host}:${proxy.port}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        }
    }
}
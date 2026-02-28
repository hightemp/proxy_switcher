package com.hightemp.proxy_switcher.ui.viewmodel

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hightemp.proxy_switcher.data.local.ProxyEntity
import com.hightemp.proxy_switcher.data.repository.ProxyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProxyViewModel @Inject constructor(
    private val repository: ProxyRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _hasSystemProxyPermission = MutableStateFlow(
        context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
    )
    val hasSystemProxyPermission: StateFlow<Boolean> = _hasSystemProxyPermission.asStateFlow()

    fun refreshPermissionState() {
        _hasSystemProxyPermission.value =
            context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
    }

    private val _isProxyRunning = MutableStateFlow(false)
    val isProxyRunning: StateFlow<Boolean> = _isProxyRunning.asStateFlow()

    fun setProxyRunning(running: Boolean) {
        _isProxyRunning.value = running
    }

    private val _selectedProxy = MutableStateFlow<ProxyEntity?>(null)
    val selectedProxy: StateFlow<ProxyEntity?> = _selectedProxy.asStateFlow()

    fun setSelectedProxy(proxy: ProxyEntity?) {
        _selectedProxy.value = proxy
    }

    val proxyList: StateFlow<List<ProxyEntity>> = repository.getAllProxies()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addProxy(proxy: ProxyEntity) {
        viewModelScope.launch {
            repository.insertProxy(proxy)
        }
    }

    fun updateProxy(proxy: ProxyEntity) {
        viewModelScope.launch {
            repository.updateProxy(proxy)
        }
    }

    fun deleteProxy(proxy: ProxyEntity) {
        viewModelScope.launch {
            repository.deleteProxy(proxy)
        }
    }
    
    suspend fun getProxyById(id: Long): ProxyEntity? {
        return repository.getProxyById(id)
    }
}
package com.hightemp.proxy_switcher.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hightemp.proxy_switcher.data.local.ProxyEntity
import com.hightemp.proxy_switcher.data.repository.ProxyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProxyViewModel @Inject constructor(
    private val repository: ProxyRepository
) : ViewModel() {

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
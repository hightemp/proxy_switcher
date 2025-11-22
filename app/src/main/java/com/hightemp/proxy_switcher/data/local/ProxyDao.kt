package com.hightemp.proxy_switcher.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProxyDao {
    @Query("SELECT * FROM proxies")
    fun getAllProxies(): Flow<List<ProxyEntity>>

    @Query("SELECT * FROM proxies WHERE id = :id")
    suspend fun getProxyById(id: Long): ProxyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProxy(proxy: ProxyEntity)

    @Update
    suspend fun updateProxy(proxy: ProxyEntity)

    @Delete
    suspend fun deleteProxy(proxy: ProxyEntity)
}
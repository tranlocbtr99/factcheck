package com.example.data.repository

import android.content.Context
import com.example.data.local.FactCheckDao
import com.example.data.local.FactCheckDatabase
import com.example.data.local.FactCheckEntity
import com.example.engine.FactCheckEngine
import com.example.model.FactCheckRecord
import com.example.model.FactCheckVerdict
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FactCheckRepository(
    private val factCheckDao: FactCheckDao,
    val engine: FactCheckEngine,
    private val appContext: Context? = null,
) {
    private val historyLock = Mutex()

    val allRecords: Flow<List<FactCheckRecord>> =
        factCheckDao.getAllRecords().map { entities -> entities.map { it.toDomain() } }

    val totalCount: Flow<Int> = factCheckDao.getTotalCount()
    val alertCount: Flow<Int> = factCheckDao.getAlertCount()

    fun getRecordById(id: String): Flow<FactCheckRecord?> {
        return factCheckDao.getRecordById(id).map { it?.toDomain() }
    }

    fun searchRecords(query: String): Flow<List<FactCheckRecord>> {
        return factCheckDao.searchRecords(query).map { list -> list.map { it.toDomain() } }
    }

    fun getRecordsByVerdict(verdict: FactCheckVerdict): Flow<List<FactCheckRecord>> {
        return factCheckDao.getRecordsByVerdict(verdict.name).map { list ->
            list.map { it.toDomain() }
        }
    }

    suspend fun saveRecord(record: FactCheckRecord) {
        factCheckDao.insertRecord(FactCheckEntity.fromDomain(record))
    }

    suspend fun deleteById(id: String) {
        factCheckDao.deleteById(id)
    }

    suspend fun clearAll() {
        historyLock.withLock {
            appContext
                ?.getSharedPreferences("history_upgrade", Context.MODE_PRIVATE)
                ?.edit()
                ?.putBoolean("imported_v2", true)
                ?.commit()
            factCheckDao.deleteAll()
        }
    }

    companion object {
        @Volatile private var instance: FactCheckRepository? = null

        fun getInstance(context: Context): FactCheckRepository {
            return instance
                ?: synchronized(this) {
                    val db = FactCheckDatabase.getInstance(context)
                    val app = context.applicationContext as com.example.FactCheckApplication
                    val repo =
                        FactCheckRepository(
                            db.factCheckDao(),
                            FactCheckEngine(app.settings),
                            context.applicationContext,
                        )
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            repo.historyLock.withLock {
                                com.example.data.local.LegacyHistoryImporter.import(
                                    context.applicationContext,
                                    db.factCheckDao(),
                                )
                            }
                        } catch (e: Exception) {
                            com.example.engine.FactCheckServiceManager.reportError(
                                e.message
                                    ?: "Không chuyển được lịch sử cũ; dữ liệu gốc được giữ nguyên."
                            )
                        }
                    }
                    instance = repo
                    repo
                }
        }
    }
}

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
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class FactCheckRepository(
    private val factCheckDao: FactCheckDao,
    val engine: FactCheckEngine = FactCheckEngine()
) {
    val allRecords: Flow<List<FactCheckRecord>> = factCheckDao.getAllRecords().map { entities ->
        entities.map { it.toDomain() }
    }

    val totalCount: Flow<Int> = factCheckDao.getTotalCount()
    val alertCount: Flow<Int> = factCheckDao.getAlertCount()

    fun getRecordById(id: String): Flow<FactCheckRecord?> {
        return factCheckDao.getRecordById(id).map { it?.toDomain() }
    }

    fun searchRecords(query: String): Flow<List<FactCheckRecord>> {
        return factCheckDao.searchRecords(query).map { list -> list.map { it.toDomain() } }
    }

    fun getRecordsByVerdict(verdict: FactCheckVerdict): Flow<List<FactCheckRecord>> {
        return factCheckDao.getRecordsByVerdict(verdict.name).map { list -> list.map { it.toDomain() } }
    }

    suspend fun saveRecord(record: FactCheckRecord) {
        factCheckDao.insertRecord(FactCheckEntity.fromDomain(record))
    }

    suspend fun deleteById(id: String) {
        factCheckDao.deleteById(id)
    }

    suspend fun clearAll() {
        factCheckDao.deleteAll()
    }

    /**
     * Khởi tạo dữ liệu mẫu phong phú khi lần đầu chạy ứng dụng
     */
    suspend fun seedInitialDataIfEmpty() {
        val count = totalCount.firstOrNull() ?: 0
        if (count == 0) {
            val sampleClaims = listOf(
                "Uống nước chanh nóng kiềm hóa chữa khỏi ung thư hoàn toàn",
                "Mạng di động 5G làm lây lan virus và suy giảm miễn dịch",
                "Ngân hàng Nhà nước sắp đổi tiền polymer sang mẫu tiền mới",
                "Trái Đất có hình phẳng và vệ tinh vũ trụ chỉ là CGI",
                "Dự án đầu tư tiền số cam kết lãi 30% mỗi ngày không rủi ro",
                "Uống nước lá đu đủ đực kết hợp xả là thần dược trị bách bệnh"
            )
            for (claim in sampleClaims) {
                val record = engine.verifyClaim(claim, "Lịch sử kiểm chứng mẫu")
                saveRecord(record)
            }
        }
    }

    companion object {
        @Volatile
        private var instance: FactCheckRepository? = null

        fun getInstance(context: Context): FactCheckRepository {
            return instance ?: synchronized(this) {
                val db = FactCheckDatabase.getInstance(context)
                val repo = FactCheckRepository(db.factCheckDao())
                CoroutineScope(Dispatchers.IO).launch {
                    repo.seedInitialDataIfEmpty()
                }
                instance = repo
                repo
            }
        }
    }
}

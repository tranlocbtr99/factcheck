package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FactCheckDao {

    @Query("SELECT * FROM fact_check_records ORDER BY checkedAt DESC")
    fun getAllRecords(): Flow<List<FactCheckEntity>>

    @Query("SELECT * FROM fact_check_records WHERE id = :id LIMIT 1")
    fun getRecordById(id: String): Flow<FactCheckEntity?>

    @Query("SELECT * FROM fact_check_records WHERE verdict = :verdict ORDER BY checkedAt DESC")
    fun getRecordsByVerdict(verdict: String): Flow<List<FactCheckEntity>>

    @Query("SELECT * FROM fact_check_records WHERE originalClaim LIKE '%' || :query || '%' OR normalizedClaim LIKE '%' || :query || '%' OR shortExplanation LIKE '%' || :query || '%' ORDER BY checkedAt DESC")
    fun searchRecords(query: String): Flow<List<FactCheckEntity>>

    @Query("SELECT COUNT(*) FROM fact_check_records")
    fun getTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM fact_check_records WHERE isAlerted = 1")
    fun getAlertCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(entity: FactCheckEntity)

    @Query("DELETE FROM fact_check_records WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM fact_check_records")
    suspend fun deleteAll()
}

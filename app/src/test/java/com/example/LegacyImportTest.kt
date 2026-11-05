package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.FactCheckDatabase
import com.example.data.local.LegacyHistoryImporter
import com.example.model.FactCheckVerdict
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LegacyImportTest {
    @Test
    fun originalHistorySurvivesAndImportIsIdempotent() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("history_upgrade", 0).edit().clear().commit()
        val file = context.getDatabasePath("reports.db")
        file.parentFile!!.mkdirs()
        val json =
            JSONObject()
                .put("id", "legacy-1")
                .put("claim", "Câu gốc không bị sửa")
                .put("verdict", "SUPPORTED")
                .put("answer", "Lời giải cũ")
                .put("checkedAt", 1234L)
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS reports (data TEXT,checked_at INTEGER)")
            db.delete("reports", null, null)
            db.execSQL("INSERT INTO reports VALUES (?,?)", arrayOf(json.toString(), 1234L))
        }
        val db =
            Room.inMemoryDatabaseBuilder(context, FactCheckDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        try {
            LegacyHistoryImporter.import(context, db.factCheckDao())
            LegacyHistoryImporter.import(context, db.factCheckDao())
            val rows = db.factCheckDao().getAllRecords().first()
            assertEquals(1, rows.size)
            assertEquals("Câu gốc không bị sửa", rows.first().originalClaim)
            assertEquals(FactCheckVerdict.DUNG.name, rows.first().verdict)
            assertTrue(file.exists())
        } finally {
            db.close()
            context.deleteDatabase("reports.db")
        }
    }
}

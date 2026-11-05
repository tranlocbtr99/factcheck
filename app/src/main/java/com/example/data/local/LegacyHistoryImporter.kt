package com.example.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.example.model.FactCheckRecord
import com.example.model.FactCheckVerdict
import com.example.model.ReferenceSource
import org.json.JSONObject

/**
 * Read-only import of 1.x/2.x reports. Original database remains untouched. IDs make retries
 * idempotent.
 */
object LegacyHistoryImporter {
    suspend fun import(context: Context, dao: FactCheckDao) {
        val prefs = context.getSharedPreferences("history_upgrade", Context.MODE_PRIVATE)
        if (prefs.getBoolean("imported_v2", false)) return
        val file = context.getDatabasePath("reports.db")
        if (!file.exists()) {
            prefs.edit().putBoolean("imported_v2", true).apply()
            return
        }
        var failures = 0
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT data FROM reports ORDER BY checked_at", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val record =
                        try {
                            parse(JSONObject(cursor.getString(0)))
                        } catch (_: Exception) {
                            failures++
                            null
                        }
                    if (record != null) dao.insertRecord(FactCheckEntity.fromDomain(record))
                }
            }
        }
        if (failures == 0) prefs.edit().putBoolean("imported_v2", true).apply()
        else
            throw IllegalStateException(
                "Có $failures hồ sơ cũ chưa chuyển được. Dữ liệu gốc vẫn được giữ nguyên."
            )
    }

    fun parse(o: JSONObject): FactCheckRecord {
        val refs = mutableListOf<ReferenceSource>()
        val sources = o.optJSONArray("sources")
        if (sources != null)
            for (i in 0 until sources.length()) {
                val s = sources.getJSONObject(i)
                val url = s.optString("url")
                if (url.startsWith("https://"))
                    refs.add(
                        ReferenceSource(
                            s.optString("title"),
                            url,
                            "Nguồn lưu từ phiên bản cũ",
                            "",
                            "Mở bài gốc để đối chiếu.",
                            false,
                        )
                    )
            }
        val verdict =
            when (o.optString("verdict")) {
                "SUPPORTED" -> FactCheckVerdict.DUNG
                "REFUTED" -> FactCheckVerdict.SAI
                "MIXED" -> FactCheckVerdict.DUNG_MOT_PHAN
                "UNVERIFIABLE" -> FactCheckVerdict.KHONG_THE_KIEM_CHUNG
                else -> FactCheckVerdict.CHUA_DU_BANG_CHUNG
            }
        return FactCheckRecord(
            id = o.getString("id"),
            originalClaim = o.getString("claim"),
            normalizedClaim = o.optString("checkedClaim").ifBlank { o.getString("claim") },
            verdict = verdict,
            shortExplanation = o.optString("answer").take(240),
            detailedExplanation = o.optString("answer"),
            evidence = o.optString("reference"),
            counterEvidence = o.optString("correction"),
            references = refs,
            checkedAt = o.getLong("checkedAt"),
            sourceApp = "Bản lưu 2.x",
            limitations = "Hồ sơ nhập từ phiên bản cũ. Hãy kiểm chứng lại để cập nhật bằng chứng.",
            searchEntryPoint = o.optString("suggestions"),
            confidence = 0f,
        )
    }
}

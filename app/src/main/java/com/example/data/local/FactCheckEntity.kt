package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.FactCheckRecord
import com.example.model.FactCheckVerdict
import com.example.model.ReferenceSource
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "fact_check_records")
data class FactCheckEntity(
    @PrimaryKey
    val id: String,
    val originalClaim: String,
    val normalizedClaim: String,
    val verdict: String,
    val shortExplanation: String,
    val detailedExplanation: String,
    val evidence: String,
    val counterEvidence: String,
    val referencesJson: String,
    val checkedAt: Long,
    val limitations: String,
    val confidence: Float,
    val sourceApp: String,
    val rawScreenText: String,
    val isAlerted: Boolean
) {
    fun toDomain(): FactCheckRecord {
        val refs = mutableListOf<ReferenceSource>()
        try {
            val jsonArray = JSONArray(referencesJson)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                refs.add(
                    ReferenceSource(
                        title = obj.optString("title"),
                        url = obj.optString("url"),
                        publisher = obj.optString("publisher"),
                        publishDate = obj.optString("publishDate"),
                        supportingSnippet = obj.optString("supportingSnippet"),
                        isPrimarySource = obj.optBoolean("isPrimarySource", false)
                    )
                )
            }
        } catch (_: Exception) { }

        return FactCheckRecord(
            id = id,
            originalClaim = originalClaim,
            normalizedClaim = normalizedClaim,
            verdict = FactCheckVerdict.fromString(verdict),
            shortExplanation = shortExplanation,
            detailedExplanation = detailedExplanation,
            evidence = evidence,
            counterEvidence = counterEvidence,
            references = refs,
            checkedAt = checkedAt,
            limitations = limitations,
            confidence = confidence,
            sourceApp = sourceApp,
            rawScreenText = rawScreenText,
            isAlerted = isAlerted
        )
    }

    companion object {
        fun fromDomain(record: FactCheckRecord): FactCheckEntity {
            val jsonArray = JSONArray()
            record.references.forEach { ref ->
                val obj = JSONObject()
                obj.put("title", ref.title)
                obj.put("url", ref.url)
                obj.put("publisher", ref.publisher)
                obj.put("publishDate", ref.publishDate)
                obj.put("supportingSnippet", ref.supportingSnippet)
                obj.put("isPrimarySource", ref.isPrimarySource)
                jsonArray.put(obj)
            }

            return FactCheckEntity(
                id = record.id,
                originalClaim = record.originalClaim,
                normalizedClaim = record.normalizedClaim,
                verdict = record.verdict.name,
                shortExplanation = record.shortExplanation,
                detailedExplanation = record.detailedExplanation,
                evidence = record.evidence,
                counterEvidence = record.counterEvidence,
                referencesJson = jsonArray.toString(),
                checkedAt = record.checkedAt,
                limitations = record.limitations,
                confidence = record.confidence,
                sourceApp = record.sourceApp,
                rawScreenText = record.rawScreenText,
                isAlerted = record.isAlerted
            )
        }
    }
}

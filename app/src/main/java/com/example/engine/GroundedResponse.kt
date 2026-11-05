package com.example.engine

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject

/** Validates provider data before it can become a saved fact-check result. */
internal object GroundedResponse {
    private val verdicts =
        setOf(
            "DUNG",
            "SAI",
            "DUNG_MOT_PHAN",
            "GAY_HIEU_LAM",
            "CHUA_DU_BANG_CHUNG",
            "KHONG_THE_KIEM_CHUNG",
        )
    private val factual = setOf("DUNG", "SAI", "DUNG_MOT_PHAN", "GAY_HIEU_LAM")

    fun text(data: JSONObject): String {
        val parts =
            data
                .optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts") ?: JSONArray()
        return (0 until parts.length())
            .mapNotNull { index ->
                val part = parts.optJSONObject(index)
                if (part == null || part.optBoolean("thought")) null
                else (part.opt("text") as? String)
            }
            .joinToString("\n")
            .trim()
            .ifBlank { throw FactCheckException("AI chưa trả về nội dung có thể sử dụng.") }
    }

    fun validUrl(url: String): Boolean =
        url.toHttpUrlOrNull()?.let {
            it.isHttps && it.username.isEmpty() && it.password.isEmpty()
        } == true

    fun sources(metadata: JSONObject): JSONArray {
        val chunks = metadata.optJSONArray("groundingChunks") ?: JSONArray()
        val supports = metadata.optJSONArray("groundingSupports") ?: JSONArray()
        val result = JSONArray()
        for (i in 0 until chunks.length()) {
            val web = chunks.optJSONObject(i)?.optJSONObject("web") ?: continue
            val url = web.opt("uri") as? String ?: continue
            val title = web.opt("title") as? String ?: continue
            if (!validUrl(url) || title.isBlank()) continue
            val related =
                (0 until supports.length()).mapNotNull { j ->
                    val support = supports.optJSONObject(j) ?: return@mapNotNull null
                    val ids =
                        support.optJSONArray("groundingChunkIndices") ?: return@mapNotNull null
                    if ((0 until ids.length()).none { exactId(ids.opt(it)) == i })
                        return@mapNotNull null
                    (support.optJSONObject("segment")?.opt("text") as? String)?.takeIf {
                        it.isNotBlank()
                    }
                }
            if (related.isEmpty()) continue
            result.put(
                JSONObject()
                    .put("id", i)
                    .put("title", title.take(500))
                    .put("publisher", title.take(500))
                    .put("url", url)
                    .put("publishDate", "")
                    .put("supportingSnippet", related.joinToString("\n").take(3000))
                    .put("isPrimarySource", false)
            )
        }
        return result
    }

    fun singlePass(data: JSONObject): JSONObject {
        val metadata =
            data.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("groundingMetadata")
                ?: JSONObject()
        val raw =
            try {
                JSONObject(text(data))
            } catch (e: FactCheckException) {
                throw e
            } catch (_: Exception) {
                throw FactCheckException("Không đọc được kết quả kiểm chứng của Gemini.")
            }
        if (raw.opt("verdict") !in setOf("DUNG", "SAI", "CHUA_DU_BANG_CHUNG"))
            throw FactCheckException("Kết luận của Gemini không hợp lệ.")
        val sources = sources(metadata)
        // Source identifiers come from Google's citation metadata, never model-invented URLs.
        raw.put(
            "sourceIds",
            JSONArray().apply {
                for (i in 0 until sources.length()) put(sources.getJSONObject(i).getInt("id"))
            },
        )
        return assessment(raw, sources)
            .put(
                "searchEntryPoint",
                metadata.optJSONObject("searchEntryPoint")?.optString("renderedContent").orEmpty(),
            )
    }

    private fun exactId(value: Any?): Int? =
        (value as? Number)?.let {
            val number = it.toDouble()
            if (
                number.isFinite() &&
                    number >= 0 &&
                    number <= Int.MAX_VALUE &&
                    number == it.toInt().toDouble()
            )
                it.toInt()
            else null
        }

    fun insufficient(): JSONObject =
        JSONObject()
            .put("verdict", "CHUA_DU_BANG_CHUNG")
            .put("shortExplanation", "Chưa đủ bằng chứng có thể truy xuất để đưa ra kết luận.")
            .put(
                "detailedExplanation",
                "Dịch vụ chưa trả về nguồn liên kết hợp lệ hỗ trợ kết luận. Vui lòng bổ sung tài liệu hoặc kiểm chứng lại.",
            )
            .put("evidence", "")
            .put("counterEvidence", "")
            .put("limitations", "Không sử dụng kết luận chưa có nguồn hỗ trợ.")
            .put("references", JSONArray())

    fun assessment(raw: JSONObject, sources: JSONArray): JSONObject {
        val verdict = raw.opt("verdict") as? String
        if (
            verdict !in verdicts ||
                (raw.opt("shortExplanation") as? String).isNullOrBlank() ||
                (raw.opt("detailedExplanation") as? String).isNullOrBlank()
        ) {
            throw FactCheckException("Kết quả AI không đúng định dạng. Hãy thử lại.")
        }
        val ids = raw.optJSONArray("sourceIds") ?: JSONArray()
        val selected = (0 until ids.length()).mapNotNull { exactId(ids.opt(it)) }.toSet()
        val refs = JSONArray()
        for (i in 0 until sources.length()) {
            val source = sources.optJSONObject(i) ?: continue
            if (exactId(source.opt("id")) in selected && validUrl(source.optString("url"))) {
                refs.put(JSONObject(source.toString()).apply { remove("id") })
            }
        }
        if (verdict in factual && refs.length() == 0) return insufficient()
        fun field(name: String, max: Int) = (raw.opt(name) as? String).orEmpty().take(max)
        return JSONObject()
            .put("verdict", verdict)
            .put("shortExplanation", field("shortExplanation", 1500))
            .put("detailedExplanation", field("detailedExplanation", 12000))
            .put("evidence", field("evidence", 8000))
            .put("counterEvidence", field("counterEvidence", 8000))
            .put("limitations", field("limitations", 2000))
            .put("references", refs)
    }
}

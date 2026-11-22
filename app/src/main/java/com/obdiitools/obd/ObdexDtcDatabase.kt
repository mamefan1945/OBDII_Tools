package com.obdiitools.obd

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Offline generic DTC reference data. The JSON asset is populated by the APK
 * workflow from the public OBDex CC0 dataset. Missing or malformed assets
 * deliberately fall back to the app's small built-in title list.
 */
object ObdexDtcDatabase {
    private const val ASSET_NAME = "obdex-generic.min.json"
    private val lock = Any()

    @Volatile
    private var cachedEntries: Map<String, DtcDetails>? = null

    fun enrich(context: Context, dtc: DTC): DTC {
        val details = entries(context)[dtc.code.uppercase()]
        val fallback = DTCDatabase.getDescription(dtc.code)
        return dtc.copy(
            description = details?.title?.takeIf { it.isNotBlank() } ?: fallback,
            details = details,
        )
    }

    private fun entries(context: Context): Map<String, DtcDetails> {
        cachedEntries?.let { return it }
        synchronized(lock) {
            cachedEntries?.let { return it }
            val parsed = runCatching {
                val json = context.applicationContext.assets.open(ASSET_NAME)
                    .bufferedReader(Charsets.UTF_8).use { it.readText() }
                parse(JSONArray(json))
            }.getOrDefault(emptyMap())
            cachedEntries = parsed
            return parsed
        }
    }

    internal fun parse(array: JSONArray): Map<String, DtcDetails> {
        val result = LinkedHashMap<String, DtcDetails>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val code = item.optString("code").uppercase()
            if (!code.matches(Regex("^[PBCU][0-9A-F]{4}$"))) continue

            val title = item.optJSONObject("title")?.optString("en").orEmpty()
            val description = item.optJSONObject("description")?.optString("en").orEmpty()
            val causes = mutableListOf<DtcCause>()
            val causeArray = item.optJSONArray("common_causes")
            if (causeArray != null) {
                for (causeIndex in 0 until causeArray.length()) {
                    val cause = causeArray.optJSONObject(causeIndex) ?: continue
                    val label = cause.optJSONObject("label")?.optString("en")
                        ?.takeIf { it.isNotBlank() } ?: cause.optString("id")
                    if (label.isNotBlank()) {
                        causes += DtcCause(label, cause.optString("likelihood", "unspecified"))
                    }
                }
            }

            val symptoms = mutableListOf<String>()
            val symptomArray = item.optJSONArray("symptoms")
            if (symptomArray != null) {
                for (symptomIndex in 0 until symptomArray.length()) {
                    val value = symptomArray.opt(symptomIndex)
                    val symptom = when (value) {
                        is JSONObject -> value.optString("en")
                        is String -> value
                        else -> ""
                    }
                    if (symptom.isNotBlank()) symptoms += symptom
                }
            }

            val repair = item.optJSONObject("repair")
            val flags = item.optJSONObject("flags")
            result[code] = DtcDetails(
                title = title,
                description = description,
                category = item.optString("category"),
                commonCauses = causes,
                symptoms = symptoms,
                repairDifficulty = repair?.optString("difficulty")?.takeIf { it.isNotBlank() },
                diyPossible = repair?.let { if (it.has("diy_possible")) it.optBoolean("diy_possible") else null },
                emissionsRelevant = flags?.let { if (it.has("emissions_relevant")) it.optBoolean("emissions_relevant") else null },
                limpModePossible = flags?.let { if (it.has("limp_mode_possible")) it.optBoolean("limp_mode_possible") else null },
                reviewed = item.optString("reviewed").takeIf { it.isNotBlank() },
                sourceCount = item.optInt(
                    "source_count",
                    item.optJSONArray("sources")?.length() ?: 0,
                ),
            )
        }
        return result
    }
}

package com.obdiitools.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CustomPidRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val KEY = stringPreferencesKey("custom_pids")

    val customPids: Flow<List<CustomPidDefinition>> = dataStore.data.map { prefs ->
        prefs[KEY]?.let { deserialize(it) } ?: emptyList()
    }

    suspend fun addPid(definition: CustomPidDefinition) {
        dataStore.edit { prefs ->
            val current = prefs[KEY]?.let { deserialize(it) } ?: emptyList()
            prefs[KEY] = serialize(current + definition)
        }
    }

    suspend fun removePid(id: String) {
        dataStore.edit { prefs ->
            val current = prefs[KEY]?.let { deserialize(it) } ?: emptyList()
            prefs[KEY] = serialize(current.filter { it.id != id })
        }
    }

    private fun serialize(pids: List<CustomPidDefinition>): String {
        val arr = JSONArray()
        pids.forEach { pid ->
            arr.put(JSONObject().apply {
                put("id", pid.id)
                put("name", pid.name)
                put("command", pid.command)
                put("unit", pid.unit)
                put("formula", pid.formula.name)
            })
        }
        return arr.toString()
    }

    private fun deserialize(json: String): List<CustomPidDefinition> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            val obj = arr.getJSONObject(i)
            runCatching {
                CustomPidDefinition(
                    id      = obj.getString("id"),
                    name    = obj.getString("name"),
                    command = obj.getString("command"),
                    unit    = obj.getString("unit"),
                    formula = FormulaType.valueOf(obj.getString("formula")),
                )
            }.getOrNull()
        }
    } catch (e: Exception) { emptyList() }
}

fun newCustomPidId(): String = UUID.randomUUID().toString()

package com.obdiitools.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class VehicleDiscoveryItem(val key:String,val name:String,val ecuAddress:String,val category:String,val known:Boolean,val status:String,val rawResponse:String="",val updatedAtMs:Long=System.currentTimeMillis())
data class VehicleDiscoveryProfile(val id:String,val vin:String,val make:String,val model:String,val year:String,val lastSeenMs:Long,val ecuCount:Int,val items:List<VehicleDiscoveryItem>) {
    val knownCount get()=items.count{it.known}
    val unknownCount get()=items.count{!it.known}
}
@Singleton
class VehicleDiscoveryRepository @Inject constructor(@ApplicationContext context:Context) {
    private val prefs=context.getSharedPreferences("vehicle_discovery_profiles",Context.MODE_PRIVATE)
    private val _profiles=MutableStateFlow(load())
    val profiles:StateFlow<List<VehicleDiscoveryProfile>> = _profiles.asStateFlow()
    @Synchronized fun deleteProfile(id:String) {
        save(_profiles.value.filterNot { it.id == id })
    }
    @Synchronized fun recordScan(
        vin:String?,
        items:List<VehicleDiscoveryItem>,
        ecuCount:Int,
        make:String="",
        model:String="",
        year:String="",
    ) {
        // A valid VIN is the stable identity key. Do not create duplicate
        // "unknown vehicle" profiles for every adapter connection.
        val valid=vin.orEmpty().uppercase().filter{it.isLetterOrDigit()}
            .takeIf{it.matches(Regex("^[A-HJ-NPR-Z0-9]{17}$")) && it!="00000000000000000"}
            ?: return
        val old=_profiles.value.firstOrNull{it.id==valid}
        val mergedItems=(old?.items.orEmpty().associateBy{it.key}+items.associateBy{it.key})
            .values.sortedBy{it.key}
        val profile=VehicleDiscoveryProfile(
            id=valid,
            vin=valid,
            make=make.takeIf{it.isNotBlank()}?:old?.make?.takeIf{it.isNotBlank()}?:makeFromVin(valid),
            model=model.takeIf{it.isNotBlank()}?:old?.model.orEmpty(),
            year=year.takeIf{it.isNotBlank()}?:old?.year.orEmpty(),
            lastSeenMs=System.currentTimeMillis(),
            ecuCount=maxOf(old?.ecuCount?:0,ecuCount),
            items=mergedItems,
        )
        save((_profiles.value.filterNot{it.id==valid}+profile).sortedByDescending{it.lastSeenMs})
    }
    @Synchronized fun recordMazdaDids(items:List<VehicleDiscoveryItem>) {
        val old=_profiles.value.firstOrNull{it.make.equals("Mazda",true)}?:return
        val updated=old.copy(lastSeenMs=System.currentTimeMillis(),items=(old.items+items).distinctBy{it.key})
        save((_profiles.value.filterNot{it.id==old.id}+updated).sortedByDescending{it.lastSeenMs})
    }
    private fun save(list:List<VehicleDiscoveryProfile>) {
        val a=JSONArray()
        list.forEach{p-> val ia=JSONArray();p.items.forEach{x->ia.put(JSONObject().apply{put("key",x.key);put("name",x.name);put("ecuAddress",x.ecuAddress);put("category",x.category);put("known",x.known);put("status",x.status);put("rawResponse",x.rawResponse);put("updatedAtMs",x.updatedAtMs)})}
            a.put(JSONObject().apply{put("id",p.id);put("vin",p.vin);put("make",p.make);put("model",p.model);put("year",p.year);put("lastSeenMs",p.lastSeenMs);put("ecuCount",p.ecuCount);put("items",ia)})}
        prefs.edit().putString("profiles",a.toString()).apply();_profiles.value=list
    }
    private fun load():List<VehicleDiscoveryProfile> = runCatching {
        val a=JSONArray(prefs.getString("profiles","[]"))
        List(a.length()){i->val p=a.getJSONObject(i);val ia=p.optJSONArray("items")?:JSONArray()
            val items=List(ia.length()){j->val x=ia.getJSONObject(j);VehicleDiscoveryItem(x.optString("key"),x.optString("name"),x.optString("ecuAddress"),x.optString("category"),x.optBoolean("known"),x.optString("status"),x.optString("rawResponse"),x.optLong("updatedAtMs"))}
            VehicleDiscoveryProfile(p.optString("id"),p.optString("vin"),p.optString("make"),p.optString("model"),p.optString("year"),p.optLong("lastSeenMs"),p.optInt("ecuCount"),items)
        }.sortedByDescending{it.lastSeenMs}
    }.getOrDefault(emptyList())
    private fun makeFromVin(v:String)=when(v.take(3)) {
        "JM1","JM3"->"Mazda";"1N4","1N6","3N1","JN1","JN8"->"Nissan";"1HG","2HG","JHM","5J6"->"Honda";"JT2","JT3","4T1","5TD","JTE"->"Toyota";"WBA","WBS"->"BMW";"WVW","3VW"->"Volkswagen";"WAU"->"Audi";"1FA","1FM","1FT","3FA"->"Ford";else->if(v.length==17)"VIN ${v.take(3)}" else "Unknown manufacturer"
    }
}
package com.obdiitools.ui.screens
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.obdiitools.data.VehicleDiscoveryProfile
import com.obdiitools.data.VehicleDiscoveryItem
import com.obdiitools.ui.theme.*
import com.obdiitools.viewmodel.VehicleDiscoveryViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun VehicleDiscoveryScreen(onBack:()->Unit,viewModel:VehicleDiscoveryViewModel= hiltViewModel()) {
 val profiles by viewModel.profiles.collectAsState()
 Scaffold(containerColor=BackgroundDeep,topBar={CenterAlignedTopAppBar(title={Text("VEHICLE DISCOVERY",fontFamily=FontFamily.Monospace,fontWeight=FontWeight.Bold,fontSize=13.sp,color=TextPrimary)},navigationIcon={IconButton(onClick=onBack){Icon(Icons.AutoMirrored.Filled.ArrowBack,null,tint=NeonCyan)}},colors=TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor=BackgroundCard))}) { padding ->
  if(profiles.isEmpty()) Box(Modifier.fillMaxSize().padding(padding).padding(24.dp),contentAlignment=Alignment.Center){Text("No profiles yet. Run Deep Scan while connected to a vehicle to build its discovery profile.",color=TextSecondary,fontFamily=FontFamily.Monospace)}
  else LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal=14.dp),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(vertical=14.dp)) {
   item { Text("VIN-identified vehicles are grouped together. Scans without a VIN remain separate so unrelated vehicles are never merged.",color=TextSecondary,fontSize=11.sp) }
   items(profiles,key={it.id}) { ProfileCard(it) }
  }
 }
}
@Composable private fun ProfileCard(p:VehicleDiscoveryProfile) {
 var expanded by remember(p.id){mutableStateOf(false)}
 Card(colors=CardDefaults.cardColors(containerColor=BackgroundCard),border=BorderStroke(1.dp,SurfaceBorder)) {
  Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
   Text(listOf(p.year,p.make,p.model).filter{it.isNotBlank()}.joinToString(" ").ifBlank{"Unidentified vehicle"},color=TextPrimary,fontFamily=FontFamily.Monospace,fontWeight=FontWeight.Bold,fontSize=14.sp)
   Text(if(p.vin.isNotBlank())"VIN: ${p.vin}" else "VIN unavailable — this scan is not merged with other vehicles",color=TextSecondary,fontSize=10.sp)
   Text("Last scan: ${SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.getDefault()).format(Date(p.lastSeenMs))} • ${p.ecuCount} ECUs",color=TextSecondary,fontSize=10.sp)
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){Count("KNOWN",p.knownCount,NeonGreen,Modifier.weight(1f));Count("UNKNOWN",p.unknownCount,NeonOrange,Modifier.weight(1f));Count("TOTAL",p.items.size,NeonCyan,Modifier.weight(1f))}
   TextButton(onClick={expanded=!expanded},modifier=Modifier.align(Alignment.End)){Text(if(expanded)"HIDE ITEMS" else "VIEW ITEMS",color=NeonCyan,fontFamily=FontFamily.Monospace,fontSize=10.sp)}
   if(expanded){HorizontalDivider(color=SurfaceBorder);p.items.sortedWith(compareBy<VehicleDiscoveryItem>{!it.known}.thenBy{it.category}.thenBy{it.key}).forEach { x ->
    Column(verticalArrangement=Arrangement.spacedBy(2.dp)){
     Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(x.name,color=TextPrimary,fontSize=11.sp,modifier=Modifier.weight(1f));Text(if(x.known)"KNOWN" else "UNKNOWN",color=if(x.known)NeonGreen else NeonOrange,fontSize=9.sp,fontFamily=FontFamily.Monospace)}
     Text("${x.category} • ECU 0x${x.ecuAddress} • ${x.status}",color=TextSecondary,fontSize=9.sp)
     if(x.rawResponse.isNotBlank())Text("Raw: ${x.rawResponse}",color=TextSecondary,fontSize=9.sp,fontFamily=FontFamily.Monospace)
    }
   }}
  }
 }
}
@Composable private fun Count(label:String,n:Int,color:Color,modifier:Modifier=Modifier){Column(modifier.background(BackgroundDeep,RoundedCornerShape(8.dp)).padding(9.dp),horizontalAlignment=Alignment.CenterHorizontally){Text("$n",color=color,fontFamily=FontFamily.Monospace,fontWeight=FontWeight.Bold,fontSize=18.sp);Text(label,color=TextSecondary,fontFamily=FontFamily.Monospace,fontSize=8.sp)}}

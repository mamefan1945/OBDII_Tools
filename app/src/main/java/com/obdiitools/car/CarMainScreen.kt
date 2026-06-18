package com.obdiitools.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import com.obdiitools.R
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Tab
import androidx.car.app.model.TabContents
import androidx.car.app.model.TabTemplate
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.lifecycleScope
import com.obdiitools.bluetooth.OBDRepository
import com.obdiitools.data.AlertType
import com.obdiitools.data.PreferencesRepository
import com.obdiitools.data.UserPreferences
import com.obdiitools.obd.ConnectionState
import com.obdiitools.obd.OBDData
import com.obdiitools.util.UnitConverter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class CarMainScreen(
    carContext: CarContext,
    private val repository: OBDRepository,
    private val prefsRepository: PreferencesRepository,
) : Screen(carContext) {

    private var currentData = OBDData()
    private var connectionState: ConnectionState = ConnectionState.Disconnected
    private var currentPrefs = UserPreferences()
    private var activeAlerts: Set<AlertType> = emptySet()
    private var activeTabId = "dashboard"

    init {
        repository.obdData
            .onEach { currentData = it; invalidate() }
            .launchIn(lifecycleScope)

        repository.connectionState
            .onEach { connectionState = it; invalidate() }
            .launchIn(lifecycleScope)

        prefsRepository.userPreferences
            .onEach { currentPrefs = it; invalidate() }
            .launchIn(lifecycleScope)

        repository.activeAlerts
            .onEach { activeAlerts = it; invalidate() }
            .launchIn(lifecycleScope)
    }

    override fun onGetTemplate(): Template {
        if (connectionState !is ConnectionState.Connected) {
            return MessageTemplate.Builder(
                "Open the OBDII Tools app on your phone and connect to your ELM327 adapter."
            )
                .setTitle("Not Connected")
                .setHeaderAction(Action.APP_ICON)
                .build()
        }

        // TabTemplate requires Car API level 2; fall back to ListTemplate on level 1 hosts
        if (carContext.carAppApiLevel < 2) {
            return buildDashboardTemplate()
        }

        val activeContents = when (activeTabId) {
            "faults" -> TabContents.Builder(buildFaultsTemplate()).build()
            else     -> TabContents.Builder(buildDashboardTemplate()).build()
        }

        return TabTemplate.Builder(object : TabTemplate.TabCallback {
            override fun onTabSelected(tabContentId: String) {
                activeTabId = tabContentId
                invalidate()
            }
        })
            .setHeaderAction(Action.APP_ICON)
            .addTab(tab("dashboard", "Dashboard", R.drawable.ic_car_dashboard))
            .addTab(tab("faults",    "Faults",    R.drawable.ic_car_faults))
            .setTabContents(activeContents)
            .setActiveTabContentId(activeTabId)
            .build()
    }

    private fun tab(contentId: String, title: String, iconRes: Int) = Tab.Builder()
        .setTitle(title)
        .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, iconRes)).build())
        .setContentId(contentId)
        .build()

    private fun row(label: String, value: String?) = Row.Builder()
        .setTitle(value ?: "—")
        .addText(label)
        .build()

    private fun buildDashboardTemplate(): ListTemplate {
        val d = currentData
        val p = currentPrefs
        return ListTemplate.Builder()
            .setTitle("Dashboard")
            .setSingleList(ItemList.Builder().apply {
                activeAlerts.forEach { alert ->
                    val description = when (alert) {
                        AlertType.HIGH_COOLANT -> "Coolant temp exceeds threshold"
                        AlertType.LOW_FUEL     -> "Fuel level below threshold"
                        AlertType.HIGH_RPM     -> "Engine RPM exceeds limit"
                        AlertType.LOW_BATTERY  -> "Battery below configured threshold"
                    }
                    addItem(row(description, "⚠ ${alert.label.uppercase()}"))
                }
                addItem(row("Engine Speed",
                    d.rpm?.let { "$it RPM" }))
                addItem(row("Vehicle Speed",
                    d.speedKph?.let { "${UnitConverter.formatSpeed(it, p.speedUnit)} ${p.speedUnit.symbol}" }))
                addItem(row("Coolant Temperature",
                    d.coolantTempC?.let { "${UnitConverter.formatTemp(it, p.temperatureUnit)}${p.temperatureUnit.symbol}" }))
                addItem(row("Throttle Position",
                    d.throttlePercent?.let { "${"%.1f".format(it)} %" }))
                addItem(row("Engine Load",
                    d.engineLoadPercent?.let { "${"%.1f".format(it)} %" }))
                addItem(row("Fuel Level",
                    d.fuelLevelPercent?.let { "${"%.1f".format(it)} %" }))
                addItem(row("Battery Voltage",
                    d.batteryVoltage?.let { "${"%.2f".format(it)} V" }))
                addItem(row("Intake Air Temperature",
                    d.intakeAirTempC?.let { "${UnitConverter.formatTemp(it, p.temperatureUnit)}${p.temperatureUnit.symbol}" }))
                addItem(row("MAF Air Flow",
                    d.mafGramsPerSec?.let { "${UnitConverter.formatAirMass(it, p.airMassUnit)} ${p.airMassUnit.symbol}" }))
            }.build())
            .build()
    }

    private fun buildFaultsTemplate(): ListTemplate {
        val dtcList = repository.dtcList.value
        return ListTemplate.Builder()
            .setTitle("Fault Codes (${dtcList.size})")
            .setSingleList(ItemList.Builder().apply {
                if (dtcList.isEmpty()) {
                    addItem(row("System OK", "No Faults Detected"))
                } else {
                    dtcList.forEach { dtc ->
                        addItem(row(
                            dtc.description.ifBlank { "Unknown fault" },
                            dtc.code + if (dtc.isPending) " (Pending)" else "",
                        ))
                    }
                }
            }.build())
            .setActionStrip(ActionStrip.Builder()
                .addAction(Action.Builder()
                    .setTitle("Scan")
                    .setOnClickListener { lifecycleScope.launch { repository.scanDTCs() } }
                    .build())
                .addAction(Action.Builder()
                    .setTitle("Clear")
                    .setOnClickListener { lifecycleScope.launch { repository.clearDTCs() } }
                    .build())
                .build())
            .build()
    }
}

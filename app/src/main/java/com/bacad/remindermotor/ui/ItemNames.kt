package com.bacad.remindermotor.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DiscFull
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.OilBarrel
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TireRepair
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.bacad.remindermotor.R
import com.bacad.remindermotor.data.MaintenanceItem

@StringRes
fun itemNameRes(key: String?): Int? = when (key) {
    "engine_oil" -> R.string.item_engine_oil
    "gear_oil" -> R.string.item_gear_oil
    "air_filter" -> R.string.item_air_filter
    "spark_plug" -> R.string.item_spark_plug
    "brake_pads" -> R.string.item_brake_pads
    "tires" -> R.string.item_tires
    "chain" -> R.string.item_chain
    "battery" -> R.string.item_battery
    "coolant" -> R.string.item_coolant
    "brake_fluid" -> R.string.item_brake_fluid
    "cvt_belt" -> R.string.item_cvt_belt
    else -> null
}

/** Ikon per item. Warna status ditambahkan oleh grid, bukan di sini. */
fun itemIcon(key: String?): ImageVector = when (key) {
    "engine_oil" -> Icons.Filled.OilBarrel
    "gear_oil" -> Icons.Filled.Settings
    "air_filter" -> Icons.Filled.Air
    "spark_plug" -> Icons.Filled.ElectricBolt
    "brake_pads" -> Icons.Filled.DiscFull
    "tires" -> Icons.Filled.TireRepair
    "chain" -> Icons.Filled.Link
    "battery" -> Icons.Filled.BatteryFull
    "coolant" -> Icons.Filled.WaterDrop
    "brake_fluid" -> Icons.Filled.Opacity
    "cvt_belt" -> Icons.Filled.Loop
    else -> Icons.Filled.Build
}

@Composable
fun itemDisplayName(item: MaintenanceItem): String {
    val res = itemNameRes(item.key)
    return when {
        res != null -> stringResource(res)
        item.customName != null -> item.customName
        else -> item.key ?: "?"
    }
}

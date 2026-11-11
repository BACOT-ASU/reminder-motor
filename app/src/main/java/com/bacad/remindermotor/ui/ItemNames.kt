package com.bacad.remindermotor.ui

import androidx.annotation.StringRes
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

@Composable
fun itemDisplayName(item: MaintenanceItem): String {
    val res = itemNameRes(item.key)
    return when {
        res != null -> stringResource(res)
        item.customName != null -> item.customName
        else -> item.key ?: "?"
    }
}

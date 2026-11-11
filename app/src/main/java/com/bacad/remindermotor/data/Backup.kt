package com.bacad.remindermotor.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject

/**
 * Backup ke satu file JSON dan restore dari file tersebut.
 * Restore MENGGANTI semua data yang ada di aplikasi.
 */
object Backup {
    private const val FORMAT_VERSION = 1

    suspend fun export(db: AppDatabase): String {
        val dao = db.dao()
        val root = JSONObject()
        root.put("app", "reminder-motor")
        root.put("version", FORMAT_VERSION)

        root.put("motors", JSONArray().apply {
            dao.getMotors().forEach { m ->
                put(JSONObject().apply {
                    put("id", m.id); put("name", m.name); put("type", m.type.name)
                    put("hasRadiator", m.hasRadiator); put("odometerKm", m.odometerKm)
                    put("odometerDate", m.odometerDate); putOpt("bluetoothTrigger", m.bluetoothTrigger)
                })
            }
        })
        root.put("items", JSONArray().apply {
            dao.getAllItems().forEach { i ->
                put(JSONObject().apply {
                    put("id", i.id); put("motorId", i.motorId); putOpt("key", i.key)
                    putOpt("customName", i.customName); put("type", i.type.name)
                    putOpt("intervalKm", i.intervalKm); putOpt("intervalDays", i.intervalDays)
                    put("active", i.active)
                })
            }
        })
        root.put("records", JSONArray().apply {
            dao.getAllRecords().forEach { r ->
                put(JSONObject().apply {
                    put("id", r.id); put("itemId", r.itemId); put("date", r.date); put("km", r.km)
                    put("action", r.action.name); putOpt("cost", r.cost); putOpt("note", r.note)
                })
            }
        })
        root.put("odometerLogs", JSONArray().apply {
            dao.getAllOdometerLogs().forEach { o ->
                put(JSONObject().apply {
                    put("id", o.id); put("motorId", o.motorId); put("date", o.date)
                    put("km", o.km); put("source", o.source.name)
                })
            }
        })
        return root.toString(2)
    }

    /** @throws IllegalArgumentException jika file bukan backup aplikasi ini. */
    suspend fun import(db: AppDatabase, json: String) {
        val root = try { JSONObject(json) } catch (e: Exception) {
            throw IllegalArgumentException("not json")
        }
        require(root.optString("app") == "reminder-motor") { "not a reminder-motor backup" }

        val motors = root.getJSONArray("motors").objects().map { o ->
            Motor(
                id = o.getLong("id"), name = o.getString("name"),
                type = MotorType.valueOf(o.getString("type")), hasRadiator = o.getBoolean("hasRadiator"),
                odometerKm = o.getInt("odometerKm"), odometerDate = o.getLong("odometerDate"),
                bluetoothTrigger = o.optStringOrNull("bluetoothTrigger")
            )
        }
        val items = root.getJSONArray("items").objects().map { o ->
            MaintenanceItem(
                id = o.getLong("id"), motorId = o.getLong("motorId"), key = o.optStringOrNull("key"),
                customName = o.optStringOrNull("customName"), type = ItemType.valueOf(o.getString("type")),
                intervalKm = o.optIntOrNull("intervalKm"), intervalDays = o.optIntOrNull("intervalDays"),
                active = o.optBoolean("active", true)
            )
        }
        val records = root.getJSONArray("records").objects().map { o ->
            ServiceRecord(
                id = o.getLong("id"), itemId = o.getLong("itemId"), date = o.getLong("date"),
                km = o.getInt("km"), action = ServiceAction.valueOf(o.getString("action")),
                cost = if (o.isNull("cost") || !o.has("cost")) null else o.getLong("cost"),
                note = o.optStringOrNull("note")
            )
        }
        val logs = root.getJSONArray("odometerLogs").objects().map { o ->
            OdometerLog(
                id = o.getLong("id"), motorId = o.getLong("motorId"), date = o.getLong("date"),
                km = o.getInt("km"), source = OdometerSource.valueOf(o.getString("source"))
            )
        }

        val dao = db.dao()
        db.withTransaction {
            dao.clearRecords(); dao.clearOdometerLogs(); dao.clearItems(); dao.clearMotors()
            dao.insertMotors(motors); dao.insertItems(items)
            dao.insertRecords(records); dao.insertOdometerLogs(logs)
        }
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
    private fun JSONObject.optStringOrNull(name: String): String? = if (isNull(name) || !has(name)) null else getString(name)
    private fun JSONObject.optIntOrNull(name: String): Int? = if (isNull(name) || !has(name)) null else getInt(name)
}

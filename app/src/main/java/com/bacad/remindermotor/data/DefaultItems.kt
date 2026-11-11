package com.bacad.remindermotor.data

/**
 * Daftar item perawatan bawaan. Angka hanya perkiraan umum untuk motor harian;
 * pengguna wajib mencocokkan dengan buku manual (bisa diubah di tahap pengaturan).
 */
data class DefaultItem(
    val key: String,
    val type: ItemType,
    val intervalKm: Int?,
    val intervalDays: Int?,
    /** null = berlaku untuk semua jenis motor. */
    val onlyFor: MotorType? = null,
    val needsRadiator: Boolean = false
)

object DefaultItems {
    val all = listOf(
        DefaultItem("engine_oil", ItemType.REPLACE, 2_000, 30),
        DefaultItem("gear_oil", ItemType.REPLACE, 8_000, 180, onlyFor = MotorType.MATIC),
        DefaultItem("air_filter", ItemType.REPLACE, 8_000, 180),
        DefaultItem("spark_plug", ItemType.REPLACE, 8_000, 365),
        DefaultItem("brake_pads", ItemType.CHECK, 10_000, 90),
        DefaultItem("tires", ItemType.CHECK, 20_000, 730),
        DefaultItem("chain", ItemType.CHECK, 15_000, 30, onlyFor = MotorType.MANUAL),
        DefaultItem("battery", ItemType.CHECK, null, 730),
        DefaultItem("coolant", ItemType.REPLACE, 12_000, 365, needsRadiator = true),
        DefaultItem("brake_fluid", ItemType.REPLACE, null, 730),
        DefaultItem("cvt_belt", ItemType.REPLACE, 20_000, 730, onlyFor = MotorType.MATIC)
    )

    fun forMotor(type: MotorType, hasRadiator: Boolean): List<DefaultItem> =
        all.filter { item ->
            (item.onlyFor == null || item.onlyFor == type) &&
                (!item.needsRadiator || hasRadiator)
        }
}

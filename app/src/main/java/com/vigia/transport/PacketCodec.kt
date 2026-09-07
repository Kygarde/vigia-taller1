package com.vigia.transport

import com.vigia.model.*

object PacketCodec {
    const val MANUFACTURER_ID = 0x0BDA   // ID arbitrario del equipo

    fun encode(id: Int, ctx: ContextSnapshot, d: AdaptationDecision): ByteArray {
        var flags = 0
        if (ctx.wifiEnabled) flags = flags or 0b0001
        if (ctx.mobileDataEnabled) flags = flags or 0b0010
        if (ctx.screenOn) flags = flags or 0b0100

        return byteArrayOf(
            1,                                        // versión
            (id shr 8).toByte(), id.toByte(),         // idAlumno
            d.risk.ordinal.toByte(),
            (ctx.movementIndex * 255).toInt().toByte(),
            ctx.batteryLevel.toByte(),
            d.mode.ordinal.toByte(),
            flags.toByte()
        )
    }

    fun decode(b: ByteArray): StudentStatus? {
        if (b.size < 8) return null
        return StudentStatus(
            id = ((b[1].toInt() and 0xFF) shl 8) or (b[2].toInt() and 0xFF),
            risk = RiskLevel.entries[b[3].toInt()],
            movement = (b[4].toInt() and 0xFF) / 255f,
            battery = b[5].toInt() and 0xFF,
            mode = OperatingMode.entries[b[6].toInt()],
            wifi = (b[7].toInt() and 0b0001) != 0,
            mobile = (b[7].toInt() and 0b0010) != 0,
            screenOn = (b[7].toInt() and 0b0100) != 0
        )
    }
}

data class StudentStatus(
    val id: Int, val risk: RiskLevel, val movement: Float,
    val battery: Int, val mode: OperatingMode,
    val wifi: Boolean, val mobile: Boolean, val screenOn: Boolean,
    val lastSeen: Long = System.currentTimeMillis()
)
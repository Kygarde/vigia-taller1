package com.vigia.transport

import com.vigia.model.*

/**
 * Los dos tipos de anuncio BLE que usa la aplicacion.
 *
 * Cabecera comun:
 *   [0] version del formato
 *   [1] tipo de paquete
 *   [2] codigo de aula
 *
 * AULA_ABIERTA (3 bytes) lo emite el equipo del docente mientras el panel esta
 * abierto. Es lo que permite al alumno saber que el aula existe antes de unirse:
 * sin servidor, un aula solo "existe" mientras alguien la esta anunciando.
 *
 * ESTADO_ALUMNO agrega:
 *   [3] [4]  identificador del equipo
 *   [5]      nivel de riesgo
 *   [6]      indice de movimiento (0..255)
 *   [7]      bateria (%)
 *   [8]      modo de operacion
 *   [9]      banderas: wifi, datos, pantalla
 *   [10]     largo del nombre en bytes
 *   [11..]   nombre en UTF-8
 *
 * El anuncio BLE admite 31 bytes en total, por eso el nombre se limita a 10.
 */
object PacketCodec {

    const val MANUFACTURER_ID = 0x0BDA   // ID arbitrario del equipo
    const val VERSION: Byte = 4

    const val TIPO_AULA: Byte = 1
    const val TIPO_ALUMNO: Byte = 2

    private const val CABECERA_ALUMNO = 11

    /** Baliza del docente: "el aula N esta abierta". */
    fun encodeAula(sala: Int): ByteArray =
        byteArrayOf(VERSION, TIPO_AULA, sala.toByte())

    fun encodeAlumno(
        id: Int,
        sala: Int,
        nombre: String,
        ctx: ContextSnapshot,
        d: AdaptationDecision
    ): ByteArray {
        var flags = 0
        if (ctx.wifiEnabled) flags = flags or 0b0001
        if (ctx.mobileDataEnabled) flags = flags or 0b0010
        if (ctx.screenOn) flags = flags or 0b0100

        val bytesNombre = EquipoStore.recortar(nombre).toByteArray(Charsets.UTF_8)

        val cabecera = byteArrayOf(
            VERSION,
            TIPO_ALUMNO,
            sala.toByte(),
            (id shr 8).toByte(), id.toByte(),
            d.risk.ordinal.toByte(),
            (ctx.movementIndex * 255).toInt().toByte(),
            ctx.batteryLevel.toByte(),
            d.mode.ordinal.toByte(),
            flags.toByte(),
            bytesNombre.size.toByte()
        )
        return cabecera + bytesNombre
    }

    /** Codigo de aula de cualquier paquete valido, o null si no lo es. */
    fun salaDe(b: ByteArray): Int? {
        if (b.size < 3 || b[0] != VERSION) return null
        return b[2].toInt() and 0xFF
    }

    fun esAulaAbierta(b: ByteArray): Boolean =
        b.size >= 3 && b[0] == VERSION && b[1] == TIPO_AULA

    fun decodeAlumno(b: ByteArray): StudentStatus? {
        if (b.size < CABECERA_ALUMNO) return null
        if (b[0] != VERSION || b[1] != TIPO_ALUMNO) return null

        val riesgo = RiskLevel.entries.getOrNull(b[5].toInt()) ?: return null
        val modo = OperatingMode.entries.getOrNull(b[8].toInt()) ?: return null

        val largo = (b[10].toInt() and 0xFF).coerceAtMost(b.size - CABECERA_ALUMNO)
        val nombre = if (largo > 0) String(b, CABECERA_ALUMNO, largo, Charsets.UTF_8) else ""

        return StudentStatus(
            id = ((b[3].toInt() and 0xFF) shl 8) or (b[4].toInt() and 0xFF),
            sala = b[2].toInt() and 0xFF,
            nombre = nombre,
            risk = riesgo,
            movement = (b[6].toInt() and 0xFF) / 255f,
            battery = b[7].toInt() and 0xFF,
            mode = modo,
            wifi = (b[9].toInt() and 0b0001) != 0,
            mobile = (b[9].toInt() and 0b0010) != 0,
            screenOn = (b[9].toInt() and 0b0100) != 0
        )
    }
}

data class StudentStatus(
    val id: Int, val sala: Int, val nombre: String,
    val risk: RiskLevel, val movement: Float,
    val battery: Int, val mode: OperatingMode,
    val wifi: Boolean, val mobile: Boolean, val screenOn: Boolean,
    val lastSeen: Long = System.currentTimeMillis()
) {
    /** Lo que se muestra en el panel: el nombre si lo puso, si no el numero de equipo. */
    val etiqueta: String get() = if (nombre.isNotBlank()) nombre else "Equipo $id"
}

/**
 * Un alumno tal como lo ve el panel: su ultimo estado recibido mas el historial
 * que el docente acumula durante la sesion.
 *
 * Las incidencias NO viajan en el paquete: las cuenta el equipo del docente cada
 * vez que ve a un alumno entrar en ALERTA. Asi el conteo no depende de que el
 * alumno sea honesto con lo que anuncia.
 */
data class AlumnoVigilado(
    val estado: StudentStatus,
    val incidencias: Int
)

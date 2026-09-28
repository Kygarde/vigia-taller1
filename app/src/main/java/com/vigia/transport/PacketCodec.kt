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
 * AULA_ABIERTA (5 bytes) lo emite el equipo del docente mientras el panel esta
 * abierto. Es lo que permite al alumno saber que el aula existe antes de unirse:
 * sin servidor, un aula solo "existe" mientras alguien la esta anunciando.
 *
 * ESTADO_ALUMNO agrega:
 *   [3] [4]  identificador del equipo
 *   [5]      nivel de riesgo
 *   [6]      indice de movimiento (0..255)
 *   [7]      bateria (%)
 *   [8]      modo de operacion
 *   [9]      banderas: wifi, datos, pantalla, salio de la app, bloqueado, no boca abajo
 *   [10]     largo del codigo en bytes
 *   [11..]   codigo de alumno en UTF-8
 *
 * El anuncio BLE admite 31 bytes en total. El codigo UNI son 9 (8 digitos y una
 * letra), asi que el paquete completo queda en 20 bytes.
 */
object PacketCodec {

    const val MANUFACTURER_ID = 0x0BDA
    const val VERSION: Byte = 6              // 5 -> 6: la baliza lleva el PIN

    const val TIPO_AULA: Byte = 1
    const val TIPO_ALUMNO: Byte = 2
    const val TIPO_CIERRE: Byte = 3

    private const val CABECERA_ALUMNO = 11

    // Banderas del byte [9]
    private const val F_WIFI          = 0b000001
    private const val F_DATOS         = 0b000010
    private const val F_PANTALLA      = 0b000100
    private const val F_SALIO         = 0b001000
    private const val F_BLOQUEO       = 0b010000
    private const val F_NO_BOCA_ABAJO = 0b100000

    /**
     * Baliza del docente: "el aula N esta abierta, y su PIN tiene esta huella".
     *
     *   [0] version  [1] tipo  [2] sala  [3][4] huella del PIN
     *
     * Viaja la huella y no el PIN. Un alumno que capturara el anuncio no puede leer
     * los cuatro digitos directamente; tendria que probarlos todos. No es criptografia
     * —son 10 000 combinaciones— pero cierra la lectura casual, que es el ataque real:
     * alguien mirando por encima del hombro o un anuncio a la vista.
     */
    fun encodeAula(sala: Int, huellaPin: Int): ByteArray =
        byteArrayOf(
            VERSION, TIPO_AULA, sala.toByte(),
            (huellaPin shr 8).toByte(), huellaPin.toByte()
        )

    /** Huella de 16 bits del PIN, amarrada al aula para que no sirva en otro salon. */
    fun huellaPin(pin: String, sala: Int): Int {
        var h = 7
        for (c in pin) h = (h * 31 + c.code) and 0xFFFF
        return (h * 31 + sala) and 0xFFFF
    }

    /**
     * "El examen del aula N termino."
     *
     * Sin esto, el alumno solo se entera cuando la baliza caduca: quince segundos de
     * caducidad mas la confirmacion, casi medio minuto mirando una pantalla que no
     * cambia. Con el aviso explicito sale en el acto.
     */
    fun encodeCierre(sala: Int): ByteArray =
        byteArrayOf(VERSION, TIPO_CIERRE, sala.toByte())

    fun esCierreDeAula(b: ByteArray): Boolean =
        b.size >= 3 && b[0] == VERSION && b[1] == TIPO_CIERRE

    /** Huella que declara una baliza de aula, o null si el anuncio no la trae. */
    fun huellaDeAula(b: ByteArray): Int? {
        if (!esAulaAbierta(b) || b.size < 5) return null
        return ((b[3].toInt() and 0xFF) shl 8) or (b[4].toInt() and 0xFF)
    }

    fun encodeAlumno(
        id: Int,
        sala: Int,
        codigo: String,
        ctx: ContextSnapshot,
        d: AdaptationDecision,
        bloqueado: Boolean = false
    ): ByteArray {
        var flags = 0
        if (ctx.wifiEnabled) flags = flags or F_WIFI
        if (ctx.mobileDataEnabled) flags = flags or F_DATOS
        if (ctx.screenOn) flags = flags or F_PANTALLA
        if (!ctx.appEnPrimerPlano) flags = flags or F_SALIO
        if (bloqueado) flags = flags or F_BLOQUEO
        if (!ctx.estaBocaAbajo) flags = flags or F_NO_BOCA_ABAJO

        val bytesCodigo = EquipoStore.recortar(codigo).toByteArray(Charsets.UTF_8)

        val cabecera = byteArrayOf(
            VERSION,
            TIPO_ALUMNO,
            sala.toByte(),
            (id shr 8).toByte(), id.toByte(),
            (d.risk.ordinal and 0xFF).toByte(),
            (ctx.movementIndex * 255).toInt().toByte(),
            ctx.batteryLevel.toByte(),
            (d.mode.ordinal and 0xFF).toByte(),
            flags.toByte(),
            bytesCodigo.size.toByte()
        )
        return cabecera + bytesCodigo
    }

    fun salaDe(b: ByteArray): Int? {
        if (b.size < 3 || b[0] != VERSION) return null
        return b[2].toInt() and 0xFF
    }

    fun esAulaAbierta(b: ByteArray): Boolean =
        b.size >= 3 && b[0] == VERSION && b[1] == TIPO_AULA

    fun decodeAlumno(b: ByteArray): StudentStatus? {
        if (b.size < CABECERA_ALUMNO) return null
        if (b[0] != VERSION || b[1] != TIPO_ALUMNO) return null

        val riesgoOrdinal = b[5].toInt() and 0xFF
        val modoOrdinal = b[8].toInt() and 0xFF

        val riesgo = RiskLevel.entries.getOrNull(riesgoOrdinal) ?: return null
        val modo = OperatingMode.entries.getOrNull(modoOrdinal) ?: return null

        val largo = (b[10].toInt() and 0xFF).coerceAtMost(b.size - CABECERA_ALUMNO)
        val codigo = if (largo > 0) String(b, CABECERA_ALUMNO, largo, Charsets.UTF_8) else ""
        val flags = b[9].toInt() and 0xFF

        return StudentStatus(
            id = ((b[3].toInt() and 0xFF) shl 8) or (b[4].toInt() and 0xFF),
            sala = b[2].toInt() and 0xFF,
            codigo = codigo,
            risk = riesgo,
            movement = (b[6].toInt() and 0xFF) / 255f,
            battery = b[7].toInt() and 0xFF,
            mode = modo,
            wifi = (flags and F_WIFI) != 0,
            mobile = (flags and F_DATOS) != 0,
            screenOn = (flags and F_PANTALLA) != 0,
            salioDeLaApp = (flags and F_SALIO) != 0,
            bloqueado = (flags and F_BLOQUEO) != 0,
            noBocaAbajo = (flags and F_NO_BOCA_ABAJO) != 0
        )
    }
}

data class StudentStatus(
    val id: Int, val sala: Int, val codigo: String,
    val risk: RiskLevel, val movement: Float,
    val battery: Int, val mode: OperatingMode,
    val wifi: Boolean, val mobile: Boolean, val screenOn: Boolean,
    val salioDeLaApp: Boolean = false,
    val bloqueado: Boolean = false,
    val noBocaAbajo: Boolean = false,
    val lastSeen: Long = System.currentTimeMillis()
) {
    val etiqueta: String get() = if (codigo.isNotBlank()) codigo else "Equipo $id"
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
    val incidencias: Int,
    val ausenteDesde: Long? = null,
    val enPadron: Boolean = true
) {
    val ausente: Boolean get() = ausenteDesde != null
}
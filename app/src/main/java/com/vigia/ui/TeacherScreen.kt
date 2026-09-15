package com.vigia.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import com.vigia.transport.Bitacora
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vigia.R
import com.vigia.model.OperatingMode
import com.vigia.model.RiskLevel
import com.vigia.transport.AlumnoVigilado
import com.vigia.transport.EstadoAnuncio
import com.vigia.transport.PadronStore
import com.vigia.transport.SesionExamen

private fun colorRiesgo(riesgo: RiskLevel, enPadron: Boolean, ausente: Boolean): Color = when {
    !enPadron -> Paleta.PlomoClaro
    ausente -> Paleta.Plomo
    riesgo == RiskLevel.ALERTA -> Paleta.Intensivo
    riesgo == RiskLevel.ATENCION -> Paleta.Ahorro
    else -> Paleta.Normal
}

private fun textoRiesgo(riesgo: RiskLevel, enPadron: Boolean, ausente: Boolean): String = when {
    !enPadron -> "No estaba al pasar lista"
    ausente -> "Sin señal"
    riesgo == RiskLevel.ALERTA -> "Está usando el equipo"
    riesgo == RiskLevel.ATENCION -> "Se movió"
    else -> "Sin novedad"
}

private fun textoModo(modo: OperatingMode): String = when (modo) {
    OperatingMode.NORMAL -> "vigilancia normal"
    OperatingMode.INTENSIVO -> "vigilancia reforzada"
    OperatingMode.AHORRO -> "consumo reducido"
    OperatingMode.DESCONECTADO -> "sin enlace"
}

@Composable
private fun Contador(puntos: Int) {
    val activo = puntos > 0
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(23.dp))
                .background(if (activo) Paleta.Guinda else Paleta.PlomoFondo),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "$puntos",
                fontSize = 20.sp, fontWeight = FontWeight.Bold,
                color = if (activo) Paleta.Blanco else Paleta.PlomoClaro
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            if (puntos == 1) "incidencia" else "incidencias",
            fontSize = 9.sp, color = Paleta.PlomoClaro, textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun FilaAlumno(a: AlumnoVigilado) {
    val e = a.estado
    val ausente = a.ausenteDesde != null
    val color = colorRiesgo(e.risk, a.enPadron, ausente)

    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(color))
        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    e.etiqueta,
                    fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Paleta.Tinta
                )
                if (!a.enPadron) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "[FUERA DE PADRÓN]",
                        fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Paleta.Guinda
                    )
                }
            }
            Text(
                textoRiesgo(e.risk, a.enPadron, ausente),
                fontSize = 13.sp, color = color, fontWeight = FontWeight.Medium
            )
            Text(
                "${e.mode.name} · ${textoModo(e.mode)}",
                fontSize = 11.sp, color = Paleta.PlomoClaro
            )
            Spacer(Modifier.height(2.dp))
            Text(
                buildString {
                    append("movimiento %.2f  ·  batería %d%%".format(e.movement, e.battery))
                    if (!e.screenOn) append("  ·  pantalla apagada")
                    if (e.salioDeLaApp) append("  ·  FUERA APP")
                    a.ausenteDesde?.let { t ->
                        val segs = (System.currentTimeMillis() - t) / 1000
                        append("  ·  ausente ${segs}s")
                    }
                },
                fontSize = 11.sp, color = Paleta.Plomo
            )
        }

        Spacer(Modifier.width(12.dp))
        Contador(a.incidencias)
    }
}

@Composable
fun TeacherScreen(
    alumnos: List<AlumnoVigilado>,
    bluetoothListo: Boolean,
    permisosOk: Boolean,
    estadoAnuncio: EstadoAnuncio,
    sala: Int,
    /** PIN que el docente eligio para este examen. Se muestra solo en su panel. */
    pinAula: String,
    otrasAulas: Set<Int>,
    onPedirPermisos: () -> Unit,
    onFinalizar: () -> Unit
) {
    val enAlerta = alumnos.count { it.estado.risk == RiskLevel.ALERTA }
    val totalIncidencias = alumnos.sumOf { it.incidencias }
    // Ojo: un panel duplicado de MI PROPIA aula no se puede detectar, porque
    // aulasAbiertas() devuelve codigos de aula y mi propia baliza tambien esta ahi.
    // Lo que si se ve es que haya otro examen anunciandose cerca, que es cuando
    // conviene revisar que los codigos no se pisen.
    val hayOtroPanel = otrasAulas.isNotEmpty()

    var mostrarCierre by rememberSaveable { mutableStateOf(false) }
    val contextoCierre = LocalContext.current

    if (mostrarCierre) {
        val (equipos, incidencias, eventos) = SesionExamen.resumen()
        AlertDialog(
            onDismissRequest = { mostrarCierre = false },
            title = { Text("Finalizar el examen del aula $sala") },
            text = {
                Column {
                    Text(
                        "$equipos equipos · $incidencias incidencias · $eventos eventos",
                        fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Paleta.Tinta
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Al finalizar se deja de anunciar el aula y se borra la sesión. " +
                            "Los alumnos saldrán solos cuando dejen de ver la baliza.",
                        fontSize = 13.sp, color = Paleta.Plomo
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = { exportarBitacora(contextoCierre, sala) },
                        enabled = eventos > 0,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Paleta.Guinda, contentColor = Paleta.Blanco
                        )
                    ) { Text("Exportar la bitácora") }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Expórtala antes de finalizar: después se borra.",
                        fontSize = 12.sp, color = Paleta.Guinda
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { mostrarCierre = false; onFinalizar() }) {
                    Text("Finalizar y cerrar aula", color = Paleta.Guinda,
                        fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { mostrarCierre = false }) {
                    Text("Seguir vigilando", color = Paleta.Plomo)
                }
            },
            containerColor = Paleta.Blanco
        )
    }

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {

        Row(
            Modifier.fillMaxWidth().background(Paleta.Guinda)
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Aula $sala", color = Paleta.Blanco,
                    fontSize = 15.sp, fontWeight = FontWeight.Bold
                )
                if (pinAula.isNotBlank()) {
                    Text("PIN de desbloqueo · $pinAula",
                        color = Paleta.SobreGuinda, fontSize = 11.sp)
                }
            }
            // Del panel solo se sale finalizando: un examen esta abierto o
            // cerrado, no hay un estado intermedio de "panel minimizado".
            TextButton(onClick = { mostrarCierre = true }) {
                Text("Finalizar", color = Paleta.Blanco,
                    fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        Row(
            Modifier.fillMaxWidth().background(Paleta.Guinda)
                .padding(horizontal = 20.dp).padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    "${alumnos.size}",
                    fontSize = 48.sp, fontWeight = FontWeight.Bold, color = Paleta.Blanco
                )
                Text(
                    if (alumnos.size == 1) "alumno conectado" else "alumnos conectados",
                    fontSize = 14.sp, color = Paleta.SobreGuinda
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "$totalIncidencias",
                    fontSize = 48.sp, fontWeight = FontWeight.Bold,
                    color = if (totalIncidencias > 0) Color(0xFFFFCDD2) else Paleta.SobreGuinda
                )
                Text("incidencias registradas", fontSize = 14.sp, color = Paleta.SobreGuinda)
                if (enAlerta > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "$enAlerta usando el equipo ahora",
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFCDD2)
                    )
                }
            }
        }

        val lineas by Bitacora.lineas.collectAsState()
        var pestana by rememberSaveable { mutableStateOf(0) }
        val contexto = LocalContext.current

        TabRow(
            selectedTabIndex = pestana,
            containerColor = Paleta.Blanco,
            contentColor = Paleta.Guinda
        ) {
            Tab(pestana == 0, { pestana = 0 }) {
                Text("Alumnos", Modifier.padding(12.dp), fontSize = 14.sp)
            }
            Tab(pestana == 1, { pestana = 1 }) {
                Text("Bitácora (${lineas.size})", Modifier.padding(12.dp), fontSize = 14.sp)
            }
        }

        if (pestana == 1) Column(Modifier.weight(1f)) {
            PanelBitacora(lineas) { exportarBitacora(contexto, sala) }
        } else Column(Modifier.weight(1f).padding(horizontal = 22.dp, vertical = 14.dp)) {
            if (hayOtroPanel) {
                Mensaje(
                    "Hay otro examen cerca",
                    "Se detectaron otros paneles activos: " +
                        otrasAulas.sorted().joinToString(", ") { "aula $it" } + ". " +
                        "Verifica que tu código sea el $sala antes de continuar.",
                    accion = null, onAccion = null
                )
                Spacer(Modifier.height(12.dp))
            }

            BarraPadron(alumnos)
            Spacer(Modifier.height(10.dp))

            when {
                !permisosOk -> Mensaje(
                    "Falta el permiso de Bluetooth",
                    "Sin él la app no puede anunciar el aula ni escuchar a los alumnos.",
                    accion = "Conceder permiso", onAccion = onPedirPermisos
                )
                !bluetoothListo -> Mensaje(
                    "Bluetooth apagado",
                    "Enciende el Bluetooth para anunciar el aula y recibir a los alumnos."
                )
                estadoAnuncio !is EstadoAnuncio.Anunciando -> Mensaje(
                    "El aula no se está anunciando",
                    "Motivo: ${estadoAnuncio.mensaje}. Sin la señal del aula los alumnos " +
                            "no pueden unirse."
                )
                alumnos.isEmpty() -> Mensaje(
                    "Aula $sala abierta y anunciándose",
                    "Dicta el código $sala al salón. Los alumnos aparecerán aquí conforme se unan."
                )
                else -> LazyColumn {
                    items(alumnos, key = { it.estado.id }) { a ->
                        FilaAlumno(a)
                        HorizontalDivider(color = Color(0xFFE8EAED))
                    }
                }
            }
        }
    }
}

@Composable
private fun Mensaje(
    titulo: String,
    detalle: String,
    accion: String? = null,
    onAccion: (() -> Unit)? = null
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(Paleta.PlomoFondo).padding(18.dp)
    ) {
        Text(titulo, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Paleta.Tinta)
        Spacer(Modifier.height(6.dp))
        Text(detalle, fontSize = 13.sp, color = Paleta.Plomo)
        if (accion != null && onAccion != null) {
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onAccion,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Paleta.Guinda, contentColor = Paleta.Blanco
                )
            ) { Text(accion) }
        }
    }
}

/**
 * Pasar lista y cerrar el padron.
 *
 * Mientras el padron esta abierto se acepta a cualquiera. Al cerrarlo queda fijado
 * que equipos estaban presentes, y cualquier equipo nuevo que aparezca despues sale
 * marcado. Es lo que acota —no elimina— el problema del segundo celular.
 */
@Composable
private fun BarraPadron(alumnos: List<AlumnoVigilado>) {
    var cerrado by remember { mutableStateOf(PadronStore.cerrado) }
    var presentes by rememberSaveable { mutableStateOf("") }
    var sinEquipo by rememberSaveable { mutableStateOf("") }

    val conectados = alumnos.count { !it.ausente }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(Paleta.PlomoFondo).padding(14.dp)
    ) {
        if (!cerrado) {

            Text("Pasar lista", fontSize = 15.sp,
                fontWeight = FontWeight.Bold, color = Paleta.Tinta)
            Spacer(Modifier.height(10.dp))

            // Lo que cuenta la app, separado de lo que cuenta el docente.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$conectados",
                    fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Paleta.Guinda
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        if (conectados == 1) "alumno conectado" else "alumnos conectados",
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Paleta.Tinta
                    )
                    Text("lo cuenta la app, en vivo",
                        fontSize = 11.sp, color = Paleta.PlomoClaro)
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("Cuéntalos tú en el salón:", fontSize = 12.sp, color = Paleta.Plomo)
            Spacer(Modifier.height(8.dp))

            Row {
                OutlinedTextField(
                    value = presentes,
                    onValueChange = { presentes = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("Personas") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(10.dp))
                OutlinedTextField(
                    value = sinEquipo,
                    onValueChange = { sinEquipo = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("Sin celular") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }

            // La resta es el punto: quien esta en el salon y no aparece en la app.
            val p = presentes.toIntOrNull()
            if (p != null) {
                val faltan = p - conectados - (sinEquipo.toIntOrNull() ?: 0)
                Spacer(Modifier.height(8.dp))
                Text(
                    when {
                        faltan > 0 -> "⚠ $faltan sin explicar: están en el salón, " +
                            "no tienen el celular apagado y no aparecen en la lista"
                        faltan < 0 -> "Hay más equipos conectados que personas contadas. " +
                            "Revisa el conteo o el código de aula."
                        else -> "Todo cuadra: $p personas = $conectados conectados + " +
                            "${sinEquipo.toIntOrNull() ?: 0} sin celular"
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (faltan == 0) Paleta.Normal else Paleta.Intensivo
                )
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    PadronStore.cerrar(
                        alumnos.map { it.estado.id }.toSet(),
                        presentes.toIntOrNull() ?: conectados,
                        sinEquipo.toIntOrNull() ?: 0
                    )
                    cerrado = true
                },
                enabled = alumnos.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Paleta.Guinda, contentColor = Paleta.Blanco
                )
            ) { Text("Cerrar lista") }

        } else {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Lista cerrada · ${PadronStore.tamano} equipos registrados",
                        fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Paleta.Tinta
                    )
                    if (PadronStore.sinEquipo > 0) {
                        Text(
                            "${PadronStore.sinEquipo} sin celular — ubicados adelante",
                            fontSize = 12.sp, color = Paleta.Guinda,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    val faltan = PadronStore.presentes - PadronStore.tamano -
                        PadronStore.sinEquipo
                    if (faltan > 0) {
                        Text("⚠ $faltan personas sin explicar",
                            fontSize = 12.sp, color = Paleta.Intensivo)
                    }
                }
                TextButton(onClick = { PadronStore.reabrir(); cerrado = false }) {
                    Text("Reabrir", fontSize = 12.sp, color = Paleta.Plomo)
                }
            }
        }
    }
}

/** El registro del examen, con boton para llevarselo. */
@Composable
private fun PanelBitacora(lineas: List<Bitacora.Linea>, onExportar: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onExportar, enabled = lineas.isNotEmpty()) {
                Text("Exportar", color = Paleta.Guinda, fontSize = 13.sp)
            }
        }

        if (lineas.isEmpty()) {
            Column(Modifier.padding(horizontal = 22.dp)) {
                Mensaje(
                    "Todavía no hay eventos",
                    "Aquí se registra todo lo que pasa durante el examen: quién se une, " +
                        "quién entra en alerta y quién deja de emitir."
                )
            }
        } else {
            LazyColumn(Modifier.padding(horizontal = 22.dp)) {
                items(lineas.reversed()) { l ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                        Text(
                            Bitacora.hora(l.hora), fontSize = 12.sp,
                            color = Paleta.PlomoClaro, modifier = Modifier.width(66.dp)
                        )
                        Text(
                            l.codigo, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            color = Paleta.Tinta, modifier = Modifier.width(92.dp)
                        )
                        Text(l.evento.texto, fontSize = 12.sp, color = Paleta.Plomo)
                    }
                    HorizontalDivider(color = Color(0xFFE8EAED))
                }
            }
        }
    }
}

/** Comparte la bitacora como texto plano. No hace falta FileProvider. */
private fun exportarBitacora(contexto: android.content.Context, sala: Int) {
    val envio = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_SUBJECT, "Bitácora VIGÍA — aula $sala")
        putExtra(android.content.Intent.EXTRA_TEXT, Bitacora.csv())
    }
    contexto.startActivity(
        android.content.Intent.createChooser(envio, "Exportar bitácora")
    )
}

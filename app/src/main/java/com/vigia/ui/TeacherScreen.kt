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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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

private fun colorRiesgo(riesgo: RiskLevel, enPadron: Boolean, ausente: Boolean): Color = when {
    !enPadron -> Paleta.PlomoClaro
    ausente -> Paleta.Plomo
    riesgo == RiskLevel.ALERTA -> Paleta.Intensivo
    riesgo == RiskLevel.ATENCION -> Paleta.Ahorro
    else -> Paleta.Normal
}

private fun textoRiesgo(riesgo: RiskLevel, enPadron: Boolean, ausente: Boolean): String = when {
    !enPadron -> "NO REGISTRADO EN PADRÓN"
    ausente -> "Sin señal (ausente)"
    riesgo == RiskLevel.ALERTA -> "Usando el equipo"
    riesgo == RiskLevel.ATENCION -> "Requiere atención"
    else -> "Sin novedad"
}

private fun textoModo(modo: OperatingMode): String = when (modo) {
    OperatingMode.NORMAL -> "vigilancia estándar"
    OperatingMode.INTENSIVO -> "vigilancia reinforced"
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
                    if (!e.screenOn) append("  ·  pantalla off")
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
    otrasAulas: Set<Int>,
    onPedirPermisos: () -> Unit,
    onVolver: () -> Unit
) {
    val enAlerta = alumnos.count { it.estado.risk == RiskLevel.ALERTA }
    val totalIncidencias = alumnos.sumOf { it.incidencias }
    val estaDuplicado = otrasAulas.contains(sala)

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {

        Row(
            Modifier.fillMaxWidth().background(Paleta.Guinda)
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.escudo_uni),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "VIGÍA UNI", color = Paleta.Blanco,
                        fontSize = 13.sp, fontWeight = FontWeight.Bold
                    )
                    Text("panel del docente", color = Paleta.SobreGuinda, fontSize = 11.sp)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("aula $sala", color = Paleta.SobreGuinda, fontSize = 12.sp)
                TextButton(onClick = onVolver) {
                    Text("Salir", color = Color(0xCCE8D5DA), fontSize = 12.sp)
                }
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
                    if (alumnos.size == 1) "equipo conectado" else "equipos conectados",
                    fontSize = 14.sp, color = Paleta.SobreGuinda
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "$totalIncidencias",
                    fontSize = 48.sp, fontWeight = FontWeight.Bold,
                    color = if (totalIncidencias > 0) Color(0xFFFFCDD2) else Paleta.SobreGuinda
                )
                Text("incidencias", fontSize = 14.sp, color = Paleta.SobreGuinda)
                if (enAlerta > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "$enAlerta usando ahora",
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFCDD2)
                    )
                }
            }
        }

        Column(Modifier.weight(1f).padding(horizontal = 22.dp, vertical = 14.dp)) {
            if (estaDuplicado) {
                Mensaje(
                    "¡Atención: Aula Duplicada!",
                    "Existe otro panel transmitiendo en el aula $sala cerca. " +
                            "Verifica el número de aula antes de continuar.",
                    accion = null, onAccion = null
                )
                Spacer(Modifier.height(12.dp))
            }

            if (PadronStore.cerrado) {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .background(Paleta.PlomoFondo).padding(10.dp)
                ) {
                    Text(
                        "Padrón cerrado · ${PadronStore.presentes} presentes / ${PadronStore.tamano} registrados",
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Paleta.Tinta
                    )
                }
                Spacer(Modifier.height(10.dp))
            }

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
                    "Aula $sala abierta",
                    "Dicta el código a los alumnos. Aparecerán aquí conforme se unan."
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
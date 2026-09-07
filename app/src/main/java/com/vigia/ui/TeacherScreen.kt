package com.vigia.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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

private fun colorRiesgo(riesgo: RiskLevel): Color = when (riesgo) {
    RiskLevel.NORMAL -> Paleta.Normal
    RiskLevel.ATENCION -> Paleta.Ahorro
    RiskLevel.ALERTA -> Paleta.Intensivo
}

private fun textoRiesgo(riesgo: RiskLevel): String = when (riesgo) {
    RiskLevel.NORMAL -> "Sin novedad"
    RiskLevel.ATENCION -> "Requiere atención"
    RiskLevel.ALERTA -> "Usando el equipo"
}

private fun textoModo(modo: OperatingMode): String = when (modo) {
    OperatingMode.NORMAL -> "vigilancia estándar"
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
    val color = colorRiesgo(e.risk)

    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(color))
        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            Text(
                e.etiqueta,
                fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Paleta.Tinta
            )
            Text(
                textoRiesgo(e.risk),
                fontSize = 13.sp, color = color, fontWeight = FontWeight.Medium
            )
            Text(
                "${e.mode.name} · ${textoModo(e.mode)}",
                fontSize = 11.sp, color = Paleta.PlomoClaro
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "movimiento %.2f  ·  batería %d%%".format(e.movement, e.battery),
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
    sala: Int,
    aulaAnunciada: Boolean,
    onVolver: () -> Unit
) {
    val enAlerta = alumnos.count { it.estado.risk == RiskLevel.ALERTA }
    val totalIncidencias = alumnos.sumOf { it.incidencias }

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
            when {
                !bluetoothListo -> Mensaje(
                    "Bluetooth apagado",
                    "Enciende el Bluetooth para anunciar el aula y recibir a los alumnos."
                )
                !aulaAnunciada -> Mensaje(
                    "El aula no se está anunciando",
                    "Sin la señal del aula los alumnos no pueden unirse. Sal y vuelve a " +
                        "abrir el panel, y concede el permiso de Bluetooth si te lo pide."
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
private fun Mensaje(titulo: String, detalle: String) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(Paleta.PlomoFondo).padding(18.dp)
    ) {
        Text(titulo, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Paleta.Tinta)
        Spacer(Modifier.height(6.dp))
        Text(detalle, fontSize = 13.sp, color = Paleta.Plomo)
    }
}

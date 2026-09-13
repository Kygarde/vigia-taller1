package com.vigia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vigia.model.AlumnoVigilado
import com.vigia.transport.Bitacora
import com.vigia.transport.PadronStore

@Composable
fun TeacherScreen(
    sala: Int,
    alumnos: List<AlumnoVigilado>,
    otrasAulas: List<Int>,
    onCerrar: () -> Unit
) {
    var pestana by rememberSaveable { mutableStateOf(0) }
    val lineas by Bitacora.lineas.collectAsState()
    val contexto = LocalContext.current

    val onExportar = {
        val envio = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_SUBJECT, "Bitácora VIGÍA — aula $sala")
            putExtra(android.content.Intent.EXTRA_TEXT, Bitacora.csv())
        }
        contexto.startActivity(
            android.content.Intent.createChooser(envio, "Exportar bitácora")
        )
    }

    Column(Modifier.fillMaxSize().background(Paleta.Blanco)) {
        Row(
            Modifier.fillMaxWidth().background(Paleta.Guinda).padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Aula $sala", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Paleta.Blanco)
            TextButton(onClick = onCerrar) {
                Text("Cerrar", color = Paleta.Blanco)
            }
        }

        if (otrasAulas.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().background(Paleta.Intensivo).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "⚠  Se detectó otro panel activo (aula ${otrasAulas.joinToString()})",
                    fontSize = 12.sp, color = Paleta.Blanco, fontWeight = FontWeight.Bold
                )
            }
        }

        TabRow(
            selectedTabIndex = pestana,
            containerColor = Paleta.Blanco,
            contentColor = Paleta.Guinda
        ) {
            Tab(selected = pestana == 0, onClick = { pestana = 0 }) {
                Text("Alumnos", Modifier.padding(12.dp))
            }
            Tab(selected = pestana == 1, onClick = { pestana = 1 }) {
                Text("Bitácora (${lineas.size})", Modifier.padding(12.dp))
            }
        }

        if (pestana == 0) {
            BarraPadron(alumnos)
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 22.dp)) {
                items(alumnos) { alumno ->
                    FilaAlumno(alumno)
                    HorizontalDivider(color = Color(0xFFE8EAED))
                }
            }
        } else {
            PanelBitacora(lineas = lineas, onExportar = onExportar)
        }
    }
}

@Composable
private fun BarraPadron(alumnos: List<AlumnoVigilado>) {
    var cerrado by remember { mutableStateOf(PadronStore.cerrado) }
    var presentes by rememberSaveable { mutableStateOf("") }
    var sinEquipo by rememberSaveable { mutableStateOf("") }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(10.dp)).background(Paleta.PlomoFondo).padding(14.dp)
    ) {
        if (!cerrado) {
            Text("Pasar lista", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Paleta.Tinta)
            Spacer(Modifier.height(4.dp))
            Text("Pide a todos que enciendan Bluetooth y se unan. Luego cierra la lista.", fontSize = 12.sp, color = Paleta.Plomo)
            Spacer(Modifier.height(10.dp))

            Row {
                OutlinedTextField(
                    value = presentes,
                    onValueChange = { presentes = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("Presentes") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(10.dp))
                OutlinedTextField(
                    value = sinEquipo,
                    onValueChange = { sinEquipo = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("Sin equipo") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(6.dp))
            Text("Conectados ahora: ${alumnos.size}", fontSize = 12.sp, color = Paleta.Plomo)

            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    PadronStore.cerrar(
                        alumnos.map { it.estado.id }.toSet(),
                        presentes.toIntOrNull() ?: alumnos.size,
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
                    Text("Lista cerrada · ${PadronStore.tamano} equipos", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Paleta.Tinta)

                    if (PadronStore.sinEquipo > 0) {
                        Text("${PadronStore.sinEquipo} sin equipo — ubicados adelante", fontSize = 12.sp, color = Paleta.Guinda, fontWeight = FontWeight.Bold)
                    }

                    val faltan = PadronStore.presentes - PadronStore.tamano - PadronStore.sinEquipo
                    if (faltan > 0) {
                        Text("⚠ $faltan presentes sin explicar", fontSize = 12.sp, color = Paleta.Intensivo)
                    }
                }
                TextButton(onClick = { PadronStore.reabrir(); cerrado = false }) {
                    Text("Reabrir", fontSize = 12.sp, color = Paleta.Plomo)
                }
            }
        }
    }
}

@Composable
private fun FilaAlumno(a: AlumnoVigilado) {
    val e = a.estado
    val color = when {
        a.ausente -> Paleta.PlomoClaro
        !a.enPadron -> Paleta.Intensivo
        else -> colorRiesgo(e.risk)
    }

    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(color))
        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            Text(
                e.etiqueta,
                fontSize = 17.sp, fontWeight = FontWeight.Bold,
                color = if (a.ausente) Paleta.Plomo else Paleta.Tinta
            )

            Text(
                when {
                    a.ausente -> "sin señal desde ${Bitacora.hora(a.ausenteDesde!!)}"
                    e.bloqueado -> "BLOQUEADO · salió de la aplicación"
                    e.salioDeLaApp -> "fuera de la aplicación"
                    else -> textoRiesgo(e.risk)
                },
                fontSize = 13.sp, color = color, fontWeight = FontWeight.Medium
            )

            if (!a.enPadron) {
                Text(
                    "equipo NO registrado en el padrón",
                    fontSize = 11.sp, color = Paleta.Intensivo, fontWeight = FontWeight.Bold
                )
            }

            if (!a.ausente) {
                Text(
                    "${e.mode.name} · ${textoModo(e.mode)}",
                    fontSize = 11.sp, color = Paleta.PlomoClaro
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    buildString {
                        append("movimiento %.2f".format(e.movement))
                        append("  ·  batería ${e.battery}%")
                        if (!e.screenOn) append("  ·  pantalla apagada")
                    },
                    fontSize = 11.sp, color = Paleta.Plomo
                )
            }
        }

        Spacer(Modifier.width(12.dp))
        Contador(a.incidencias)
    }
}

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
            Text(
                "Sin eventos todavía\nAquí se registra todo lo que pasa durante el examen.",
                modifier = Modifier.padding(22.dp), color = Paleta.Plomo, fontSize = 13.sp
            )
        } else {
            LazyColumn(Modifier.padding(horizontal = 22.dp)) {
                items(lineas.reversed()) { l ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                        Text(Bitacora.hora(l.hora), fontSize = 12.sp, color = Paleta.PlomoClaro, modifier = Modifier.width(66.dp))
                        Text(l.codigo, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Paleta.Tinta, modifier = Modifier.width(90.dp))
                        Text(l.evento.texto, fontSize = 12.sp, color = Paleta.Plomo)
                    }
                    HorizontalDivider(color = Color(0xFFE8EAED))
                }
            }
        }
    }
}

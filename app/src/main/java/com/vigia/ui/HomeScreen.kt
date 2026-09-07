package com.vigia.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vigia.R
import com.vigia.transport.EquipoStore

private enum class Rol { NINGUNO, ALUMNO, DOCENTE }

@Composable
private fun Campo(
    valor: String,
    onCambio: (String) -> Unit,
    etiqueta: String,
    numerico: Boolean = false,
    error: Boolean = false
) {
    OutlinedTextField(
        value = valor,
        onValueChange = onCambio,
        singleLine = true,
        isError = error,
        label = { Text(etiqueta) },
        keyboardOptions = if (numerico)
            KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Paleta.Tinta,
            unfocusedTextColor = Paleta.Tinta,
            focusedBorderColor = Paleta.Guinda,
            unfocusedBorderColor = Paleta.PlomoClaro,
            focusedLabelColor = Paleta.Guinda,
            unfocusedLabelColor = Paleta.Plomo,
            cursorColor = Paleta.Guinda
        )
    )
}

@Composable
private fun Aviso(texto: String, ok: Boolean) {
    Text(
        texto,
        fontSize = 13.sp,
        color = if (ok) Paleta.Guinda else Paleta.Plomo,
        fontWeight = if (ok) FontWeight.Bold else FontWeight.Normal,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun BotonPrincipal(texto: String, habilitado: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = habilitado,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Paleta.Guinda,
            contentColor = Paleta.Blanco,
            disabledContainerColor = Paleta.PlomoFondo,
            disabledContentColor = Paleta.PlomoClaro
        )
    ) { Text(texto, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun BotonSecundario(texto: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, SolidColor(Paleta.Guinda)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Paleta.Guinda)
    ) { Text(texto, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun HomeScreen(
    nombreGuardado: String,
    salaGuardada: Int,
    aulasAbiertas: Set<Int>,
    bluetoothListo: Boolean,
    onEntrarComoAlumno: (String, Int) -> Unit,
    onEntrarComoDocente: (Int) -> Unit
) {
    // rememberSaveable: lo que el usuario escribio no se pierde al girar el equipo.
    var rolNombre by rememberSaveable { mutableStateOf(Rol.NINGUNO.name) }
    val rol = Rol.valueOf(rolNombre)
    var nombre by rememberSaveable { mutableStateOf(nombreGuardado) }
    var sala by rememberSaveable { mutableStateOf(salaGuardada.toString()) }

    val valorSala = sala.toIntOrNull()
    val salaValida = valorSala != null &&
        valorSala in EquipoStore.SALA_MINIMA..EquipoStore.SALA_MAXIMA

    // Un aula solo existe mientras el equipo del docente la esta anunciando.
    val aulaExiste = salaValida && valorSala in aulasAbiertas

    Column(
        Modifier.fillMaxSize().background(Paleta.Blanco)
            .statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(if (rol == Rol.NINGUNO) 72.dp else 48.dp))

        Image(
            painter = painterResource(R.drawable.escudo_uni),
            contentDescription = "Escudo de la Universidad Nacional de Ingeniería",
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(if (rol == Rol.NINGUNO) 128.dp else 88.dp)
        )

        Spacer(Modifier.height(18.dp))
        Text(
            "VIGÍA UNI",
            fontSize = if (rol == Rol.NINGUNO) 38.sp else 28.sp,
            fontWeight = FontWeight.Bold,
            color = Paleta.Guinda
        )
        Text(
            "Supervisión adaptativa de exámenes",
            fontSize = 13.sp, color = Paleta.Plomo, textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(48.dp))

        when (rol) {
            Rol.NINGUNO -> {
                BotonPrincipal("Soy alumno") { rolNombre = Rol.ALUMNO.name }
                Spacer(Modifier.height(14.dp))
                BotonSecundario("Soy docente") { rolNombre = Rol.DOCENTE.name }
            }

            Rol.ALUMNO -> {
                Campo(nombre, { nombre = EquipoStore.recortar(it) }, "Nombre")
                Spacer(Modifier.height(12.dp))
                Campo(
                    sala, { sala = it.filter { c -> c.isDigit() }.take(3) },
                    "Código de aula", numerico = true,
                    error = salaValida && !aulaExiste
                )

                Spacer(Modifier.height(10.dp))
                Aviso(
                    when {
                        !bluetoothListo -> "Enciende el Bluetooth"
                        !salaValida -> "Escribe el código que dictó el docente"
                        aulaExiste -> "Aula $valorSala disponible"
                        aulasAbiertas.isEmpty() -> "Buscando aulas abiertas…"
                        else -> "El aula $valorSala no está abierta"
                    },
                    ok = aulaExiste
                )

                Spacer(Modifier.height(18.dp))
                BotonPrincipal("Unirme", aulaExiste && nombre.isNotBlank()) {
                    valorSala?.let { onEntrarComoAlumno(nombre.trim(), it) }
                }
            }

            Rol.DOCENTE -> {
                Campo(
                    sala, { sala = it.filter { c -> c.isDigit() }.take(3) },
                    "Código de aula", numerico = true,
                    error = sala.isNotEmpty() && !salaValida
                )
                if (!bluetoothListo) {
                    Spacer(Modifier.height(10.dp))
                    Aviso("Enciende el Bluetooth", ok = false)
                }
                Spacer(Modifier.height(24.dp))
                BotonPrincipal("Abrir panel", salaValida) {
                    valorSala?.let(onEntrarComoDocente)
                }
            }
        }

        if (rol != Rol.NINGUNO) {
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = { rolNombre = Rol.NINGUNO.name }) {
                Text("Atrás", fontSize = 14.sp, color = Paleta.Plomo)
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}

package com.vigia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vigia.decision.AdaptationRules
import com.vigia.model.*

// ---------- Traducciones de los valores crudos a lenguaje legible ----------

private fun textoMovimiento(indice: Float): String = when {
    indice >= AdaptationRules.MOVEMENT_ALERTA -> "Equipo siendo manipulado"
    indice >= AdaptationRules.MOVEMENT_ATENCION -> "Movimiento leve detectado"
    else -> "Equipo en reposo"
}

/** Que debe hacer el alumno segun lo que se esta detectando. */
private fun indicacion(riesgo: RiskLevel): String = when (riesgo) {
    RiskLevel.NORMAL -> "Todo en orden. Manten el equipo sobre la mesa."
    RiskLevel.ATENCION -> "Se registro movimiento. Evita manipular el equipo."
    RiskLevel.ALERTA -> "Movimiento sostenido registrado. Deja el equipo sobre la mesa."
}

private fun textoRiesgo(riesgo: RiskLevel): String = when (riesgo) {
    RiskLevel.NORMAL -> "Sin novedad"
    RiskLevel.ATENCION -> "Requiere atencion"
    RiskLevel.ALERTA -> "Movimiento sostenido"
}

private fun colorRiesgo(riesgo: RiskLevel): Color = when (riesgo) {
    RiskLevel.NORMAL -> Color(0xFF2E7D32)
    RiskLevel.ATENCION -> Color(0xFFEF6C00)
    RiskLevel.ALERTA -> Color(0xFFC62828)
}

private fun textoModo(modo: OperatingMode): String = when (modo) {
    OperatingMode.NORMAL -> "Vigilancia estandar"
    OperatingMode.INTENSIVO -> "Vigilancia reforzada"
    OperatingMode.AHORRO -> "Consumo reducido"
    OperatingMode.DESCONECTADO -> "Sin enlace, guardando local"
}

private fun onOff(activo: Boolean): String = if (activo) "ON" else "OFF"

private fun colorOnOff(activo: Boolean): Color =
    if (activo) Color(0xFF2E7D32) else Color(0xFF9AA0A6)

@Composable
private fun Fila(etiqueta: String, valor: String, colorValor: Color = Color.Unspecified) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(etiqueta, fontSize = 15.sp, color = Color(0xFF5F6368))
        Text(valor, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = colorValor)
    }
}

@Composable
fun StudentScreen(
    ctx: ContextSnapshot,
    decision: AdaptationDecision,
    samplingLabel: String
) {
    val color = when (decision.mode) {
        OperatingMode.NORMAL -> Color(0xFF2E7D32)
        OperatingMode.INTENSIVO -> Color(0xFFC62828)
        OperatingMode.AHORRO -> Color(0xFFEF6C00)
        OperatingMode.DESCONECTADO -> Color(0xFF455A64)
    }

    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
    ) {

        // ----- Franja de contexto: por que esta corriendo esta app -----
        Row(
            Modifier.fillMaxWidth().background(Color(0xFF1A1C1E))
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "EXAMEN EN CURSO", color = Color.White,
                fontSize = 13.sp, fontWeight = FontWeight.Bold
            )
            Text("supervision activa", color = Color(0xFFB0B4B8), fontSize = 12.sp)
        }

        // ----- Bloque de modo: grande, a color, visible desde lejos -----
        Column(
            Modifier.fillMaxWidth().background(color).padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("MODO DE OPERACION", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
            Text(
                decision.mode.name, color = Color.White,
                fontSize = 44.sp, fontWeight = FontWeight.Bold
            )
            Text(textoModo(decision.mode), color = Color.White.copy(alpha = 0.9f), fontSize = 16.sp)
            Spacer(Modifier.height(10.dp))
            Text(
                decision.reason, color = Color.White, fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Column(Modifier.padding(horizontal = 22.dp, vertical = 16.dp)) {

            // ----- Lectura principal + que debe hacer el alumno -----
            Text("QUE ESTA PASANDO", fontSize = 12.sp, color = Color(0xFF5F6368))
            Spacer(Modifier.height(6.dp))
            Text(
                textoMovimiento(ctx.movementIndex),
                fontSize = 23.sp, fontWeight = FontWeight.Bold,
                color = colorRiesgo(decision.risk)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                indicacion(decision.risk),
                fontSize = 14.sp, color = Color(0xFF3C4043)
            )
            Spacer(Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { ctx.movementIndex },
                modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)),
                color = colorRiesgo(decision.risk)
            )
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text("reposo", fontSize = 11.sp, color = Color(0xFF9AA0A6))
                Text(
                    "umbral de alerta: %.2f".format(AdaptationRules.MOVEMENT_ALERTA),
                    fontSize = 11.sp, color = Color(0xFF9AA0A6)
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 14.dp))

            // ----- Decision tomada -----
            Text("DECISION", fontSize = 12.sp, color = Color(0xFF5F6368))
            Spacer(Modifier.height(4.dp))
            Fila(
                "Nivel de riesgo",
                "${decision.risk.name} · ${textoRiesgo(decision.risk)}",
                colorRiesgo(decision.risk)
            )
            Fila("Frecuencia de muestreo", samplingLabel)

            HorizontalDivider(Modifier.padding(vertical = 14.dp))

            // ----- Contexto crudo -----
            Text("CONTEXTO DETECTADO", fontSize = 12.sp, color = Color(0xFF5F6368))
            Spacer(Modifier.height(4.dp))
            Fila("Indice de movimiento", "%.3f".format(ctx.movementIndex))
            Fila(
                "Bateria",
                "${ctx.batteryLevel}%" + if (ctx.batterySaver) "  ·  ahorro activo" else ""
            )
            Fila("WiFi", onOff(ctx.wifiEnabled), colorOnOff(ctx.wifiEnabled))
            Fila("Datos moviles", onOff(ctx.mobileDataEnabled), colorOnOff(ctx.mobileDataEnabled))
            Fila("Pantalla", if (ctx.screenOn) "encendida" else "apagada")

            Spacer(Modifier.height(18.dp))

            Spacer(Modifier.height(24.dp))
        }
    }
}

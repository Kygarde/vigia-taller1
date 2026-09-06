package com.vigia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vigia.model.*

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

    Column(Modifier.fillMaxSize()) {

        // Bloque de modo: grande, a color, visible desde lejos
        Column(
            Modifier.fillMaxWidth().background(color).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("MODO", color = Color.White, fontSize = 14.sp)
            Text(decision.mode.name, color = Color.White,
                fontSize = 46.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(decision.reason, color = Color.White, fontSize = 15.sp)
        }

        Column(Modifier.padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text("Riesgo", fontWeight = FontWeight.Bold)
                Text(decision.risk.name)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text("Frecuencia activa", fontWeight = FontWeight.Bold)
                Text(samplingLabel)
            }

            Divider(Modifier.padding(vertical = 18.dp))

            Text("Contexto detectado", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = ctx.movementIndex,
                modifier = Modifier.fillMaxWidth().height(10.dp)
            )
            Text("Movimiento: %.3f".format(ctx.movementIndex))
            Text("Batería: ${ctx.batteryLevel}%  ·  Ahorro: ${ctx.batterySaver}")
            Text("WiFi: ${ctx.wifiEnabled}  ·  Datos: ${ctx.mobileDataEnabled}")
            Text("Pantalla: ${if (ctx.screenOn) "encendida" else "apagada"}")
        }
    }
}
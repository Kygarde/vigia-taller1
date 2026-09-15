# VIGÍA UNI

Sistema Android que **detecta el contexto del propio dispositivo y adapta
automáticamente su comportamiento de sensado**, sin que el usuario configure nada.

Supervisa exámenes presenciales sin invadir: no accede a cámara, micrófono,
contenido de pantalla ni ubicación GPS, y no consume los datos del alumno. El
equipo de cada alumno observa cuánto se mueve, cuánta batería le queda, por dónde
está conectado y si la pantalla está encendida; con eso decide un nivel de riesgo y
un modo de operación, cambia la frecuencia con la que muestrea sus sensores, y
anuncia su estado al panel del docente por Bluetooth. Sin servidor y sin red.

**Taller 1 — Desarrollo Adaptativo e Integración de Sistemas · UNI 2026-2**
Versión **2.0**

---

## Índice

1. [Dos aplicaciones, un solo código](#dos-aplicaciones-un-solo-código)
2. [Capturas](#capturas)
3. [Qué hace](#qué-hace)
4. [Requisitos](#requisitos)
5. [Instalación](#instalación)
6. [El ciclo del examen](#el-ciclo-del-examen)
7. [Cómo provocar cada adaptación](#cómo-provocar-cada-adaptación)
8. [Bloqueo por salir de la aplicación](#bloqueo-por-salir-de-la-aplicación)
9. [Padrón, matrícula y bitácora](#padrón-matrícula-y-bitácora)
10. [Transporte BLE](#transporte-ble)
11. [Permisos y privacidad](#permisos-y-privacidad)
12. [Arquitectura](#arquitectura)
13. [Ajustar los umbrales](#ajustar-los-umbrales)
14. [Problemas frecuentes](#problemas-frecuentes)
15. [Integrantes](#integrantes)

---

## Dos aplicaciones, un solo código

El rol **no se elige en una pantalla**: lo fija la variante instalada. El proyecto
declara dos *product flavors* de Gradle sobre la misma base de código:

| Variante | Nombre en el equipo | `applicationId` | Qué hace |
|---|---|---|---|
| `alumno` | **VIGÍA Alumno** | `com.vigia.alumno` | Mide y emite |
| `docente` | **VIGÍA Docente** | `com.vigia.docente` | Escucha, consolida el aula y registra |

```kotlin
flavorDimensions += "rol"
productFlavors {
    create("alumno")  { applicationIdSuffix = ".alumno";  buildConfigField("boolean", "ES_DOCENTE", "false") }
    create("docente") { applicationIdSuffix = ".docente"; buildConfigField("boolean", "ES_DOCENTE", "true")  }
}
```

**Por qué así y no con un botón.** Un alumno no puede entrar al panel del docente
porque **ese camino no existe en su APK**: no es una validación que se pueda saltar,
es código que no está compilado. Como los `applicationId` son distintos, las dos
variantes conviven en un mismo equipo sin pisarse — útil para probar con un solo
celular.

---

## Capturas

> Algunas capturas se tomaron en la versión 1.x y no muestran los campos añadidos en
> la 2.0 (PIN del docente, pasar lista, pestaña de bitácora). El flujo que ilustran
> sigue siendo el mismo.

### El recorrido, de principio a fin

| 1. El docente abre el aula | 2. El panel escucha | 3. El alumno se une |
|---|---|---|
| ![Crear aula](docs/img/docente-crear-aula.png) | ![Panel del docente](docs/img/panel-docente.png) | ![Aula no abierta](docs/img/alumno-aula-no-abierta.png) |
| Código de aula + PIN de desbloqueo | Los alumnos aparecen al unirse | **Unirme** sigue bloqueado hasta escuchar el aula |

| 4. Monitoreo en curso |
|---|
| ![Alumno transmitiendo](docs/img/alumno-transmitiendo.png) |
| Modo, contexto y `Reportando al docente: ON` |

La tercera captura muestra la validación funcionando: el código está escrito pero el
equipo todavía no ha escuchado la baliza de esa aula, así que el botón permanece
deshabilitado. **Un aula solo existe mientras el docente la está anunciando.**

### Los tres modos

| Modo NORMAL | Modo INTENSIVO | Modo AHORRO |
|---|---|---|
| ![Modo NORMAL](docs/img/modo-normal.png) | ![Modo INTENSIVO](docs/img/modo-intensivo.png) | ![Modo AHORRO](docs/img/modo-ahorro.png) |
| Equipo en reposo, 1 Hz | Movimiento sostenido, 5 Hz | Ahorro de batería, 0.2 Hz |

### El tiempo sostenido en acción

<img src="docs/img/riesgo-atencion.png" width="320">

El índice de movimiento ya cruzó el umbral de alerta (0.413 sobre 0.25), pero el
riesgo sigue en `ATENCION` y el modo en `NORMAL`: el movimiento todavía no lleva
`SUSTAINED_MS` sostenido. El sistema distingue un golpe puntual de una manipulación
real.

---

## Qué hace

### Comportamiento adaptativo

| | Condición detectada | Adaptación automática |
|---|---|---|
| **A1** | Movimiento sostenido con la pantalla encendida | Modo `INTENSIVO`: sube el muestreo a 5 Hz y la emisión BLE a 200 ms |
| **A2** | Batería < 15 % o ahorro de energía del sistema | Modo `AHORRO`: baja el muestreo a 0.2 Hz y la emisión a 5 s |

Hay una **jerarquía explícita: la batería gana sobre el riesgo.** Si las dos
condiciones se cumplen a la vez, el modo resultante es `AHORRO`, porque un equipo
apagado no vigila nada.

Los cuatro modos son `NORMAL`, `INTENSIVO`, `AHORRO` y `DESCONECTADO`. La pantalla
muestra en todo momento el modo activo, el motivo del último cambio y la frecuencia
de muestreo vigente.

Nada de esto lo activa el usuario: la condición la detecta el software, la decisión
se produce sola y el efecto es observable en pantalla.

### La regla de decisión

El riesgo asciende a `ALERTA` solo si se cumplen las tres condiciones a la vez:

```
índice de movimiento > 0.25   Y   sostenido 1.2 s   Y   pantalla encendida
```

**La adaptación no se dispara al cruzar un umbral, sino al sostenerlo.** Un golpe
puntual produce un pico alto pero brevísimo; manipular un celular produce movimiento
prolongado. Escribir junto al equipo no eleva el riesgo; levantarlo y usarlo, sí.

### El pipeline

```
CONTEXTO      →   PROCESAMIENTO   →   DECISIÓN        →   ADAPTACIÓN
sensing/          processing/         decision/           adaptation/ + ui/
4 providers       ventana + EMA       riesgo + modo       frecuencia + pantalla
    ▲                                                     transport/ → anuncio BLE
    │                                                         │
    └──────────── realimentación: la salida reconfigura la captura
```

La realimentación es lo que hace al sistema **adaptativo y no meramente
configurable**: la salida de la última etapa modifica la frecuencia de captura de la
primera, en tiempo de ejecución.

---

## Requisitos

- **Android 8.0 (API 26) o superior**
- **Celular físico.** El emulador no sirve: no tiene acelerómetro real ni reporta el
  modo de ahorro de energía del sistema.
- **Bluetooth** en los dos equipos.
- **Dos equipos** como mínimo: uno con VIGÍA Alumno y otro con VIGÍA Docente.
- Android Studio con JDK 11 o superior

---

## Instalación

```bash
git clone https://github.com/Kygarde/vigia-taller1.git
cd vigia-taller1
```

Abrir la carpeta en Android Studio y esperar el *Gradle Sync*.

> ⚠️ **La ruta del proyecto no puede tener tildes, ñ ni caracteres especiales.**
> El plugin de Android para Gradle falla con
> `Your project path contains non-ASCII characters`.
> Por ejemplo, `.../Integración de Sistemas/...` no funciona; hay que renombrar la
> carpeta a `Integracion`.

### Elegir la variante

En Android Studio: **View → Tool Windows → Build Variants**, y elegir
`alumnoDebug` o `docenteDebug` antes de pulsar **Run ▶**.

Desde la línea de comandos:

```bash
./gradlew installAlumnoDebug     # instala VIGÍA Alumno
./gradlew installDocenteDebug    # instala VIGÍA Docente
```

Los dos APK pueden convivir en el mismo equipo.

### Preparar el celular

1. Ajustes → Acerca del teléfono → tocar 7 veces **Número de compilación**
   (en Xiaomi/HyperOS: **Versión del sistema operativo**)
2. Opciones de desarrollador → activar **Depuración USB** y, en Xiaomi,
   también **Instalar vía USB**
3. Conectar por cable y aceptar el diálogo de depuración
4. Seleccionar el dispositivo en Android Studio y pulsar **Run ▶**

### Instalar en un segundo equipo

**Build → Build Bundle(s) / APK(s) → Build APK(s)**. Los archivos quedan en
`app/build/outputs/apk/alumno/debug/` y `app/build/outputs/apk/docente/debug/`, y se
pueden compartir e instalar directamente, sin Android Studio.

> ⚠️ **Todos los equipos deben tener la misma versión de la app.** El paquete BLE va
> por la **versión 6** y los anuncios de versiones anteriores se descartan. Un APK
> viejo en uno de los equipos hace que el otro no lo vea, sin ningún mensaje de error.

---

## El ciclo del examen

```
   DOCENTE                                ALUMNO
   ───────                                ──────
1. Abrir aula (código + PIN)
        │  baliza BLE: aula N abierta
        ▼
2. Dictar el código al salón   ────────►  3. Escribir código UNI + aula
                                                   │
                                          4. Unirme (solo si escucha el aula)
                                                   │
5. Pasar lista ◄──────── anuncios cada 1 s ────────┘
   (cierra el padrón)
        │
6. Vigilar: incidencias, bitácora, cotejo con la matrícula
        │
7. Finalizar examen
        │  baliza de cierre
        ▼
8. Exportar la bitácora        ────────►  Los alumnos salen en ~8 s
```

**1. El docente abre el aula.** Escribe un código de aula (0–255, por ejemplo `204`)
y un **PIN de cuatro dígitos** que él mismo define para ese examen. Pulsa
**Abrir panel**; su equipo empieza a anunciar que esa aula está abierta.

Si ese código ya está en el aire, la app lo rechaza: dos paneles con la misma aula
mezclarían a los alumnos de los dos salones en ambas listas.

**2. Dicta el código de aula** al salón. **El PIN no se dicta.**

**3. Cada alumno se une.** Escribe su **código UNI** (8 dígitos y una letra, por
ejemplo `20192589B`) y el código de aula, y pulsa **Unirme**.

El botón permanece bloqueado hasta que la app **escucha** el aula. Un aula solo
existe mientras el equipo del docente la está anunciando: no hay servidor donde
consultarla.

**4. El panel lista a los alumnos** conforme se unen, ordenados por nivel de riesgo.

**5. El docente pasa lista** cuando todos están conectados: cuenta las personas
presentes en el salón y cuántas no traen equipo, y **cierra el padrón**. A partir de
ahí, un equipo que aparezca y no estuviera en el padrón se marca como
*"No estaba al pasar lista"*.

**6. Finalizar.** El panel emite una baliza de cierre durante 12 s, deja de anunciar
el aula y borra la sesión. Los alumnos ven *"El examen terminó"* y salen solos en
unos 8 segundos, sin esperar a que caduque la baliza.

### Qué muestra el panel del docente

<img src="docs/img/panel-docente.png" width="330">

| Elemento | Significado |
|---|---|
| Alumnos conectados | Cuántos están transmitiendo ahora |
| Incidencias registradas | Total de veces que algún alumno entró en `ALERTA` |
| Estado por alumno | `Sin novedad` · `Se movió` · `Está usando el equipo` · `Sin señal` · `No estaba al pasar lista` |
| Contador por alumno | Sus incidencias durante la sesión |
| Pestaña **Bitácora** | Cada evento con su hora exacta |

Una **incidencia** es una *entrada* en `ALERTA`, no cada anuncio recibido en ese
estado. En modo `INTENSIVO` el equipo anuncia cada 200 ms: contando anuncios, agitar
el equipo diez segundos sumaría cincuenta puntos. Contando transiciones, es **1**.

El conteo lo lleva el equipo del docente, no viaja en el paquete: así no depende de
lo que el alumno decida anunciar. Se mantiene aunque el alumno salga del alcance y
vuelva, y se borra al finalizar el examen.

---

## Cómo provocar cada adaptación

Esta es la sección importante: cualquier persona debe poder reproducir las dos
adaptaciones sin ayuda del equipo.

### A1 — Modo INTENSIVO

1. Entrar como alumno y dejar el equipo quieto sobre la mesa unos 10 segundos.
   Debe mostrar modo **`NORMAL`**, movimiento por debajo de 0.10 y frecuencia **1 Hz**.
2. **Tomar el equipo y moverlo durante unos 3 segundos**, con la pantalla encendida.
3. El riesgo pasa a `ALERTA` y el modo cambia solo a **`INTENSIVO`**. La frecuencia
   sube a **5 Hz** y el motivo en pantalla dice
   *"Movimiento sostenido: aumentando frecuencia"*.
4. Dejarlo quieto otra vez. Vuelve a `NORMAL` después de unos **5 segundos**.

> **Por qué tarda en volver:** hay una histéresis de 5 s que bloquea cambios de modo
> consecutivos. Sin ella, el modo oscilaría sin parar cuando el contexto queda justo
> en el umbral.

> **La pantalla debe estar encendida.** Agitar el equipo con la pantalla apagada no
> eleva el riesgo: es ruido de alguien caminando con el celular en el bolsillo, no un
> indicio útil.

### A2 — Modo AHORRO

1. **Desconectar el equipo del cable USB.**
2. Activar el **ahorro de batería** del sistema desde la barra de notificaciones.
3. El modo cambia a **`AHORRO`** y la frecuencia baja a **0.2 Hz**. El motivo muestra
   el nivel real de batería.
4. Desactivar el ahorro para volver a `NORMAL`.

> ⚠️ **Tiene que estar desconectado del cargador.** Android desactiva el ahorro de
> batería mientras el equipo se está cargando, así que enchufado esta adaptación no
> se dispara y parece que la app falla.

También se activa solo cuando la batería baja del **15 %**.

### Sensado en segundo plano

Con la app abierta aparece una notificación permanente **"VIGÍA activo"**. Es el
`MonitoringService`, un servicio en primer plano; sin él Android suspende la app y el
sensado se detiene.

---

## Bloqueo por salir de la aplicación

Si el alumno se va a otra aplicación durante el examen, su equipo queda **bloqueado**:
la pantalla se cubre con el aviso *"SALISTE DEL EXAMEN"* y solo se libera escribiendo
el **PIN del docente**.

El bloqueo es **persistente**: sobrevive a cerrar la app, a reiniciarla e incluso a
reiniciar el equipo. Se guarda junto a la huella del PIN del aula en la que ocurrió,
y `MainActivity` lo comprueba **antes** de decidir qué pantalla mostrar, así que no
hay forma de esquivarlo reabriendo la aplicación.

### Apagar la pantalla NO es salir

Esta distinción costó trabajo y es la que más se pregunta:

```kotlin
// MainViewModel
if (!enPrimerPlano && powerManager.isInteractive) {
    // la app perdió el foco PERO la pantalla sigue encendida → se fue a otra app
    bloquear()
}
```

Android entrega `onStop()` en los dos casos —irse a WhatsApp y pulsar el botón de
encendido— así que `onStop()` por sí solo no distingue nada. La discriminación real
es `PowerManager.isInteractive`: si la pantalla quedó **apagada**, el equipo no se
está usando y no hay falta. Se espera además **700 ms** antes de decidir, para no
confundir una transición momentánea del sistema con una salida deliberada.

El estado viaja al panel en dos banderas del paquete BLE (`SALIO` y `BLOQUEO`), así
que el docente ve quién salió y quién sigue bloqueado sin que el alumno se lo diga.

### El PIN lo define el docente en cada examen

No hay PIN fijo en el código. El docente escribe cuatro dígitos al abrir el panel, y
lo que viaja en la baliza **no es el PIN sino una huella de 16 bits** amarrada al
número de aula:

```kotlin
fun huellaPin(pin: String, sala: Int): Int {
    var h = 7
    for (c in pin) h = (h * 31 + c.code) and 0xFFFF
    return (h * 31 + sala) and 0xFFFF
}
```

Un alumno que capture el anuncio no puede leer los cuatro dígitos: tendría que
probarlos todos. No es criptografía —son 10 000 combinaciones— pero cierra la lectura
casual, que es el ataque real: alguien mirando por encima del hombro. Y como la
huella depende del aula, un PIN capturado en un salón no sirve en otro.

---

## Padrón, matrícula y bitácora

### Padrón — quién estaba al empezar

El docente pulsa **Pasar lista** cuando todos se conectaron: la app le muestra cuántos
equipos ve, él cuenta las personas del salón y cuántas no trajeron equipo, y cierra el
padrón. Desde ese momento, un equipo nuevo que aparezca se marca como
*"No estaba al pasar lista"* y queda registrado.

Es lo que cierra el hueco de un alumno que se conecta tarde desde fuera del salón.

### Matrícula — quién debía estar

El docente puede pegar la lista de matriculados del curso (un código por línea, o
separados por comas). La app los coteja con los códigos conectados y devuelve tres
grupos:

| Grupo | Significado |
|---|---|
| **Presentes** | Matriculados que están conectados |
| **No conectados** | Matriculados que no aparecen: ausentes o sin equipo |
| **No matriculados** | Códigos conectados que no están en la lista |

> No hay integración con UniVirtual. Eso exigiría API institucional, credenciales y
> red — exactamente lo que este proyecto decidió no usar. La lista se pega a mano.

### Bitácora — qué pasó y cuándo

Cada evento queda registrado con su hora exacta:

| Evento | Cuándo se registra |
|---|---|
| `UNIDO` | El alumno se unió al aula |
| `ALERTA` | Entró en `ALERTA` (movimiento sostenido) |
| `SALIO_APP` | Se fue a otra aplicación |
| `SIN_SENAL` | Dejó de emitir |
| `REGRESO` | Volvió a emitir |
| `NO_REGISTRADO` | Apareció sin estar en el padrón |

Se exporta como CSV (`hora,codigo,evento`) desde el diálogo de finalización.
**Hay que exportarla antes de finalizar: después se borra.**

Sin bitácora, el sistema resolvería una discusión igual que la vigilancia a ojo — la
palabra de uno contra la del otro. Con ella, hay una hora exacta que mirar.

---

## Transporte BLE

### Anuncios, no conexiones

No hay emparejamiento ni conexión. El equipo del alumno **anuncia** su estado y el del
docente **escucha**: es *advertising* BLE, sin conexión.

Android admite alrededor de **siete conexiones GATT simultáneas**, así que conectarse
a cada alumno no escalaría a un salón completo. El advertising sí: nadie se conecta,
todos anuncian, y el receptor escucha a cuantos alcance.

Consecuencias prácticas:

- No hace falta emparejar los equipos, ni WiFi, ni datos, ni internet
- Alcance de unos 10 a 30 metros
- Si un alumno apaga el equipo, deja de aparecer a los 15 segundos

También se descartó una arquitectura cliente-servidor: la red WiFi del aula no está
bajo control del equipo, y un sistema de supervisión que depende de la red deja de
funcionar justo cuando más se necesita.

### Los tres tipos de anuncio — formato v6

**Baliza del aula** — 5 bytes. La emite el equipo del docente mientras el panel está
abierto.

```
[0]      versión del formato (6)
[1]      tipo = AULA_ABIERTA
[2]      código de aula
[3] [4]  huella de 16 bits del PIN
```

El anuncio se reintenta cuando la app vuelve al primer plano y cuando se conceden los
permisos: Android puede detener el advertising por su cuenta —ahorro de energía,
restricciones del fabricante— y sin ese reintento el aula desaparecería en silencio.

**Estado del alumno** — 11 bytes de cabecera más el código UNI:

```
[0]      versión del formato (6)
[1]      tipo = ESTADO_ALUMNO
[2]      código de aula
[3] [4]  identificador del equipo
[5]      nivel de riesgo
[6]      índice de movimiento (0..255)
[7]      batería (%)
[8]      modo de operación
[9]      banderas: wifi · datos · pantalla · salió de la app · bloqueado
[10]     largo del código en bytes
[11..]   código UNI en UTF-8
```

**Cierre del aula** — 3 bytes. La emite el panel al finalizar el examen, durante 12 s.

```
[0] versión (6)   [1] tipo = CIERRE   [2] código de aula
```

Sin este paquete, el alumno solo se enteraría cuando la baliza caduca: quince segundos
de caducidad más la confirmación, casi medio minuto mirando una pantalla que no cambia.
Con el aviso explícito **sale en unos 8 segundos**.

El anuncio BLE admite **31 bytes en total**. El código UNI son 9 (8 dígitos y una
letra), así que el paquete del alumno queda en 20 bytes — holgado. Esa restricción de
31 bytes es la razón de diseñar un protocolo binario propio en vez de usar JSON.

### El intervalo de emisión también se adapta

Lo fija el modo activo, igual que la frecuencia del sensor:

| Modo | Anuncia cada |
|---|---|
| `NORMAL` | 1 s |
| `INTENSIVO` | 200 ms |
| `AHORRO` | 5 s |

Además, si el paquete no cambió no se reanuncia: el anuncio anterior sigue vigente y
repetirlo solo gasta batería.

### Código de aula

Cada anuncio lleva un código de aula (0–255, un byte) y el panel descarta los que no
coinciden. Es lo que evita que dos salones vecinos usando la app se mezclen en la misma
lista. Lo define el docente al abrir el panel; el valor sugerido es **101**.

> El código de aula es un separador operativo, no un control de seguridad. Lo que sí
> protege el acceso es el **PIN por examen**, que no se dicta y viaja como huella.

### Sesión del examen

El estado del aula (alumnos vistos, incidencias, padrón) vive en `SesionExamen`,
**fuera del flujo de escaneo**, porque ese flujo se recrea cada vez que la app vuelve
al primer plano. Si el estado viviera dentro, minimizar el panel un segundo borraría
la lista de alumnos.

La sesión se limpia sola al cambiar de aula o al cumplir **4 horas** sin movimiento,
para que un panel reabierto al día siguiente no arrastre los datos del examen anterior.

---

## Permisos y privacidad

| Permiso | Para qué | Cuándo se solicita |
|---|---|---|
| `FOREGROUND_SERVICE` · `FOREGROUND_SERVICE_DATA_SYNC` | Mantener el sensado con la app minimizada | Automático |
| `POST_NOTIFICATIONS` | Notificación del servicio de monitoreo | Al abrir la app |
| `ACCESS_NETWORK_STATE` | Detectar wifi y datos móviles | Automático |
| `BLUETOOTH_ADVERTISE` · `BLUETOOTH_SCAN` · `BLUETOOTH_CONNECT` | Anunciar el estado y escuchar a los demás | Al abrir la app |
| `ACCESS_FINE_LOCATION` | Solo en Android 11 o anterior, que exigía ubicación para escanear BLE | Solo en esos equipos |

En Android 12 en adelante el permiso de escaneo lleva la bandera `neverForLocation`:
la app declara explícitamente que no usa el Bluetooth para deducir dónde está el
alumno.

Si los permisos se conceden después de abrir la app, el escaneo y el anuncio se
reinician solos.

### Qué NO hace

VIGÍA **no** captura pantalla, audio, ubicación GPS ni contenido de aplicaciones, y
**no** consume los datos móviles del alumno. Procesa únicamente metadatos derivados de
sensores del propio dispositivo, y muestra en todo momento su modo de operación y el
motivo de cada cambio.

Lo único que sale del equipo son los bytes descritos arriba: un identificador derivado
del dispositivo (no de la persona), el código UNI que el propio alumno escribió, el
código de aula, el nivel de riesgo, el índice de movimiento, la batería, el modo y las
cinco banderas de estado.

El alumno decide cuándo entra: la app **no anuncia nada** hasta que pulsa *Unirme*.
Una vez dentro del examen no hay botón de salida — el examen lo cierra el docente, o
el alumno queda bloqueado si se va por su cuenta. El equipo del docente solo escucha,
nunca anuncia su estado.

Los datos viven en el equipo del docente **durante la sesión** y se borran al
finalizar el examen.

---

## Arquitectura

Arquitectura **MVVM en capas**, con un único `MainViewModel` como orquestador. La capa
de transporte es paralela al pipeline y no lo condiciona.

```
sensing/      →   processing/   →   decision/     →   adaptation/ + ui/
CONTEXTO          PROCESAMIENTO     DECISIÓN          ADAPTACIÓN
4 providers       ventana + EMA     riesgo + modo     frecuencia + pantalla
```

| Etapa | Archivos |
|---|---|
| Captura del contexto | `sensing/AccelerometerProvider.kt`, `BatteryProvider.kt`, `ConnectivityProvider.kt`, `ScreenStateProvider.kt` |
| Procesamiento | `processing/ContextManager.kt`, `processing/SignalProcessor.kt` |
| Decisión | `decision/AdaptationEngine.kt`, `decision/AdaptationRules.kt`, `decision/RiskEvaluator.kt` |
| Adaptación | `adaptation/SamplingPolicy.kt`, `ui/StudentScreen.kt` |
| Transporte | `transport/PacketCodec.kt`, `BleAdvertiser.kt`, `BleScanner.kt`, `EquipoStore.kt` |
| Ciclo del examen | `transport/SesionExamen.kt`, `PadronStore.kt`, `MatriculaStore.kt`, `Bitacora.kt`, `BloqueoStore.kt` |
| Interfaz | `ui/HomeScreen.kt`, `ui/StudentScreen.kt`, `ui/TeacherScreen.kt`, `ui/Paleta.kt` |
| Orquestación | `MainViewModel.kt`, `MainActivity.kt` |
| Modelo de datos | `model/Model.kt` |
| Robustez | `MonitoringService.kt` |

**Tecnologías:** Kotlin 2.2.10 · Jetpack Compose (BOM 2026.02.01, Material 3) ·
Coroutines/Flow · SensorManager · BLE Advertising · Gradle 9.3.2 con product flavors

### Notas de diseño

**Cada provider expone un `Flow` y nada más.** Ninguno sabe qué es un modo de
operación ni un nivel de riesgo: solo importan `android.*` y `kotlinx.coroutines.*`.
Esa es la separación que el taller califica.

**La adaptación real son dos líneas.** Android no permite cambiar la frecuencia de un
sensor ya registrado, así que hay que des-registrar el listener y volver a registrarlo:

```kotlin
fun changeDelay(nuevo: Int) {
    if (running) { stop(); start() }   // esto ES la adaptación en caliente
}
```

**El estado de navegación vive en el `MainViewModel`.** Al girar el equipo Android
destruye y recrea la Activity; si la pantalla activa viviera en un `remember`, el
usuario saldría del examen al rotar el celular.

**El bloqueo se comprueba en `MainActivity`, antes de navegar.** Si se comprobara
dentro de la pantalla del alumno, cerrar y reabrir la app dejaría al alumno en la
pantalla de inicio con el bloqueo activo pero invisible.

**El modo `DESCONECTADO` no se activa al fallar el anuncio.** Tiene prioridad sobre
todos los demás modos, así que un fallo puntual del Bluetooth dejaría la app clavada
ahí y ocultaría A1 y A2 por completo. El estado de la transmisión se muestra aparte.

**La pantalla del alumno mantiene el equipo despierto** (`FLAG_KEEP_SCREEN_ON`):
Android limita el escaneo BLE con la pantalla apagada y el panel dejaría de ver a ese
alumno.

---

## Ajustar los umbrales

Todos los valores viven en un solo archivo:
`app/src/main/java/com/vigia/decision/AdaptationRules.kt`

| Constante | Valor | Qué controla |
|---|---|---|
| `WINDOW_SIZE` | 4 | Muestras de la ventana deslizante (~0.8 s) |
| `EMA_ALPHA` | 0.6 | Peso de la muestra nueva: más alto, más reactivo |
| `NORMALIZATION_CEILING` | 2.0 | Aceleración (m/s²) que mapea a movimiento = 1.0 |
| `MOVEMENT_ALERTA` | 0.25 | Umbral para considerar que el equipo se está manipulando |
| `MOVEMENT_ATENCION` | 0.12 | Umbral del nivel intermedio |
| `SUSTAINED_MS` | 1200 | Cuánto debe sostenerse el movimiento para llegar a `ALERTA` |
| `BATTERY_LOW` | 15 | Porcentaje de batería que dispara `AHORRO` |
| `HYSTERESIS_MS` | 5000 | Tiempo mínimo entre cambios de modo |

Están calibrados para supervisión de exámenes: el equipo pasa casi todo el tiempo
apoyado en la mesa y hay que detectar que alguien lo tome. **Escribir junto al equipo
no debe elevar el riesgo a `ALERTA`.**

Si se dispara estando quieto, subir `MOVEMENT_ALERTA`. Si no se dispara al moverlo,
bajar `NORMALIZATION_CEILING`.

---

## Problemas frecuentes

**El alumno no ve el aula.**
El docente debe abrir el panel **primero**: un aula solo existe mientras se está
anunciando. Revisar además que ambos equipos tengan la misma versión del APK
(protocolo v6), el Bluetooth encendido y los permisos concedidos.

**Instalé la app pero no aparece la pantalla del docente.**
Está instalada la variante `alumno`. El rol lo fija el APK: hay que compilar
`docenteDebug`. En el equipo se llaman **VIGÍA Alumno** y **VIGÍA Docente**.

**El panel dice "El aula no se está anunciando".**
El mensaje incluye el motivo. Si dice *"este equipo no puede emitir por Bluetooth"*,
ese celular no tiene modo periférico BLE: intercambiar los roles y usar el otro como
docente.

**"El aula ya está abierta en otro equipo".**
Otro panel está anunciando ese mismo código. Usar otro número, o cerrar el otro panel
y esperar unos segundos a que caduque su baliza.

**El equipo de un alumno quedó bloqueado y no recuerdo el PIN.**
El PIN se muestra en la cabecera del panel del docente, junto al número de aula. Si se
cerró el panel, hay que reabrirlo con **el mismo código de aula y el mismo PIN**: la
huella tiene que coincidir.

**Se bloqueó solo al apagar la pantalla.**
No debería: apagar la pantalla no cuenta como salir. Si ocurre, el equipo está
entregando `onStop()` con la pantalla aún interactiva — revisar
`MainViewModel.appVisible()`.

**El alumno estaba conectado y de pronto desapareció del panel.**
Revisar la batería del equipo del docente. Por debajo del 15 % muchos fabricantes
restringen el Bluetooth y cortan el anuncio del aula. Llevar los equipos cargados el
día de la sustentación.

**Se concedieron los permisos pero sigue sin funcionar.**
Salir de la app y volver a entrar. Al regresar a primer plano se rehacen el escaneo y
el anuncio.

**El modo `AHORRO` no se activa.**
El equipo está conectado al cargador. Android desactiva el ahorro de batería mientras
carga.

**Gradle falla con `non-ASCII characters`.**
La ruta del proyecto tiene tildes o ñ. Renombrar las carpetas.

**Gradle falla con `Product Flavor contains custom resource values, but the feature is disabled`.**
Falta `resValues = true` en el bloque `buildFeatures` de `app/build.gradle.kts`.

---

## Integrantes

| Integrante | Responsabilidad principal |
|---|---|
| **Cesar Alonso Dionicio Achachagua** | `sensing/` y transporte BLE · bloqueo por salir de la app · ciclo del examen · `MonitoringService` |
| **Ernesto Ramon Salazar Ramos** | `processing/` y `decision/` · bitácora de eventos · padrón del examen |
| **Giancarlo Aguirre Alvarado** | `adaptation/` y `ui/` · política de muestreo · cotejo con la matrícula |

**Docente:** Ramos Montes Carlos Nelson

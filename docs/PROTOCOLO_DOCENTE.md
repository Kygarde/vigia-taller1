# Protocolo de Aplicación para el Docente — VIGÍA

Guía operativa estándar para la administración, supervisión y control de evaluaciones presenciales mediante la plataforma **VIGÍA**.

---

## Fase 1: Preparación Previa al Examen

1. **Apertura de la aplicación**
   * Iniciar la aplicación **VIGÍA** en el dispositivo del docente[cite: 1].
   * Verificar que la conectividad **Bluetooth** esté encendida[cite: 1].
   * Seleccionar el modo **Docente**[cite: 1].

2. **Carga del padrón de matriculados**
   * Copiar la relación oficial de códigos de alumnos (desde el sistema académico institucional o UniVirtual)[cite: 1].
   * Pegar la lista en el cuadro de texto de la aplicación[cite: 1]. La app procesará automáticamente los códigos válidos (separados por líneas, espacios o comas)[cite: 1].

3. **Configuración y anuncio del aula**
   * Ingresar un identificador de sala (código numérico de 0 a 255)[cite: 1].
   * Iniciar la emisión de la baliza del aula y **dictar en voz alta** el código a los estudiantes presentes[cite: 1].

---

## Fase 2: Registro y Cotejo de Asistencia

4. **Ingreso de los estudiantes**
   * Indicar a los alumnos que enciendan su Bluetooth, abran VIGÍA en modo Alumno e ingresen el código de aula dictado junto con su código universitario[cite: 1].

5. **Conteo manual y verificación física**
   * Realizar el conteo físico de los alumnos en el salón[cite: 1].
   * Identificar y anotar cuántos alumnos están físicamente presentes y cuántos declaran no portar celular o tenerlo descargado[cite: 1].

6. **Reubicación preventiva de excepciones**
   * Los alumnos que **no dispongan de smartphone o lo tengan apagado** deben ser reubicados en las **primeras filas** del aula para supervisión visual directa[cite: 1].

7. **Cierre de lista y cotejo automático**
   * Presionar el botón **Cerrar lista** en el panel docente[cite: 1].
   * Validar las tres categorías del sistema[cite: 1]:
     * **Presentes:** Alumnos matriculados conectados exitosamente[cite: 1].
     * **No conectados:** Matriculados ausentes o sin dispositivo registrado[cite: 1].
     * **No matriculados:** Equipos conectados cuyos códigos no están en el padrón[cite: 1].
   * Asegurar que no existan discrepancias o "presentes sin explicar" antes de continuar[cite: 1].

---

## Fase 3: Durante el Examen

8. **Inicio de la evaluación**
   * Repartir las hojas de examen e iniciar el cronómetro de la prueba[cite: 1].

9. **Monitoreo y vigilancia activa**
   Durante toda la sesión, observar el panel docente prestando atención a tres eventos críticos[cite: 1]:
   * **Alertas contextuales:** Movimientos anómalos persistentes o estados de riesgo alto reportados por la heurística local[cite: 1].
   * **Salidas o bloqueos:** Notificación de alumnos que cambiaron de app, bloquearon la pantalla o salieron de primer plano[cite: 1].
   * **Ausencias / Desconexión:** Equipos que dejan de emitir señal BLE o se alejan del rango del aula[cite: 1].

---

## Fase 4: Finalización

10. **Cierre y respaldo de evidencias**
    * Al concluir el tiempo del examen y recolectar las pruebas, presionar **Exportar**[cite: 1].
    * Respaldar la bitácora generada con el registro de eventos e incidencias para fines de auditoría académica[cite: 1].
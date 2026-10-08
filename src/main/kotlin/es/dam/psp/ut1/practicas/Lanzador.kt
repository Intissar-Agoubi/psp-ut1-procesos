package es.dam.psp.ut1.practicas

import es.dam.psp.ut1.Jvm
import java.io.File
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

/**
 * Lanzador.kt · Práctica 3 · Lanzador de órdenes.
 *
 * Apuntes UT-1, apartados 5.1 (ProcessBuilder, Jvm.comandoShell), 5.3 (leer la salida),
 * 6.1 (waitFor con límite, destroy) y 6.2 (no bloquearse leyendo).
 * Cómo ejecutarlo: triángulo ▶ junto a main, sin argumentos; escribe las órdenes en la consola
 * de IntelliJ (p. ej. "echo hola", "dir noexiste" o "ping -n 11 127.0.0.1").
 *
 * Bucle: pide una orden al usuario, la ejecuta como proceso hijo y muestra
 *   - su salida (stdout y stderr juntas), con cada línea numerada (3.a),
 *   - su código de salida y el tiempo que ha tardado en milisegundos (3.b).
 * Si la orden tarda más de TIMEOUT_S segundos, se termina el proceso y se avisa (3.c).
 * Cada ejecución se añade a historial.txt con el formato:  orden;codigo;milisegundos (3.d)
 * La orden "salir" termina el programa.
 */

const val TIMEOUT_S = 5L   // segundos que se deja trabajar a cada orden

/** Fichero donde se registra cada ejecución (en el directorio de trabajo: la raíz del proyecto). */
private val HISTORIAL = File("historial.txt")

/** Codificación de la salida de cmd en Windows (página de códigos 850) o UTF-8 en Linux/macOS. */
private val CODIFICACION: Charset = if (Jvm.esWindows) Charset.forName("IBM850") else Charsets.UTF_8

fun main() {
    println("Lanzador PSP · sistema: ${System.getProperty("os.name")} · escribe 'salir' para terminar")
    while (true) {
        print("> ")
        val orden = readlnOrNull()?.trim() ?: break   // null = fin de la entrada (Ctrl+D)
        if (orden == "salir") break
        if (orden.isEmpty()) continue

        // 3.c (preparación): la salida del hijo (stdout + stderr) va a un fichero temporal.
        // Así el padre no se bloquea leyendo y puede controlar el tiempo máximo.
        val salida = File.createTempFile("lanzador", ".txt")
        val pb = ProcessBuilder(Jvm.comandoShell(orden))
            .redirectErrorStream(true)
            .redirectOutput(salida)

        // 3.b: arrancar el hijo y medir el tiempo desde start()
        val inicio = System.nanoTime()
        val proceso = pb.start()

        // 3.c: esperar como máximo TIMEOUT_S segundos; si no termina, se termina con sus descendientes
        val codigo = if (proceso.waitFor(TIMEOUT_S, TimeUnit.SECONDS)) {
            proceso.exitValue()
        } else {
            terminar(proceso)
            println("[tiempo agotado: más de $TIMEOUT_S s · proceso terminado]")
            -1
        }
        val ms = (System.nanoTime() - inicio) / 1_000_000

        // 3.a: mostrar la salida con cada línea numerada y borrar el fichero temporal
        salida.readLines(CODIFICACION).forEachIndexed { i, linea -> println("%3d | %s".format(i + 1, linea)) }
        salida.delete()

        // 3.b: código de salida y tiempo transcurrido
        println("[código $codigo · $ms ms]")

        // 3.d: registrar la ejecución en el historial
        HISTORIAL.appendText("$orden;$codigo;$ms\n")
    }
    println("Hasta luego")
}

/** Termina [proceso] y todos sus descendientes: primero con destroy() y, si sigue vivo, a la fuerza. */
private fun terminar(proceso: Process) {
    proceso.descendants().forEach { it.destroy() }   // antes que el padre: después ya no serían accesibles
    proceso.destroy()
    if (!proceso.waitFor(1, TimeUnit.SECONDS)) {
        proceso.destroyForcibly().waitFor()
    }
}
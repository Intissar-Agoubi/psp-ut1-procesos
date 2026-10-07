package es.dam.psp.ut1.practicas

import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * BuscarProcesos.kt · Práctica 2.c · busca procesos del sistema por su comando.
 *
 * Basado en ejemplos/E6_MiniPs.kt (apuntes UT-1, apartado 6.4).
 * Cómo ejecutarlo: triángulo ▶ junto a main, con el texto a buscar en
 * Run > Edit Configurations > Program arguments (p. ej. java).
 * Muestra PID, PPID, usuario, hora de inicio y tiempo total de CPU de cada proceso
 * cuyo comando contiene el texto (sin distinguir mayúsculas y minúsculas).
 * Solo se ven los datos que el sistema operativo permite leer a tu usuario.
 */

/** Formato de la hora de inicio, en la zona horaria del equipo. */
private val FORMATO_HORA: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.systemDefault())

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Uso: BuscarProcesos <texto a buscar>")
        return
    }
    val buscar = args[0]

    println("Mi PID: ${ProcessHandle.current().pid()}")
    println("Procesos cuyo comando contiene \"$buscar\":")
    println("%-8s %-8s %-20s %-20s %-10s %s".format("PID", "PPID", "USUARIO", "INICIO", "CPU", "COMANDO"))

    val encontrados = ProcessHandle.allProcesses()
        .filter { p -> p.info().command().map { it.contains(buscar, ignoreCase = true) }.orElse(false) }
        .toList()

    encontrados.forEach { p ->
        val info = p.info()
        println(
            "%-8d %-8s %-20s %-20s %-10s %s".format(
                p.pid(),
                p.parent().map { it.pid().toString() }.orElse("-"),
                info.user().orElse("?").takeLast(20),
                info.startInstant().map { FORMATO_HORA.format(it) }.orElse("?"),
                info.totalCpuDuration().map { formatearCpu(it) }.orElse("?"),
                info.command().get().takeLast(60)
            )
        )
    }
    println("Procesos encontrados: ${encontrados.size}")
}

/** Convierte un tiempo de CPU en segundos con dos decimales (p. ej. "1,25 s"). */
private fun formatearCpu(cpu: Duration): String = "%.2f s".format(cpu.toMillis() / 1000.0)
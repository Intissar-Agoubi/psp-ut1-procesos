package es.dam.psp.ut1.practicas

import es.dam.psp.ut1.Jvm
import java.io.File
import java.nio.charset.Charset
import kotlin.time.measureTime

/**
 * Coordinador.kt · Práctica 4 · proceso PADRE.
 *
 * Apuntes UT-1, apartados 5.2 (Jvm.proceso), 5.1 (redirectInput), 6 (leer antes de esperar)
 * y 6.3 (lanzar todos y después esperar a todos).
 * Antes: ejecuta practicas/GeneradorDatos.kt para crear datos/ en la raíz del proyecto.
 * Cómo ejecutarlo: triángulo ▶ junto a main, sin argumentos. Cada hijo es practicas/Contador.kt.
 *
 * Reparte los ficheros de datos/ entre procesos hijo Contador, recoge sus resultados
 * y muestra el total. Compara el tiempo en modo secuencial y en modo concurrente.
 * Mejora opcional: concurrenteLimitado(ficheros, maximo), medido con 1, 2, 4 y 8.
 */

/** Resultado que devuelve un hijo para un fichero. */
data class Resultado(
    val fichero: String,
    val lineas: Long,
    val palabras: Long,
    val caracteres: Long,
    val masFrecuente: String
)

const val CLASE_HIJO = "es.dam.psp.ut1.practicas.ContadorKt"   // main de Contador.kt

/**
 * Codificación con la que escribe el hijo cuando su salida va a una tubería: la nativa del sistema
 * (Cp1252 en Windows, UTF-8 en Linux/macOS). Así las tildes de la palabra más frecuente se leen bien.
 */
private val CODIFICACION_HIJO: Charset = Charset.forName(System.getProperty("native.encoding"))

/** 4.a · Crea y ARRANCA un Contador cuya entrada estándar es [fichero]. Hereda su salida de error. */
fun lanzarContador(fichero: File): Process =
    Jvm.proceso(CLASE_HIJO)
        .redirectInput(fichero)
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start()

/**
 * 4.b · Lee la línea del hijo, espera a que termine y construye el Resultado.
 * Si el código de salida no es 0, lanza una IllegalStateException.
 */
fun recogerResultado(fichero: File, hijo: Process): Resultado {
    val linea = hijo.inputStream.bufferedReader(CODIFICACION_HIJO).use { it.readLine() }   // leer ANTES de esperar
    val codigo = hijo.waitFor()
    if (codigo != 0 || linea == null) {
        throw IllegalStateException("El Contador de ${fichero.name} ha terminado con código $codigo")
    }
    val campos = linea.split(";")
    if (campos.size != 4) {
        throw IllegalStateException("Respuesta inesperada del Contador de ${fichero.name}: $linea")
    }
    return Resultado(fichero.name, campos[0].toLong(), campos[1].toLong(), campos[2].toLong(), campos[3])
}

/** 4.c · Procesa los ficheros uno detrás de otro: lanzar, esperar, lanzar, esperar... */
fun secuencial(ficheros: List<File>): List<Resultado> =
    ficheros.map { f -> recogerResultado(f, lanzarContador(f)) }

/** 4.d · Lanza TODOS los hijos a la vez y después recoge los resultados. */
fun concurrente(ficheros: List<File>): List<Resultado> {
    val hijos = ficheros.map { f -> f to lanzarContador(f) }        // 1. lanzar todos
    return hijos.map { (f, hijo) -> recogerResultado(f, hijo) }      // 2. esperar a todos
}

/** Mejora opcional · Nunca tiene más de [maximo] hijos vivos a la vez: los procesa en grupos. */
fun concurrenteLimitado(ficheros: List<File>, maximo: Int): List<Resultado> {
    require(maximo >= 1) { "El máximo de hijos debe ser al menos 1" }
    return ficheros.chunked(maximo).flatMap { grupo -> concurrente(grupo) }
}

/** 4.e · Muestra una fila por fichero y una fila TOTAL con líneas, palabras y caracteres sumados. */
fun mostrarTabla(resultados: List<Resultado>) {
    val cabecera = "%-12s %10s %10s %12s  %s"
    val fila = "%-12s %10d %10d %12d  %s"
    println(cabecera.format("FICHERO", "LÍNEAS", "PALABRAS", "CARACTERES", "MÁS FRECUENTE"))
    resultados.forEach { r -> println(fila.format(r.fichero, r.lineas, r.palabras, r.caracteres, r.masFrecuente)) }
    println(
        fila.format(
            "TOTAL",
            resultados.sumOf { it.lineas },
            resultados.sumOf { it.palabras },
            resultados.sumOf { it.caracteres },
            ""
        )
    )
}

fun main() {
    // Ruta relativa: datos/ dentro del directorio de trabajo (la raíz del proyecto en IntelliJ)
    val ficheros = File("datos").listFiles { f -> f.extension == "txt" }?.sortedBy { it.name }
    if (ficheros.isNullOrEmpty()) {
        println("No hay ficheros en datos/. Ejecuta antes GeneradorDatos.kt")
        return
    }
    println("Procesadores disponibles: ${Runtime.getRuntime().availableProcessors()}")

    lateinit var r1: List<Resultado>          // se asignan dentro de measureTime { }
    lateinit var r2: List<Resultado>
    val t1 = measureTime { r1 = secuencial(ficheros) }
    val t2 = measureTime { r2 = concurrente(ficheros) }

    // 4.e: tabla de resultados y comprobación de que los dos modos coinciden
    println()
    mostrarTabla(r1)
    println()
    println("¿Secuencial y concurrente dan el mismo resultado? ${if (r1 == r2) "sí" else "NO"}")

    println()
    println("Secuencial:  $t1")
    println("Concurrente: $t2")
    println("Aceleración: %.2f".format(t1 / t2))   // 4.f: t1 / t2

    // Mejora opcional: concurrente con un máximo de hijos vivos a la vez
    println()
    println("Concurrente limitado:")
    for (maximo in listOf(1, 2, 4, 8)) {
        val t = measureTime { concurrenteLimitado(ficheros, maximo) }
        println("  máximo $maximo hijo(s): $t")
    }
}
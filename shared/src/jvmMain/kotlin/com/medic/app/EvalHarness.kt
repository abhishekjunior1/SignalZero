package com.medic.app

import com.medic.app.nav.SolarMath
import kotlinx.datetime.Instant

/**
 * Command-line entry point for the evaluation harness.
 *
 * Exists so the evaluation drives the *real shipped Kotlin* — the same
 * SolarMath the Android and iOS apps call — rather than a Python
 * reimplementation of it. A harness that tests a copy of the algorithm proves
 * nothing about the app.
 *
 * Reads one case per line on stdin as `id<TAB>iso8601<TAB>lat<TAB>lon`,
 * writes `id<TAB>azimuth<TAB>elevation` on stdout. Deliberately TSV rather
 * than JSON so there is no serialization dependency in the measurement path.
 */
fun main() {
    generateSequence(::readLine).forEach { line ->
        if (line.isBlank() || line.startsWith("#")) return@forEach
        val parts = line.split("\t")
        require(parts.size == 4) { "malformed case: $line" }
        val (id, iso, latS, lonS) = parts
        val pos = SolarMath.solarPosition(
            Instant.parse(iso),
            latS.toDouble(),
            lonS.toDouble(),
        )
        println("$id\t${pos.azimuthDeg}\t${pos.elevationDeg}")
    }
}

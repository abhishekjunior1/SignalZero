package com.medic.app

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Platform seams.
 *
 * `Dispatchers.IO` exists on the JVM but not in common code — Kotlin/Native has
 * no separate IO pool, because its threading model does not distinguish
 * blocking IO from CPU work the way the JVM's does. Each target supplies the
 * right dispatcher for offloading model inference off the main thread.
 */
expect val ioDispatcher: CoroutineDispatcher

/**
 * Fixed-decimal formatting. `String.format` is JVM-only, and the only use here
 * is rendering retrieval scores into a prompt, so a small correct helper beats
 * pulling in a formatting library.
 *
 * Half-up at the final digit, and negative zero is normalised away.
 */
fun Double.toFixed(digits: Int): String {
    require(digits in 0..10) { "digits out of range: $digits" }
    if (isNaN()) return "NaN"
    if (isInfinite()) return if (this > 0) "Infinity" else "-Infinity"

    val factor = 10.0.pow(digits)
    val scaled = (abs(this) * factor).roundToLong()
    val negative = this < 0 && scaled != 0L

    val whole = scaled / factor.toLong()
    val frac = scaled % factor.toLong()

    val sign = if (negative) "-" else ""
    if (digits == 0) return "$sign$whole"
    return "$sign$whole." + frac.toString().padStart(digits, '0')
}

/** Float overload — retrieval scores are Float. */
fun Float.toFixed(digits: Int): String = toDouble().toFixed(digits)

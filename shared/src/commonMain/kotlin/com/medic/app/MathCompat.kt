package com.medic.app

import kotlin.math.PI

/**
 * `java.lang.Math` is JVM-only. The navigation maths uses `Math.toRadians` and
 * `Math.toDegrees` in about thirty places, so rather than inline the
 * conversion at every call site (and risk a typo in code that computes true
 * north), we provide the same two functions in common code.
 *
 * Definitions are identical to the JDK's: a plain multiply by pi/180 or its
 * reciprocal, in Double precision.
 */

fun toRadians(deg: Double): Double = deg * PI / 180.0

fun toDegrees(rad: Double): Double = rad * 180.0 / PI

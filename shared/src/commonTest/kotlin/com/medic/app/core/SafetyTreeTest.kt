package com.medic.app.core

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Ported from the Android-only JUnit suite to kotlin.test so it runs on both
 * the JVM and Kotlin/Native. Two changes from the original, no case dropped:
 *
 *  - org.junit -> kotlin.test (assertEquals keeps expected-then-actual order)
 *  - backticked method names -> camelCase. Kotlin/Native's test runner is not
 *    reliable with spaces and apostrophes in method names, and the original
 *    names contained both ("hasn't stopped maps to CRITICAL").
 *
 * The triage behaviour under test is unchanged; this is the same 16 cases.
 */
class SafetyTreeTest {

    // ---- Required proof cases from the spec ----

    @Test
    fun hasntStoppedMapsToCritical() {
        val result = SafetyTree.evaluate("The bleeding hasn't stopped, it's still pouring out")
        assertEquals(Severity.CRITICAL, result.severity)
    }

    @Test
    fun hasStoppedNowMapsToSeriousNotCritical() {
        val result = SafetyTree.evaluate("The bleeding has stopped now after I applied pressure")
        assertEquals(Severity.SERIOUS, result.severity)
    }

    // ---- Additional negation edge cases ----

    @Test
    fun hasNotStoppedFullFormMapsToCritical() {
        val result = SafetyTree.evaluate("It has not stopped bleeding at all")
        assertEquals(Severity.CRITICAL, result.severity)
    }

    @Test
    fun explicitAbsenceDoesNotReturnCritical() {
        val result = SafetyTree.evaluate("He is bleeding but it stopped already, looks fine")
        assertEquals(Severity.SERIOUS, result.severity)
    }

    @Test
    fun unrelatedNegationDoesNotBleedOntoStopped() {
        // "hasn't continued" must not negate the later, separate "stopped".
        val result = SafetyTree.evaluate(
            "The bleeding hasn't continued, it stopped right after we packed it"
        )
        assertEquals(Severity.SERIOUS, result.severity)
    }

    @Test
    fun contrastWordResetsNegationWindow() {
        // The negation attaches to "sure", not to "stopped".
        val result = SafetyTree.evaluate("I'm not sure, but the bleeding has stopped")
        assertEquals(Severity.SERIOUS, result.severity)
    }

    @Test
    fun arterialSpurtingWithNoResolutionIsCritical() {
        val result = SafetyTree.evaluate(
            "Blood is spurting out of his leg in time with his heartbeat"
        )
        assertEquals(Severity.CRITICAL, result.severity)
    }

    @Test
    fun stoppedThenStartedAgainIsCritical() {
        val result = SafetyTree.evaluate(
            "The bleeding stopped but then started again, it is still going"
        )
        assertEquals(Severity.CRITICAL, result.severity)
    }

    @Test
    fun notAnymoreResolvesToSerious() {
        val result = SafetyTree.evaluate("It was bleeding badly but not anymore, all clear now")
        assertEquals(Severity.SERIOUS, result.severity)
    }

    @Test
    fun notReallyStoppedIsCritical() {
        val result = SafetyTree.evaluate("Bleeding has not really stopped, just slowed a little")
        assertEquals(Severity.CRITICAL, result.severity)
    }

    // ---- Priority ordering ----

    @Test
    fun notBreathingWinsOverBleeding() {
        val result = SafetyTree.evaluate(
            "He is not breathing and there is a small cut that stopped bleeding"
        )
        assertEquals(Severity.CRITICAL, result.severity)
        assertEquals("NOT_BREATHING", result.matchedRule)
    }

    @Test
    fun severeBurnIsCritical() {
        val result = SafetyTree.evaluate("There's a third degree burn covering his forearm")
        assertEquals(Severity.CRITICAL, result.severity)
    }

    @Test
    fun fractureIsSerious() {
        val result = SafetyTree.evaluate("I think he has an open fracture in his arm")
        assertEquals(Severity.SERIOUS, result.severity)
    }

    @Test
    fun infectionSignsAreModerate() {
        val result = SafetyTree.evaluate(
            "The wound has some redness and swelling around it, might be infection"
        )
        assertEquals(Severity.MODERATE, result.severity)
    }

    @Test
    fun smallCutIsMinor() {
        val result = SafetyTree.evaluate("Just a small cut on my finger from the knife")
        assertEquals(Severity.MINOR, result.severity)
    }

    @Test
    fun noMatchIsUnknown() {
        val result = SafetyTree.evaluate("My foot feels weird today")
        assertEquals(Severity.UNKNOWN, result.severity)
    }
}

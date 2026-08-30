package com.medic.app.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

/**
 * Regressions found by an independent review, all of the same shape as the
 * arterial-bleeding defect: the tree answering confidently and wrongly.
 */
class SafetyTreeReviewRegressionTest {

    @Test
    fun cannotBreatheIsNotTreatedAsAbsentBreathing() {
        // A choking, asthmatic or anaphylactic casualty saying "I can't breathe"
        // has a pulse. The tree previously matched this as NOT_BREATHING and
        // told the user to start chest compressions.
        val r = SafetyTree.evaluate("I can't breathe, something is stuck in my throat")
        assertEquals(Severity.CRITICAL, r.severity)
        assertEquals("AIRWAY_DISTRESS", r.matchedRule)
        assertTrue(
            !r.directive.contains("CPR") || r.directive.contains("Do NOT start CPR"),
            "must not instruct CPR on a casualty who still has a pulse: ${r.directive}",
        )
    }

    @Test
    fun absentBreathingStillGetsCpr() {
        val r = SafetyTree.evaluate("he is not breathing and has no pulse")
        assertEquals("NOT_BREATHING", r.matchedRule)
        assertTrue(r.directive.contains("CPR"))
    }

    @Test
    fun severeBurnIsNotShadowedByControlledBleeding() {
        // A SERIOUS bleeding verdict used to return before the CRITICAL burn
        // rule was ever reached.
        val r = SafetyTree.evaluate(
            "third degree burn on his arm, the bleeding has stopped now"
        )
        assertEquals(Severity.CRITICAL, r.severity)
        assertEquals("SEVERE_BURN", r.matchedRule)
    }

    @Test
    fun slowedBleedingIsNotReportedAsControlled() {
        // Bleeding that has slowed is still bleeding.
        val r = SafetyTree.evaluate("the bleeding slowed a lot after we packed it")
        assertTrue(
            r.matchedRule != "BLEEDING_CONTROLLED",
            "slowed bleeding must not produce the 'bleeding is controlled' directive",
        )
    }

    @Test
    fun ordinaryWordsContainingNotAreNotNegations() {
        // "another", "knotted" and "noticed" all contain the substring "not".
        for (phrase in listOf(
            "I put another bandage on and the bleeding stopped",
            "we knotted a tourniquet and the bleeding stopped",
            "I noticed the bleeding stopped",
        )) {
            assertEquals(
                Severity.SERIOUS, SafetyTree.evaluate(phrase).severity,
                "substring match on 'not' wrongly escalated: $phrase",
            )
        }
    }

    @Test
    fun realNegationsStillEscalate() {
        assertEquals(Severity.CRITICAL, SafetyTree.evaluate("the bleeding hasn't stopped").severity)
        assertEquals(Severity.CRITICAL, SafetyTree.evaluate("it has not stopped bleeding").severity)
    }
}

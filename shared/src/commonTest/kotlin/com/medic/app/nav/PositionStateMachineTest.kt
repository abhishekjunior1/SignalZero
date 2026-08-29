package com.medic.app.nav

import kotlin.test.Test
import kotlin.test.assertEquals

class PositionStateMachineTest {

    @Test
    fun gpsAvailableAndNotSpoofedStaysTrusted() {
        val state = PositionState(source = PositionSource.GPS_TRUSTED)
        val next = PositionStateMachine.transition(state, gpsAvailable = true, gpsSpoofed = false)
        assertEquals(PositionSource.GPS_TRUSTED, next.source)
        assertEquals(false, next.spoofDetected)
    }

    @Test
    fun gpsSpoofedFallsBackToDeadReckoningAndFlagsSpoof() {
        val state = PositionState(source = PositionSource.GPS_TRUSTED)
        val next = PositionStateMachine.transition(state, gpsAvailable = true, gpsSpoofed = true)
        assertEquals(PositionSource.DEAD_RECKONING, next.source)
        assertEquals(true, next.spoofDetected)
    }

    @Test
    fun gpsUnavailableWithoutSpoofFallsBackToDeadReckoningWithoutSpoofFlag() {
        val state = PositionState(source = PositionSource.GPS_TRUSTED)
        val next = PositionStateMachine.transition(state, gpsAvailable = false, gpsSpoofed = false)
        assertEquals(PositionSource.DEAD_RECKONING, next.source)
        assertEquals(false, next.spoofDetected)
    }

    @Test
    fun deadReckoningFallsBackToSolarFixAfterThreshold() {
        val state = PositionState(source = PositionSource.DEAD_RECKONING, drElapsedSeconds = 650)
        val next = PositionStateMachine.transition(state, gpsAvailable = false, gpsSpoofed = false)
        assertEquals(PositionSource.SOLAR_FIX, next.source)
    }

    @Test
    fun deadReckoningStaysDeadReckoningBeforeThreshold() {
        val state = PositionState(source = PositionSource.DEAD_RECKONING, drElapsedSeconds = 100)
        val next = PositionStateMachine.transition(state, gpsAvailable = false, gpsSpoofed = false)
        assertEquals(PositionSource.DEAD_RECKONING, next.source)
    }

    @Test
    fun gpsReturningAfterDeadReckoningRestoresTrustedState() {
        val state = PositionState(source = PositionSource.DEAD_RECKONING, drElapsedSeconds = 300)
        val next = PositionStateMachine.transition(state, gpsAvailable = true, gpsSpoofed = false)
        assertEquals(PositionSource.GPS_TRUSTED, next.source)
        assertEquals(0L, next.drElapsedSeconds)
    }
}

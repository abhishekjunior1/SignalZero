package com.medic.app.ai

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Ported from JUnit to kotlin.test. Two changes were required for Kotlin/Native
 * that the JVM target did not force, both worth recording:
 *
 *  - `@Test(expected = X::class)` is JUnit-only. On the JVM `kotlin.test.Test`
 *    is a typealias to JUnit's annotation so it compiled fine; on Native it is
 *    a distinct annotation with no parameters. Replaced with `assertFailsWith`.
 *
 *  - Suspending tests must be `fun x() = runTest { ... }`, not
 *    `fun x() { runTest { ... } }`. On the JVM runTest blocks, so both forms
 *    work. On Native it returns a TestResult the framework must await — if it
 *    is discarded the body never runs and the test passes without asserting.
 */
class RealAiServiceTest {

    @Test
    fun isReadyRequiresEmbedGenerateAndTranscribe() {
        val service = RealAiService(
            backend = FakeBackend(
                status = OnDeviceBackendStatus(
                    backendName = "partial",
                    loadedCapabilities = setOf(OnDeviceCapability.EMBED, OnDeviceCapability.GENERATE)
                )
            )
        )

        assertFalse(service.isReady)
    }

    @Test
    fun isReadyIsTrueWhenRequiredCapabilitiesAreLoaded() {
        val service = RealAiService(
            backend = FakeBackend(
                status = OnDeviceBackendStatus(
                    backendName = "full",
                    loadedCapabilities = setOf(
                        OnDeviceCapability.EMBED,
                        OnDeviceCapability.GENERATE,
                        OnDeviceCapability.TRANSCRIBE
                    )
                )
            )
        )

        assertTrue(service.isReady)
    }

    @Test
    fun generateDelegatesToBackendAndTrimsResponse() = runTest {
        val service = RealAiService(
            backend = FakeBackend(
                status = readyStatus(),
                generateResult = "  grounded answer  "
            )
        )

        val result = service.generate("prompt")

        assertEquals("grounded answer", result)
    }

    @Test
    fun translateShortCircuitsWhenLanguagesAlreadyMatch() = runTest {
        val backend = FakeBackend(status = readyStatus())
        val service = RealAiService(backend = backend)

        val result = service.translate("hola", "Spanish", "spanish")

        assertEquals("hola", result)
        assertFalse(backend.translateCalled)
    }

    @Test
    fun translateFailsFastWhenTranslationModelIsUnavailable() = runTest {
        val service = RealAiService(
            backend = FakeBackend(status = readyStatus())
        )

        assertFailsWith<AiCapabilityUnavailableException> {
            service.translate("hello", "English", "Spanish")
        }
    }

    @Test
    fun embedDelegatesToBackend() = runTest {
        val expected = floatArrayOf(0.1f, 0.2f, 0.3f)
        val service = RealAiService(
            backend = FakeBackend(
                status = readyStatus(),
                embedResult = expected
            )
        )

        val result = service.embed("tourniquet")

        assertContentEquals(expected, result)
    }

    private fun readyStatus(): OnDeviceBackendStatus = OnDeviceBackendStatus(
        backendName = "ready",
        loadedCapabilities = setOf(
            OnDeviceCapability.EMBED,
            OnDeviceCapability.GENERATE,
            OnDeviceCapability.TRANSCRIBE
        )
    )

    private class FakeBackend(
        override val status: OnDeviceBackendStatus,
        private val embedResult: FloatArray = floatArrayOf(1f),
        private val generateResult: String = "response",
        private val transcribeResult: String = "transcript",
        private val translateResult: String = "translation"
    ) : OnDeviceModelBackend {
        var translateCalled: Boolean = false

        override suspend fun embed(text: String): FloatArray = embedResult

        override suspend fun generate(prompt: String): String = generateResult

        override suspend fun transcribe(audioPcm16: ShortArray): String = transcribeResult

        override suspend fun translate(text: String, fromLang: String, toLang: String): String {
            translateCalled = true
            return translateResult
        }
    }
}

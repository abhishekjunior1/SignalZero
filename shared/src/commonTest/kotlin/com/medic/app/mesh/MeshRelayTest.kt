package com.medic.app.mesh

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end check of the claim the feature actually makes: a message reaches
 * someone the sender cannot hear, by way of a node in between.
 *
 * Routers are wired by hand here rather than through a transport, so the test
 * states exactly who can hear whom. A -- B -- C, with A and C out of range of
 * each other.
 */
class MeshRelayTest {

    private fun msg(id: String, sender: String, ttl: Int = 5) = MeshMessage(
        id = id, senderId = sender, senderName = sender,
        kind = MessageKind.SOS, body = "help", sentAtMillis = 1_000, ttl = ttl,
    )

    @Test
    fun messageReachesANodeTheSenderCannotHear() {
        val a = MeshRouter("A")
        val b = MeshRouter("B")
        val c = MeshRouter("C")

        val fromA = a.originate(msg("m1", "A"))
        val bForwards = b.receive(fromA)          // B hears A
        assertTrue(bForwards != null, "B should relay")
        val cForwards = c.receive(bForwards!!)    // C hears B, never A

        assertEquals(listOf("m1"), c.messages().map { it.id }, "C should hold A's message")
        assertEquals(2, c.messages().first().hops, "C should see it arrived via 2 hops")
        assertTrue(cForwards != null, "C may still relay onward")
    }

    @Test
    fun aMessageDoesNotLoopBetweenNodesThatCanAllHearEachOther() {
        // The failure this prevents: three mutually-audible nodes rebroadcasting
        // the same message forever until the link is unusable.
        val a = MeshRouter("A")
        val b = MeshRouter("B")
        val c = MeshRouter("C")

        var inFlight = listOf(a.originate(msg("m1", "A")))
        var deliveries = 0
        repeat(20) {
            val next = mutableListOf<MeshMessage>()
            for (m in inFlight) {
                for (node in listOf(a, b, c)) {
                    node.receive(m)?.let { next += it; deliveries++ }
                }
            }
            inFlight = next
        }
        assertTrue(deliveries < 10, "traffic should die out quickly, saw $deliveries forwards")
        assertTrue(inFlight.isEmpty(), "nothing should still be circulating")
    }

    @Test
    fun aLateJoinerGetsTheBacklogFromARelay() = runTest {
        // The store-and-forward promise: D was not present when A spoke, and
        // still receives it from B afterwards.
        val a = MeshRouter("A")
        val b = MeshRouter("B")
        b.receive(a.originate(msg("m1", "A")))

        val d = MeshRouter("D")
        b.backlogFor("D").forEach { d.receive(it) }

        assertEquals(listOf("m1"), d.messages().map { it.id })
    }

    @Test
    fun simulatedTransportCarriesAMessageBetweenTwoNodes() = runTest {
        // Covers the transport seam itself, which the router tests bypass.
        val network = MeshNetwork()
        val alice = SimulatedMeshTransport(MeshPeer("A", "Alice"), network)
        val bob = SimulatedMeshTransport(MeshPeer("B", "Bob"), network)

        alice.start()
        bob.start()

        val received = mutableListOf<MeshMessage>()
        // backgroundScope is cancelled automatically when the test ends.
        backgroundScope.launch { bob.incoming.collect { received += it } }
        testScheduler.runCurrent()

        alice.broadcast(msg("m1", "A"))
        testScheduler.runCurrent()

        alice.stop()
        bob.stop()
        assertEquals(listOf("m1"), received.map { it.id })
    }

    @Test
    fun peersAppearAndDisappearAsNodesStartAndStop() = runTest {
        val network = MeshNetwork()
        val alice = SimulatedMeshTransport(MeshPeer("A", "Alice"), network)
        val bob = SimulatedMeshTransport(MeshPeer("B", "Bob"), network)

        val seen = mutableListOf<List<MeshPeer>>()
        alice.start()
        backgroundScope.launch { alice.peers.collect { seen += it } }
        testScheduler.runCurrent()

        bob.start()
        testScheduler.runCurrent()
        assertEquals(listOf("B"), seen.last().map { it.id }, "Alice should see Bob arrive")

        bob.stop()
        testScheduler.runCurrent()
        assertTrue(seen.last().isEmpty(), "Alice should see Bob leave")

        alice.stop()
    }
}

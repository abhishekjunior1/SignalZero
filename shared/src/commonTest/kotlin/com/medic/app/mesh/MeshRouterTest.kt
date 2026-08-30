package com.medic.app.mesh

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The routing rules cannot be checked by hand on two phones in a field, so they
 * are pinned here. Each test names the failure it prevents.
 */
class MeshRouterTest {

    private fun msg(
        id: String,
        sender: String = "peer-a",
        kind: MessageKind = MessageKind.CHAT,
        ttl: Int = 5,
        at: Long = 1_000,
    ) = MeshMessage(
        id = id, senderId = sender, senderName = sender,
        kind = kind, body = "body-$id", sentAtMillis = at, ttl = ttl,
    )

    @Test
    fun forwardsAMessageItHasNotSeen() {
        val r = MeshRouter(selfId = "me")
        val out = r.receive(msg("m1"))
        assertNotNull(out)
        assertEquals(4, out.ttl, "ttl should decrement on forward")
        assertEquals(1, out.hops, "hop count should increment")
    }

    @Test
    fun doesNotForwardTheSameMessageTwice() {
        // Without this, three nodes in mutual range rebroadcast forever and
        // saturate the link.
        val r = MeshRouter(selfId = "me")
        assertNotNull(r.receive(msg("m1")))
        assertNull(r.receive(msg("m1")), "second sighting must be dropped")
    }

    @Test
    fun doesNotForwardItsOwnMessageBackOutward() {
        val r = MeshRouter(selfId = "me")
        assertNull(r.receive(msg("m1", sender = "me")))
    }

    @Test
    fun stopsForwardingWhenOutOfHops() {
        val r = MeshRouter(selfId = "me")
        assertNull(r.receive(msg("m1", ttl = 0)), "a dead message must not travel")
    }

    @Test
    fun ttlReachesZeroAfterExactlyTtlHops() {
        var m: MeshMessage? = msg("m1", ttl = 3)
        var hops = 0
        // Each hop is a different node, so dedup does not interfere.
        while (m != null) {
            val router = MeshRouter(selfId = "node-$hops")
            m = router.receive(m)
            if (m != null) hops++
        }
        assertEquals(3, hops, "a ttl of 3 should permit exactly 3 forwards")
    }

    @Test
    fun storesReceivedMessagesForLaterPeers() {
        // The store-and-forward promise: hold what you heard so you can pass it
        // on to somebody who was not in range at the time.
        val r = MeshRouter(selfId = "me")
        r.receive(msg("m1"))
        r.receive(msg("m2", sender = "peer-b"))
        assertEquals(2, r.size())
        assertEquals(listOf("m1", "m2"), r.backlogFor("peer-c").map { it.id })
    }

    @Test
    fun backlogExcludesMessagesThePeerItselfSent() {
        val r = MeshRouter(selfId = "me")
        r.receive(msg("m1", sender = "peer-a"))
        r.receive(msg("m2", sender = "peer-b"))
        assertEquals(listOf("m2"), r.backlogFor("peer-a").map { it.id })
    }

    @Test
    fun backlogExcludesMessagesWithNoHopsLeft() {
        val r = MeshRouter(selfId = "me")
        r.originate(msg("mine", sender = "me", ttl = 0))
        assertTrue(r.backlogFor("peer-x").isEmpty(), "spent messages are not worth link time")
    }

    @Test
    fun remembersEvictedIdsSoTheyAreNotReAccepted() {
        // If an evicted id were forgotten, a peer still holding that message
        // would hand it back and the pair would trade it indefinitely.
        val r = MeshRouter(selfId = "me", capacity = 2)
        r.receive(msg("m1", at = 1))
        r.receive(msg("m2", at = 2))
        r.receive(msg("m3", at = 3))
        assertEquals(2, r.size())
        assertTrue(r.hasSeen("m1"), "an evicted id must still be remembered")
        assertNull(r.receive(msg("m1", at = 1)), "an evicted message must not be re-accepted")
    }

    @Test
    fun evictsChatBeforeSos() {
        // The rule that matters most: a casualty's SOS must outlive small talk.
        val r = MeshRouter(selfId = "me", capacity = 2)
        r.receive(msg("sos", kind = MessageKind.SOS, at = 1))
        r.receive(msg("chat", kind = MessageKind.CHAT, at = 2))
        r.receive(msg("chat2", kind = MessageKind.CHAT, at = 3))
        val kept = r.messages().map { it.id }
        assertTrue("sos" in kept, "SOS was evicted while chat survived: $kept")
    }

    @Test
    fun evictsOldestWithinTheSameKind() {
        val r = MeshRouter(selfId = "me", capacity = 2)
        r.receive(msg("old", at = 1))
        r.receive(msg("mid", at = 2))
        r.receive(msg("new", at = 3))
        assertEquals(listOf("mid", "new"), r.messages().map { it.id })
    }

    @Test
    fun evictsSosOnlyWhenNothingElseRemains() {
        val r = MeshRouter(selfId = "me", capacity = 2)
        r.receive(msg("sos1", kind = MessageKind.SOS, at = 1))
        r.receive(msg("sos2", kind = MessageKind.SOS, at = 2))
        r.receive(msg("sos3", kind = MessageKind.SOS, at = 3))
        assertEquals(listOf("sos2", "sos3"), r.messages().map { it.id },
            "with only SOS held, the oldest should go")
    }

    @Test
    fun originatedMessagesAreStoredAndRelayable() {
        val r = MeshRouter(selfId = "me")
        val out = r.originate(msg("mine", sender = "me"))
        assertEquals(5, out.ttl, "originating must not consume a hop")
        assertEquals(1, r.size())
    }
}

package com.medic.app.mesh

/**
 * Store-and-forward routing for the offline mesh.
 *
 * The whole problem this solves: two people out of radio range of each other
 * can still exchange messages if somebody walks between them. A node holds what
 * it has heard and re-offers it whenever a new peer appears, so messages travel
 * at walking pace when they cannot travel at radio speed.
 *
 * Pure logic on purpose -- no BLE, no Android, no coroutines. Every rule below
 * (dedup, TTL, eviction order) is a decision that has to be *right*, and this
 * way it can be tested exhaustively without two phones in a field.
 *
 * Not thread-safe. Callers confine it to a single dispatcher; on Android that
 * is the mesh service's scope.
 */
class MeshRouter(
    private val selfId: String,
    private val capacity: Int = DEFAULT_CAPACITY,
) {
    /** Insertion-ordered, so eviction can fall back to oldest-first. */
    private val store = LinkedHashMap<String, MeshMessage>()

    /**
     * Every id this node has ever accepted, including ones since evicted.
     *
     * Kept separately from [store] and deliberately never trimmed with it: if a
     * message were forgotten as soon as it was evicted, a peer still holding it
     * would hand it straight back and the two nodes would trade it forever.
     */
    private val seen = LinkedHashSet<String>()

    /** What this node currently holds, newest last. */
    fun messages(): List<MeshMessage> = store.values.toList()

    fun size(): Int = store.size

    fun hasSeen(id: String): Boolean = id in seen

    /**
     * Take a message that arrived from a peer.
     *
     * Returns the form that should be passed on, or null when this node should
     * stay quiet -- because it has seen the message before, because the message
     * is out of hops, or because this node sent it in the first place.
     */
    fun receive(message: MeshMessage): MeshMessage? {
        if (message.id in seen) return null       // already know it; stay quiet
        if (message.senderId == selfId) return null  // our own, echoed back

        // Store the local view (one hop further out) so the UI can honestly say
        // "arrived via N hops", then offer that same copy onward.
        val local = message.asReceived()
        admit(local)
        return if (message.forwardable) local else null
    }

    /**
     * Originate a message from this node. It is stored and returned ready to
     * transmit; the caller hands it to the transport.
     */
    fun originate(message: MeshMessage): MeshMessage {
        admit(message)
        return message
    }

    /**
     * Everything worth offering to a peer that just came into range.
     *
     * Excludes messages with no hops left: they cannot legally travel further,
     * so sending them would be pure noise on a link that is already scarce.
     */
    fun backlogFor(peerId: String): List<MeshMessage> =
        store.values.filter { it.forwardable && it.senderId != peerId }

    private fun admit(message: MeshMessage) {
        seen += message.id
        store[message.id] = message
        while (store.size > capacity) evictOne()
    }

    /**
     * Drop exactly one message when full.
     *
     * Order matters: chat goes before beacons, beacons before SOS, and only
     * within a class does age decide. Evicting an SOS to keep somebody's chat
     * message is the one outcome this must never produce -- so SOS is only ever
     * dropped when the store holds nothing but SOS.
     */
    private fun evictOne() {
        val victim = store.values
            .sortedWith(compareByDescending<MeshMessage> { it.kind.priority }
                .thenBy { it.sentAtMillis })
            .firstOrNull() ?: return
        store.remove(victim.id)
    }

    companion object {
        /**
         * Messages held before eviction starts. Sized for a phone that may be
         * relaying for hours with no chance to offload, while staying trivial
         * against any modern device's memory.
         */
        const val DEFAULT_CAPACITY = 500
    }
}

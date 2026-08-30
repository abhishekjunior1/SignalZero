package com.medic.app.mesh

/**
 * What the chat screen needs to render, kept out of the Android layer so the
 * state shape is shared and testable.
 */
data class MeshUiState(
    val enabled: Boolean = false,
    val selfName: String = "You",
    val selfId: String = "",
    val peers: List<MeshPeer> = emptyList(),
    val messages: List<MeshMessage> = emptyList(),
    val draft: String = "",
    /** Set when the transport could not start, e.g. Bluetooth off or denied. */
    val error: String? = null,
    /** True when running over the simulated transport rather than a radio. */
    val simulated: Boolean = false,
) {
    val canSend: Boolean
        get() = enabled && draft.isNotBlank() &&
            draft.encodeToByteArray().size <= MeshMessage.MAX_BODY_BYTES

    /**
     * Deliberately phrased as reach, not delivery. Nothing on this mesh
     * acknowledges, so the UI must never imply a message was received.
     */
    val reachSummary: String
        get() = when {
            !enabled -> "Off"
            peers.isEmpty() -> "No one in range"
            peers.size == 1 -> "1 person in range"
            else -> "${peers.size} people in range"
        }
}

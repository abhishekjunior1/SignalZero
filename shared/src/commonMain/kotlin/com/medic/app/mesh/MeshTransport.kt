package com.medic.app.mesh

import kotlinx.coroutines.flow.Flow

/**
 * How messages physically move between devices.
 *
 * [MeshRouter] decides *what* to send; a transport decides *how*. Keeping them
 * apart is what makes the routing rules testable without radios, and it is why
 * swapping BLE for Wi-Fi Aware later touches nothing above this line.
 *
 * Implementations must be safe to call from any thread and must never throw for
 * an ordinary condition like "no peers in range" -- on this mesh, nobody
 * listening is the normal case, not an error.
 */
interface MeshTransport {

    /** Peers currently reachable. Emits a new list whenever that changes. */
    val peers: Flow<List<MeshPeer>>

    /** Messages heard from peers, already decoded. */
    val incoming: Flow<MeshMessage>

    /**
     * Offer a message to everyone in range.
     *
     * Best-effort by definition: returning normally means it was transmitted,
     * never that anyone received it. Nothing above this may report delivery.
     */
    suspend fun broadcast(message: MeshMessage)

    /** Begin advertising and scanning. */
    suspend fun start()

    /** Stop advertising and scanning, and release the radio. */
    suspend fun stop()
}

/** Another device on the mesh. */
data class MeshPeer(
    val id: String,
    val name: String,
    /**
     * Received signal strength in dBm, when the transport can measure it.
     * Roughly: -40 is very close, -90 is at the edge of range. Used only to
     * hint proximity in the UI -- it is far too noisy to navigate by.
     */
    val rssi: Int? = null,
)

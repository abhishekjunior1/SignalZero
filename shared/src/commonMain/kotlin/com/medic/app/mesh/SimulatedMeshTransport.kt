package com.medic.app.mesh

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * An in-process mesh, for tests and for demonstrating the app without radios.
 *
 * This exists for a concrete reason: an Android emulator has no Bluetooth LE
 * radio, and two emulators cannot see each other. Every routing rule in
 * [MeshRouter] is therefore unobservable on a development machine unless the
 * transport can be faked. Nodes here share a [MeshNetwork] object instead of a
 * radio; everything above the transport interface is the same code that runs
 * over BLE.
 *
 * What it deliberately does NOT simulate: packet loss, range, interference, or
 * the connection churn of real BLE. It is honest about being a demonstration
 * aid, not a network model -- a green run here is not evidence the radio path
 * works.
 */
class MeshNetwork {
    private val nodes = mutableMapOf<String, SimulatedMeshTransport>()

    internal fun register(node: SimulatedMeshTransport) {
        nodes[node.selfPeer.id] = node
        announcePeers()
    }

    internal fun unregister(node: SimulatedMeshTransport) {
        nodes.remove(node.selfPeer.id)
        announcePeers()
    }

    /** Hand a message to every node except the one that sent it. */
    internal suspend fun deliver(from: String, message: MeshMessage) {
        nodes.values.filter { it.selfPeer.id != from }.forEach { it.accept(message) }
    }

    private fun announcePeers() {
        nodes.values.forEach { node ->
            node.updatePeers(nodes.values.map { it.selfPeer }.filter { it.id != node.selfPeer.id })
        }
    }
}

class SimulatedMeshTransport(
    val selfPeer: MeshPeer,
    private val network: MeshNetwork,
    /** Fake propagation delay, so the UI shows arrival rather than a jump. */
    private val latencyMillis: Long = 0,
) : MeshTransport {

    private val _peers = MutableStateFlow<List<MeshPeer>>(emptyList())
    override val peers: Flow<List<MeshPeer>> = _peers.asStateFlow()

    private val _incoming = MutableSharedFlow<MeshMessage>(extraBufferCapacity = 64)
    override val incoming: Flow<MeshMessage> = _incoming.asSharedFlow()

    private var running = false

    override suspend fun start() {
        if (running) return
        running = true
        network.register(this)
    }

    override suspend fun stop() {
        if (!running) return
        running = false
        network.unregister(this)
        _peers.value = emptyList()
    }

    override suspend fun broadcast(message: MeshMessage) {
        if (!running) return          // radio off is not an error
        if (latencyMillis > 0) delay(latencyMillis)
        network.deliver(selfPeer.id, message)
    }

    internal suspend fun accept(message: MeshMessage) {
        if (!running) return
        _incoming.emit(message)
    }

    internal fun updatePeers(list: List<MeshPeer>) {
        if (running) _peers.value = list
    }
}

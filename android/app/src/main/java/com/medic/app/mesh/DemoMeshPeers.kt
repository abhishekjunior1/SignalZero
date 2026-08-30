package com.medic.app.mesh

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Two scripted peers on the simulated mesh.
 *
 * They exist so the mesh can be *seen* working on a machine with no Bluetooth:
 * an Android emulator has no BLE radio and two emulators cannot discover each
 * other, so without this the feature is invisible until you have two physical
 * phones in a field.
 *
 * These are a demonstration aid and nothing more. They are only started when
 * the app is running on the simulated transport, and the UI labels that state
 * plainly rather than implying real devices are present.
 */
object DemoMeshPeers {

    private data class Script(
        val peer: MeshPeer,
        val joinAfterMillis: Long,
        val lines: List<Pair<Long, String>>,
        val kind: MessageKind = MessageKind.CHAT,
    )

    private val scripts = listOf(
        Script(
            peer = MeshPeer("demo-rin", "Rin", rssi = -52),
            joinAfterMillis = 1_500,
            lines = listOf(
                2_500L to "Anyone else near the ridge? Comms are dead here.",
                9_000L to "I have a first aid kit and about 2L of water.",
            ),
        ),
        Script(
            peer = MeshPeer("demo-tomas", "Tomás", rssi = -78),
            joinAfterMillis = 5_000,
            lines = listOf(
                6_500L to "Leg injury at the north trail marker. Bleeding controlled.",
            ),
            kind = MessageKind.SOS,
        ),
    )

    /**
     * Start the scripted peers on [network]. Returns immediately; the peers
     * join and speak on their own schedule inside [scope].
     */
    fun start(network: MeshNetwork, scope: CoroutineScope, clock: () -> Long) {
        scripts.forEach { script ->
            scope.launch {
                delay(script.joinAfterMillis)
                val transport = SimulatedMeshTransport(script.peer, network)
                transport.start()
                var elapsed = script.joinAfterMillis
                script.lines.forEach { (at, text) ->
                    delay((at - elapsed).coerceAtLeast(0))
                    elapsed = at
                    transport.broadcast(
                        MeshMessage(
                            id = "${script.peer.id}-$at",
                            senderId = script.peer.id,
                            senderName = script.peer.name,
                            kind = script.kind,
                            body = text,
                            sentAtMillis = clock(),
                        )
                    )
                }
            }
        }
    }
}

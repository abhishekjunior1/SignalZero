package com.medic.app.mesh

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Joins the routing rules to a transport and exposes state for the UI.
 *
 * Everything policy-shaped lives in [MeshRouter]; everything radio-shaped lives
 * behind [MeshTransport]. This class is only the wiring between them, which is
 * why it stays short enough to read in one pass.
 */
class MeshService(
    private val selfId: String,
    private val selfName: String,
    private val transport: MeshTransport,
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    /** Injected so ids are deterministic in tests; on device this is a UUID. */
    private val idFactory: () -> String,
    private val simulated: Boolean = false,
) {
    private val router = MeshRouter(selfId)

    private val _state = MutableStateFlow(
        MeshUiState(selfName = selfName, selfId = selfId, simulated = simulated)
    )
    val state: StateFlow<MeshUiState> = _state.asStateFlow()

    private var jobs = mutableListOf<Job>()

    suspend fun start() {
        if (_state.value.enabled) return
        runCatching { transport.start() }
            .onFailure { e ->
                _state.value = _state.value.copy(
                    enabled = false,
                    error = e.message ?: "Could not start the mesh radio",
                )
                return
            }

        jobs += scope.launch {
            transport.incoming.collect { incoming ->
                val forward = router.receive(incoming)
                // Relay before touching the UI: on a mesh, passing a message on
                // matters more than rendering it a few milliseconds sooner.
                if (forward != null) runCatching { transport.broadcast(forward) }
                publish()
            }
        }
        jobs += scope.launch {
            transport.peers.collect { peers ->
                _state.value = _state.value.copy(peers = peers)
                // A peer that just appeared may have missed everything said
                // before it arrived. This is the store-and-forward moment.
                peers.forEach { peer ->
                    router.backlogFor(peer.id).forEach { held ->
                        runCatching { transport.broadcast(held) }
                    }
                }
            }
        }
        _state.value = _state.value.copy(enabled = true, error = null)
        publish()
    }

    suspend fun stop() {
        jobs.forEach { it.cancel() }
        jobs.clear()
        runCatching { transport.stop() }
        _state.value = _state.value.copy(enabled = false, peers = emptyList())
    }

    fun onDraftChange(text: String) {
        _state.value = _state.value.copy(draft = text)
    }

    /** Send the current draft as ordinary chat. */
    suspend fun send() {
        val body = _state.value.draft.trim()
        if (body.isEmpty()) return
        dispatch(body, MessageKind.CHAT)
        _state.value = _state.value.copy(draft = "")
    }

    /**
     * Send an SOS. Kept separate from [send] because it carries the priority
     * that survives eviction, and because it must work with an empty draft.
     */
    suspend fun sendSos(body: String) = dispatch(body, MessageKind.SOS)

    private suspend fun dispatch(body: String, kind: MessageKind) {
        val message = MeshMessage(
            id = idFactory(),
            senderId = selfId,
            senderName = selfName,
            kind = kind,
            body = body.take(MeshMessage.MAX_BODY_BYTES),
            sentAtMillis = clock(),
        )
        router.originate(message)
        publish()
        runCatching { transport.broadcast(message) }
            .onFailure { e ->
                _state.value = _state.value.copy(error = e.message ?: "Send failed")
            }
    }

    private fun publish() {
        _state.value = _state.value.copy(messages = router.messages())
    }
}

package com.medic.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medic.app.mesh.MeshMessage
import com.medic.app.mesh.MeshUiState
import com.medic.app.mesh.MessageKind
import com.medic.app.ui.theme.*

/**
 * Person-to-person messaging with no network.
 *
 * Two things this screen is careful about, both for the same reason -- nothing
 * on this mesh acknowledges, so the app must never imply more than it knows:
 *
 *  - It reports who is *in range*, never that a message was delivered.
 *  - When running on the simulated transport it says so, rather than letting a
 *    demo read as live radio traffic.
 */
@Composable
fun MeshScreen(
    state: MeshUiState,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onSos: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {

        ReachBanner(state)

        if (state.simulated) {
            Text(
                "Simulated peers — this device has no mesh radio available.",
                color = SgTextMuted,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        state.error?.let { err ->
            Text(
                err,
                color = SgMedical.icon,
                fontSize = 13.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        val listState = rememberLazyListState()
        LaunchedEffect(state.messages.size) {
            if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
        }

        if (state.messages.isEmpty()) {
            EmptyState(modifier = Modifier.weight(1f))
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.messages, key = { it.id }) { m ->
                    MessageBubble(m, isMine = m.senderId == state.selfId)
                }
            }
        }

        Composer(state, onDraftChange, onSend, onSos)
    }
}

@Composable
private fun ReachBanner(state: MeshUiState) {
    val tint = if (state.peers.isEmpty()) SgTextMuted else SgHospital.icon
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Groups, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(state.reachSummary, color = tint, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        if (state.peers.isNotEmpty()) {
            Text(
                state.peers.joinToString(", ") { it.name },
                color = SgTextMuted,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Text("No messages yet", color = SgTextSecondary, fontSize = 16.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                "Messages reach phones nearby and hop between them. " +
                    "Anything you send is held and passed on when someone new comes into range.",
                color = SgTextMuted,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
        }
    }
}

@Composable
private fun MessageBubble(m: MeshMessage, isMine: Boolean) {
    val sos = m.kind == MessageKind.SOS
    val bg = when {
        sos -> SgMedical.tile
        isMine -> SgAssistant.tile
        else -> SgSurface
    }
    val titleColor = when {
        sos -> SgMedical.title
        isMine -> SgAssistant.title
        else -> SgText
    }
    val bodyColor = when {
        sos -> SgMedical.title
        isMine -> SgAssistant.title
        else -> SgTextSecondary
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(bg)
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isMine) "You" else m.senderName,
                    color = titleColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                if (sos) {
                    Spacer(Modifier.width(6.dp))
                    Text("SOS", color = SgMedical.icon, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(m.body, color = bodyColor, fontSize = 15.sp, lineHeight = 21.sp)
            if (!isMine && m.hops > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    if (m.hops == 1) "direct" else "relayed via ${m.hops - 1} phone${if (m.hops > 2) "s" else ""}",
                    color = SgTextMuted,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun Composer(
    state: MeshUiState,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onSos: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().background(SgSurface).padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(SgBg)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                if (state.draft.isEmpty()) {
                    Text("Message people nearby…", color = SgTextMuted, fontSize = 15.sp)
                }
                BasicTextField(
                    value = state.draft,
                    onValueChange = onDraftChange,
                    textStyle = TextStyle(color = SgText, fontSize = 15.sp),
                    cursorBrush = SolidColor(SgAssistant.icon),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(if (state.canSend) SgAssistant.icon else SgBg)
                    .clickable(enabled = state.canSend, onClick = onSend),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (state.canSend) Color.White else SgTextMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SgMedical.tile)
                .clickable(enabled = state.enabled, onClick = onSos)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Broadcast SOS", color = SgMedical.title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
    }
}

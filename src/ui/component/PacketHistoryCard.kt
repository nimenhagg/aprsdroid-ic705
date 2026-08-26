package org.aprsdroid.app.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.aprsdroid.app.R
import org.aprsdroid.app.aprs.AprsPacketKind
import org.aprsdroid.app.aprs.AprsPacketSummaryParser
import org.aprsdroid.app.model.LogPostItem
import java.util.Locale

@Composable
fun PacketHistoryCard(post: LogPostItem) {
    val parsed = remember(post.message) { AprsPacketSummaryParser.parse(post.message) }
    var showRaw by rememberSaveable(post.id) { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = post.tss,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                val status = post.status
                if (!status.isNullOrEmpty()) {
                    Text(text = status, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                }
            }

            Spacer(modifier = Modifier.height(7.dp))
            Text(
                text = packetKindLabel(parsed.kind),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            val sourceDest = listOfNotNull(parsed.source, parsed.destination).joinToString(" → ")
            if (sourceDest.isNotEmpty()) DetailLine(stringResource(R.string.packet_route), sourceDest)
            if (parsed.path.isNotEmpty()) DetailLine(stringResource(R.string.packet_path), parsed.path.joinToString(" → "))
            if (parsed.latitude != null && parsed.longitude != null) {
                DetailLine(
                    stringResource(R.string.packet_position),
                    String.format(Locale.US, "%.5f°, %.5f°", parsed.latitude, parsed.longitude)
                )
            }
            if (parsed.course != null || parsed.speedKnots != null) {
                val movement = buildList {
                    parsed.course?.let { add(stringResource(R.string.packet_course_value, it)) }
                    parsed.speedKnots?.let { add(stringResource(R.string.packet_speed_value, it)) }
                }.joinToString(" · ")
                DetailLine(stringResource(R.string.packet_movement), movement)
            }
            parsed.altitudeFeet?.let { DetailLine(stringResource(R.string.packet_altitude), "$it ft") }
            parsed.frequency?.let { DetailLine(stringResource(R.string.packet_frequency), "$it MHz") }
            parsed.message?.let { DetailLine(stringResource(R.string.packet_message), it) }
            parsed.comment?.let { DetailLine(stringResource(R.string.packet_comment), it) }
            if (parsed.kind == AprsPacketKind.UNKNOWN && parsed.payload.isNotBlank()) {
                DetailLine(stringResource(R.string.packet_payload), parsed.payload)
            }

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showRaw = !showRaw },
                modifier = Modifier.heightIn(min = 44.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = if (showRaw) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
                Text(
                    text = stringResource(if (showRaw) R.string.packet_hide_raw else R.string.packet_show_raw),
                    modifier = Modifier.padding(start = 6.dp)
                )
            }

            if (showRaw) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    SelectionContainer {
                        Text(
                            text = parsed.raw,
                            modifier = Modifier.padding(10.dp),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    if (value.isBlank()) return
    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(
            text = "$label：",
            modifier = Modifier.padding(end = 6.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun packetKindLabel(kind: AprsPacketKind): String = stringResource(
    when (kind) {
        AprsPacketKind.POSITION -> R.string.packet_type_position
        AprsPacketKind.MESSAGE -> R.string.packet_type_message
        AprsPacketKind.STATUS -> R.string.packet_type_status
        AprsPacketKind.OBJECT -> R.string.packet_type_object
        AprsPacketKind.ITEM -> R.string.packet_type_item
        AprsPacketKind.WEATHER -> R.string.packet_type_weather
        AprsPacketKind.TELEMETRY -> R.string.packet_type_telemetry
        AprsPacketKind.MICE -> R.string.packet_type_mice
        AprsPacketKind.THIRD_PARTY -> R.string.packet_type_third_party
        AprsPacketKind.UNKNOWN -> R.string.packet_type_unknown
    }
)

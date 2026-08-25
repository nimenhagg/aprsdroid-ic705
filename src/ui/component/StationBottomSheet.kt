package org.aprsdroid.app.ui.component

import android.content.Context
import android.content.Intent
import android.location.Location
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.compose.ui.unit.sp
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.aprsdroid.app.MessageActivity
import org.aprsdroid.app.R
import org.aprsdroid.app.StorageDatabase
import org.aprsdroid.app.UIHelper
import org.aprsdroid.app.model.StationItem
import org.aprsdroid.app.ui.components.SymbolBadge
import org.aprsdroid.app.ui.theme.AprsTheme
import java.util.Locale

@Composable
fun StationBottomSheetContent(
    station: StationItem,
    myLat: Int,
    myLon: Int,
    onSendMessage: () -> Unit,
    onViewDetails: () -> Unit,
    onNavigate: () -> Unit
) {
    val context = LocalContext.current

    val hasMyPosition = myLat != 0 || myLon != 0
    val distStr = if (hasMyPosition) {
        val dist = remember(myLat, myLon, station.lat, station.lon) {
            val results = FloatArray(2)
            val mcd = 1000000.0
            Location.distanceBetween(myLat / mcd, myLon / mcd, station.lat / mcd, station.lon / mcd, results)
            results
        }
        String.format(Locale.US, "%1.1f km %s", dist[0] / 1000.0, getBearing(dist[1].toDouble()))
    } else {
        "—"
    }
    val age = DateUtils.getRelativeTimeSpanString(context, station.ts).toString()

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Drag handle pill
            Box_DragHandle()

            Spacer(modifier = Modifier.height(12.dp))

            // Header: Symbol + Callsign + Distance/Age
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SymbolBadge(
                    symbol = station.symbol,
                    size = 56.dp
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = station.call,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$distStr • $age",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Middle: Frequency Chip + Coordinates + Comment
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    val qrg = station.qrg
                    if (!qrg.isNullOrEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = qrg,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Lat / Lon
                    val latStr = String.format(Locale.US, "%.5f°", station.lat / 1000000.0)
                    val lonStr = String.format(Locale.US, "%.5f°", station.lon / 1000000.0)
                    Text(
                        text = stringResource(R.string.station_coordinates, latStr, lonStr),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val comment = station.comment
                    if (!comment.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = comment,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilledTonalButton(
                    onClick = onSendMessage,
                    modifier = Modifier
                        .weight(1.1f)
                        .height(46.dp),
                    shape = RoundedCornerShape(23.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Chat,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.action_send_message),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                Button(
                    onClick = onViewDetails,
                    modifier = Modifier
                        .weight(1.3f)
                        .height(46.dp),
                    shape = RoundedCornerShape(23.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.action_view_track),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                OutlinedButton(
                    onClick = onNavigate,
                    modifier = Modifier
                        .weight(0.95f)
                        .height(46.dp),
                    shape = RoundedCornerShape(23.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Navigation,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.action_navigate),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
private fun Box_DragHandle() {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .width(36.dp)
                .height(4.dp),
            shape = RoundedCornerShape(2.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        ) {}
    }
}

private fun getBearing(deg: Double): String {
    val directions = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    val norm = ((deg % 360) + 360) % 360
    val idx = (((norm + 22.5) / 45).toInt()) % 8
    return directions[idx]
}

object StationBottomSheetHelper {
    fun show(context: Context, call: String, db: StorageDatabase, myLat: Int, myLon: Int) {
        val cursor = db.getStations("CALL = ?", arrayOf(call), "1")
        val items = StationItem.fromCursor(cursor) // fromCursor iterates and closes cursor
        if (items.isEmpty()) {
            UIHelper.openCallsignDetails(context, call)
            return
        }
        val station = items[0]

        val dialog = BottomSheetDialog(context)
        val composeView = ComposeView(context).apply {
            setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AprsTheme {
                    StationBottomSheetContent(
                        station = station,
                        myLat = myLat,
                        myLon = myLon,
                        onSendMessage = {
                            dialog.dismiss()
                            context.startActivity(
                                Intent(context, MessageActivity::class.java).putExtra("call", station.call)
                            )
                        },
                        onViewDetails = {
                            dialog.dismiss()
                            UIHelper.openCallsignDetails(context, station.call)
                        },
                        onNavigate = {
                            dialog.dismiss()
                            val lat = station.lat / 1000000.0
                            val lon = station.lon / 1000000.0
                            val uri = "geo:$lat,$lon?q=$lat,$lon(${Uri.encode(station.call)})".toUri()
                            val intent = Intent(Intent.ACTION_VIEW, uri)
                            context.startActivity(intent)
                        }
                    )
                }
            }
        }
        dialog.setContentView(composeView)
        dialog.show()
    }
}

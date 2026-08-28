package org.aprsdroid.app.ui.screen

import android.location.Location
import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import org.aprsdroid.app.R
import org.aprsdroid.app.model.StationItem
import org.aprsdroid.app.ui.component.StationTagRow
import org.aprsdroid.app.ui.components.SymbolBadge
import java.util.Locale

private val LETTERS = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
private fun getBearing(b: Double): String = LETTERS[(((b.toInt() + 22 + 720) % 360) / 45)]

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubStationScreen(
    myCall: String,
    isRunning: Boolean,
    stations: List<StationItem>,
    myLat: Int,
    myLon: Int,
    onSendPosition: () -> Unit,
    onToggleTracking: () -> Unit,
    onStationClick: (StationItem) -> Unit,
    onStationLongClick: (StationItem) -> Unit,
    onOpenMap: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenMessages: () -> Unit,
    onOpenSettings: () -> Unit,
    onClearLogs: () -> Unit = {}
) {
    val context = LocalContext.current
    var showTopMenu by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_hub),
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = {
                                    android.widget.Toast.makeText(
                                        context,
                                        R.string.share_diagnostic_logs_generating,
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                    org.aprsdroid.app.diagnostic.LogReportManager.shareDiagnosticReport(context)
                                }
                            )
                        }
                    )
                },
                actions = {
                    Box {
                        IconButton(onClick = { showTopMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.action_menu)
                            )
                        }
                        DropdownMenu(
                            expanded = showTopMenu,
                            onDismissRequest = { showTopMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.preferences)) },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                onClick = {
                                    showTopMenu = false
                                    onOpenSettings()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.clear_log)) },
                                leadingIcon = { Icon(Icons.Default.DeleteSweep, contentDescription = null) },
                                onClick = {
                                    showTopMenu = false
                                    showClearConfirmDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.share_diagnostic_logs)) },
                                leadingIcon = { Icon(Icons.Default.BugReport, contentDescription = null) },
                                onClick = {
                                    showTopMenu = false
                                    org.aprsdroid.app.diagnostic.LogReportManager.shareDiagnosticReport(context)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.about)) },
                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                onClick = {
                                    showTopMenu = false
                                    showAboutDialog = true
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onSendPosition,
                icon = {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = null
                    )
                },
                text = {
                    Text(
                        text = stringResource(R.string.singlelog),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TrackingStatusCard(
                myCall = myCall,
                isRunning = isRunning,
                onToggleTracking = onToggleTracking
            )

            if (stations.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.empty_logview).substringBefore('\n'),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(
                        start = 14.dp,
                        top = 0.dp,
                        end = 14.dp,
                        bottom = 96.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(stations, key = { it.call }) { item ->
                        val isMyOwn = item.call.equals(myCall, ignoreCase = true)
                        StationCardItem(
                            item = item,
                            isMyOwn = isMyOwn,
                            myLat = myLat,
                            myLon = myLon,
                            onClick = { onStationClick(item) },
                            onLongClick = { onStationLongClick(item) }
                        )
                    }
                }
            }
        }
    }

    if (showAboutDialog) {
        val dialogContext = LocalContext.current
        org.aprsdroid.app.ui.component.AboutDialogContent(
            onDismiss = { showAboutDialog = false },
            onOpenGithub = {
                val intent = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    "https://github.com/nimenhagg/aprsdroid-ic705".toUri(),
                )
                dialogContext.startActivity(intent)
            }
        )
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text(stringResource(R.string.clear_log)) },
            text = { Text(stringResource(R.string.confirm_clear_stations_and_logs)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearLogs()
                        showClearConfirmDialog = false
                    }
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun TrackingStatusCard(
    myCall: String,
    isRunning: Boolean,
    onToggleTracking: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = myCall.ifBlank { "—" },
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .background(
                                color = if (isRunning) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline
                                },
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.aprsservice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Button(
                onClick = onToggleTracking,
                modifier = Modifier.height(44.dp),
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(if (isRunning) R.string.stoplog else R.string.startlog),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StationCardItem(
    item: StationItem,
    isMyOwn: Boolean,
    myLat: Int,
    myLon: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    val cardShape = RoundedCornerShape(16.dp)

    val containerColor = if (isMyOwn) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    val outlineColor = if (isMyOwn) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    val callColor = if (isMyOwn) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Card(
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(outlineColor),
            width = if (isMyOwn) 1.5.dp else 1.dp
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SymbolBadge(
                symbol = item.symbol,
                size = 46.dp
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.call,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 17.sp,
                        color = callColor
                    )

                    Column(horizontalAlignment = Alignment.End) {
                        val hasMyPosition = myLat != 0 || myLon != 0
                        val distStr = if (hasMyPosition) {
                            val dist = remember(myLat, myLon, item.lat, item.lon) {
                                val results = FloatArray(2)
                                val mcd = 1000000.0
                                Location.distanceBetween(
                                    myLat / mcd,
                                    myLon / mcd,
                                    item.lat / mcd,
                                    item.lon / mcd,
                                    results
                                )
                                results
                            }
                            String.format(
                                Locale.US,
                                "%1.1f km %s",
                                dist[0] / 1000.0,
                                getBearing(dist[1].toDouble())
                            )
                        } else {
                            "—"
                        }
                        val age = DateUtils.getRelativeTimeSpanString(context, item.ts).toString()

                        Text(
                            text = distStr,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = age,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }

                if (item.isFmo || !item.qrg.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    StationTagRow(item)
                }

                val comment = item.comment
                if (!comment.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = comment,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

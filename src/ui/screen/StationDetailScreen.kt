package org.aprsdroid.app.ui.screen

import android.location.Location
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.aprsdroid.app.model.LogPostItem
import org.aprsdroid.app.model.StationItem
import org.aprsdroid.app.ui.components.SymbolBadge
import java.util.Locale

private val LETTERS = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
private fun getBearing(b: Double): String = LETTERS[(((b.toInt() + 22 + 720) % 360) / 45)]

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationDetailScreen(
    targetCall: String,
    stationItem: StationItem?,
    ssidList: List<StationItem>,
    postList: List<LogPostItem>,
    myLat: Int,
    myLon: Int,
    onBack: () -> Unit,
    onSendMessage: (String) -> Unit,
    onOpenMap: (String) -> Unit,
    onOpenQrz: (String) -> Unit,
    onOpenAprsFi: (String) -> Unit,
    onSelectSsid: (String) -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("历史数据包 (${postList.size})", "关联 SSID (${ssidList.size})")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = targetCall,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        stationItem?.let {
                            val age = DateUtils.getRelativeTimeSpanString(context, it.ts).toString()
                            Text(
                                text = age,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { onOpenMap(targetCall) }) {
                        Icon(
                            imageVector = Icons.Default.Map,
                            contentDescription = "地图查看",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { onSendMessage(targetCall) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Chat,
                            contentDescription = "发送消息",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Station Main Summary Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            SymbolBadge(
                                symbol = stationItem?.symbol ?: "/$",
                                size = 56.dp
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = targetCall,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                stationItem?.let {
                                    val dist = remember(myLat, myLon, it.lat, it.lon) {
                                        val results = FloatArray(2)
                                        val mcd = 1000000.0
                                        Location.distanceBetween(myLat / mcd, myLon / mcd, it.lat / mcd, it.lon / mcd, results)
                                        results
                                    }
                                    val distStr = String.format(Locale.US, "%1.1f km %s", dist[0] / 1000.0, getBearing(dist[1].toDouble()))
                                    Text(
                                        text = distStr,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        stationItem?.let { item ->
                            val qrg = item.qrg
                            val comment = item.comment
                            if (!qrg.isNullOrEmpty() || !comment.isNullOrEmpty()) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 12.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )

                                if (!qrg.isNullOrEmpty()) {
                                    SuggestionChip(
                                        onClick = {},
                                        label = { Text("语音频率: $qrg MHz", fontWeight = FontWeight.SemiBold) },
                                        icon = { Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                        colors = SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                            labelColor = MaterialTheme.colorScheme.onTertiaryContainer
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }

                                if (!comment.isNullOrEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = comment,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Quick Actions
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onSendMessage(targetCall) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("发消息")
                            }
                            OutlinedButton(
                                onClick = { onOpenMap(targetCall) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("地图轨迹")
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { onOpenQrz(targetCall) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("QRZ.com", fontSize = 12.sp)
                            }
                            FilledTonalButton(
                                onClick = { onOpenAprsFi(targetCall) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("aprs.fi", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Tab Selector
            item {
                PrimaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(text = title, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal) }
                        )
                    }
                }
            }

            // Tab Content
            if (selectedTab == 0) {
                if (postList.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("暂无历史数据包", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    items(postList, key = { it.id }) { post ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            )
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
                                        Text(
                                            text = status,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = post.message,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            } else {
                if (ssidList.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("无同名 SSID 台站", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    items(ssidList, key = { it.call }) { ssidItem ->
                        val isCurrent = ssidItem.call.equals(targetCall, ignoreCase = true)
                        val comment = ssidItem.comment
                        val age = DateUtils.getRelativeTimeSpanString(context, ssidItem.ts).toString()
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectSsid(ssidItem.call) },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                SymbolBadge(
                                    symbol = ssidItem.symbol,
                                    size = 36.dp
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = ssidItem.call,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (!comment.isNullOrEmpty()) {
                                        Text(
                                            text = comment,
                                            fontSize = 12.sp,
                                            color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                }
                                Text(
                                    text = age,
                                    fontSize = 11.sp,
                                    color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

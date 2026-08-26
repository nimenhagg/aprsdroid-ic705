package org.aprsdroid.app.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.aprsdroid.app.model.StationItem

@Composable
fun StationTagRow(station: StationItem, modifier: Modifier = Modifier) {
    val qrg = station.qrg
    if (!station.isFmo && qrg.isNullOrEmpty()) return

    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (station.isFmo) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(7.dp)
            ) {
                Text(
                    text = "FMO",
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        if (!qrg.isNullOrEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(7.dp)
            ) {
                Text(
                    text = qrg,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

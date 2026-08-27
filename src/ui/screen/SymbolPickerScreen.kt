package org.aprsdroid.app.ui.screen

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.aprsdroid.app.R
import org.aprsdroid.app.ui.components.SymbolBadge

data class SymbolCategory(@param:StringRes val nameRes: Int, val symbols: List<String>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SymbolPickerScreen(
    initialSymbol: String,
    onSaveSymbol: (String) -> Unit,
    onCancel: () -> Unit
) {
    val overlayableChars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"

    var selectedBaseSymbol by remember {
        val base = if (initialSymbol.length >= 2) {
            val ov = initialSymbol[0]
            if (ov != '/' && ov != '\\') "\\${initialSymbol[1]}" else initialSymbol.substring(0, 2)
        } else "/$"
        mutableStateOf(base)
    }

    var overlayText by remember {
        val ov = if (initialSymbol.isNotEmpty() && initialSymbol[0] != '/' && initialSymbol[0] != '\\') {
            initialSymbol[0].toString()
        } else ""
        mutableStateOf(ov)
    }

    val isOverlayAllowed = selectedBaseSymbol.length > 1 &&
            selectedBaseSymbol[0] != '/' &&
            overlayableChars.contains(selectedBaseSymbol[1])

    val computedFullSymbol = if (isOverlayAllowed && overlayText.isNotEmpty()) {
        "${overlayText.take(1)}${selectedBaseSymbol[1]}"
    } else {
        selectedBaseSymbol
    }

    val categories = remember {
        listOf(
            SymbolCategory(R.string.symbol_category_common, listOf("/$", "/>", "/[", "/-", "/r", "/#", "/k", "/u", "/v", "/^", "/_", "/X", "/s", "/p", "/a")),
            SymbolCategory(R.string.symbol_category_vehicle, listOf("/>", "/k", "/u", "/v", "/j", "/p", "/a", "\\>", "\\u", "\\v")),
            SymbolCategory(R.string.symbol_category_base, listOf("/-", "/r", "/#", "/T", "/S", "/H", "\\-", "\\#", "\\r")),
            SymbolCategory(R.string.symbol_category_person, listOf("/[", "/b", "/'", "\\p", "\\b", "\\s")),
            SymbolCategory(R.string.symbol_category_weather, listOf("/_", "\\_", "/W", "/w")),
            SymbolCategory(R.string.symbol_category_navigation, listOf("/^", "/X", "/s", "/Y", "\\^", "\\s")),
            SymbolCategory(R.string.symbol_category_primary, (33..126).map { "/${it.toChar()}" }),
            SymbolCategory(R.string.symbol_category_alternate, (33..126).map { "\\${it.toChar()}" })
        )
    }

    var selectedCategoryIndex by remember { mutableIntStateOf(0) }
    val currentSymbolList = categories[selectedCategoryIndex].symbols

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.symbol_picker_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = { onSaveSymbol(computedFullSymbol) },
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .height(44.dp),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(stringResource(R.string.action_save), fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    SymbolBadge(
                        symbol = computedFullSymbol,
                        size = 52.dp
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.symbol_current_code, computedFullSymbol),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (isOverlayAllowed) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.symbol_overlay_supported),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (isOverlayAllowed) {
                        OutlinedTextField(
                            value = overlayText,
                            onValueChange = { input ->
                                val filtered = input.uppercase().filter { overlayableChars.contains(it) }
                                overlayText = filtered.take(1)
                            },
                            label = { Text(stringResource(R.string.symbol_overlay), fontSize = 11.sp) },
                            modifier = Modifier.width(64.dp),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
                        )
                    }
                }
            }

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(categories) { index, cat ->
                    val selected = selectedCategoryIndex == index
                    FilterChip(
                        selected = selected,
                        onClick = { selectedCategoryIndex = index },
                        label = {
                            Text(
                                text = stringResource(cat.nameRes),
                                fontSize = 14.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        modifier = Modifier.height(36.dp),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 56.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(currentSymbolList, key = { it }) { sym ->
                    val isSelected = selectedBaseSymbol == sym ||
                            (selectedBaseSymbol.length > 1 && sym.length > 1 &&
                                    selectedBaseSymbol[1] == sym[1] && selectedBaseSymbol[0] == sym[0])

                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerLow
                            )
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                                },
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable { selectedBaseSymbol = sym },
                        contentAlignment = Alignment.Center
                    ) {
                        SymbolBadge(
                            symbol = sym,
                            size = 42.dp,
                            drawContainer = false
                        )

                        if (isSelected) {
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(5.dp)
                                    .size(18.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

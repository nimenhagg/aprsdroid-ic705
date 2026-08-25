package org.aprsdroid.app.ui.screen

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.Color
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

    // Parse initial symbol and overlay
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

    // Categories
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
                    Button(
                        onClick = { onSaveSymbol(computedFullSymbol) },
                        modifier = Modifier.padding(end = 8.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.action_save))
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
            // Preview & Overlay Input Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Preview Icon Box
                    SymbolBadge(
                        symbol = computedFullSymbol,
                        size = 56.dp
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.symbol_current_code, computedFullSymbol),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(
                                if (isOverlayAllowed) R.string.symbol_overlay_supported else R.string.symbol_standard,
                            ),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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

            // Category Scrollable Chips
            PrimaryScrollableTabRow(
                selectedTabIndex = selectedCategoryIndex,
                edgePadding = 16.dp,
                containerColor = Color.Transparent,
                divider = {}
            ) {
                categories.forEachIndexed { index, cat ->
                    FilterChip(
                        selected = selectedCategoryIndex == index,
                        onClick = { selectedCategoryIndex = index },
                        label = { Text(stringResource(cat.nameRes)) },
                        modifier = Modifier.padding(horizontal = 4.dp),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Grid of Symbols
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 56.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(currentSymbolList, key = { it }) { sym ->
                    val isSelected = selectedBaseSymbol == sym || (selectedBaseSymbol.length > 1 && sym.length > 1 && selectedBaseSymbol[1] == sym[1] && selectedBaseSymbol[0] == sym[0])
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerLow
                            )
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable {
                                selectedBaseSymbol = sym
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        SymbolBadge(
                            symbol = sym,
                            size = 36.dp
                        )
                    }
                }
            }
        }
    }
}

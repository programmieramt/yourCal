package com.example.calorietracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.calorietracker.data.ExerciseEntry
import com.example.calorietracker.data.FavoriteEntry
import com.example.calorietracker.data.FoodEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onOpenSettings: () -> Unit,
) {
    val summary by viewModel.weekSummary.collectAsState()
    val dailyCalories by viewModel.dailyCalories.collectAsState()
    val entriesByDay by viewModel.entriesByDay.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val dayOptions = remember { viewModel.planDayOptions() }
    var selectedDayStart by rememberSaveable { mutableStateOf(dayOptions.first().dayStart) }
    var description by rememberSaveable { mutableStateOf("") }
    var exerciseCaloriesInput by rememberSaveable { mutableStateOf("") }
    var editingEntry by remember { mutableStateOf<FoodEntry?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    editingEntry?.let { entry ->
        EditEntryDialog(
            entry = entry,
            onDismiss = { editingEntry = null },
            onSave = {
                viewModel.updateEntry(it)
                editingEntry = null
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kalorien Tracker") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Einstellungen")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val keyboardController = LocalSoftwareKeyboardController.current
        val focusManager = LocalFocusManager.current
        val submitEntry: () -> Unit = {
            val text = description
            if (text.isNotBlank() && !isLoading) {
                description = ""
                viewModel.addEntry(text, selectedDayStart)
                focusManager.clearFocus()
                keyboardController?.hide()
            }
        }
        val parsedExerciseCalories = exerciseCaloriesInput.toIntOrNull()
        val submitExercise: () -> Unit = {
            val calories = parsedExerciseCalories
            if (calories != null && calories > 0) {
                viewModel.addExerciseEntry(calories)
                exerciseCaloriesInput = ""
                focusManager.clearFocus()
                keyboardController?.hide()
            }
        }

        // Ein einziger LazyColumn für den ganzen Screen: eine LazyColumn *innerhalb*
        // einer scrollbaren Column würde mit unendlicher Höhen-Constraint abstürzen,
        // deshalb sind Karte/Chart/Eingabe/Liste hier alles Items derselben Liste.
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .imePadding(),
        ) {
            item {
                Column {
                    WeekProgressCard(summary)

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        "Tagesverteilung",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    WeekBarChart(days = dailyCalories, dailyTargetCalories = summary.dailyTargetCalories)
                }
            }

            item {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        "Für welchen Tag?",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(dayOptions, key = { it.dayStart }) { option ->
                            FilterChip(
                                selected = selectedDayStart == option.dayStart,
                                onClick = { selectedDayStart = option.dayStart },
                                label = { Text(option.label) },
                            )
                        }
                    }
                }
            }

            if (favorites.isNotEmpty()) {
                item {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Favoriten",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(favorites, key = { it.id }) { favorite ->
                                FavoriteChip(
                                    favorite = favorite,
                                    onClick = { viewModel.addFromFavorite(favorite, selectedDayStart) },
                                    onRemove = { viewModel.removeFavorite(favorite) },
                                )
                            }
                        }
                    }
                }
            }

            item {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Was hast du gegessen?") },
                        placeholder = { Text("z.B. 150g Reis mit Kikkoman Sushi-Sauce") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { submitEntry() }),
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = submitEntry,
                        enabled = description.isNotBlank() && !isLoading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("Hinzufügen")
                        }
                    }
                }
            }

            item {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Sport",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = exerciseCaloriesInput,
                            onValueChange = { exerciseCaloriesInput = it },
                            label = { Text("Verbrannte kcal") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Send,
                            ),
                            keyboardActions = KeyboardActions(onSend = { submitExercise() }),
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = submitExercise,
                            enabled = parsedExerciseCalories != null && parsedExerciseCalories > 0,
                        ) {
                            Text("Eintragen")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        "Letzte 7 Tage",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            entriesByDay.forEach { day ->
                item(key = "header_${day.dayStart}") {
                    DayHeader(day = day)
                }
                items(day.exerciseEntries, key = { "ex_${it.id}" }) { entry ->
                    ExerciseEntryRow(entry = entry, onDelete = { viewModel.deleteExerciseEntry(entry) })
                    HorizontalDivider()
                }
                items(day.entries, key = { "food_${it.id}" }) { entry ->
                    FoodEntryRow(
                        entry = entry,
                        onDelete = { viewModel.deleteEntry(entry) },
                        onEdit = { editingEntry = entry },
                        onFavorite = { viewModel.addFavorite(entry) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun WeekProgressCard(summary: WeekSummary) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val headline = if (summary.totalExerciseCalories > 0) {
                "${summary.netCalories} (${summary.totalCalories}-${summary.totalExerciseCalories}) / " +
                    "${summary.goalCalories} kcal"
            } else {
                "${summary.netCalories} / ${summary.goalCalories} kcal"
            }
            Text(
                headline,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            val remainingText = if (summary.remainingCalories >= 0) {
                "${summary.remainingCalories} übrig"
            } else {
                "${-summary.remainingCalories} über Ziel"
            }
            Text(remainingText, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { summary.progress.coerceAtMost(1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            MacroChip("Protein", summary.totalProteinG)
            MacroChip("Kohlenhydrate", summary.totalCarbsG)
            MacroChip("Fett", summary.totalFatG)
        }
    }
}

/**
 * Gruppierter Balken pro Tag: heller Balken = Tagesziel (Wochenziel / 7),
 * dunkler Balken daneben = tatsächlich gegessene Kalorien an dem Tag.
 * Bewusst eine einzige neutrale Farbe unabhängig davon, ob das Ziel
 * überschritten wurde — die Balkenhöhe im Vergleich zum Zielbalken zeigt das
 * schon von selbst, ohne eine Warnfarbe, die vom Erfassen abschreckt.
 */
@Composable
private fun WeekBarChart(days: List<DayCalories>, dailyTargetCalories: Int) {
    if (days.isEmpty()) return

    val targetColor = MaterialTheme.colorScheme.surfaceVariant
    val actualColor = MaterialTheme.colorScheme.primary

    val maxValue = remember(days, dailyTargetCalories) {
        val maxDay = days.maxOf { it.calories }
        maxOf(maxDay, dailyTargetCalories, 1).toFloat() * 1.15f
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
        ) {
            val groupWidth = size.width / days.size
            val barWidth = groupWidth * 0.28f
            val gap = groupWidth * 0.08f
            val sidePadding = (groupWidth - (barWidth * 2 + gap)) / 2f
            val corner = CornerRadius(4.dp.toPx(), 4.dp.toPx())

            days.forEachIndexed { index, day ->
                val groupLeft = index * groupWidth
                val targetHeight = (dailyTargetCalories / maxValue) * size.height
                val actualHeight = (day.calories.coerceAtLeast(0) / maxValue) * size.height

                drawRoundRect(
                    color = targetColor,
                    topLeft = Offset(groupLeft + sidePadding, size.height - targetHeight),
                    size = Size(barWidth, targetHeight),
                    cornerRadius = corner,
                )
                drawRoundRect(
                    color = actualColor,
                    topLeft = Offset(groupLeft + sidePadding + barWidth + gap, size.height - actualHeight),
                    size = Size(barWidth, actualHeight),
                    cornerRadius = corner,
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            days.forEach { day ->
                Text(
                    day.label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
                    color = if (day.isToday) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            LegendItem(color = targetColor, label = "Ziel/Tag")
            Spacer(modifier = Modifier.width(16.dp))
            LegendItem(color = actualColor, label = "Netto")
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, shape = RoundedCornerShape(2.dp)),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MacroChip(label: String, grams: Double) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("${grams.toInt()} g", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun DayHeader(day: DayEntries) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            day.label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        val text = if (day.exerciseCalories > 0) {
            "${day.netCalories} (${day.foodCalories}-${day.exerciseCalories}) kcal"
        } else {
            "${day.netCalories} kcal"
        }
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val timeFormat = SimpleDateFormat("EEE HH:mm", Locale.GERMAN)

@Composable
fun FoodEntryRow(
    entry: FoodEntry,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onFavorite: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            val isPlanned = entry.timestamp > System.currentTimeMillis()
            Text(
                if (isPlanned) "${entry.description} · geplant" else entry.description,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "${entry.calories} kcal · P ${entry.proteinG.toInt()}g · " +
                    "KH ${entry.carbsG.toInt()}g · F ${entry.fatG.toInt()}g · " +
                    timeFormat.format(Date(entry.timestamp)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onFavorite) {
            Icon(Icons.Filled.Star, contentDescription = "Als Favorit speichern")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Löschen")
        }
    }
}

@Composable
fun ExerciseEntryRow(entry: ExerciseEntry, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.DirectionsRun,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Sport", style = MaterialTheme.typography.bodyLarge)
            Text(
                "-${entry.caloriesBurned} kcal · ${timeFormat.format(Date(entry.timestamp))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Löschen")
        }
    }
}

@Composable
private fun FavoriteChip(
    favorite: FavoriteEntry,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    AssistChip(
        onClick = onClick,
        label = {
            Text(
                "${favorite.description.take(24)} · ${favorite.calories} kcal",
                style = MaterialTheme.typography.bodySmall,
            )
        },
        trailingIcon = {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Favorit entfernen",
                modifier = Modifier
                    .size(16.dp)
                    .clickable(onClick = onRemove),
            )
        },
    )
}

@Composable
fun EditEntryDialog(
    entry: FoodEntry,
    onDismiss: () -> Unit,
    onSave: (FoodEntry) -> Unit,
) {
    var description by remember(entry.id) { mutableStateOf(entry.description) }
    var calories by remember(entry.id) { mutableStateOf(entry.calories.toString()) }
    var proteinG by remember(entry.id) { mutableStateOf(entry.proteinG.toString()) }
    var carbsG by remember(entry.id) { mutableStateOf(entry.carbsG.toString()) }
    var fatG by remember(entry.id) { mutableStateOf(entry.fatG.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Eintrag bearbeiten") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Beschreibung") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = calories,
                    onValueChange = { calories = it },
                    label = { Text("Kalorien (kcal)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = proteinG,
                    onValueChange = { proteinG = it },
                    label = { Text("Protein (g)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = carbsG,
                    onValueChange = { carbsG = it },
                    label = { Text("Kohlenhydrate (g)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = fatG,
                    onValueChange = { fatG = it },
                    label = { Text("Fett (g)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    entry.copy(
                        description = description.trim().ifBlank { entry.description },
                        calories = calories.toIntOrNull() ?: entry.calories,
                        proteinG = proteinG.toDoubleOrNull() ?: entry.proteinG,
                        carbsG = carbsG.toDoubleOrNull() ?: entry.carbsG,
                        fatG = fatG.toDoubleOrNull() ?: entry.fatG,
                    ),
                )
            }) {
                Text("Speichern")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        },
    )
}

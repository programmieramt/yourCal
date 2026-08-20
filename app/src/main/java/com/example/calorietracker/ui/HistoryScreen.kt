package com.example.calorietracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.calorietracker.data.FoodEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: MainViewModel) {
    val weeklyTrend by viewModel.weeklyTrend.collectAsState()
    val historyByDay by viewModel.historyByDay.collectAsState()
    val weeklyGoal by viewModel.settingsStore.weeklyGoalFlow.collectAsState()
    var editingEntry by remember { mutableStateOf<FoodEntry?>(null) }

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
        topBar = { TopAppBar(title = { Text("Historie") }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            item {
                Column {
                    Text(
                        "Gewicht & Kalorien",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (weeklyTrend.isEmpty()) {
                        Text(
                            "Noch keine Daten für einen Trend.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        WeightCalorieTrendChart(points = weeklyTrend, weeklyGoal = weeklyGoal)
                    }

                    if (weeklyTrend.any { it.avgBodyFatPercent != null }) {
                        Spacer(modifier = Modifier.height(24.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Körperzusammensetzung",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        BodyFatTrendChart(points = weeklyTrend)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Alle Einträge",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            if (historyByDay.isEmpty()) {
                item {
                    Text(
                        "Noch keine Einträge erfasst.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            historyByDay.forEach { day ->
                item(key = "header_${day.dayStart}") {
                    DayHeader(day = day, dailyTargetCalories = weeklyGoal / 7)
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
                        onRepeat = { viewModel.repeatEntry(entry) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

/**
 * Zwei ausgerichtete Diagramme untereinander statt einer Doppel-Achse: Gewicht
 * oben, Netto-Kalorien pro Woche unten, beide über denselben Wochen-Index (x).
 * So lässt sich der Zusammenhang visuell ablesen, ohne zwei Skalen in einem
 * Chart zu vermischen (irreführend bei unterschiedlichen Einheiten/Bereichen).
 */
@Composable
private fun WeightCalorieTrendChart(points: List<WeeklyPoint>, weeklyGoal: Int) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.surfaceVariant
    val barColor = MaterialTheme.colorScheme.primary

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Gewicht (kg)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))

        val weightPoints = points.mapIndexedNotNull { index, point -> point.avgWeightKg?.let { index to it } }
        if (weightPoints.size >= 2 && points.size >= 2) {
            val minWeight = weightPoints.minOf { it.second }
            val maxWeight = weightPoints.maxOf { it.second }
            val range = (maxWeight - minWeight).takeIf { it > 0.1 } ?: 1.0
            val paddedMin = minWeight - range * 0.15
            val paddedRange = range * 1.3

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
            ) {
                val stepX = size.width / (points.size - 1)

                fun yFor(weight: Double): Float {
                    val fraction = ((weight - paddedMin) / paddedRange).toFloat()
                    return size.height - fraction * size.height
                }

                // Nur direkt aufeinanderfolgende Wochen mit Gewichtsdaten verbinden —
                // fehlende Wochen dazwischen erzeugen bewusst eine Lücke, keine Linie.
                for (i in 0 until weightPoints.size - 1) {
                    val (indexA, weightA) = weightPoints[i]
                    val (indexB, weightB) = weightPoints[i + 1]
                    if (indexB == indexA + 1) {
                        drawLine(
                            color = lineColor,
                            start = Offset(indexA * stepX, yFor(weightA)),
                            end = Offset(indexB * stepX, yFor(weightB)),
                            strokeWidth = 2.5.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                }
                weightPoints.forEach { (index, weight) ->
                    drawCircle(color = lineColor, radius = 4.dp.toPx(), center = Offset(index * stepX, yFor(weight)))
                }
            }
        } else {
            Text(
                "Noch nicht genug Gewichtsdaten für einen Verlauf.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Netto-Kalorien pro Woche (Ziel: $weeklyGoal kcal)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))

        val maxValue = remember(points, weeklyGoal) {
            val maxWeek = points.maxOfOrNull { it.netCalories.coerceAtLeast(0) } ?: 0
            maxOf(maxWeek, weeklyGoal, 1).toFloat() * 1.15f
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp),
        ) {
            val groupWidth = size.width / points.size
            val barWidth = groupWidth * 0.5f
            val corner = CornerRadius(3.dp.toPx(), 3.dp.toPx())

            val goalY = size.height - (weeklyGoal / maxValue) * size.height
            drawLine(
                color = gridColor,
                start = Offset(0f, goalY),
                end = Offset(size.width, goalY),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
            )

            points.forEachIndexed { index, point ->
                val barHeight = (point.netCalories.coerceAtLeast(0) / maxValue) * size.height
                val left = index * groupWidth + (groupWidth - barWidth) / 2f
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(left, size.height - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = corner,
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            points.forEach { point ->
                Text(
                    point.label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Körperfettanteil pro Woche als Linienverlauf, bewusst ohne Zielkurve — laut
 * dem Trainingsplan (bodyComposition.noTargetCurve) ist das eine reine
 * Beobachtungsgröße, kein hartes Ziel wie das Gewicht. Fett-/Magermasse
 * darunter nur als aktuelle Zahl, nicht als eigener Chart — hält die Ansicht
 * überschaubar; die Rechnung (rollingWeightKg * bodyFat% / 100) steckt in
 * WeeklyPoint.avgFatMassKg/avgLeanMassKg.
 */
@Composable
private fun BodyFatTrendChart(points: List<WeeklyPoint>) {
    val lineColor = MaterialTheme.colorScheme.primary

    val fatPoints = points.mapIndexedNotNull { index, point -> point.avgBodyFatPercent?.let { index to it } }
    if (fatPoints.size >= 2) {
        val minFat = fatPoints.minOf { it.second }
        val maxFat = fatPoints.maxOf { it.second }
        val range = (maxFat - minFat).takeIf { it > 0.1 } ?: 1.0
        val paddedMin = minFat - range * 0.15
        val paddedRange = range * 1.3

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp),
        ) {
            val stepX = size.width / (points.size - 1).coerceAtLeast(1)

            fun yFor(percent: Double): Float {
                val fraction = ((percent - paddedMin) / paddedRange).toFloat()
                return size.height - fraction * size.height
            }

            for (i in 0 until fatPoints.size - 1) {
                val (indexA, valueA) = fatPoints[i]
                val (indexB, valueB) = fatPoints[i + 1]
                if (indexB == indexA + 1) {
                    drawLine(
                        color = lineColor,
                        start = Offset(indexA * stepX, yFor(valueA)),
                        end = Offset(indexB * stepX, yFor(valueB)),
                        strokeWidth = 2.5.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }
            fatPoints.forEach { (index, value) ->
                drawCircle(color = lineColor, radius = 4.dp.toPx(), center = Offset(index * stepX, yFor(value)))
            }
        }
    } else {
        Text(
            "Noch nicht genug Körperfett-Daten für einen Verlauf.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    val latest = points.lastOrNull { it.avgBodyFatPercent != null }
    if (latest != null) {
        Spacer(modifier = Modifier.height(8.dp))
        val fatMass = latest.avgFatMassKg
        val leanMass = latest.avgLeanMassKg
        val muscleMass = latest.avgMuscleMassKg
        val summary = buildString {
            append("${"%.1f".format(latest.avgBodyFatPercent)}% Körperfett")
            if (fatMass != null && leanMass != null) {
                append(" · ~${"%.1f".format(fatMass)} kg Fettmasse · ~${"%.1f".format(leanMass)} kg Magermasse")
            }
            if (muscleMass != null) {
                append(" · ${"%.1f".format(muscleMass)} kg Muskelmasse (Waage)")
            }
        }
        Text(
            summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

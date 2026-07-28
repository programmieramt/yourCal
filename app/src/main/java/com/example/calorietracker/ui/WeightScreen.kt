package com.example.calorietracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.calorietracker.data.WeightEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val weightEntries by viewModel.weightEntries.collectAsState()
    var weightInput by rememberSaveable { mutableStateOf("") }
    val parsedWeight = weightInput.replace(",", ".").toDoubleOrNull()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val submitWeight: () -> Unit = {
        parsedWeight?.let {
            viewModel.addWeightEntry(it)
            weightInput = ""
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gewichtsverlauf") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .imePadding(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = weightInput,
                    onValueChange = { weightInput = it },
                    label = { Text("Gewicht (kg)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Send,
                    ),
                    keyboardActions = KeyboardActions(onSend = { submitWeight() }),
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = submitWeight,
                    enabled = parsedWeight != null,
                ) {
                    Text("Eintragen")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (weightEntries.size >= 2) {
                Text(
                    "Verlauf",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                WeightLineChart(entries = weightEntries.reversed())
                Spacer(modifier = Modifier.height(16.dp))
            }

            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "Einträge",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(weightEntries, key = { it.id }) { entry ->
                    WeightRow(entry = entry, onDelete = { viewModel.deleteWeightEntry(entry) })
                    HorizontalDivider()
                }
            }
        }
    }
}

/** Einfacher Linienverlauf, älteste Werte links. Gestrichelte Linie = Durchschnitt. */
@Composable
private fun WeightLineChart(entries: List<WeightEntry>) {
    if (entries.size < 2) return

    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.surfaceVariant

    val minWeight = entries.minOf { it.weightKg }
    val maxWeight = entries.maxOf { it.weightKg }
    val range = (maxWeight - minWeight).takeIf { it > 0.1 } ?: 1.0
    val paddedMin = minWeight - range * 0.15
    val paddedRange = range * 1.3

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
    ) {
        val stepX = size.width / (entries.size - 1)

        fun yFor(weight: Double): Float {
            val fraction = ((weight - paddedMin) / paddedRange).toFloat()
            return size.height - fraction * size.height
        }

        val avgY = yFor(entries.map { it.weightKg }.average())
        drawLine(
            color = gridColor,
            start = Offset(0f, avgY),
            end = Offset(size.width, avgY),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
        )

        val points = entries.mapIndexed { index, entry -> Offset(index * stepX, yFor(entry.weightKg)) }
        for (i in 0 until points.size - 1) {
            drawLine(
                color = lineColor,
                start = points[i],
                end = points[i + 1],
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        points.forEach { point ->
            drawCircle(color = lineColor, radius = 4.dp.toPx(), center = point)
        }
    }
}

private val weightDateFormat = SimpleDateFormat("EEE, dd.MM.yyyy", Locale.GERMAN)

@Composable
private fun WeightRow(entry: WeightEntry, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                "${entry.weightKg} kg",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                weightDateFormat.format(Date(entry.timestamp)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Löschen")
        }
    }
}

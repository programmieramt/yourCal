package com.example.calorietracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.calorietracker.data.LiveCalorieTarget
import com.example.calorietracker.data.PlanSession
import com.example.calorietracker.data.PlanWeek
import com.example.calorietracker.data.TrainingPlan
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val dateFormat = DateTimeFormatter.ofPattern("dd.MM.")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingPlanScreen(viewModel: MainViewModel) {
    val plan = viewModel.trainingPlan
    val currentWeek by viewModel.currentPlanWeek.collectAsState()
    val liveTarget by viewModel.liveCalorieTarget.collectAsState()
    val completions by viewModel.sessionCompletions.collectAsState()
    val onToggleSession: (Int, Int, Boolean) -> Unit = { week, index, checked ->
        viewModel.setSessionCompleted(week, index, checked)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Trainingsplan") }) },
    ) { padding ->
        if (plan == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Kein Trainingsplan hinterlegt.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }

        var expandedWeek by rememberSaveable { mutableStateOf(currentWeek?.week ?: -1) }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            item { RaceHeaderCard(plan) }

            if (currentWeek != null) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    CurrentWeekCard(plan, currentWeek!!, liveTarget, completions, onToggleSession)
                }
            } else {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    val today = LocalDate.now()
                    val message = if (today.isBefore(plan.planStartDate)) {
                        val days = ChronoUnit.DAYS.between(today, plan.planStartDate)
                        "Plan startet in $days Tagen (${plan.planStartDate.format(dateFormat)})."
                    } else {
                        "Plan-Zeitraum ist vorbei."
                    }
                    Text(message, style = MaterialTheme.typography.bodyMedium)
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Alle Wochen", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
            }

            items(plan.weeks, key = { it.week }) { week ->
                WeekRow(
                    week = week,
                    isCurrent = week.week == currentWeek?.week,
                    liveTarget = if (week.week == currentWeek?.week) liveTarget else null,
                    completions = completions,
                    expanded = expandedWeek == week.week,
                    onToggle = { expandedWeek = if (expandedWeek == week.week) -1 else week.week },
                    onToggleSession = onToggleSession,
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun RaceHeaderCard(plan: TrainingPlan) {
    val today = LocalDate.now()
    val daysToRace = ChronoUnit.DAYS.between(today, plan.race.date)
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(plan.race.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "${plan.race.date.format(dateFormat)} ${plan.race.date.year} · Ziel ${plan.race.goalTime} " +
                "(${plan.race.goalPacePerKm}/km)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            if (daysToRace >= 0) "Noch $daysToRace Tage bis zum Rennen" else "Rennen war vor ${-daysToRace} Tagen",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CurrentWeekCard(
    plan: TrainingPlan,
    week: PlanWeek,
    liveTarget: LiveCalorieTarget?,
    completions: Set<Pair<Int, Int>>,
    onToggleSession: (Int, Int, Boolean) -> Unit,
) {
    val phase = plan.phases.firstOrNull { it.id == week.phaseId }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Woche ${week.week} von ${plan.weeks.size} · ${week.phaseName}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "${week.startDate.format(dateFormat)} – ${week.endDate.format(dateFormat)} · ${week.volumeKm} km geplant",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (phase != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(phase.focus, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(12.dp))
            if (liveTarget != null) {
                Text(
                    "${liveTarget.targetKcalPerDay} kcal/Tag · ${liveTarget.targetKcalPerWeek} kcal/Woche",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Live berechnet aus Ø-Gewicht ${"%.1f".format(liveTarget.rollingWeightKg)} kg " +
                        "(letzte Wiege-Einträge)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "${week.placeholderTargetKcalPerDay} kcal/Tag · ${week.placeholderTargetKcalPerWeek} kcal/Woche",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Platzhalter (kein Gewicht erfasst) — trag dein Gewicht ein, damit das Ziel live " +
                        "berechnet wird.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (week.milestone != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    week.milestone,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            val doneCount = week.sessions.indices.count { completions.contains(week.week to it) }
            Text(
                "$doneCount von ${week.sessions.size} Einheiten erledigt",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            week.sessions.forEachIndexed { index, session ->
                SessionRow(
                    session = session,
                    checked = completions.contains(week.week to index),
                    onCheckedChange = { checked -> onToggleSession(week.week, index, checked) },
                )
            }
        }
    }
}

@Composable
private fun SessionRow(session: PlanSession, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        if (session.day != null) {
            Text(
                session.day,
                modifier = Modifier.padding(end = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column {
            Text(
                session.type,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (session.isRest || checked) FontWeight.Normal else FontWeight.Bold,
                color = if (session.isRest || checked) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None,
            )
            Text(
                session.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None,
            )
        }
    }
}

@Composable
private fun WeekRow(
    week: PlanWeek,
    isCurrent: Boolean,
    liveTarget: LiveCalorieTarget?,
    completions: Set<Pair<Int, Int>>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onToggleSession: (Int, Int, Boolean) -> Unit,
) {
    val doneCount = week.sessions.indices.count { completions.contains(week.week to it) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "W${week.week} · ${week.startDate.format(dateFormat)}–${week.endDate.format(dateFormat)} · " +
                    week.phaseName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            )
            val kcalPerDay = liveTarget?.targetKcalPerDay ?: week.placeholderTargetKcalPerDay
            val doneSuffix = if (doneCount > 0) " · $doneCount/${week.sessions.size} ✓" else ""
            Text(
                "$kcalPerDay kcal$doneSuffix",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (week.milestone != null) {
            Text(
                week.milestone,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
            )
        }
        if (expanded) {
            Spacer(modifier = Modifier.height(6.dp))
            week.sessions.forEachIndexed { index, session ->
                SessionRow(
                    session = session,
                    checked = completions.contains(week.week to index),
                    onCheckedChange = { checked -> onToggleSession(week.week, index, checked) },
                )
            }
        }
    }
}

package com.example.calorietracker.widget

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.calorietracker.MainActivity
import com.example.calorietracker.data.AppDatabase
import com.example.calorietracker.data.SettingsStore
import java.util.Calendar
import kotlinx.coroutines.flow.first

/** Feste Graustufen statt Material-You-Dynamikfarben — konsistent mit dem E-Ink-Theme der App. */
private data class WidgetColors(
    val surface: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val track: Color,
)

private val LightWidgetColors = WidgetColors(
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF000000),
    onSurfaceVariant = Color(0xFF3A3A3A),
    outline = Color(0xFF6E6E6E),
    track = Color(0xFFE3E3E3),
)

private val DarkWidgetColors = WidgetColors(
    surface = Color(0xFF000000),
    onSurface = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFFCCCCCC),
    outline = Color(0xFF8A8A8A),
    track = Color(0xFF2A2A2A),
)

/**
 * Zeigt Nettokalorien (gegessen minus Sport) für heute plus Tagesziel.
 * Tippen auf "+" öffnet die App direkt mit Fokus im Eingabefeld, Tippen
 * irgendwo sonst öffnet die App normal.
 */
class CalorieWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db = AppDatabase.getInstance(context)
        val settingsStore = SettingsStore(context)

        val dayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val now = System.currentTimeMillis()

        val food = db.foodDao().observeSince(dayStart).first().filter { it.timestamp <= now }
        val exercise = db.exerciseDao().observeSince(dayStart).first()
        val weeklyGoal = settingsStore.weeklyGoalFlow.first()

        val net = food.sumOf { it.calories } - exercise.sumOf { it.caloriesBurned }
        val target = weeklyGoal / 7
        val remaining = target - net

        val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val colors = if (nightMode == Configuration.UI_MODE_NIGHT_YES) DarkWidgetColors else LightWidgetColors

        provideContent {
            CalorieWidgetContent(net = net, target = target, remaining = remaining, colors = colors)
        }
    }
}

@Composable
private fun CalorieWidgetContent(net: Int, target: Int, remaining: Int, colors: WidgetColors) {
    val context = androidx.glance.LocalContext.current
    val openAppIntent = Intent(context, MainActivity::class.java)
    val quickAddIntent = Intent(context, MainActivity::class.java).apply {
        putExtra(MainActivity.EXTRA_QUICK_ADD, true)
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(colors.surface))
            .padding(12.dp)
            .clickable(actionStartActivity(openAppIntent)),
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Text(
                "CalorieTracker",
                style = TextStyle(fontSize = 12.sp, color = ColorProvider(colors.onSurfaceVariant)),
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            Box(
                modifier = GlanceModifier
                    .size(28.dp)
                    .background(ColorProvider(colors.track))
                    .clickable(actionStartActivity(quickAddIntent)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "+",
                    style = TextStyle(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorProvider(colors.onSurface),
                    ),
                )
            }
        }
        Spacer(modifier = GlanceModifier.size(6.dp))
        Text(
            "$net / $target kcal",
            style = TextStyle(
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = ColorProvider(colors.onSurface),
            ),
        )
        val remainingLabel = if (remaining >= 0) "$remaining übrig" else "${-remaining} über Ziel"
        Text(
            remainingLabel,
            style = TextStyle(fontSize = 12.sp, color = ColorProvider(colors.onSurfaceVariant)),
        )
        Spacer(modifier = GlanceModifier.size(6.dp))
        val progress = if (target <= 0) 0f else (net.toFloat() / target).coerceIn(0f, 1f)
        LinearProgressIndicator(
            progress = progress,
            modifier = GlanceModifier.fillMaxWidth(),
            color = ColorProvider(colors.outline),
            backgroundColor = ColorProvider(colors.track),
        )
    }
}

package com.adman234.pillbox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adman234.pillbox.app
import com.adman234.pillbox.data.DoseEvent
import com.adman234.pillbox.data.DoseStatus
import com.adman234.pillbox.data.Medication
import com.adman234.pillbox.data.Settings
import com.adman234.pillbox.engine.ScheduleEngine
import com.adman234.pillbox.reminder.DoseAction
import com.adman234.pillbox.util.Fmt
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One line in a day's schedule. [event] is null for doses that exist only as a forecast. */
private data class DoseRow(val at: Long, val med: Medication, val event: DoseEvent?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var dayEpoch by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    val day = LocalDate.ofEpochDay(dayEpoch)
    val zone = ZoneId.systemDefault()
    val from = day.atStartOfDay(zone).toInstant().toEpochMilli()
    val to = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    val meds by ctx.app.db.meds().observeAll().collectAsStateWithLifecycle(emptyList())
    val events by remember(from, to) { ctx.app.db.doses().observeRange(from, to) }
        .collectAsStateWithLifecycle(emptyList())
    val settings by ctx.app.settings.flow.collectAsStateWithLifecycle(Settings())

    val medById = meds.associateBy { it.id }
    val now = System.currentTimeMillis()
    val rows = buildList {
        events.forEach { e -> medById[e.medId]?.let { add(DoseRow(e.scheduledAt, it, e)) } }
        // Doses further out than the generated horizon are shown as a forecast.
        val known = events.map { it.medId to it.scheduledAt }.toSet()
        meds.filter { it.reminderEnabled }.forEach { m ->
            ScheduleEngine.occurrences(
                m.spec(),
                Instant.ofEpochMilli(maxOf(from, m.reminderSince, now)), Instant.ofEpochMilli(to), zone, settings.dstAdjust,
            ).forEach { t -> if ((m.id to t.toEpochMilli()) !in known) add(DoseRow(t.toEpochMilli(), m, null)) }
        }
    }.sortedBy { it.at }

    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("Schedule") }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { dayEpoch-- }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous day") }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(Fmt.dayLabel(day), style = MaterialTheme.typography.titleMedium)
                    Text(Fmt.date(day), style = MaterialTheme.typography.bodySmall)
                }
                if (day != LocalDate.now()) TextButton(onClick = { dayEpoch = LocalDate.now().toEpochDay() }) { Text("Today") }
                IconButton(onClick = { dayEpoch++ }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next day") }
            }
            if (rows.isEmpty()) {
                Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Nothing scheduled", style = MaterialTheme.typography.titleMedium)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(rows, key = { it.med.id.toString() + ":" + it.at }) { r ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                PillIcon(r.med.shape, r.med.color, 36.dp)
                                Column(Modifier.weight(1f)) {
                                    Text(r.med.name, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        Fmt.time(r.at) + if (r.med.amount.isNotBlank()) " · " + r.med.amount else "",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    val e = r.event
                                    if (e != null && e.status != DoseStatus.PENDING) {
                                        Text(
                                            statusText(e),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = statusColor(e.status),
                                        )
                                    }
                                }
                                val e = r.event
                                if (e != null && e.status == DoseStatus.PENDING) {
                                    OutlinedButton(onClick = { scope.launch { ctx.app.scheduler.act(e.id, DoseAction.SKIP) } }) { Text("Skip") }
                                    Button(onClick = { scope.launch { ctx.app.scheduler.act(e.id, DoseAction.TAKE) } }) { Text("Take") }
                                } else if (e == null) {
                                    Text("Upcoming", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun statusText(e: DoseEvent): String = when (e.status) {
    DoseStatus.TAKEN -> "Taken" + (e.actedAt?.let { " at " + Fmt.time(it) } ?: "")
    DoseStatus.SKIPPED -> "Skipped"
    DoseStatus.MISSED -> "Missed"
    DoseStatus.PENDING -> "Due"
}

@Composable
fun statusColor(s: DoseStatus) = when (s) {
    DoseStatus.TAKEN -> MaterialTheme.colorScheme.primary
    DoseStatus.SKIPPED -> MaterialTheme.colorScheme.onSurfaceVariant
    DoseStatus.MISSED -> MaterialTheme.colorScheme.error
    DoseStatus.PENDING -> MaterialTheme.colorScheme.tertiary
}

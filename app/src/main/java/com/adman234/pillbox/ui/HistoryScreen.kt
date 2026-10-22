package com.adman234.pillbox.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.adman234.pillbox.data.Backup
import com.adman234.pillbox.data.DoseEvent
import com.adman234.pillbox.data.DoseStatus
import com.adman234.pillbox.util.Fmt
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val meds by ctx.app.db.meds().observeAll().collectAsStateWithLifecycle(emptyList())
    val history by ctx.app.db.doses().observeHistory().collectAsStateWithLifecycle(emptyList())
    var filter by rememberSaveable { mutableStateOf(-1L) }
    var editing by remember { mutableStateOf<DoseEvent?>(null) }
    val medById = meds.associateBy { it.id }
    val zone = ZoneId.systemDefault()

    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch { Backup.exportCsv(ctx, uri) }
    }

    val shown = history.filter { filter < 0 || it.medId == filter }
    val grouped = shown.groupBy { Instant.ofEpochMilli(it.actedAt ?: it.scheduledAt).atZone(zone).toLocalDate() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("History") },
                actions = { TextButton(onClick = { csv.launch("pillbox-history.csv") }) { Text("Export") } },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (meds.size > 1) {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(selected = filter < 0, onClick = { filter = -1 }, label = { Text("All") }) }
                    items(meds, key = { it.id }) { m ->
                        FilterChip(selected = filter == m.id, onClick = { filter = m.id }, label = { Text(m.name) })
                    }
                }
            }
            if (shown.isEmpty()) {
                Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No history yet", style = MaterialTheme.typography.titleMedium)
                    Text("Doses you take, skip or miss show up here.", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    grouped.forEach { (day, list) ->
                        item(key = "h$day") {
                            Text(
                                Fmt.dayLabel(day), style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp),
                            )
                        }
                        items(list, key = { it.id }) { e ->
                            val med = medById[e.medId]
                            Row(
                                Modifier.fillMaxWidth().clickable { editing = e }.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                if (med != null) PillIcon(med.shape, med.color, 32.dp)
                                Column(Modifier.weight(1f)) {
                                    Text(med?.name ?: "Deleted medication", style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        (if (e.extra) "Not scheduled" else "Scheduled " + Fmt.time(e.scheduledAt)),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                Text(statusText(e), color = statusColor(e.status), style = MaterialTheme.typography.bodyMedium)
                            }
                            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }
        }
    }

    editing?.let { e ->
        val name = medById[e.medId]?.name ?: "dose"
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("$name, ${Fmt.dateTime(e.scheduledAt)}") },
            text = {
                Column {
                    Text("Change what happened with this dose.")
                    listOf(DoseStatus.TAKEN to "Taken", DoseStatus.SKIPPED to "Skipped", DoseStatus.MISSED to "Missed").forEach { (st, label) ->
                        TextButton(onClick = { scope.launch { ctx.app.scheduler.setStatus(e.id, st) }; editing = null }) {
                            Text(label + if (st == e.status) " (current)" else "")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { scope.launch { ctx.app.scheduler.deleteEvent(e.id) }; editing = null }) {
                    Text("Delete entry", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Close") } },
        )
    }
}

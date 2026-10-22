package com.adman234.pillbox.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adman234.pillbox.app
import com.adman234.pillbox.data.Settings
import com.adman234.pillbox.util.Fmt
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedsScreen(onAdd: () -> Unit, onEdit: (Long) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val meds by ctx.app.db.meds().observeAll().collectAsStateWithLifecycle(emptyList())
    val last by ctx.app.db.doses().observeLastTaken().collectAsStateWithLifecycle(emptyList())
    val settings by ctx.app.settings.flow.collectAsStateWithLifecycle(Settings())
    val lastByMed = last.associate { it.medId to it.at }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("Medications") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) { Icon(Icons.Default.Add, "Add medication") }
        },
    ) { pad ->
        if (meds.isEmpty()) {
            Column(
                Modifier.padding(pad).fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PillIcon(com.adman234.pillbox.data.PillShape.CAPSULE, PillColors[3], 96.dp)
                Text("No medications yet", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
                Text("Tap + to add your first one.", style = MaterialTheme.typography.bodyMedium)
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(meds, key = { it.id }) { m ->
                Card(Modifier.fillMaxWidth().clickable { onEdit(m.id) }) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            PillIcon(m.shape, m.color, 44.dp)
                            Column(Modifier.weight(1f)) {
                                Text(m.name, style = MaterialTheme.typography.titleMedium)
                                val sub = listOf(m.amount, m.description).filter { it.isNotBlank() }.joinToString(" · ")
                                if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { onEdit(m.id) }) { Icon(Icons.Default.Edit, "Edit") }
                        }
                        Text(Fmt.summary(m), style = MaterialTheme.typography.bodyMedium)
                        if (settings.showLastDose) {
                            val at = lastByMed[m.id]
                            Text(
                                "Last taken: " + (at?.let { Fmt.dateTime(it) } ?: "never"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Reminder", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = { scope.launch { ctx.app.scheduler.logExtraDose(m.id) } }) { Text("Take now") }
                            Switch(
                                checked = m.reminderEnabled,
                                onCheckedChange = { on -> scope.launch { ctx.app.meds.setReminderEnabled(m, on) } },
                                modifier = Modifier.size(width = 52.dp, height = 32.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

package com.adman234.pillbox.ui.editor

import android.os.Parcelable
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.adman234.pillbox.app
import com.adman234.pillbox.data.DayRule
import com.adman234.pillbox.data.Freq
import com.adman234.pillbox.data.Medication
import com.adman234.pillbox.data.PillShape
import com.adman234.pillbox.data.ReminderType
import com.adman234.pillbox.ui.PillColors
import com.adman234.pillbox.ui.PillIcon
import com.adman234.pillbox.util.Fmt
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import java.time.LocalDate

@Parcelize
data class Draft(
    val id: Long = 0,
    val name: String = "",
    val description: String = "",
    val amount: String = "",
    val shape: PillShape = PillShape.ROUND,
    val color: Int = PillColors[3],
    val reminderEnabled: Boolean = true,
    val freq: Freq = Freq.TIMES,
    val times: List<Int> = listOf(480),
    val intervalMinutes: Int = 480,
    val intervalStartMin: Int = 480,
    val intervalEndMin: Int = -1,
    val dayRule: DayRule = DayRule.EVERY_DAY,
    val weekdayMask: Int = 127,
    val everyN: Int = 2,
    val reminderType: ReminderType = ReminderType.NOTIFICATION,
    val durationDays: Int = 0,
    val startEpochDay: Long = LocalDate.now().toEpochDay(),
    val createdAt: Long = System.currentTimeMillis(),
) : Parcelable {
    fun toMedication() = Medication(
        id = id, name = name.trim(), description = description.trim(), amount = amount.trim(),
        shape = shape, color = color, reminderEnabled = reminderEnabled, freq = freq,
        timesMinutes = times.sorted().distinct().joinToString(","),
        intervalMinutes = intervalMinutes, intervalStartMin = intervalStartMin, intervalEndMin = intervalEndMin,
        dayRule = dayRule, weekdayMask = weekdayMask, everyN = everyN, reminderType = reminderType,
        durationDays = durationDays, startEpochDay = startEpochDay, createdAt = createdAt,
    )

    companion object {
        fun from(m: Medication) = Draft(
            id = m.id, name = m.name, description = m.description, amount = m.amount,
            shape = m.shape, color = m.color, reminderEnabled = m.reminderEnabled, freq = m.freq,
            times = m.times().ifEmpty { listOf(480) },
            intervalMinutes = m.intervalMinutes, intervalStartMin = m.intervalStartMin, intervalEndMin = m.intervalEndMin,
            dayRule = m.dayRule, weekdayMask = m.weekdayMask, everyN = m.everyN, reminderType = m.reminderType,
            durationDays = m.durationDays, startEpochDay = m.startEpochDay, createdAt = m.createdAt,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(medId: Long, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var draft by rememberSaveable { mutableStateOf(Draft()) }
    var loaded by rememberSaveable { mutableStateOf(medId <= 0) }
    var step by rememberSaveable { mutableStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(medId) {
        if (!loaded) {
            ctx.app.db.meds().get(medId)?.let { draft = Draft.from(it) }
            loaded = true
        }
    }

    fun back() { if (step == 1) step = 0 else onDone() }
    BackHandler(enabled = step == 1) { step = 0 }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (draft.id == 0L) "New medication" else "Edit medication") },
                navigationIcon = {
                    IconButton(onClick = ::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    if (draft.id != 0L) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Delete") }
                    }
                },
            )
        },
    ) { pad ->
        if (!loaded) return@Scaffold
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (step == 0) {
                DetailsStep(draft, { draft = it })
            } else {
                ReminderStep(draft, { draft = it })
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (step == 0) {
                    Button(
                        onClick = {
                            if (draft.name.isBlank()) error = "Give the medication a name" else { error = null; step = 1 }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Next") }
                } else {
                    OutlinedButton(onClick = { step = 0 }, modifier = Modifier.weight(1f)) { Text("Back") }
                    Button(
                        onClick = {
                            val err = validate(draft)
                            if (err != null) { error = err; return@Button }
                            scope.launch {
                                ctx.app.meds.save(draft.toMedication())
                                onDone()
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Save") }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${draft.name}?") },
            text = { Text("This also deletes its dose history. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        ctx.app.db.meds().get(draft.id)?.let { ctx.app.meds.delete(it) }
                        onDone()
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

private fun validate(d: Draft): String? {
    if (!d.reminderEnabled) return null
    if (d.freq == Freq.TIMES && d.times.isEmpty()) return "Add at least one reminder time"
    if (d.freq == Freq.INTERVAL && d.intervalEndMin in 0..d.intervalStartMin) return "The last dose must be after the first dose"
    if (d.dayRule == DayRule.WEEKDAYS && d.weekdayMask == 0) return "Pick at least one day"
    return null
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsStep(d: Draft, set: (Draft) -> Unit) {
    OutlinedTextField(
        value = d.name, onValueChange = { set(d.copy(name = it)) },
        label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = d.description, onValueChange = { set(d.copy(description = it)) },
        label = { Text("Description (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2,
    )
    OutlinedTextField(
        value = d.amount, onValueChange = { set(d.copy(amount = it)) },
        label = { Text("Dose amount (optional)") }, placeholder = { Text("1 tablet, 500 mg, 2 puffs") },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
    )

    Text("Shape", style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PillShape.values().forEach { s ->
            val selected = s == d.shape
            Box(
                Modifier.size(56.dp)
                    .background(
                        if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                        RoundedCornerShape(14.dp),
                    )
                    .border(
                        BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                        RoundedCornerShape(14.dp),
                    )
                    .clickable { set(d.copy(shape = s)) },
                contentAlignment = Alignment.Center,
            ) { PillIcon(s, d.color, 40.dp) }
        }
    }

    Text("Color", style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PillColors.forEach { c ->
            val selected = c == d.color
            Box(
                Modifier.size(44.dp)
                    .background(Color(c), CircleShape)
                    .border(
                        BorderStroke(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                        CircleShape,
                    )
                    .clickable { set(d.copy(color = c)) },
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Icon(Icons.Default.Check, null, tint = if (c == PillColors[0] || c == PillColors[1] || c == PillColors[2]) Color.Black else Color.White)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReminderStep(d: Draft, set: (Draft) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Reminder", style = MaterialTheme.typography.titleMedium)
            Text(
                if (d.reminderEnabled) "You will be reminded" else "As needed: log doses yourself",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Switch(checked = d.reminderEnabled, onCheckedChange = { set(d.copy(reminderEnabled = it)) })
    }
    if (!d.reminderEnabled) return
    HorizontalDivider()

    Section("Frequency") {
        Segmented(
            listOf("At specific times", "Interval"), if (d.freq == Freq.TIMES) 0 else 1,
        ) { set(d.copy(freq = if (it == 0) Freq.TIMES else Freq.INTERVAL)) }
    }

    Section("Days") {
        Segmented(
            listOf("Every day", "Specific days", "Every X days"),
            d.dayRule.ordinal,
        ) { set(d.copy(dayRule = DayRule.values()[it])) }
        when (d.dayRule) {
            DayRule.WEEKDAYS -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val letters = listOf("M", "T", "W", "T", "F", "S", "S")
                letters.forEachIndexed { i, l ->
                    val on = d.weekdayMask and (1 shl i) != 0
                    FilterChip(
                        selected = on, onClick = { set(d.copy(weekdayMask = d.weekdayMask xor (1 shl i))) },
                        label = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Text(l) } },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            DayRule.EVERY_N -> Stepper("Every", "${d.everyN} days", { set(d.copy(everyN = (d.everyN - 1).coerceAtLeast(2))) }, { set(d.copy(everyN = (d.everyN + 1).coerceAtMost(90))) })
            DayRule.EVERY_DAY -> {}
        }
    }

    if (d.freq == Freq.TIMES) {
        Section("Reminder times") {
            var picking by remember { mutableStateOf(false) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                d.times.sorted().forEach { t ->
                    FilterChip(
                        selected = true, onClick = { set(d.copy(times = d.times - t)) },
                        label = { Text(Fmt.minuteOfDay(t)) },
                        trailingIcon = { Icon(Icons.Default.Close, "Remove", Modifier.size(16.dp)) },
                    )
                }
                OutlinedButton(onClick = { picking = true }) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.size(4.dp)); Text("Add time")
                }
            }
            if (picking) TimeDialog(480, "Add reminder time", { picking = false }) { m ->
                picking = false
                set(d.copy(times = (d.times + m).distinct().sorted()))
            }
        }
    } else {
        Section("Interval") {
            Stepper(
                "Every", Fmt.duration(d.intervalMinutes),
                { set(d.copy(intervalMinutes = (d.intervalMinutes - 30).coerceAtLeast(30))) },
                { set(d.copy(intervalMinutes = (d.intervalMinutes + 30).coerceAtMost(24 * 60))) },
            )
            var pickStart by remember { mutableStateOf(false) }
            var pickEnd by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("First dose", Modifier.weight(1f))
                OutlinedButton(onClick = { pickStart = true }) { Text(Fmt.minuteOfDay(d.intervalStartMin)) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Stop after a last dose")
                    Text("Off: the grid runs around the clock", style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = d.intervalEndMin >= 0,
                    onCheckedChange = { set(d.copy(intervalEndMin = if (it) (d.intervalStartMin + 12 * 60).coerceAtMost(23 * 60 + 59) else -1)) },
                )
            }
            if (d.intervalEndMin >= 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Last dose by", Modifier.weight(1f))
                    OutlinedButton(onClick = { pickEnd = true }) { Text(Fmt.minuteOfDay(d.intervalEndMin)) }
                }
            }
            if (pickStart) TimeDialog(d.intervalStartMin, "First dose", { pickStart = false }) { set(d.copy(intervalStartMin = it)); pickStart = false }
            if (pickEnd) TimeDialog(d.intervalEndMin, "Last dose by", { pickEnd = false }) { set(d.copy(intervalEndMin = it)); pickEnd = false }
        }
    }

    Section("Reminder type") {
        Segmented(listOf("Notification", "Alarm"), d.reminderType.ordinal) { set(d.copy(reminderType = ReminderType.values()[it])) }
        Text(
            if (d.reminderType == ReminderType.ALARM) "Rings loudly with a full screen on the lock screen until you respond."
            else "A heads-up notification with Take, Skip and Snooze buttons.",
            style = MaterialTheme.typography.bodySmall,
        )
    }

    Section("Duration") {
        Segmented(listOf("Continuous", "Number of days"), if (d.durationDays > 0) 1 else 0) {
            set(d.copy(durationDays = if (it == 0) 0 else 7, startEpochDay = LocalDate.now().toEpochDay()))
        }
        if (d.durationDays > 0) {
            Stepper("For", "${d.durationDays} days", { set(d.copy(durationDays = (d.durationDays - 1).coerceAtLeast(1))) }, { set(d.copy(durationDays = (d.durationDays + 1).coerceAtMost(365))) })
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        content()
    }
}

@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, label ->
            SegmentedButton(
                selected = i == selected, onClick = { onSelect(i) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
            ) { Text(label, maxLines = 1) }
        }
    }
}

@Composable
private fun Stepper(label: String, value: String, minus: () -> Unit, plus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        OutlinedButton(onClick = minus) { Text("−") }
        Text(value, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = plus) { Text("+") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDialog(initialMinutes: Int, title: String, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val is24 = DateFormat.is24HourFormat(LocalContext.current)
    val state = rememberTimePickerState((initialMinutes / 60) % 24, initialMinutes % 60, is24)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state) },
        confirmButton = { TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

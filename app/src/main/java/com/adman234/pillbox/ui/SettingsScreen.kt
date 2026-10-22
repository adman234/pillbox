package com.adman234.pillbox.ui

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adman234.pillbox.app
import com.adman234.pillbox.data.Backup
import com.adman234.pillbox.data.SOUND_DEFAULT
import com.adman234.pillbox.data.SOUND_SILENT
import com.adman234.pillbox.data.Settings
import com.adman234.pillbox.reminder.AlarmService
import com.adman234.pillbox.reminder.Notifier
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by ctx.app.settings.flow.collectAsStateWithLifecycle(Settings())

    fun change(block: (Settings) -> Settings) {
        scope.launch {
            val before = ctx.app.settings.current()
            ctx.app.settings.update(block)
            val after = ctx.app.settings.current()
            Notifier.ensureChannels(ctx, after)
            // A different DST rule moves reminder times, so upcoming doses must be recalculated.
            if (before.dstAdjust != after.dstAdjust) ctx.app.scheduler.reconcile(rebuildFuture = true)
        }
    }

    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            Backup.exportJson(ctx, uri)
            Toast.makeText(ctx, "Backup saved", Toast.LENGTH_SHORT).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingImport = uri
    }

    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("Settings") }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Header("Notification reminders")
            SoundRow("Sound", s.notifSound, alarm = false) { v -> change { it.copy(notifSound = v) } }
            SwitchRow("Vibrate", null, s.notifVibrate) { v -> change { it.copy(notifVibrate = v) } }

            Header("Alarm reminders")
            SoundRow("Sound", s.alarmSound, alarm = true) { v -> change { it.copy(alarmSound = v) } }
            SwitchRow("Vibrate", null, s.alarmVibrate) { v -> change { it.copy(alarmVibrate = v) } }

            Header("Repeating and snoozing")
            SwitchRow(
                "Auto repeat until done",
                "Keep reminding until you tap Take or Skip (up to 12 times)",
                s.autoRepeat,
            ) { v -> change { it.copy(autoRepeat = v) } }
            if (s.autoRepeat) {
                SliderRow("Repeat every", s.repeatMinutes, 1..30) { v -> change { it.copy(repeatMinutes = v) } }
            }
            SliderRow("Snooze duration", s.snoozeMinutes, 1..60) { v -> change { it.copy(snoozeMinutes = v) } }

            Header("Time")
            SwitchRow(
                "Automatic daylight saving detection",
                if (s.dstAdjust) "Reminders stay at the same clock time when the clocks change"
                else "Reminders keep their absolute time, so the clock time shifts by an hour",
                s.dstAdjust,
            ) { v -> change { it.copy(dstAdjust = v) } }
            Text(
                "Reminders always follow this phone's time zone.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Header("Display")
            SwitchRow(
                "Show last dose",
                "On medication cards and in reminders",
                s.showLastDose,
            ) { v -> change { it.copy(showLastDose = v) } }

            Header("Reminder health")
            HealthSection()

            Header("Backup")
            Text(
                "There is no account, so your data lives only on this phone. Save a backup file before changing phones.",
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp),
            )
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { exportLauncher.launch("pillbox-backup.json") }) { Text("Export backup") }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }) { Text("Import backup") }
            }

            Header("About")
            val version = remember {
                runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: ""
            }
            Text("Pillbox $version", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyLarge)
            Text(
                "Pillbox is a reminder tool, not a medical device. Do not rely on it alone for medication that matters to your health. " +
                    "It has no internet access and keeps everything on your phone.",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp, 4.dp),
            )
        }
    }

    pendingImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Replace all data?") },
            text = { Text("Importing replaces every medication and all history on this phone.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingImport = null
                    scope.launch {
                        val ok = Backup.importJson(ctx, uri)
                        Toast.makeText(ctx, if (ok) "Backup restored" else "That is not a Pillbox backup", Toast.LENGTH_LONG).show()
                    }
                }) { Text("Replace") }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Header(text: String) {
    HorizontalDivider(Modifier.padding(top = 8.dp))
    Text(
        text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp),
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked, onChange)
    }
}

@Composable
private fun SliderRow(title: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    var local by remember(value) { mutableIntStateOf(value) }
    Column(Modifier.padding(16.dp, 8.dp)) {
        Row {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text("$local min", style = MaterialTheme.typography.bodyLarge)
        }
        Slider(
            value = local.toFloat(), onValueChange = { local = it.toInt() },
            onValueChangeFinished = { onChange(local) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = range.last - range.first - 1,
        )
    }
}

@Composable
private fun SoundRow(title: String, value: String, alarm: Boolean, onPick: (String) -> Unit) {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            val uri = r.data?.let { IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java) }
            onPick(
                when {
                    uri == null -> SOUND_SILENT
                    uri == Notifier.defaultUri(alarm) -> SOUND_DEFAULT
                    else -> uri.toString()
                },
            )
        }
    }
    val label = remember(value) {
        when (value) {
            SOUND_SILENT -> "Silent"
            SOUND_DEFAULT -> "Default"
            else -> runCatching { RingtoneManager.getRingtone(ctx, Uri.parse(value)).getTitle(ctx) }.getOrDefault("Custom")
        }
    }
    Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(onClick = {
            val type = if (alarm) RingtoneManager.TYPE_ALARM else RingtoneManager.TYPE_NOTIFICATION
            launcher.launch(
                Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, type)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Notifier.defaultUri(alarm))
                    .putExtra(
                        RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                        when (value) { SOUND_SILENT -> null; SOUND_DEFAULT -> Notifier.defaultUri(alarm); else -> Uri.parse(value) },
                    ),
            )
        }) { Text("Change") }
    }
}

// ---------------------------------------------------------------------------------------
// Reminder health: everything that can silently stop a reminder from arriving.

private data class Check(val title: String, val ok: Boolean, val fix: String, val action: () -> Unit)

@Composable
private fun HealthSection() {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }

    @Suppress("UNUSED_EXPRESSION") tick
    val checks = buildList {
        add(Check("Notifications allowed", NotificationManagerCompat.from(ctx).areNotificationsEnabled(), "Allow") {
            if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            ctx.startActivity(
                Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, ctx.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        })
        val am = ctx.getSystemService(AlarmManager::class.java)
        add(Check("Exact alarms allowed", Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms(), "Allow") {
            if (Build.VERSION.SDK_INT >= 31) {
                ctx.startActivity(
                    Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        })
        if (Build.VERSION.SDK_INT >= 34) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            add(Check("Full screen alarms allowed", nm.canUseFullScreenIntent(), "Allow") {
                ctx.startActivity(
                    Intent(AndroidSettings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${ctx.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            })
        }
        val pm = ctx.getSystemService(PowerManager::class.java)
        add(Check("Not battery restricted", pm.isIgnoringBatteryOptimizations(ctx.packageName), "Fix") {
            ctx.startActivity(Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        })
    }
    checks.forEach { c ->
        Row(Modifier.fillMaxWidth().padding(16.dp, 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                (if (c.ok) "✓  " else "⚠  ") + c.title,
                Modifier.weight(1f),
                color = if (c.ok) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
            )
            if (!c.ok) OutlinedButton(onClick = c.action) { Text(c.fix) }
        }
    }
    val scope = rememberCoroutineScope()
    Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = {
            scope.launch { Notifier.postTest(ctx, ctx.app.settings.current()) }
        }) { Text("Test notification") }
        OutlinedButton(onClick = { AlarmService.startTest(ctx) }) { Text("Test alarm") }
    }
    Text(
        "Some phone makers also stop apps in the background. If reminders arrive late, allow Pillbox to run without restrictions in the phone's battery settings.",
        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp, 8.dp),
    )
}

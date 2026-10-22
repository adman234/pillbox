package com.adman234.pillbox.reminder

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.adman234.pillbox.app
import com.adman234.pillbox.data.DoseEvent
import com.adman234.pillbox.data.DoseStatus
import com.adman234.pillbox.data.Medication
import com.adman234.pillbox.ui.PillIcon
import com.adman234.pillbox.ui.PillboxTheme
import com.adman234.pillbox.util.Fmt
import kotlinx.coroutines.launch

/** The full screen shown over the lock screen for alarm-type reminders. */
class AlarmActivity : ComponentActivity() {
    private var eventId = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        eventId = intent.getLongExtra(EXTRA_EVENT, -1)
        setContent {
            PillboxTheme {
                AlarmScreen(eventId, onAction = { action ->
                    app.appScope.launch { app.scheduler.act(eventId, action, feedback = true) }
                    finish()
                }, onGone = { finish() })
            }
        }
    }

    companion object {
        private const val EXTRA_EVENT = "event"
        fun intent(ctx: Context, eventId: Long) =
            Intent(ctx, AlarmActivity::class.java)
                .putExtra(EXTRA_EVENT, eventId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}

@Composable
private fun AlarmScreen(eventId: Long, onAction: (DoseAction) -> Unit, onGone: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var loaded by remember { mutableStateOf<Pair<DoseEvent, Medication>?>(null) }
    LaunchedEffect(eventId) {
        val e = ctx.app.db.doses().get(eventId)
        val m = e?.let { ctx.app.db.meds().get(it.medId) }
        if (e == null || m == null || e.status != DoseStatus.PENDING) onGone() else loaded = e to m
    }
    val snoozeMin by remember { mutableStateOf(10) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        val (e, m) = loaded ?: return@Surface
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            PillIcon(m.shape, m.color, 120.dp)
            Spacer(Modifier.height(24.dp))
            Text(Fmt.time(e.scheduledAt), style = MaterialTheme.typography.displayMedium)
            Text(m.name, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            if (m.amount.isNotBlank()) Text(m.amount, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(48.dp))
            Button(onClick = { onAction(DoseAction.TAKE) }, Modifier.fillMaxWidth().height(64.dp)) {
                Text("Take", style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { onAction(DoseAction.SNOOZE) }, Modifier.fillMaxWidth().height(56.dp)) { Text("Snooze") }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { onAction(DoseAction.SKIP) }, Modifier.fillMaxWidth().height(56.dp)) { Text("Skip") }
        }
    }
}

package com.adman234.pillbox.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.adman234.pillbox.app
import kotlinx.coroutines.launch

private fun BroadcastReceiver.async(ctx: Context, block: suspend () -> Unit) {
    val pending = goAsync()
    ctx.app.appScope.launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

/** An alarm for a dose went off. The scheduler decides what is due, so this just wakes it. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        async(context) { context.app.scheduler.reconcile() }
    }
}

/** Boot, app update, clock change, time zone change and the periodic refill all land here. */
class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val rebuild = when (intent.action) {
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> true
            else -> false
        }
        async(context) { context.app.scheduler.reconcile(rebuildFuture = rebuild) }
    }
}

/** Take, Skip and Snooze buttons on the notification. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_EVENT, -1)
        val action = intent.getStringExtra(EXTRA_ACTION)?.let { runCatching { DoseAction.valueOf(it) }.getOrNull() }
        if (id < 0 || action == null) return
        // A swipe-away arrives as a snooze with feedback turned off.
        val feedback = intent.getBooleanExtra(EXTRA_FEEDBACK, true)
        async(context) { context.app.scheduler.act(id, action, feedback) }
    }

    companion object {
        const val EXTRA_EVENT = "event"
        const val EXTRA_ACTION = "action"
        const val EXTRA_FEEDBACK = "feedback"
    }
}

package com.adman234.pillbox

import android.app.Application
import android.content.Context
import com.adman234.pillbox.data.MedRepo
import com.adman234.pillbox.data.PillboxDb
import com.adman234.pillbox.data.SettingsRepo
import com.adman234.pillbox.reminder.Notifier
import com.adman234.pillbox.reminder.Scheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PillboxApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var db: PillboxDb
        private set
    lateinit var settings: SettingsRepo
        private set
    lateinit var scheduler: Scheduler
        private set
    lateinit var meds: MedRepo
        private set

    override fun onCreate() {
        super.onCreate()
        db = PillboxDb.create(this)
        settings = SettingsRepo(this)
        scheduler = Scheduler(this, db, settings)
        meds = MedRepo(db, scheduler)
        appScope.launch {
            Notifier.ensureChannels(this@PillboxApp, settings.current())
            scheduler.reconcile()
        }
    }
}

val Context.app: PillboxApp get() = applicationContext as PillboxApp

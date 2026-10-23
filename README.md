# Pillbox

A simple, private pill reminder for Android. No account, no ads, no internet permission. Everything stays on your phone.

> Pillbox is a reminder tool, not a medical device. Do not rely on it alone for medication that matters to your health.

## Features

- **Medications:** name, description, optional dose amount, pill shape and color. Toggle each reminder on or off, or log a dose right now ("Take now") for as-needed medication.
- **Flexible reminders:**
  - at specific times, or at an interval (every 30 minutes up to every 24 hours, optionally stopping after a last dose of the day)
  - every day, on specific weekdays, or every X days
  - continuous, or for a set number of days
  - as a notification or as a full screen alarm
- **Take, Skip or Snooze** straight from the notification or alarm screen. The reminder turns into a short confirmation for a few seconds after each tap, and swiping a reminder away counts as a snooze.
- **Schedule:** the day's doses with Take and Skip buttons, plus a forecast for future days.
- **History:** every taken, skipped and missed dose. Edit entries, filter by medication, export to CSV.
- **Settings:** sound and vibration for notifications and alarms (or silent), auto repeat until done, snooze length, daylight saving behavior, show last dose.
- **Reminder health check:** shows anything that could stop a reminder arriving (notification permission, exact alarms, full screen alarms, battery restrictions), with buttons to send a test notification and a test alarm.
- **Backup and restore** to a JSON file.

## Install and update

Pillbox is not on an app store. Install it with [Obtainium](https://obtainium.imranr.dev/):

1. Add the app with the source URL `https://github.com/adman234/pillbox`.
2. Obtainium picks up each new release and updates the app.

Or download the APK from the [releases page](https://github.com/adman234/pillbox/releases).

## How reminders work

- Times are wall-clock times in the phone's current time zone. With **automatic daylight saving detection** on (default), 8:00 stays 8:00 when the clocks change. With it off, a reminder keeps its absolute time, so the clock time shifts by an hour.
- Interval reminders stay on their grid: if you take a dose late, the next one still arrives at the scheduled time.
- A dose nobody responds to becomes **missed** when the next dose is due, or 4 hours later.
- Alarms are scheduled with the Android alarm clock API so they ring in Doze mode and after a reboot.

## Building

Requires JDK 21 and the Android SDK (platform 36).

```
./gradlew testDebugUnitTest assembleDebug
```

Releases are built by GitHub Actions when a `v*` tag is pushed. The APK is signed with the key stored in the repository secrets `PILLBOX_KEYSTORE_B64` and `PILLBOX_KEYSTORE_PASSWORD`. For a local signed build, create a git-ignored `keystore.properties` with `storeFile`, `storePassword` and `keyAlias`.

## License

MIT

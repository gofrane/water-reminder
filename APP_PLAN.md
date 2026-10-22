# Water Reminder — Stage 1 design plan

Status: core first-version scope implemented; reliability fixes and release acceptance checks are tracked in `docs/testing/V1_REVIEW.md`.

## Purpose

Help users log water intake and remember to drink during their chosen waking hours. Native Android app using Kotlin and Jetpack Compose. Local storage; no account required.

## Visual direction

Use the three blue screen concepts developed in this chat: off-white backgrounds, navy text, bright blue actions, rounded cards, and a circular water progress graphic. Keep text readable, support larger font sizes, and provide at least 48 dp touch targets. Progress must be understandable without relying on color.

## First-version scope

- Today: daily goal progress, quick drink logging, custom amount, recent entries, and next reminder status.
- History: daily entries and a seven-day progress chart with date navigation.
- Settings: daily goal, three quick-add amounts, reminder toggle, interval, and start/end time.
- Save settings and timestamped entries on the device across app restarts.
- Request notification permission when users turn on reminders.

Defaults: 2,000 ml daily goal, quick-add amounts of 150/250/500 ml, 60-minute reminders between 08:00 and 22:00. These are editable starting values, not personalized health recommendations. Reminders start disabled until the user enables them.

Defer monthly charts, unit conversion, sound selection, accounts, cloud sync, widgets, and wearable integration. The original screen concepts show some of these future options; hide them in the first implementation.

## Screen layouts

### Today

1. App title, local date, Settings shortcut.
2. Circular progress: consumed amount, goal, and percentage.
3. Quick-add buttons: +150 ml, +250 ml, +500 ml; secondary Custom amount action.
4. Reminder card: next scheduled time, or clear disabled/permission-blocked status.
5. Recent drinks with time, amount, and entry menu.
6. Bottom navigation: Today / History / Settings.

Logging immediately updates progress and shows an Undo action. Custom amount opens a numeric entry dialog. An entry menu allows deletion after confirmation. If no drinks exist, show a friendly empty state and invite the first entry.

### History

1. Title and selected seven-day date range with previous/next controls.
2. Average intake and number of days meeting their goal.
3. Seven daily bars with goal markers; tap a day to select it.
4. Selected day's total and timestamped drink entries.
5. Same bottom navigation, with History selected.

Treat days before the first logged day as unavailable, not zero intake, when calculating the average. Store each day's goal so later goal changes do not rewrite past success. Show an empty state when no data exists. Do not navigate into future date ranges.

### Settings

1. Daily goal row opens a numeric editor.
2. Quick-add sizes row opens three numeric fields.
3. Reminder switch.
4. Interval selector: 30, 60, 90, or 120 minutes.
5. Start and end rows open Android time pickers.
6. Helper text explains the active reminder window.
7. Privacy note: data stays on the phone.
8. Same bottom navigation, with Settings selected.

Changes save immediately after validation. Intake and quick-add amounts accept positive whole milliliters; goal accepts 250–10,000 ml and drink amounts accept 10–5,000 ml. These are input limits, not recommended consumption ranges. Show inline errors for invalid values. Require start time earlier than end time; overnight windows are deferred.

## Reminder behavior

- Ask for notification permission in context when reminders are enabled.
- If permission is refused or notifications are blocked, show a clear status and a link to system settings; drink tracking continues to work.
- Schedule reminders at the selected interval within the active window. Logging a drink restarts the interval from that drink.
- Stop reminders for the day once the daily goal is reached; resume in the next day's active window.
- Editing reminder settings replaces the previous schedule. Turning reminders off cancels pending reminders.
- Restore the schedule after device reboot and reconcile it after time/timezone changes.
- Use battery-friendly scheduling. Delivery can be delayed by Android power management; do not promise exact timing.
- Notification opens Today. No custom alarm or background location permissions.

## Data and day boundaries

Store each drink's ID, amount, and timestamp. Group drinks by the phone's current local date. Today's total changes to the new day's entries at midnight without deleting history. Refresh date and totals when the app returns to the foreground. Persist daily goal snapshots, including the current day's latest selected goal. Deleting a drink recalculates that day's total and statistics.

## First launch

Open Today with zero progress and the editable default goal. No onboarding carousel or account setup. Show a small invitation to customize the goal and enable reminders. Explain permission before triggering the Android notification prompt.

## Stage 1 acceptance checklist

- Three main screens and their navigation are defined.
- First-version features are separated from future additions.
- Logging, undo, deletion, validation, empty states, and permission denial have defined behavior.
- Reminder hours, goal completion, and day boundaries have defined behavior.
- Existing concept images serve as the visual reference, with deferred settings omitted.

## Following stages

2. Create the Android project and implement navigation, screens, persistent logging, and settings.
3. Implement notification permissions and reminder scheduling.
4. Test on an emulator/device, verify accessibility and scheduling behavior, then produce a debug APK for installation.

Build setup verified: Android SDK Platform 35 and JDK 17. Debug APK compilation, JVM tests and lint are available; see README.md for current validation and remaining device acceptance checks.

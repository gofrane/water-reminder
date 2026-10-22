# First-version readiness review

Reviewed 21 October 2026. Scope: source, current build, existing emulator evidence, and targeted execution of the compiled scheduling/statistics code. The user's running emulator and saved data were not changed.

Verdict: the six source findings have been addressed. Complete the remaining device acceptance checks before calling this a release candidate. Core scope is already implemented; additional feature breadth is not the priority.

## Fix implementation — 21 October 2026

### Compact Today layout — 22 October 2026

Reduced the progress circle from 260 dp to 180 dp and its container from 280 dp to 188 dp. Today uses 12 dp section spacing and 16 dp padding; quick-add buttons have smaller icons and padding. Verified on the API 35 emulator at 720 × 1280, density 320, from a cold launch with no scrolling: all three quick-add buttons and Custom amount are fully visible above bottom navigation. The existing 150 ml entry remained present after the in-place update. Build and lint succeeded (zero errors, six informational warnings). Evidence: [opening-screen screenshot](compact-today.png).

The six findings below describe the reviewed baseline. Fixes now implemented:

- Scheduling resolves repeated hours as a continuous window, from the earlier start offset to the later end offset. Boundaries within a skipped hour clamp to the first valid instant. Entirely skipped windows move to the next day. Eligibility and scheduling share this policy.
- Snapshot schema v2 stores the tracking-start instant, preserved after deletion and converted using the current timezone. Legacy migration uses surviving drink timestamps where possible; an earlier date with deleted entries is approximated at midnight in the timezone at migration, since the old format did not retain its original timezone.
- Editors, numeric drafts, validation state, reminder explanation and pending deletion IDs use saveable state. Numeric editor content scrolls when space is limited.
- Each main tab retains its own scroll position; older History ranges use “Selected seven days”.
- Explicit legacy backup and Android 12+ cloud/device-transfer exclusions cover local storage.
- The versioned codec validates settings and records separately. Invalid records do not discard valid records. The first damaged raw snapshot is preserved under `recovery_snapshot` before repaired data is written, and the app shows a persistent recovery notice. If that preservation write fails, further snapshot writes are disabled and the notice explains that changes cannot be saved.

No update was installed into the user's running emulator during this work. Device rotation, TalkBack, manufacturer transfer behavior and sustained Doze checks remain acceptance work; source changes and JVM tests do not establish those device results.

Final validation on 21 October 2026: `testDebugUnitTest lintDebug assembleDebug --console=plain --offline --no-daemon` succeeded after the final UI change. All 21 tests executed and passed (3 hydration, 10 scheduling, 8 snapshot/migration). Lint reported zero errors and eight informational warnings: two dependency update suggestions and six KTX suggestions. The backup-policy and mutable-collection state warnings are resolved. `git diff --check` passed. Updated APK: `app/build/outputs/apk/debug/app-debug.apk`.

### Follow-up emulator validation — 21 October 2026

- Started the existing API 37 AVD and installed the update in place. The original installed APK used the sandbox debug certificate, while the latest Gradle build used the ADMIN debug certificate. A test copy was signed with the matching original debug key, then installed successfully with `adb install -r`; no uninstall or data clearing was used. The standard build APK retains its ADMIN debug signature.
- Confirmed schema v2 migration on the emulator. All nine existing drink records (IDs, amounts and timestamps) match the pre-update backup exactly. Today rendered 1,250 ml for 21 October.
- Android repeatedly displayed “Process system isn't responding”. Repeated Wait attempts did not produce a usable UI testing session. Fresh uniquely named UI dumps and a screenshot confirmed the system dialog; stale dumps were excluded from evidence.
- Rotation, independent scrolling, large text and TalkBack remain unverified on-device. Only the API 37 system image is installed; a stable API 35 test image or a physical device is needed to continue reliably. No font-scale or rotation settings were changed in this attempt.
- Backups, matching-key test APK and fresh UI captures are kept under ignored `.tools/`. The installed app and its data were preserved.

## Original review evidence and limits (before fixes)

- `testDebugUnitTest lintDebug assembleDebug --console=plain --offline --no-daemon` succeeded. Gradle reused up-to-date outputs; the existing test reports contain nine passing tests, zero failures. This is not a fresh execution of all tests.
- Lint: zero errors, eight warnings: one backup-policy warning, two dependency update suggestions, five optional KTX suggestions. Dependency update notices alone do not establish defects.
- Existing `EMULATOR_TEST_REPORT.md` documents real notification delivery, permission denial, persistence, goal pausing, cancellation and reboot restoration on API 37. Its image had system-app instability. No new interactive device checks were performed in this review.
- A Java probe in ignored `.tools/ReviewProbe.java` called the actual compiled `ReminderPlan` and `Hydration` classes. Results reproduced findings 1 and 2 below. No production files were modified.

## Original findings (addressed above)

### 1. Repeated clock hour can incorrectly end reminders early — P2, reproduced

Location: `app/src/main/java/com/waterreminder/app/ReminderPlan.kt:16`.

The scheduler constructs the day's end from midnight, selecting the earlier offset during a repeated hour, while `inWindow` compares local clock minutes. These disagree after the clock moves backward.

Reproduction: Europe/Berlin, 25 October 2026, second occurrence of 02:00 (`+01:00`); active window 01:00–02:30; interval 30 minutes; anchor 40 minutes ago. `inWindow` returns true, but `next` returns 26 October at 01:00, skipping the remainder of the current active window.

Improvement: define repeated-hour behavior explicitly and use the same resolved window boundaries for both delivery eligibility and scheduling. If both occurrences are active, resolve the end to its later offset. Add regressions for fall-back overlap and spring-forward nonexistent times; the existing DST test covers only an ordinary 08:00 start after the transition.

### 2. Timezone changes can exclude real drinks from statistics — P2, reproduced

Locations: `WaterStore.kt:24`, `WaterStore.kt:42`, `Hydration.kt:31`, `MainActivity.kt:319`.

Drink dates are recalculated in the current timezone, but the first tracked date is a fixed saved calendar date. A first drink at 20 October 00:30 UTC, originally tracked as 20 October, becomes 19 October in Los Angeles. For that selected day, the actual code reports total 1,000 ml but average 0 because the persisted first day is still 20 October. The History tracked-day denominator uses the same inconsistent boundary.

Improvement: persist the tracking-start instant and derive its local date using the same timezone as drink grouping. Preserve that instant even when the first drink is deleted. Define migration behavior for the existing date-only value and test eastward/westward travel and deletion.

### 3. Unsaved dialogs disappear on rotation — P2, source-confirmed

Locations: `MainActivity.kt:85`, `MainActivity.kt:405`.

The active editor and its numeric values use `remember`, so activity recreation loses them. Example: open Custom amount, type an amount, rotate; the dialog closes and the draft disappears. The main selected tab survives because it already uses `rememberSaveable`.

Improvement: save editor identity and small draft values using saveable state; preserve the pending confirmation by drink ID if appropriate. Verify recreation with an invalid draft as well as a valid one. Android's [Compose state-saving guidance](https://developer.android.com/develop/ui/compose/state-saving) describes the relevant configuration-change behavior.

### 4. Backup configuration does not fully support the privacy wording — P2, source and platform documentation

Location: `AndroidManifest.xml:5`; Settings says data stays on the phone.

Only `allowBackup=false` is configured. Android documents that some manufacturers can still allow device-to-device transfer on Android 12+ despite that flag. This is also the substantive lint warning; no data transfer was observed during this review.

Improvement: explicitly configure data extraction rules for cloud backup and device transfer to match the intended local-only policy, or accurately explain permitted transfers. Exclude both intake storage and device-specific alarm state if transfers are disabled. See [Android backup rules](https://developer.android.com/identity/data/autobackup).

### 5. Invalid saved data has no recovery path — P2, defensive hardening

Location: `WaterStore.kt:11–24`.

Loading parses the whole JSON snapshot, individual records and the first date without recovery; persisted settings also bypass the validation used for edits. A malformed snapshot/date can throw during Activity or reminder receiver creation. A stored zero goal can reach division by zero in Today. Normal UI inputs do not generate these values; this is resilience against damaged data or future migration mistakes, not an observed user-data failure.

Improvement: add schema versioning, validate on read, isolate invalid records and preserve the original snapshot for recovery. Do not silently replace all history with defaults. Test malformed JSON, missing fields, invalid dates and unsupported setting values.

### 6. Tab navigation shares one scroll position — P3, source-confirmed

Location: `MainActivity.kt:144`.

One scroll container wraps all three pages. Switching from a scrolled-down page can open the next page below its heading; switching back does not independently restore each page's position.

Improvement: retain a scroll state per tab or consistently reset on navigation. Also change History's fixed “Last seven days” label when viewing older date ranges.

## Required acceptance checks, not yet established

- Run on a stable API 35 device, API 26 minimum-supported emulator, and API 33 permission-boundary device. Include at least one physical phone before public release.
- Test large font/display scaling, 320 dp width, landscape, keyboard visibility in the three-field dialog, and TalkBack. Fixed progress dimensions, seven chart columns, and fixed rows warrant inspection; clipping is a risk, not a reproduced result.
- Test real screen-off/Doze delivery over time, notification permission revocation/restoration, and force-stop followed by reopening. Inexact delivery is intentional; verify no notifications outside the active window and no repeated overdue-alarm loop.
- Test add/custom/undo/delete, rapid repeated logging, historical goal preservation, process recreation, local midnight, and both timezone-change directions.
- Add Android-level tests for storage, permission flow and AlarmManager cancellation. The current nine JVM tests do not exercise these integrations.
- Before distribution, prepare release signing and an installable release artifact, confirm final application identity/version/icon, and verify applicable store requirements at publication time. The current verified artifact is a debug APK.

## Improvements that can wait

- Dark mode, localization/unit choices, widgets, cloud sync, monthly charts and overnight schedules remain outside the agreed initial scope.
- A “Send test reminder” action would make personal setup testing easier, but it should not replace real scheduled-delivery tests.
- Consider Room or another incremental persistence strategy if history grows: each current mutation serializes all drinks, and charts repeatedly scan the full list. Benchmark before treating this as a launch performance blocker.
- Brief first-use guidance could make goal customization and the disabled-by-default reminder switch easier to discover.

## Recommended implementation order

1. Correct date/time edge cases and add focused regressions.
2. Preserve editor state and make tab scrolling predictable.
3. Harden storage loading and make backup rules explicit.
4. Complete accessibility, device and sustained reminder checks.
5. Prepare the signed first release and update the stale stage-one status text in `APP_PLAN.md`.

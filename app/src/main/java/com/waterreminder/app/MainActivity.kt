package com.waterreminder.app

import android.app.TimePickerDialog
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Blue = Color(0xFF0088FF)
private val Navy = Color(0xFF10234A)
private val Muted = Color(0xFF637A99)
private val Pale = Color(0xFFE5F3FF)
private val Background = Color(0xFFF8FBFE)
private fun ml(value: Int) = String.format(Locale.getDefault(), "%,d ml", value)
private fun clock(minutes: Int) = String.format(Locale.getDefault(), "%02d:%02d", minutes / 60, minutes % 60)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = WaterStore(this)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Blue, onPrimary = Color.White,
                background = Background, surface = Color.White, onSurface = Navy, onBackground = Navy,
                secondaryContainer = Pale, onSecondaryContainer = Blue)) {
                WaterApp(store, lifecycle)
            }
        }
    }
}

@Composable
private fun WaterApp(store: WaterStore, lifecycle: Lifecycle) {
    var revision by remember { mutableIntStateOf(0) }
    var today by remember { mutableStateOf(LocalDate.now()) }
    var page by rememberSaveable { mutableIntStateOf(0) }
    var editor by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var explainPermission by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val pageScrolls = listOf(rememberScrollState(), rememberScrollState(), rememberScrollState())
    val scope = rememberCoroutineScope()
    fun refresh(restartInterval: Boolean = false) {
        today = LocalDate.now()
        store.refreshDay(today)
        Reminders.reconcile(context, store, restartInterval)
        revision++
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refresh(true)
        if (!it) notice = "Notifications are blocked. You can allow them in Android notification settings."
    }
    fun enableReminders() {
        store.update(store.preferences.copy(remindersEnabled = true))
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        refresh(true)
    }
    val openNotificationSettings: () -> Unit = {
        context.startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, Reminders.CHANNEL))
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) { refresh(); while (true) { delay(30_000); if (today != LocalDate.now()) refresh() else revision++ } }
    // Reading revision invalidates the snapshot after repository mutations.
    @Suppress("UNUSED_VARIABLE") val currentRevision = revision
    val prefs = store.preferences
    val drinks = store.drinks
    val add: (Int) -> Unit = { amount ->
        val drink = store.add(amount)
        refresh()
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            if (snackbar.showSnackbar("Added ${ml(amount)}", "Undo", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) {
                store.delete(drink.id); refresh()
            }
        }
    }
    Scaffold(containerColor = Background, snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
        Surface(color = Color.White, shadowElevation = 2.dp) {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().height(56.dp)) {
                listOf("Today" to Icons.Default.WaterDrop, "History" to Icons.Default.BarChart, "Settings" to Icons.Default.Settings)
                    .forEachIndexed { index, item ->
                        Column(Modifier.weight(1f).fillMaxHeight()
                            .selectable(selected = page == index, role = androidx.compose.ui.semantics.Role.Tab, onClick = { page = index }),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            val tint = if (page == index) Blue else Muted
                            Icon(item.second, contentDescription = null, tint = tint, modifier = Modifier.size(21.dp))
                            Spacer(Modifier.height(2.dp))
                            Text(item.first, color = tint, fontSize = 10.sp)
                        }
                    }
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(pageScrolls[page]).padding(horizontal = if (page == 0) 16.dp else 20.dp, vertical = if (page == 0) 8.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(if (page == 0) 6.dp else 20.dp)) {
            if (store.recoveryNotice) Text(store.recoveryMessage, color = MaterialTheme.colorScheme.error)
            when (page) {
                0 -> TodayScreen(today, prefs, drinks, add, { editor = "custom" }, { page = 2 },
                    { pendingDeleteId = it.id }, Reminders.scheduledAt(context), Reminders.allowed(context))
                1 -> HistoryScreen(today, store, drinks) { pendingDeleteId = it.id }
                else -> SettingsScreen(prefs, { editor = it }, { next -> store.update(next); refresh(true) }, { notice = it },
                    Reminders.allowed(context), { enabled ->
                        if (enabled) explainPermission = true
                        else { store.update(prefs.copy(remindersEnabled = false)); refresh() }
                    }, openNotificationSettings)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    when (editor) {
        "goal" -> NumberEditor("Daily water goal", listOf(prefs.goal), 250..10000, { editor = null }) {
            store.update(prefs.copy(goal = it.first())); refresh(); editor = null
        }
        "quick" -> NumberEditor("Quick-add sizes", prefs.quick, 10..5000, { editor = null }) {
            store.update(prefs.copy(quick = it)); refresh(); editor = null
        }
        "custom" -> NumberEditor("Add water", listOf(250), 10..5000, { editor = null }) { add(it.first()); editor = null }
        "interval" -> AlertDialog(onDismissRequest = { editor = null }, title = { Text("Reminder interval") },
            text = { Column { listOf(30, 60, 90, 120).forEach { value ->
                TextButton(onClick = { store.update(prefs.copy(interval = value)); refresh(true); editor = null }, modifier = Modifier.fillMaxWidth()) {
                    Text("Every $value minutes${if (value == prefs.interval) " ✓" else ""}")
                }
            } } }, confirmButton = { TextButton(onClick = { editor = null }) { Text("Close") } })
    }
    if (explainPermission) AlertDialog(onDismissRequest = { explainPermission = false },
        title = { Text("Enable water reminders?") },
        text = { Text("Allow notifications to receive gentle reminders during your selected hours. They pause when you reach your daily goal.") },
        confirmButton = { TextButton(onClick = { explainPermission = false; enableReminders() }) { Text("Continue") } },
        dismissButton = { TextButton(onClick = { explainPermission = false }) { Text("Not now") } })
    drinks.firstOrNull { it.id == pendingDeleteId }?.let { drink ->
        AlertDialog(onDismissRequest = { pendingDeleteId = null }, title = { Text("Delete this drink?") },
            text = { Text("Remove ${ml(drink.amount)} from your history?") },
            confirmButton = { TextButton(onClick = { store.delete(drink.id); refresh(); pendingDeleteId = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { pendingDeleteId = null }) { Text("Keep") } })
    }
    notice?.let { message -> AlertDialog(onDismissRequest = { notice = null }, title = { Text("Reminders") },
        text = { Text(message) }, confirmButton = { TextButton(onClick = { notice = null }) { Text("Got it") } }) }
}

@Composable
private fun Heading(title: String, subtitle: String, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Muted, style = MaterialTheme.typography.bodyLarge)
        }
        action?.invoke()
    }
}

@Composable
private fun CardBlock(content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content) }
}

@Composable
private fun DrinkList(drinks: List<Drink>, delete: (Drink) -> Unit) {
    CardBlock {
        if (drinks.isEmpty()) {
            Text("Your first sip starts here", fontWeight = FontWeight.SemiBold)
            Text("Add a drink to see your progress.", color = Muted)
        }
        drinks.sortedByDescending { it.timestamp }.forEachIndexed { index, drink ->
            if (index > 0) HorizontalDivider(color = Pale)
            var expanded by remember(drink.id) { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocalDrink, null, tint = Blue, modifier = Modifier.padding(end = 12.dp))
                Text(Instant.ofEpochMilli(drink.timestamp).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")), color = Muted)
                Text(ml(drink.amount), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 20.dp))
                Box {
                    IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, "Actions for ${ml(drink.amount)} drink") }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(text = { Text("Delete drink") }, onClick = { expanded = false; delete(drink) })
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryScreen(today: LocalDate, store: WaterStore, drinks: List<Drink>, delete: (Drink) -> Unit) {
    var weekOffset by rememberSaveable { mutableIntStateOf(0) }
    var selectedString by rememberSaveable { mutableStateOf(today.toString()) }
    val end = today.minusWeeks(weekOffset.toLong())
    val dates = (6 downTo 0).map { end.minusDays(it.toLong()) }
    val selected = LocalDate.parse(selectedString).takeIf { it in dates } ?: end
    val reached = dates.count { day -> Hydration.total(drinks, day) >= store.goalFor(day) }
    Heading("History", "Your hydration journey")
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        IconButton(onClick = { weekOffset++ }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Previous seven days") }
        Text("${dates.first().format(DateTimeFormatter.ofPattern("d MMM"))} – ${end.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}", fontWeight = FontWeight.SemiBold)
        IconButton(onClick = { weekOffset-- }, enabled = weekOffset > 0) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Next seven days") }
    }
    CardBlock {
        Text("Daily average", color = Muted)
        Text(ml(Hydration.average(drinks, dates, store.firstDay)), fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text("Goal reached $reached of ${dates.count { store.firstDay?.let { first -> !it.isBefore(first) } == true }} tracked days", color = Blue)
    }
    CardBlock {
        Text(if (weekOffset == 0) "Last seven days" else "Selected seven days", fontWeight = FontWeight.SemiBold)
        Text("Tap a day to see its drinks. Line marks the daily goal.", color = Muted, style = MaterialTheme.typography.bodySmall)
        val maxValue = dates.maxOf { maxOf(Hydration.total(drinks, it), store.goalFor(it)) }.coerceAtLeast(1) * 1.15f
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            dates.forEach { day ->
                val amount = Hydration.total(drinks, day)
                Column(Modifier.weight(1f).clickable { selectedString = day.toString() }
                    .semantics { contentDescription = "$day: ${ml(amount)}, goal ${ml(store.goalFor(day))}" }
                    .padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (amount > 0) amount.toString() else "–", fontSize = 10.sp, color = Muted)
                    Box(Modifier.height(130.dp).fillMaxWidth().padding(horizontal = 6.dp)) {
                        Box(Modifier.fillMaxWidth().fillMaxHeight((amount / maxValue).coerceIn(0.01f, 1f))
                            .align(Alignment.BottomCenter).background(if (day == selected) Blue else Color(0xFF9AD6FF), RoundedCornerShape(5.dp)))
                        Canvas(Modifier.fillMaxSize()) {
                            val y = size.height * (1 - store.goalFor(day) / maxValue)
                            drawLine(Muted, Offset(0f, y), Offset(size.width, y), strokeWidth = 2f)
                        }
                    }
                    Text(day.format(DateTimeFormatter.ofPattern("EEEEE")), color = if (day == selected) Blue else Muted)
                    Text(day.dayOfMonth.toString(), fontWeight = if (day == selected) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
    Text(selected.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Text("${ml(Hydration.total(drinks, selected))} / ${ml(store.goalFor(selected))}", color = Muted)
    DrinkList(drinks.filter { it.date() == selected }, delete)
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Blue, modifier = Modifier.padding(end = 12.dp))
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Medium); Text(value, color = Muted) }
        Icon(Icons.Default.ChevronRight, null, tint = Muted)
    }
}

@Composable
private fun SettingsScreen(prefs: Preferences, edit: (String) -> Unit, update: (Preferences) -> Unit,
    notice: (String) -> Unit, notificationsAllowed: Boolean, toggle: (Boolean) -> Unit, openNotifications: () -> Unit) {
    val context = LocalContext.current
    fun pickTime(start: Boolean) {
        val current = if (start) prefs.start else prefs.end
        TimePickerDialog(context, { _, hour, minute ->
            val next = if (start) prefs.copy(start = hour * 60 + minute) else prefs.copy(end = hour * 60 + minute)
            if (Hydration.validWindow(next.start, next.end)) update(next)
            else notice("Choose a start time earlier than the end time. Overnight schedules are not supported yet.")
        }, current / 60, current % 60, true).show()
    }
    Heading("Settings", "Make hydration your habit")
    Text("DAILY GOAL", color = Muted, style = MaterialTheme.typography.labelLarge)
    CardBlock {
        SettingRow(Icons.Default.WaterDrop, "Water goal", ml(prefs.goal)) { edit("goal") }
        HorizontalDivider(color = Pale)
        SettingRow(Icons.Default.LocalDrink, "Quick-add sizes", prefs.quick.joinToString(" / ") { ml(it) }) { edit("quick") }
    }
    Text("REMINDERS", color = Muted, style = MaterialTheme.typography.labelLarge)
    CardBlock {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.NotificationsNone, null, tint = Blue, modifier = Modifier.padding(end = 12.dp))
            Text("Drink reminders", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
            Switch(checked = prefs.remindersEnabled, onCheckedChange = toggle,
                modifier = Modifier.semantics { contentDescription = "Drink reminders" })
        }
        if (prefs.remindersEnabled && !notificationsAllowed)
            SettingRow(Icons.Default.NotificationsOff, "Notifications blocked", "Tap to allow in Android settings", openNotifications)
        HorizontalDivider(color = Pale)
        SettingRow(Icons.Default.Schedule, "Remind me every", "${prefs.interval} minutes") { edit("interval") }
        HorizontalDivider(color = Pale)
        SettingRow(Icons.Default.WbSunny, "Start time", clock(prefs.start)) { pickTime(true) }
        HorizontalDivider(color = Pale)
        SettingRow(Icons.Default.Bedtime, "End time", clock(prefs.end)) { pickTime(false) }
    }
    Text("Reminders pause outside these hours and after your daily goal. Delivery times are approximate and may be delayed by battery saving.", color = Muted)
    CardBlock {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Icon(Icons.Default.Lock, null, tint = Muted); Text("Your data stays on your phone", color = Muted) }
        Text("Amounts are measured in milliliters. The starting goal is editable and is not a personalized health recommendation.", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun NumberEditor(title: String, initial: List<Int>, range: IntRange, dismiss: () -> Unit, save: (List<Int>) -> Unit) {
    var values by rememberSaveable(title, stateSaver = listSaver<List<String>, String>(
        save = { it }, restore = { it.toList() }
    )) { mutableStateOf(initial.map { it.toString() }) }
    var submitted by rememberSaveable(title) { mutableStateOf(false) }
    val valid = values.all { state -> state.toIntOrNull()?.let { it in range } == true }
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            values.forEachIndexed { index, state ->
                val invalid = state.toIntOrNull()?.let { it in range } != true
                OutlinedTextField(value = state, onValueChange = { text -> values = values.mapIndexed { i, old -> if (i == index) text else old } }, singleLine = true,
                    label = { Text(if (values.size == 1) "Amount (ml)" else "Button ${index + 1} (ml)") },
                    isError = invalid && submitted, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = { if (invalid && submitted) Text("Enter ${range.first}–${range.last} ml") })
            }
        }
    }, confirmButton = { TextButton(onClick = { submitted = true; if (valid) save(values.map { it.toInt() }) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}

package com.waterreminder.app

import android.app.TimePickerDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
private val Background = Color(0xFFF5FAFF)
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
    var editor by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<Drink?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun refresh() { today = LocalDate.now(); store.refreshDay(today); revision++ }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) { refresh(); while (true) { delay(30_000); if (today != LocalDate.now()) refresh() } }
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
        NavigationBar(containerColor = Color.White) {
            listOf("Today" to Icons.Default.WaterDrop, "History" to Icons.Default.BarChart, "Settings" to Icons.Default.Settings)
                .forEachIndexed { index, item ->
                    NavigationBarItem(selected = page == index, onClick = { page = index },
                        icon = { Icon(item.second, contentDescription = null) }, label = { Text(item.first) })
                }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            when (page) {
                0 -> TodayScreen(today, prefs, drinks, add, { editor = "custom" }, { page = 2 }, { pendingDelete = it })
                1 -> HistoryScreen(today, store, drinks) { pendingDelete = it }
                else -> SettingsScreen(prefs, { editor = it }, { next -> store.update(next); refresh() }, { notice = it })
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
                TextButton(onClick = { store.update(prefs.copy(interval = value)); refresh(); editor = null }, modifier = Modifier.fillMaxWidth()) {
                    Text("Every $value minutes${if (value == prefs.interval) " ✓" else ""}")
                }
            } } }, confirmButton = { TextButton(onClick = { editor = null }) { Text("Close") } })
    }
    pendingDelete?.let { drink ->
        AlertDialog(onDismissRequest = { pendingDelete = null }, title = { Text("Delete this drink?") },
            text = { Text("Remove ${ml(drink.amount)} from your history?") },
            confirmButton = { TextButton(onClick = { store.delete(drink.id); refresh(); pendingDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Keep") } })
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
private fun TodayScreen(today: LocalDate, prefs: Preferences, drinks: List<Drink>, add: (Int) -> Unit,
    custom: () -> Unit, settings: () -> Unit, delete: (Drink) -> Unit) {
    val total = Hydration.total(drinks, today)
    val percentage = (total.toLong() * 100 / prefs.goal).toInt()
    Heading("Water Reminder", today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM"))) {
        IconButton(onClick = settings) { Icon(Icons.Default.Settings, "Open settings", tint = Muted) }
    }
    Box(Modifier.fillMaxWidth().height(280.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(260.dp).semantics { contentDescription = "$percentage percent of daily goal" }) {
            val stroke = 13.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(Pale, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(Blue, -90f, 360f * (total.toFloat() / prefs.goal).coerceIn(0f, 1f), false,
                Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            val wave = Path().apply {
                moveTo(size.width * .23f, size.height * .76f)
                cubicTo(size.width * .4f, size.height * .67f, size.width * .6f, size.height * .83f, size.width * .77f, size.height * .76f)
                quadraticBezierTo(size.width * .5f, size.height * 1.04f, size.width * .23f, size.height * .76f)
                close()
            }
            drawPath(wave, Color(0xFFB9E6FF))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(ml(total), fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Text("of ${ml(prefs.goal)}", color = Muted)
            Spacer(Modifier.height(12.dp))
            Text("$percentage% of daily goal", color = Blue, modifier = Modifier.background(Pale, CircleShape).padding(horizontal = 14.dp, vertical = 6.dp))
        }
    }
    Text(if (total >= prefs.goal) "Daily goal reached. Well done!" else "A little sip goes a long way", modifier = Modifier.alignCenter(), color = Navy)
    Text("Add water", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        prefs.quick.forEach { amount ->
            FilledTonalButton(onClick = { add(amount) }, shape = RoundedCornerShape(18.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 16.dp), modifier = Modifier.weight(1f)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.LocalDrink, contentDescription = null, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.height(8.dp)); Text("+${ml(amount)}")
                }
            }
        }
    }
    OutlinedButton(onClick = custom, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Text("Custom amount") }
    Card(onClick = settings, colors = CardDefaults.cardColors(containerColor = Pale), shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(Icons.Default.NotificationsNone, null, tint = Blue)
            Column(Modifier.weight(1f)) {
                Text("Reminders", fontWeight = FontWeight.Bold)
                Text("Not active yet", color = Muted)
                Text("Schedule preferences are ready to edit", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            Icon(Icons.Default.ChevronRight, null)
        }
    }
    Text("Today’s activity", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    DrinkList(drinks.filter { it.date() == today }, delete)
}

// Keep centering local to a full-width text instead of requiring a ColumnScope receiver.
private fun Modifier.alignCenter() = fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally)

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
        Text("Last seven days", fontWeight = FontWeight.SemiBold)
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
private fun SettingsScreen(prefs: Preferences, edit: (String) -> Unit, update: (Preferences) -> Unit, notice: (String) -> Unit) {
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
        SettingRow(Icons.Default.NotificationsNone, "Drink reminders", "Available in Stage 3") {
            notice("Your schedule preferences are saved. Notification permission and reminder delivery will be added in Stage 3.")
        }
        HorizontalDivider(color = Pale)
        SettingRow(Icons.Default.Schedule, "Remind me every", "${prefs.interval} minutes") { edit("interval") }
        HorizontalDivider(color = Pale)
        SettingRow(Icons.Default.WbSunny, "Start time", clock(prefs.start)) { pickTime(true) }
        HorizontalDivider(color = Pale)
        SettingRow(Icons.Default.Bedtime, "End time", clock(prefs.end)) { pickTime(false) }
    }
    Text("Reminders will pause outside these hours.", color = Muted)
    CardBlock {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Icon(Icons.Default.Lock, null, tint = Muted); Text("Your data stays on your phone", color = Muted) }
        Text("Amounts are measured in milliliters. The starting goal is editable and is not a personalized health recommendation.", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun NumberEditor(title: String, initial: List<Int>, range: IntRange, dismiss: () -> Unit, save: (List<Int>) -> Unit) {
    val values = remember { initial.map { mutableStateOf(it.toString()) } }
    var submitted by remember { mutableStateOf(false) }
    val valid = values.all { state -> state.value.toIntOrNull()?.let { it in range } == true }
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            values.forEachIndexed { index, state ->
                val invalid = state.value.toIntOrNull()?.let { it in range } != true
                OutlinedTextField(value = state.value, onValueChange = { state.value = it }, singleLine = true,
                    label = { Text(if (values.size == 1) "Amount (ml)" else "Button ${index + 1} (ml)") },
                    isError = invalid && submitted, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = { if (invalid && submitted) Text("Enter ${range.first}–${range.last} ml") })
            }
        }
    }, confirmButton = { TextButton(onClick = { submitted = true; if (valid) save(values.map { it.value.toInt() }) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}

package com.waterreminder.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Ink = Color(0xFF102044)
private val Blue = Color(0xFF008BFF)
private val Sky = Color(0xFF34B4FF)
private val Muted = Color(0xFF758BA2)
private fun amount(value: Int) = String.format(Locale.getDefault(), "%,d ml", value)
private fun clock(value: Int) = String.format(Locale.getDefault(), "%02d:%02d", value / 60, value % 60)

@Composable
internal fun TodayScreen(today: LocalDate, prefs: Preferences, drinks: List<Drink>, add: (Int) -> Unit,
    custom: () -> Unit, settings: () -> Unit, delete: (Drink) -> Unit, due: Long, notificationsAllowed: Boolean) {
    val total = Hydration.total(drinks, today)
    val progress = (total.toFloat() / prefs.goal).coerceIn(0f, 1f)
    val percentage = (total.toLong() * 100 / prefs.goal).toInt()
    val configuration = LocalConfiguration.current
    val compact = configuration.screenHeightDp < 700
    val diameter = (if (compact) 156f else (configuration.screenHeightDp * .29f).coerceIn(180f, 228f)).dp
    val fontScale = LocalDensity.current.fontScale
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Water Reminder", color = Ink, fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
            Text(today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), color = Muted, fontSize = 12.sp, lineHeight = 16.sp)
        }
        IconButton(onClick = settings) { Icon(Icons.Default.Settings, "Open settings", tint = Muted, modifier = Modifier.size(21.dp)) }
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.size(diameter), contentAlignment = Alignment.TopCenter) {
            WaterRing(progress, percentage, Modifier.fillMaxSize())
            Column(Modifier.padding(top = diameter * .23f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(amount(total), color = Ink, fontSize = if (compact) 25.sp else 28.sp, lineHeight = if (compact) 28.sp else 32.sp, fontWeight = FontWeight.ExtraBold)
                Text("of ${amount(prefs.goal)}", color = Muted, fontSize = 11.sp, lineHeight = 14.sp)
                if (fontScale <= 1.3f) Text("$percentage% of daily goal", color = Blue, fontSize = 10.sp, lineHeight = 13.sp,
                    modifier = Modifier.padding(top = 3.dp).background(Color(0xFFE7F5FF), CircleShape).padding(horizontal = 8.dp, vertical = 3.dp))
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Text(if (total >= prefs.goal) "Daily goal reached. Well done!" else "A little sip goes a long way", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Icon(Icons.Default.WaterDrop, null, tint = Sky, modifier = Modifier.padding(start = 3.dp).size(14.dp))
    }
    Text("Add water", color = Ink, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        prefs.quick.forEachIndexed { index, value ->
            Surface(onClick = { add(value) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp),
                color = Color.White, border = BorderStroke(1.dp, Color(0xFFE9F1F7)), shadowElevation = 2.dp) {
                Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    WaterGlass(Modifier.size(28.dp, 32.dp), .50f + index * .16f)
                    Text("+$value ml", color = Blue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    Surface(onClick = settings, shape = RoundedCornerShape(12.dp), color = Color(0xFFE5F4FF), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(42.dp).background(Color(0xFFD3EDFF), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Notifications, null, tint = Blue, modifier = Modifier.size(23.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("Next reminder", color = Ink, fontSize = 11.sp, lineHeight = 14.sp)
                val status = when {
                    !prefs.remindersEnabled -> "Reminders off"
                    !notificationsAllowed -> "Notifications blocked"
                    total >= prefs.goal -> "Paused for today"
                    due > 0 -> Instant.ofEpochMilli(due).atZone(ZoneId.systemDefault()).format(
                        DateTimeFormatter.ofPattern(if (Instant.ofEpochMilli(due).atZone(ZoneId.systemDefault()).toLocalDate() == today) "HH:mm" else "EEE HH:mm"))
                    else -> "Setting up"
                }
                Text(status, color = Ink, fontSize = if (status.length <= 9) 20.sp else 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold)
                Text("Every ${prefs.interval} min · ${clock(prefs.start)}–${clock(prefs.end)}", color = Muted, fontSize = 10.sp, lineHeight = 13.sp)
            }
            Icon(Icons.Default.ChevronRight, null, tint = Muted, modifier = Modifier.size(20.dp))
        }
    }
    Text("Today’s activity", color = Ink, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
    Surface(shape = RoundedCornerShape(12.dp), color = Color.White, border = BorderStroke(1.dp, Color(0xFFE9F1F7)), modifier = Modifier.fillMaxWidth()) {
        val recent = drinks.filter { it.date() == today }.sortedByDescending { it.timestamp }
        Column {
            if (recent.isEmpty()) Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text("Your first sip starts here", color = Ink, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
                Text("Tap a glass above to log a drink.", color = Muted, fontSize = 12.sp, lineHeight = 16.sp)
            }
            recent.forEachIndexed { index, drink ->
                if (index > 0) HorizontalDivider(color = Color(0xFFEEF3F8))
                Surface(onClick = { delete(drink) }, color = Color.Transparent, modifier = Modifier.fillMaxWidth()
                    .semantics { contentDescription = "Review ${amount(drink.amount)} drink" }) {
                    Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        WaterGlass(Modifier.size(17.dp, 24.dp), .65f)
                        Text(Instant.ofEpochMilli(drink.timestamp).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")),
                            color = Muted, fontSize = 11.sp, modifier = Modifier.padding(start = 14.dp).widthIn(min = 56.dp))
                        Text(amount(drink.amount), color = Ink, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ChevronRight, null, tint = Muted, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
    TextButton(onClick = custom, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp)); Text("Custom amount", fontSize = 12.sp) }
}

@Composable
private fun WaterRing(progress: Float, percentage: Int, modifier: Modifier) {
    Canvas(modifier.semantics { contentDescription = "$percentage percent of daily goal" }) {
        val w = size.width
        val stroke = 10.dp.toPx()
        val inset = stroke / 2
        val inner = stroke + 2.dp.toPx()
        val circle = Path().apply { addOval(Rect(inner, inner, w - inner, w - inner)) }
        clipPath(circle) {
            // Decorative water sits below the text; the surrounding arc carries exact progress.
            val y = w * .66f
            val back = Path().apply {
                moveTo(0f, y - w * .06f)
                cubicTo(w * .30f, y - w * .10f, w * .48f, y + w * .09f, w, y - w * .04f)
                lineTo(w, w); lineTo(0f, w); close()
            }
            drawPath(back, Color(0xFFBDEBFF))
            val water = Path().apply {
                moveTo(0f, y)
                cubicTo(w * .28f, y - w * .06f, w * .35f, y + w * .12f, w * .65f, y + w * .015f)
                cubicTo(w * .80f, y - w * .035f, w * .92f, y - w * .08f, w, y - w * .04f)
                lineTo(w, w); lineTo(0f, w); close()
            }
            drawPath(water, Brush.linearGradient(listOf(Color(0xFF69D5FF), Color(0xFF21AEF1), Color(0xFF8DE2FF)), Offset(0f,y), Offset(w,w)))
            val crest = Path().apply {
                moveTo(0f, y)
                cubicTo(w * .28f, y - w * .06f, w * .35f, y + w * .12f, w * .65f, y + w * .015f)
                cubicTo(w * .80f, y - w * .035f, w * .92f, y - w * .08f, w, y - w * .04f)
            }
            drawPath(crest, Color(0xFF0CA6ED), style = Stroke(1.5.dp.toPx()))
            drawPath(crest, Color.White.copy(alpha = .6f), style = Stroke(.6.dp.toPx()))
            listOf(.22f to .71f, .31f to .83f, .43f to .79f, .54f to .90f, .70f to .79f, .77f to .69f, .62f to .85f).forEachIndexed { i, p ->
                val radius = w * if (i % 3 == 0) .017f else .009f
                val center = Offset(w*p.first, w*p.second)
                drawCircle(Color(0xFF039EDC).copy(alpha=.35f), radius, center, style=Stroke(.7.dp.toPx()))
                drawCircle(Color.White.copy(alpha=.75f), radius*.45f, center-Offset(radius*.3f,radius*.3f))
            }
        }
        val arcSize = Size(w-stroke,w-stroke)
        drawArc(Color(0xFFD9EFFF),0f,360f,false,Offset(inset,inset),arcSize,style=Stroke(stroke))
        drawArc(Sky,-90f,360f*progress,false,Offset(inset,inset),arcSize,style=Stroke(stroke,cap=StrokeCap.Round))
    }
}

@Composable
private fun WaterGlass(modifier: Modifier, fill: Float) {
    Canvas(modifier) {
        val w=size.width; val h=size.height
        val glass=Path().apply { moveTo(w*.15f,h*.08f); quadraticTo(w*.5f,0f,w*.85f,h*.08f); lineTo(w*.76f,h*.94f); quadraticTo(w*.5f,h,w*.24f,h*.94f); close() }
        drawPath(glass, Brush.horizontalGradient(listOf(Color(0xFFEDF9FF),Color.White,Color(0xFFD9F2FF))))
        clipPath(glass) {
            val top=h*(1-fill)
            drawRect(Brush.horizontalGradient(listOf(Color(0xFF9DE4FF),Color(0xFF3EBFFF),Color(0xFF8BDCFF))),Offset(0f,top),Size(w,h-top))
            drawOval(Color(0xFFB6EBFF), Offset(w*.12f,top-h*.025f),Size(w*.76f,h*.07f))
            drawLine(Color.White.copy(alpha=.8f),Offset(w*.28f,h*.17f),Offset(w*.34f,h*.85f),w*.055f,StrokeCap.Round)
        }
        drawPath(glass,Color(0xFF50B7F5),style=Stroke(1.dp.toPx()))
        drawOval(Color(0xFF8DCEFA),Offset(w*.15f,h*.025f),Size(w*.70f,h*.08f),style=Stroke(.7.dp.toPx()))
    }
}

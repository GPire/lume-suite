package com.weenas.castbay.ui.screen

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weenas.castbay.R
import com.weenas.castbay.service.HealthModel
import com.weenas.castbay.service.HealthPhase
import com.weenas.castbay.ui.AppBackground
import com.weenas.castbay.ui.Perf
import com.weenas.castbay.ui.usingKeys
import com.weenas.castbay.viewmodel.AirPlayViewModel
import kotlinx.coroutines.launch

/**
 * TV health: how much memory is free, and one button that tidies the system the way TV Doctor does on a
 * computer (same safe list, groups of four, automatic put-back, fully undoable). One focus, plain words, a ring
 * that fills once and then rests (no animation at idle, so the GPU stays free).
 */
@Composable
fun HealthScreen(viewModel: AirPlayViewModel) {
    val context = LocalContext.current.applicationContext
    val light = Perf.light(context)
    val model = remember { HealthModel(context) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { model.refresh() }
    val phase = model.phase
    val keys = usingKeys()
    val primary = remember { FocusRequester() }
    LaunchedEffect(phase) { if (keys) runCatching { primary.requestFocus() } }

    val shape = RoundedCornerShape(28.dp)
    AppBackground {
        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 88.dp, vertical = 32.dp), contentAlignment = Alignment.CenterStart) {
            Column(
                modifier = Modifier.widthIn(max = 720.dp).verticalScroll(rememberScrollState())
                    .clip(shape).background(Color(0x99000000))
                    .border(1.dp, Brush.verticalGradient(listOf(Color(0x55FFFFFF), Color(0x12FFFFFF))), shape)
                    .padding(horizontal = 32.dp, vertical = 24.dp)
            ) {
                Text(stringResource(R.string.health_title), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Ring(model.freeMb, model.totalMb, light)
                    Spacer(Modifier.width(28.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        val (title, body) = texts(phase)
                        Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, lineHeight = 30.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(body, color = Color.White.copy(alpha = 0.8f), fontSize = 18.sp, lineHeight = 25.sp)
                    }
                }
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val focusMod = Modifier.focusRequester(primary)
                    when (phase) {
                        is HealthPhase.Ready -> {
                            if (phase.todo > 0) HomeButton(stringResource(R.string.health_optimize), focusMod) { scope.launch { model.optimize() } }
                            else HomeButton(stringResource(R.string.health_back), focusMod) { viewModel.navigateBackToHome() }
                            if (phase.disabled > 0) HomeButton(stringResource(R.string.health_undo), muted = true) { scope.launch { model.undo() } }
                        }
                        is HealthPhase.Done -> {
                            HomeButton(stringResource(R.string.health_back), focusMod) { viewModel.navigateBackToHome() }
                            HomeButton(stringResource(R.string.health_undo), muted = true) { scope.launch { model.undo() } }
                        }
                        is HealthPhase.AdbOff, is HealthPhase.Failed, is HealthPhase.Authorizing ->
                            HomeButton(stringResource(R.string.health_retry), focusMod) { scope.launch { model.refresh() } }
                        is HealthPhase.Manual, is HealthPhase.NotFire -> HomeButton(stringResource(R.string.health_back), focusMod) { viewModel.navigateBackToHome() }
                        else -> {}
                    }
                }
            }
        }
    }
}

@Composable
private fun texts(p: HealthPhase): Pair<String, String> = when (p) {
    HealthPhase.Checking -> stringResource(R.string.health_checking) to ""
    HealthPhase.Authorizing -> stringResource(R.string.health_connect_title) to stringResource(R.string.health_connect_body)
    is HealthPhase.Manual -> stringResource(R.string.health_manual_title, p.todo) to stringResource(R.string.health_manual_body)
    HealthPhase.NotFire -> stringResource(R.string.health_notfire_title) to stringResource(R.string.health_notfire_body)
    HealthPhase.AdbOff -> stringResource(R.string.health_off_title) to stringResource(R.string.health_off_body)
    is HealthPhase.Ready ->
        if (p.todo > 0) stringResource(R.string.health_ready_title, p.todo) to stringResource(R.string.health_ready_body)
        else stringResource(R.string.health_clean_title) to stringResource(R.string.health_clean_body, p.disabled)
    is HealthPhase.Working -> stringResource(R.string.health_working, p.done, p.total) to stringResource(R.string.health_working_body)
    is HealthPhase.Done ->
        (if (p.rolledBack) stringResource(R.string.health_rollback) else stringResource(R.string.health_done)) to
            stringResource(R.string.health_done_body, p.before, p.after)
    is HealthPhase.Failed -> stringResource(R.string.health_error) to p.message
}

/** Free memory as a ring: fills once to its value, then rests. */
@Composable
private fun Ring(freeMb: Int, totalMb: Int, light: Boolean) {
    val target = (freeMb.toFloat() / totalMb).coerceIn(0f, 1f)
    val fill by animateFloatAsState(target, tween(if (light) 0 else 900, easing = FastOutSlowInEasing), label = "ring")
    Box(modifier = Modifier.size(150.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize().graphicsLayer()) {
            val w = 12.dp.toPx(); val inset = w / 2
            val arc = Size(size.width - w, size.height - w)
            drawArc(Color.White.copy(alpha = 0.16f), -90f, 360f, false, Offset(inset, inset), arc, style = Stroke(w, cap = StrokeCap.Round))
            drawArc(Color(0xFF0A84FF), -90f, 360f * fill, false, Offset(inset, inset), arc, style = Stroke(w, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$freeMb", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.health_mb_free), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
        }
    }
}

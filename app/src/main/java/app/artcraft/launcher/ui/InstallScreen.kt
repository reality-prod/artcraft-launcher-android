package app.artcraft.launcher.ui

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.artcraft.launcher.bridge.ApkInstaller
import app.artcraft.launcher.bridge.NativeCore
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The launcher's own install screen: a progress ring around the app icon, a four-step timeline,
 * and plain-language messages for every way an install can stop (including "allow installs").
 */
@Composable
fun InstallScreen(
    s: InstallSession,
    onBackground: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onOpen: () -> Unit,
) {
    val ctx = LocalContext.current
    Dialog(
        onDismissRequest = onBackground,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.weight(1f))
                Hero(s)
                Spacer(Modifier.height(24.dp))
                Headline(s)
                Spacer(Modifier.height(28.dp))
                if (s.phase == Phase.NEEDS_PERMISSION) PermissionNote() else Steps(s)
                Spacer(Modifier.weight(1f))
                Actions(s, ctx, onBackground, onCancel, onRetry, onOpen)
            }
        }
    }
}

// ------------------------------------------------------------------ hero ring
@Composable
private fun Hero(s: InstallSession) {
    val cs = MaterialTheme.colorScheme
    val done = s.phase == Phase.DONE
    val failed = s.phase == Phase.FAILED
    val working = s.phase == Phase.VERIFYING || s.phase == Phase.INSTALLING
    val ringColor = if (failed) cs.error else cs.primary
    val track = cs.surfaceVariant
    val dotA = cs.primary
    val dotB = cs.secondary

    val target = when (s.phase) {
        Phase.DOWNLOADING, Phase.FAILED -> s.fraction
        Phase.NEEDS_PERMISSION -> 0f
        else -> 1f
    }
    val progress by animateFloatAsState(target, tween(450, easing = FastOutSlowInEasing), label = "ring")

    val loop = rememberInfiniteTransition(label = "loop")
    val angle by loop.animateFloat(0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "angle")
    val pulse by loop.animateFloat(
        0.96f, 1.06f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse",
    )

    val check = remember { Animatable(0f) }
    val burst = remember { Animatable(0f) }
    val shake = remember { Animatable(0f) }
    LaunchedEffect(done) {
        if (done) {
            launch { check.animateTo(1f, tween(450, 150)) }
            burst.animateTo(1f, tween(800, easing = LinearOutSlowInEasing))
        } else {
            check.snapTo(0f)
            burst.snapTo(0f)
        }
    }
    LaunchedEffect(failed) {
        if (failed) {
            repeat(3) {
                shake.animateTo(14f, tween(55))
                shake.animateTo(-14f, tween(55))
            }
            shake.animateTo(0f, tween(55))
        }
    }
    val badgeScale by animateFloatAsState(
        if (done || failed) 1f else 0f,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow), label = "badge",
    )

    Box(
        Modifier.size(220.dp).offset { IntOffset(shake.value.roundToInt(), 0) },
        contentAlignment = Alignment.Center,
    ) {
        if (!done && !failed) {
            Box(Modifier.size(184.dp).scale(pulse).background(ringColor.copy(alpha = 0.08f), CircleShape))
        }
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 10.dp.toPx()
            val inset = stroke / 2 + 8.dp.toPx()
            val d = size.minDimension - inset * 2
            val tl = Offset(inset, inset)
            val sz = Size(d, d)
            drawArc(track, 0f, 360f, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
            if (working) {
                rotate(angle, center) {
                    drawArc(ringColor, -90f, 110f, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
                }
            } else if (progress > 0.001f) {
                drawArc(ringColor, -90f, 360f * progress, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            val b = burst.value
            if (b > 0.01f && b < 0.99f) {
                for (i in 0 until 14) {
                    val a = Math.toRadians(i * (360.0 / 14))
                    val dist = d / 2 + 6.dp.toPx() + b * 46.dp.toPx()
                    drawCircle(
                        color = (if (i % 2 == 0) dotA else dotB).copy(alpha = 1f - b),
                        radius = 4.dp.toPx() * (1f - b * 0.5f),
                        center = Offset(center.x + (cos(a) * dist).toFloat(), center.y + (sin(a) * dist).toFloat()),
                    )
                }
            }
        }
        Box(Modifier.size(112.dp).scale(if (done || failed) 1f else pulse * 0.98f)) {
            IconTile(s.name, s.iconUrls, 112.dp)
        }
        if (badgeScale > 0.01f) {
            Box(
                Modifier.align(Alignment.BottomEnd).offset((-34).dp, (-34).dp)
                    .size(52.dp).scale(badgeScale)
                    .background(if (failed) cs.error else cs.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (failed) {
                    Text("!", color = cs.onError, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                } else {
                    val onPrimary = cs.onPrimary
                    Canvas(Modifier.size(28.dp)) {
                        val p = Path().apply {
                            moveTo(size.width * 0.05f, size.height * 0.55f)
                            lineTo(size.width * 0.38f, size.height * 0.85f)
                            lineTo(size.width * 0.95f, size.height * 0.15f)
                        }
                        val pm = PathMeasure()
                        pm.setPath(p, false)
                        val seg = Path()
                        pm.getSegment(0f, pm.length * check.value, seg, true)
                        drawPath(seg, onPrimary, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ text
@Composable
private fun Headline(s: InstallSession) {
    val title = when (s.phase) {
        Phase.NEEDS_PERMISSION -> "One quick permission"
        Phase.DOWNLOADING -> "Downloading ${s.name}"
        Phase.VERIFYING -> "Checking the download"
        Phase.INSTALLING -> "Installing ${s.name}"
        Phase.DONE -> "${s.name} is ready"
        Phase.FAILED -> "Couldn't install ${s.name}"
    }
    val sub = when (s.phase) {
        Phase.NEEDS_PERMISSION -> "Android asks you to allow installs from this launcher once."
        Phase.DOWNLOADING -> downloadLine(s)
        Phase.VERIFYING -> "Comparing the file with its published checksum"
        Phase.INSTALLING -> "Confirm in the Android installer"
        Phase.DONE -> s.tag
        Phase.FAILED -> s.error ?: "Something went wrong"
    }
    AnimatedContent(
        targetState = title,
        transitionSpec = {
            (fadeIn(tween(220, 90)) + slideInVertically { it / 3 }) togetherWith
                (fadeOut(tween(90)) + slideOutVertically { -it / 3 })
        },
        label = "headline",
    ) { t ->
        Text(t, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
    Spacer(Modifier.height(6.dp))
    Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 340.dp))
}

private fun downloadLine(s: InstallSession): String {
    if (s.totalBytes <= 0 || s.bytesPerSec <= 0) return "Starting…"
    val left = ((s.totalBytes - s.doneBytes) / s.bytesPerSec).coerceAtLeast(0)
    val eta = if (left < 90) "~$left s left" else "~${left / 60} min left"
    return "${NativeCore.size(s.doneBytes)} of ${NativeCore.size(s.totalBytes)} · ${NativeCore.size(s.bytesPerSec)}/s · $eta"
}

@Composable
private fun PermissionNote() {
    Text(
        "Tap Allow, switch on “Allow from this source”, then come back. The install carries on by itself.",
        style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
        modifier = Modifier.widthIn(max = 320.dp),
    )
}

// ------------------------------------------------------------------ timeline
private val STEPS = listOf("Download", "Verify", "Install", "Done")

private enum class StepState { Done, Active, Failed, Pending }

@Composable
private fun Steps(s: InstallSession) {
    fun index(p: Phase?) = when (p) {
        Phase.DOWNLOADING -> 0
        Phase.VERIFYING -> 1
        Phase.INSTALLING -> 2
        else -> 0
    }
    val failed = s.phase == Phase.FAILED
    val current = when (s.phase) {
        Phase.DONE -> 4
        Phase.FAILED -> index(s.failedPhase)
        else -> index(s.phase)
    }
    Column(Modifier.widthIn(max = 340.dp).fillMaxWidth()) {
        STEPS.forEachIndexed { i, label ->
            val state = when {
                i < current -> StepState.Done
                i == current -> if (failed) StepState.Failed else StepState.Active
                else -> StepState.Pending
            }
            StepRow(label, detail(i, s), state, last = i == STEPS.lastIndex, connectorFilled = i < current)
        }
    }
}

private fun detail(i: Int, s: InstallSession): String? = when (i) {
    0 -> when {
        s.phase == Phase.DOWNLOADING && s.totalBytes > 0 -> "${(s.fraction * 100).toInt()}%"
        s.totalBytes > 0 -> NativeCore.size(s.totalBytes)
        else -> null
    }
    1 -> when {
        s.verified == true -> "SHA-256 matched"
        s.phase == Phase.INSTALLING || s.phase == Phase.DONE -> "No checksum published, skipped"
        else -> null
    }
    2 -> if (s.phase == Phase.INSTALLING) "Waiting for you to confirm" else null
    else -> null
}

@Composable
private fun StepRow(label: String, detail: String?, state: StepState, last: Boolean, connectorFilled: Boolean) {
    val cs = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            StepDot(state)
            if (!last) {
                val fill by animateFloatAsState(if (connectorFilled) 1f else 0f, tween(400), label = "connector")
                Box(Modifier.width(2.dp).height(26.dp).background(cs.surfaceVariant)) {
                    Box(Modifier.fillMaxWidth().fillMaxHeight(fill).background(cs.primary))
                }
            }
        }
        Column(Modifier.padding(top = 3.dp)) {
            Text(
                label, style = MaterialTheme.typography.titleSmall,
                fontWeight = if (state == StepState.Active || state == StepState.Failed) FontWeight.Bold else FontWeight.Normal,
                color = if (state == StepState.Pending) cs.onSurfaceVariant else cs.onSurface,
            )
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun StepDot(state: StepState) {
    val cs = MaterialTheme.colorScheme
    val scale by animateFloatAsState(
        if (state == StepState.Done) 1f else 0.85f,
        spring(Spring.DampingRatioMediumBouncy), label = "dot",
    )
    val loop = rememberInfiniteTransition(label = "dotpulse")
    val glow by loop.animateFloat(
        0.25f, 0.75f, infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "glow",
    )
    Box(Modifier.size(28.dp).scale(scale), contentAlignment = Alignment.Center) {
        when (state) {
            StepState.Done -> Box(Modifier.fillMaxSize().background(cs.primary, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Check, null, tint = cs.onPrimary, modifier = Modifier.size(16.dp))
            }
            StepState.Active -> Canvas(Modifier.fillMaxSize()) {
                drawCircle(cs.primary.copy(alpha = glow), radius = size.minDimension / 2)
                drawCircle(cs.primary, radius = size.minDimension / 5)
            }
            StepState.Failed -> Box(Modifier.fillMaxSize().background(cs.error, CircleShape), contentAlignment = Alignment.Center) {
                Text("!", color = cs.onError, fontWeight = FontWeight.Bold)
            }
            StepState.Pending -> Canvas(Modifier.fillMaxSize()) {
                drawCircle(cs.outlineVariant, radius = size.minDimension / 2 - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
            }
        }
    }
}

// ------------------------------------------------------------------ buttons
@Composable
private fun Actions(
    s: InstallSession,
    ctx: Context,
    onBackground: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onOpen: () -> Unit,
) {
    val big = Modifier.fillMaxWidth().heightIn(min = 52.dp)
    when (s.phase) {
        Phase.NEEDS_PERMISSION -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Button(
                onClick = { runCatching { ctx.startActivity(ApkInstaller.unknownSourcesIntent(ctx).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
                modifier = big,
            ) { Text("Allow installs") }
            TextButton(onClick = onBackground) { Text("Not now") }
        }
        Phase.DOWNLOADING, Phase.VERIFYING, Phase.INSTALLING -> Row(
            Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Spacer(Modifier.weight(1f))
            FilledTonalButton(onClick = onBackground) { Text("Run in background") }
        }
        Phase.DONE -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (s.openable) Button(onClick = onOpen, modifier = big) { Text("Open") }
            TextButton(onClick = onBackground) { Text("Done") }
        }
        Phase.FAILED -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Button(onClick = onRetry, modifier = big) { Text("Try again") }
            TextButton(onClick = onBackground) { Text("Close") }
        }
    }
}

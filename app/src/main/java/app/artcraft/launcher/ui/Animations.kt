package app.artcraft.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.pow

/**
 * Custom animation utilities for the ArtCraft Launcher.
 * 
 * Provides reusable animation components and modifiers.
 */

// =============================================================================
// Animation Specs
// =============================================================================

/** Bouncy spring animation spec */
val BouncySpringSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessLow
)

/** Fast snap animation spec */
val FastSnapSpec = snap<Float>()

/** Smooth ease in/out animation spec */
val SmoothEaseInOutSpec = tween<Float>(
    durationMillis = 300,
    easing = EaseInOut
)

/** Quick fade animation spec */
val QuickFadeSpec = tween<Float>(
    durationMillis = 150,
    easing = EaseIn
)

// =============================================================================
// Animated Modifiers
// =============================================================================

/** Modifier that adds a pulsing animation effect */
@Composable
fun Modifier.pulse(
    isPulsing: Boolean = true,
    durationMillis: Int = 1000,
    minScale: Float = 0.95f,
    maxScale: Float = 1.05f
): Modifier {
    if (!isPulsing) return this
    
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    
    val scale by infiniteTransition.animateFloat(
        initialValue = minScale,
        targetValue = maxScale,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    return this.scale(scale)
}

/** Modifier that adds a shimmer effect for loading states */
@Composable
fun Modifier.shimmer(
    isLoading: Boolean = true,
    durationMillis: Int = 1500,
    shimmerColor: Color = Color.White.copy(alpha = 0.5f)
): Modifier {
    if (!isLoading) return this
    
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )
    
    return this.then(Modifier.background(
        Brush.linearGradient(
            colors = listOf(
                shimmerColor.copy(alpha = 0f),
                shimmerColor,
                shimmerColor.copy(alpha = 0f)
            ),
            start = Offset(shimmerOffset, 0f),
            end = Offset(shimmerOffset + 1f, 1f)
        )
    ))
}

/** Modifier that makes the element shake */
@Composable
fun Modifier.shake(
    isShaking: Boolean = true,
    durationMillis: Int = 500,
    shakeIntensity: Float = 5f
): Modifier {
    if (!isShaking) return this
    
    val infiniteTransition = rememberInfiniteTransition(label = "shake")
    
    val offset by infiniteTransition.animateFloat(
        initialValue = -shakeIntensity,
        targetValue = shakeIntensity,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shakeOffset"
    )
    
    return this.rotate(offset)
}

/** Modifier that adds a rotation animation */
@Composable
fun Modifier.rotateInfinitely(
    isRotating: Boolean = true,
    durationMillis: Int = 2000
): Modifier {
    if (!isRotating) return this
    
    val infiniteTransition = rememberInfiniteTransition(label = "rotate")
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    return this.rotate(rotation)
}

// =============================================================================
// Loading Indicators
// =============================================================================

/**
 * Custom circular loading indicator with animation
 */
@Composable
fun ArtCraftCircularProgress(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = color.copy(alpha = 0.2f),
    strokeWidth: Dp = 4.dp,
    size: Dp = 48.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "circularProgress")
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Track
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(
                    width = strokeWidth,
                    color = trackColor,
                    shape = CircleShape
                )
        )
        
        // Progress arc (simplified - just rotating indicator)
        Box(
            modifier = Modifier
                .size(size * 0.6f)
                .clip(CircleShape)
                .background(color)
                .rotate(rotation)
        )
    }
}

/**
 * Dot loading indicator (three bouncing dots)
 */
@Composable
fun DotLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 8.dp,
    spacing: Dp = 4.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "dotLoading")
    
    val dot1Y by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -10f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1"
    )
    
    val dot2Y by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -10f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 150, easing = EaseOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2"
    )
    
    val dot3Y by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -10f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 300, easing = EaseOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3"
    )
    
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.padding(spacing))
        Box(
            modifier = Modifier
                .size(size)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.padding(spacing))
        Box(
            modifier = Modifier
                .size(size)
                .background(color, CircleShape)
        )
    }
}

// =============================================================================
// Transition Animations
// =============================================================================

/**
 * Animated visibility with slide and fade effects
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun SlideFadeVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically(),
        exit = fadeOut() + slideOutVertically(),
        modifier = modifier
    ) {
        content()
    }
}

/**
 * Scale fade transition
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ScaleFadeTransition(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        content()
    }
}

// =============================================================================
// Utility Functions
// =============================================================================

/**
 * Fast out slow in easing for smooth animations
 */
fun fastOutSlowInEasing(fraction: Float): Float {
    val a = 0.4f
    val b = 0.0f
    return if (fraction < 0.5f) {
        (2f * fraction).pow(2f) * ((b + 1f) * 2f * fraction - b)
    } else {
        val x = -2f * fraction + 2f
        (x.pow(2f) * ((b + 1f) * x + b)) / 2f + 0.5f
    }
}

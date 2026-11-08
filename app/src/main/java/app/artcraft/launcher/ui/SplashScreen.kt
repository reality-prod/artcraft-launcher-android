package app.artcraft.launcher.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.with
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Custom splash screen with animated ArtCraft logo and progress.
 * 
 * Features:
 * - Animated logo with scale and rotation effects
 * - Loading progress indicator
 * - Smooth transition to main screen
 * - Gradient background
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    var showSplash by remember { mutableStateOf(true) }
    var animationComplete by remember { mutableStateOf(false) }
    
    // Create gradient background
    val gradientColors = if (isSystemInDarkTheme()) {
        listOf(
            Color(0xFF0F1115),
            Color(0xFF1E222B),
            Color(0xFF232832)
        )
    } else {
        listOf(
            Color(0xFFFAF8F6),
            Color(0xFFF2EEEA),
            Color(0xFFE0D9D5)
        )
    }
    
    val gradientBrush = remember(gradientColors) {
        Brush.linearGradient(
            colors = gradientColors,
            start = Offset(0f, 0f),
            end = Offset(1f, 1f)
        )
    }
    
    // Animated logo scale
    val logoScale = animateFloatAsState(
        targetValue = if (animationComplete) 1.2f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "logoScale"
    )
    
    // Rotating animation for logo elements
    val infiniteTransition = rememberInfiniteTransition(label = "infiniteTransition")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    // Progress animation
    val progressValue by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "progress"
    )
    
    // Animate text opacity
    val textAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseOutBack),
            repeatMode = RepeatMode.Reverse
        ),
        label = "textAlpha"
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = gradientBrush),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Animated logo with scale and rotation
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .scale(logoScale.value),
                contentAlignment = Alignment.Center
            ) {
                // Outer ring that rotates
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .rotate(rotationAngle),
                ) {
                    // This creates a subtle rotation effect
                    Text(
                        text = "C",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .size(120.dp)
                            .alpha(0.1f)
                            .rotate(-rotationAngle)
                    )
                }
                
                // Main logo
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.size(120.dp)
                ) {
                    Text(
                        text = "Art",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Craft",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Tagline with pulsing animation
            AnimatedVisibility(
                visible = !animationComplete,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                Text(
                    text = "Loading creative apps...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = textAlpha),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.alpha(textAlpha)
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Progress indicator
            if (!animationComplete) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            }
        }
    }
    
    // Trigger completion and navigate after animation
    LaunchedEffect(Unit) {
        delay(3000) // Show splash for 3 seconds
        animationComplete = true
        delay(500) // Wait for animation to complete
        showSplash = false
        onTimeout()
    }
}

/**
 * Install screen with custom animations.
 * 
 * Features:
 * - Progress circle animation
 * - File information display
 * - Smooth transitions
 */
@Composable
fun InstallScreen(
    appName: String,
    progress: Float,
    fileSize: Long,
    downloadedBytes: Long,
    speed: Float,
    onCancel: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Download icon with animation
            val downloadIconRotation = animateFloatAsState(
                targetValue = if (progress > 0) 360f * progress else 0f,
                animationSpec = tween(durationMillis = 500),
                label = "downloadIconRotation"
            )
            
            Box(
                modifier = Modifier.size(100.dp),
                contentAlignment = Alignment.Center
            ) {
                // Circular progress background
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(
                            color = primaryColor.copy(alpha = 0.1f),
                            shape = MaterialTheme.shapes.large
                        )
                )
                
                // Progress text
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = primaryColor,
                    textAlign = TextAlign.Center
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // App name
            Text(
                text = appName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Status text
            Text(
                text = "Downloading...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Progress bar with file info
            Column(modifier = Modifier.fillMaxWidth()) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = primaryColor,
                    trackColor = primaryColor.copy(alpha = 0.2f)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Download stats
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatFileSize(downloadedBytes),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Text(
                        text = if (speed > 0) "${formatFileSizePerSecond(speed)}/s" else "Calculating...",
                        style = MaterialTheme.typography.labelMedium,
                        color = primaryColor
                    )
                }
                
                Text(
                    text = "Total: ${formatFileSize(fileSize)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Cancel button
            Text(
                text = "Cancel",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clickable(onClick = onCancel)
            )
        }
    }
}

/**
 * Format bytes to human readable string
 */
private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024
    if (kb < 1024) return "%.2f KB".format(kb)
    val mb = kb / 1024
    if (mb < 1024) return "%.2f MB".format(mb)
    val gb = mb / 1024
    return "%.2f GB".format(gb)
}

/**
 * Format bytes per second to human readable string
 */
private fun formatFileSizePerSecond(bytesPerSecond: Float): String {
    if (bytesPerSecond < 1024) return "%.0f B/s".format(bytesPerSecond)
    val kb = bytesPerSecond / 1024
    if (kb < 1024) return "%.2f KB/s".format(kb)
    val mb = kb / 1024
    return "%.2f MB/s".format(mb)
}

/**
 * Success animation screen
 */
@Composable
fun InstallSuccessScreen(
    appName: String,
    onDismiss: () -> Unit
) {
    var visible by remember { mutableStateOf(true) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Checkmark animation
            val checkmarkScale = animateFloatAsState(
                targetValue = if (visible) 1.5f else 1.0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "checkmarkScale"
            )
            
            Box(
                modifier = Modifier.size(100.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            shape = MaterialTheme.shapes.large
                        )
                )
                
                Text(
                    text = "✓",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.scale(checkmarkScale.value)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Success!",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "$appName has been installed",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = "Tap to continue",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clickable(onClick = { 
                    visible = false
                    onDismiss() 
                })
            )
        }
    }
    
    LaunchedEffect(visible) {
        if (!visible) {
            delay(300)
            onDismiss()
        }
    }
}

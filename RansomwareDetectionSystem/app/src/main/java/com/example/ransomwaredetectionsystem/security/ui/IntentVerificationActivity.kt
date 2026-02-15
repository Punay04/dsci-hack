package com.example.ransomwaredetectionsystem.security.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.example.ransomwaredetectionsystem.security.domain.BehavioralResult
import com.example.ransomwaredetectionsystem.security.domain.InteractionSample
import com.example.ransomwaredetectionsystem.security.services.PermissionMonitorService
import com.example.ransomwaredetectionsystem.security.viewmodel.IntentVerificationViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.hypot

class IntentVerificationActivity : ComponentActivity() {
    private val viewModel by viewModels<IntentVerificationViewModel>()
    private val resultSent = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        configureSecureWindow()

        val requestId = intent.getStringExtra(PermissionMonitorService.EXTRA_REQUEST_ID).orEmpty()
        val packageName = intent.getStringExtra(PermissionMonitorService.EXTRA_PACKAGE_NAME).orEmpty()
        val permissions = intent.getStringArrayListExtra(PermissionMonitorService.EXTRA_PERMISSIONS).orEmpty()
        val riskScore = intent.getIntExtra(PermissionMonitorService.EXTRA_RISK_SCORE, 0)
        val riskLevel = intent.getStringExtra(PermissionMonitorService.EXTRA_RISK_LEVEL).orEmpty()

        setContent {
            MaterialTheme {
                IntentVerificationScreen(
                    packageName = packageName,
                    permissions = permissions,
                    riskScore = riskScore,
                    riskLevel = riskLevel,
                    onVerify = { interactionSample ->
                        val behaviorResult = viewModel.evaluateBehavior(interactionSample)
                        dispatchVerificationResult(
                            requestId = requestId,
                            intentVerified = true,
                            behaviorResult = behaviorResult
                        )
                    },
                    onBlock = {
                        dispatchVerificationResult(
                            requestId = requestId,
                            intentVerified = false,
                            behaviorResult = BehavioralResult(
                                isGenuine = false,
                                reasons = listOf("User denied request")
                            )
                        )
                    }
                )
            }
        }
    }

    private fun configureSecureWindow() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SECURE or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.setHideOverlayWindows(true)
        }
        window.decorView.filterTouchesWhenObscured = true
        setFinishOnTouchOutside(false)
    }

    private fun dispatchVerificationResult(
        requestId: String,
        intentVerified: Boolean,
        behaviorResult: BehavioralResult
    ) {
        if (!resultSent.compareAndSet(false, true)) return

        val resultIntent = Intent(PermissionMonitorService.ACTION_VERIFICATION_RESULT).apply {
            setPackage(packageName)
            putExtra(PermissionMonitorService.EXTRA_REQUEST_ID, requestId)
            putExtra(PermissionMonitorService.EXTRA_INTENT_VERIFIED, intentVerified)
            putExtra(PermissionMonitorService.EXTRA_BEHAVIOR_GENUINE, behaviorResult.isGenuine)
            putStringArrayListExtra(
                PermissionMonitorService.EXTRA_BEHAVIOR_REASONS,
                ArrayList(behaviorResult.reasons)
            )
        }
        sendBroadcast(resultIntent)
        finish()
    }
}

@Composable
private fun IntentVerificationScreen(
    packageName: String,
    permissions: List<String>,
    riskScore: Int,
    riskLevel: String,
    onVerify: (InteractionSample) -> Unit,
    onBlock: () -> Unit
) {
    BackHandler(enabled = true) {
        // Intentionally blocked to prevent bypass.
    }

    val shownAt = remember { SystemClock.uptimeMillis() }
    val scope = rememberCoroutineScope()
    var holdProgress by remember { mutableFloatStateOf(0f) }
    var statusMessage by remember { mutableStateOf("Press and hold for 3 seconds to approve.") }
    var firstTouchAt by remember { mutableLongStateOf(0L) }
    var holdStartAt by remember { mutableLongStateOf(0L) }
    var lastEventAt by remember { mutableLongStateOf(0L) }
    var moveEvents by remember { mutableIntStateOf(0) }
    var totalDistance by remember { mutableFloatStateOf(0f) }
    var lastX by remember { mutableFloatStateOf(0f) }
    var lastY by remember { mutableFloatStateOf(0f) }
    var isHolding by remember { mutableStateOf(false) }
    val intervals = remember { mutableStateListOf<Long>() }
    var progressJob by remember { mutableStateOf<Job?>(null) }
    var hasCompleted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        statusMessage = "This app is requesting sensitive access. Hold to confirm your intent."
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF4F6FA))
            .padding(20.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Security Check: Verify Your Intent",
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "This app is requesting access to sensitive system permissions. " +
                        "Apps can misuse these permissions to lock files or spy on users. " +
                        "Only allow if you fully trust this app.",
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "App: $packageName\nRisk: $riskLevel ($riskScore)\nSignals: ${permissions.joinToString()}",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = Color(0xFF37474F)
                )

                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { holdProgress },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(Color(0xFF101820), RoundedCornerShape(14.dp))
                        .pointerInteropFilter { event ->
                            if (hasCompleted) return@pointerInteropFilter true
                            when (event.actionMasked) {
                                MotionEvent.ACTION_DOWN -> {
                                    if (firstTouchAt == 0L) {
                                        firstTouchAt = event.eventTime
                                    }
                                    holdStartAt = event.eventTime
                                    lastEventAt = event.eventTime
                                    lastX = event.x
                                    lastY = event.y
                                    moveEvents = 0
                                    totalDistance = 0f
                                    intervals.clear()
                                    holdProgress = 0f
                                    statusMessage = "Keep holding..."
                                    isHolding = true

                                    progressJob?.cancel()
                                    progressJob = scope.launch {
                                        while (isHolding) {
                                            val elapsed = SystemClock.uptimeMillis() - holdStartAt
                                            holdProgress = (elapsed / HOLD_DURATION_MS.toFloat())
                                                .coerceIn(0f, 1f)
                                            delay(16L)
                                        }
                                    }
                                    true
                                }

                                MotionEvent.ACTION_MOVE -> {
                                    if (!isHolding) return@pointerInteropFilter false
                                    val dx = event.x - lastX
                                    val dy = event.y - lastY
                                    totalDistance += hypot(dx, dy)
                                    moveEvents += 1
                                    intervals += (event.eventTime - lastEventAt).coerceAtLeast(0L)
                                    lastEventAt = event.eventTime
                                    lastX = event.x
                                    lastY = event.y
                                    true
                                }

                                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                    if (!isHolding) return@pointerInteropFilter false
                                    isHolding = false
                                    progressJob?.cancel()
                                    val duration = event.eventTime - holdStartAt
                                    holdProgress = (duration / HOLD_DURATION_MS.toFloat()).coerceIn(0f, 1f)

                                    if (event.actionMasked == MotionEvent.ACTION_UP && duration >= HOLD_DURATION_MS) {
                                        hasCompleted = true
                                        statusMessage = "Verification complete. Evaluating behavior..."
                                        onVerify(
                                            InteractionSample(
                                                pressDurationMs = duration,
                                                responseDelayMs = (firstTouchAt - shownAt).coerceAtLeast(0L),
                                                moveEventCount = moveEvents,
                                                gestureDistancePx = totalDistance,
                                                eventIntervalsMs = intervals.toList()
                                            )
                                        )
                                    } else {
                                        holdProgress = 0f
                                        statusMessage = "Hold for at least 3 seconds to approve."
                                    }
                                    true
                                }

                                else -> false
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            progress = { holdProgress },
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Press and Hold",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Button(
                    onClick = onBlock,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !hasCompleted
                ) {
                    Text("Block Request")
                }
            }
        }
    }
}

private const val HOLD_DURATION_MS = 3_000L

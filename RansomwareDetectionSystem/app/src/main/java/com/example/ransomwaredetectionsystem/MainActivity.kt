package com.example.ransomwaredetectionsystem

import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ransomwaredetectionsystem.security.viewmodel.ShieldViewModel
import com.example.ransomwaredetectionsystem.ui.theme.RansomwareDetectionSystemTheme
import com.example.ransomwaredetectionsystem.util.CanaryManager
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RansomwareDetectionSystemTheme {
                MainScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(shieldViewModel: ShieldViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val canaryManager = remember { CanaryManager(context) }
    val shieldUiState by shieldViewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    
    var canaryCount by remember { mutableIntStateOf(0) }
    var isThreatDetected by remember { mutableStateOf(false) }
    var isChecking by remember { mutableStateOf(false) }
    var showLocationDetails by remember { mutableStateOf(false) }

    // Check protection status and threats on launch
    LaunchedEffect(Unit) {
        isChecking = true
        isThreatDetected = canaryManager.checkThreats()
        canaryCount = canaryManager.getCanaryCount()
        isChecking = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Ransomware Detection")
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    val intent = Intent(context, CommunityActivity::class.java)
                    context.startActivity(intent)
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Chat, contentDescription = "Community")
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isThreatDetected) 
                        Color(0xFFFFEBEE) else Color(0xFFE8F5E9)
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = if (isThreatDetected) 
                            Icons.Default.GppBad else Icons.Default.GppGood,
                        contentDescription = null,
                        tint = if (isThreatDetected) Color.Red else Color(0xFF2E7D32),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (isThreatDetected) "Potential Threat Detected!" else "System Secure",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isThreatDetected) Color.Red else Color(0xFF2E7D32)
                    )
                    if (isThreatDetected) {
                        Text(
                            text = "Canary files have been modified or deleted.",
                            fontSize = 14.sp,
                            color = Color.DarkGray,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (shieldUiState.enabled) Color(0xFFE8F5E9) else Color(0xFFFFF8E1)
                )
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (shieldUiState.enabled) Icons.Default.Security else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (shieldUiState.enabled) Color(0xFF2E7D32) else Color(0xFFF57C00)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Human Intent Verification Shield",
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (shieldUiState.enabled) {
                                    "Running: intercepting dangerous permission flows."
                                } else {
                                    "Disabled: high-risk permission requests are not being intercepted."
                                },
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                if (shieldUiState.enabled) {
                                    shieldViewModel.disableShield()
                                } else {
                                    shieldViewModel.enableShield()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (shieldUiState.enabled) "Disable Shield" else "Enable Shield")
                        }
                        OutlinedButton(
                            onClick = {
                                context.startActivity(shieldViewModel.accessibilitySettingsIntent())
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Accessibility Settings")
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            context.startActivity(shieldViewModel.usageAccessSettingsIntent())
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Text("Usage Access Settings")
                    }

                    if (shieldUiState.recentEvents.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Recent Security Events",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        shieldUiState.recentEvents.take(3).forEach { event ->
                            val formattedTime = DateFormat.format("hh:mm:ss a", event.timestamp)
                            Text(
                                text = "$formattedTime • ${event.eventType} • ${event.packageName ?: "unknown"}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Monitoring Info Card
            if (canaryCount > 0 && !isThreatDetected) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Active Monitoring",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = "$canaryCount canary files are being monitored.",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { showLocationDetails = !showLocationDetails }) {
                                Icon(
                                    imageVector = if (showLocationDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = "Show details"
                                )
                            }
                        }
                        
                        if (showLocationDetails) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                            )
                            Text(
                                text = "Base Location:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = canaryManager.canaryBaseDir,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            Text(
                                text = "\nNote: Folders and files are hidden (start with '.') to avoid accidental deletion.",
                                fontSize = 10.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            } else if (canaryCount == 0 && !isChecking) {
                Text(
                    text = "Protection is currently disabled.",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    scope.launch {
                        isChecking = true
                        canaryCount = canaryManager.generateCanaries()
                        isThreatDetected = false
                        isChecking = false
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = !isChecking
            ) {
                Icon(Icons.Default.Shield, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (canaryCount > 0) "Refresh Protection" else "Enable Protection")
            }

            if (isChecking) {
                Spacer(modifier = Modifier.height(16.dp))
                CircularProgressIndicator()
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    RansomwareDetectionSystemTheme {
        MainScreen()
    }
}

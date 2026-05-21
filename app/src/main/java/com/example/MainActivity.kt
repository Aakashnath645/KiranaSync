package com.example

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.GroceryItem
import com.example.ui.theme.MyApplicationTheme
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Read quick commerce sync extra from notification tap
        val syncAppExtra = intent.getStringExtra("EXTRA_SYNC_APP")

        setContent {
            MyApplicationTheme {
                val viewModel: GroceryViewModel = viewModel()
                MainAppScreen(
                    viewModel = viewModel,
                    initialSyncApp = syncAppExtra,
                    onOpenNotificationSettings = {
                        openNotificationListenerSettings()
                    }
                )
            }
        }
    }

    private fun openNotificationListenerSettings() {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Please search 'Notification Access' in device settings", Toast.LENGTH_LONG).show()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    viewModel: GroceryViewModel,
    initialSyncApp: String?,
    onOpenNotificationSettings: () -> Unit
) {
    val context = LocalContext.current
    val items by viewModel.allItems.collectAsStateWithLifecycle()
    val userPrefState by viewModel.userPreference.collectAsStateWithLifecycle()

    // Screen navigation tabs state
    var activeTab by remember { mutableStateOf(AppTab.Home) }

    // Screen navigation
    var showScanner by remember { mutableStateOf(false) }
    var showManualAddDialog by remember { mutableStateOf(false) }
    
    // Quick Commerce interception state
    var showQuickSyncDialogForApp by remember { mutableStateOf(initialSyncApp) }

    // Rationale dialog states
    var showCameraRationale by remember { mutableStateOf(false) }
    var showNotificationRationale by remember { mutableStateOf(false) }

    // Permissions launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showScanner = true
        } else {
            Toast.makeText(context, "Camera permission is required to scan products", Toast.LENGTH_LONG).show()
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    // Check notification permission (Android 13+)
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Is notification listener enabled?
    var isNotificationAccessGranted by remember { mutableStateOf(false) }
    
    // Check listener status periodically when screen resumes or state changes
    LaunchedEffect(showQuickSyncDialogForApp) {
        isNotificationAccessGranted = isNotificationListenerEnabled(context)
    }

    // Dynamic categories priority list from Room DB preferences!
    val baseCategories = remember(userPrefState) {
        userPrefState?.categoryOrderJson?.split(",") ?: listOf("Staples", "Dairy", "Snacks", "Spices", "Other")
    }
    val categories = listOf("All") + baseCategories
    var selectedCategory by remember { mutableStateOf("All") }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Kitchen,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                "KiranaSync",
                                fontWeight = FontWeight.Black,
                                style = MaterialTheme.typography.titleLarge,
                                letterSpacing = (-0.5).sp
                            )
                        }
                        Text(
                            "Aapka Pantry Assistant • Indian Market Focus",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                actions = {
                    Box(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable {
                                Toast.makeText(context, "KiranaSync helps you track pantry expiry. Scan barcodes to add!", Toast.LENGTH_LONG).show()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (userPrefState?.username?.isNotEmpty() == true) {
                                userPrefState?.username?.split(" ")?.mapNotNull { it.firstOrNull() }?.joinToString("")?.take(2)?.uppercase() ?: "AP"
                            } else "AP",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            )
        },
        bottomBar = {
            KiranaElegantBottomBar(
                activeTab = activeTab,
                onTabSelect = { activeTab = it },
                onScanClick = {
                    val hasCam = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                    if (hasCam) {
                        showScanner = true
                    } else {
                        showCameraRationale = true
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (activeTab) {
                AppTab.Home -> {
                    // Notification Listener Info banner (Tailored green organic container)
                    if (!isNotificationAccessGranted) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFD3EABC) // Precise organic light-green background from Design HTML Markup
                            ),
                            shape = RoundedCornerShape(24.dp), // rounded-3xl equivalent!
                            border = BorderStroke(1.dp, Color(0xFFB7CC9F))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color.White),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.NotificationsActive,
                                        contentDescription = "Active Alert",
                                        tint = Color(0xFF386A20),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "ZEPTO ORDER DETECTED",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 11.sp,
                                        color = Color(0xFF141F07),
                                        letterSpacing = 0.5.sp
                                    )
                                    Text(
                                        "Sync delivery items from Zepto, Blinkit or Swiggy automatically.",
                                        fontSize = 12.sp,
                                        color = Color(0xFF386A20),
                                        lineHeight = 16.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = { showNotificationRationale = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF386A20)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("enable_interceptor_button")
                                ) {
                                    Text("SETUP", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Quick Status Card + Simulation Buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Total Count Card
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Total Items", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "${items.size}",
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // Expiring Count Card
                        val thresholdDays = userPrefState?.reminderThresholdDays ?: 3
                        val expiringCount = items.count { item ->
                            val days = ((item.expiryTimestamp - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).toInt()
                            days < thresholdDays
                        }
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(
                                containerColor = if (expiringCount > 0) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Expires Soon (<$thresholdDays days)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "$expiringCount",
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (expiringCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Test Simulation Panel
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "Test Q-Commerce Interception",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                              ) {
                                Button(
                                    onClick = {
                                        simulateDeliveryNotification(context, "Zepto")
                                        Toast.makeText(context, "Zepto notification posted! Check your notification drawer.", Toast.LENGTH_LONG).show()
                                    },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                                ) {
                                    Text("Simulate Zepto", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = {
                                        simulateDeliveryNotification(context, "Blinkit")
                                        Toast.makeText(context, "Blinkit notification posted! Check notification drawer.", Toast.LENGTH_LONG).show()
                                    },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                                ) {
                                    Text("Simulate Blinkit", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Manual Add Shortcut Banner
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "MY PANTRY INVENTORY",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = {
                                viewModel.resetForm()
                                showManualAddDialog = true
                            },
                            modifier = Modifier.testTag("manual_item_add_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Manually", fontWeight = FontWeight.Bold)
                        }
                    }

                    // Categories Filter Tabs
                    ScrollableTabRow(
                        selectedTabIndex = categories.indexOf(selectedCategory),
                        edgePadding = 12.dp,
                        divider = {},
                        indicator = {},
                        containerColor = Color.Transparent,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        categories.forEach { category ->
                            val isSelected = category == selectedCategory
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedCategory = category },
                                label = { Text(category, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                ),
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .testTag("filter_chip_$category")
                            )
                        }
                    }

                    // Filtered Grocery List
                    val filteredItems = if (selectedCategory == "All") {
                        items
                    } else {
                        items.filter { it.category == selectedCategory }
                    }

                    if (filteredItems.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.verticalScroll(rememberScrollState())
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ShoppingCart,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .padding(bottom = 12.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                                Text(
                                    "No items in $selectedCategory",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "Tap 'Scan Barcode' or 'Add Manually' to stack up your pantry inventory.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 12.dp)
                                .testTag("grocery_list"),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 80.dp)
                        ) {
                            items(items = filteredItems, key = { it.id }) { item ->
                                GroceryItemCard(
                                    item = item,
                                    onDelete = {
                                        viewModel.deleteItem(item)
                                        Toast.makeText(context, "${item.name} removed from KiranaSync!", Toast.LENGTH_SHORT).show()
                                    },
                                    onTransferToShopping = {
                                        viewModel.addShoppingItem(item.name, item.category, item.quantity)
                                        Toast.makeText(context, "Added ${item.name} ($item.quantity) To Shopping List!", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                }
                AppTab.Stats -> {
                    PantryStatsSection(viewModel = viewModel)
                }
                AppTab.ShoppingList -> {
                    ShoppingListSection(viewModel = viewModel)
                }
                AppTab.Settings -> {
                    SettingsSection(viewModel = viewModel)
                }
            }
        }
    }

    // Camera Rationale Dialog
    if (showCameraRationale) {
        AlertDialog(
            onDismissRequest = { showCameraRationale = false },
            title = { Text("Camera Permission Required") },
            text = { Text("KiranaSync uses the camera to scan product barcodes and fetch grocery details automatically. This lets you manage your pantry without typing!") },
            confirmButton = {
                Button(onClick = {
                    showCameraRationale = false
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }) {
                    Text("Allow Camera")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCameraRationale = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Notification Access Rationale Dialog
    if (showNotificationRationale) {
        AlertDialog(
            onDismissRequest = { showNotificationRationale = false },
            title = { Text("Notification Interceptor Access") },
            text = { Text("KiranaSync can automatically intercept finished grocery deliveries from Blinkit, Zepto, and Swiggy to automatically add items to your virtual pantry. This requires 'Notification Access' in device settings.") },
            confirmButton = {
                Button(onClick = {
                    showNotificationRationale = false
                    onOpenNotificationSettings()
                }) {
                    Text("Go to Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNotificationRationale = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Smart Barcode Scanner Immersive Overlay
    if (showScanner) {
        Dialog(
            onDismissRequest = { showScanner = false }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                // Camera preview holder
                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    onBarcodeScanned = { barcode ->
                        viewModel.onBarcodeScanned(barcode)
                        showScanner = false
                        showManualAddDialog = true // launches bottom-sheet-like dialog
                    }
                )

                // High fidelity visual target frame
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(280.dp, 180.dp)
                                .border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Align Barcode in the frame",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                // Cheat Sheet barcodes row for testing!
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.8f))
                        .padding(16.dp)
                ) {
                    Text(
                        "No barcode? Sim-tap mock barcodes below:",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        viewModel.barcodeMap.forEach { (ean, product) ->
                            Button(
                                onClick = {
                                    viewModel.onBarcodeScanned(ean)
                                    showScanner = false
                                    showManualAddDialog = true
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(product.name, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text(ean, fontSize = 9.sp, color = Color.White.copy(alpha = 0.7f))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = { showScanner = false },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close scanner")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Cancel Scanning")
                    }
                }
            }
        }
    }

    // Quick commerce Intercepted Dialog / Sync
    showQuickSyncDialogForApp?.let { app ->
        AlertDialog(
            onDismissRequest = { showQuickSyncDialogForApp = null },
            icon = { Icon(Icons.Default.Sync, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("$app Delivery Found!") },
            text = {
                Column {
                    Text("KiranaSync detected a delivered order from $app. Would you like to sync these items directly into your pantry?")
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("📦 Order Items (Auto Detected):", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("• Amul Butter (Dairy) - 100g", fontSize = 11.sp)
                            Text("• Maggi 70g (Snacks) - 70g", fontSize = 11.sp)
                            Text("• Aashirvaad Atta 5kg (Staples) - 5kg", fontSize = 11.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        // Prepopulate database with order items
                        val itemsToSync = listOf(
                            GroceryItem(name = "Amul Butter", quantity = "100g", category = "Dairy", expiryTimestamp = System.currentTimeMillis() + 86400000 * 5),
                            GroceryItem(name = "Maggi 70g", quantity = "70g", category = "Snacks", expiryTimestamp = System.currentTimeMillis() + 86400000 * 90),
                            GroceryItem(name = "Aashirvaad Atta 5kg", quantity = "5kg", category = "Staples", expiryTimestamp = System.currentTimeMillis() + 86400000 * 180)
                        )
                        itemsToSync.forEach { item ->
                            viewModel.setDirectForm(item.name, item.category, item.quantity)
                            viewModel.saveItem()
                        }
                        showQuickSyncDialogForApp = null
                        Toast.makeText(context, "Synced $app order items successfully!", Toast.LENGTH_LONG).show()
                    },
                    modifier = Modifier.testTag("confirm_sync_button")
                ) {
                    Text("Sync Items")
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuickSyncDialogForApp = null }) {
                    Text("Ignore")
                }
            }
        )
    }

    // Form Add / Edit Item Dialog
    if (showManualAddDialog) {
        val formName by viewModel.formName.collectAsStateWithLifecycle()
        val formQuantity by viewModel.formQuantity.collectAsStateWithLifecycle()
        val formCategory by viewModel.formCategory.collectAsStateWithLifecycle()
        val formExpiryDays by viewModel.formExpiryDays.collectAsStateWithLifecycle()
        val scannedBarcode by viewModel.scannedBarcode.collectAsStateWithLifecycle()

        Dialog(
            onDismissRequest = {
                viewModel.resetForm()
                showManualAddDialog = false
            }
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = if (scannedBarcode != null) "Smart Scanner Add" else "Add Pantry Item",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    if (scannedBarcode != null) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCodeScanner,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "EAN Detected: $scannedBarcode",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Field 1: Name
                    OutlinedTextField(
                        value = formName,
                        onValueChange = { viewModel.formName.value = it },
                        label = { Text("Product Name") },
                        placeholder = { Text("e.g. Amul Butter") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .testTag("item_name_input")
                    )

                    // Field 2: Quantity
                    OutlinedTextField(
                        value = formQuantity,
                        onValueChange = { viewModel.formQuantity.value = it },
                        label = { Text("Quantity / Vol") },
                        placeholder = { Text("e.g. 500g, 1L, 2 Packets") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .testTag("item_qty_input")
                    )

                    // Field 3: Category
                    Text(
                        "Pantry Category",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Staples", "Dairy", "Snacks", "Spices", "Other").forEach { cat ->
                            val isSelected = cat == formCategory
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.formCategory.value = cat },
                                label = { Text(cat, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Field 4: Expiry days
                    Text(
                        "Expires in how many days?",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    OutlinedTextField(
                        value = formExpiryDays,
                        onValueChange = { viewModel.formExpiryDays.value = it },
                        label = { Text("Days to Expiry") },
                        placeholder = { Text("e.g. 7") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .testTag("item_exp_input")
                    )

                    // Fast Day Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("2", "5", "7", "30").forEach { d ->
                            val label = when (d) {
                                "2" -> "2d (Critical)"
                                "5" -> "5d (Warning)"
                                "7" -> "1 Week"
                                "30" -> "1 Month"
                                else -> d
                            }
                            OutlinedButton(
                                onClick = { viewModel.formExpiryDays.value = d },
                                border = BorderStroke(1.dp, if (formExpiryDays == d) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(label, fontSize = 10.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Buttons save / cancel
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                viewModel.resetForm()
                                showManualAddDialog = false
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                if (formName.trim().isNotEmpty()) {
                                    viewModel.saveItem()
                                    showManualAddDialog = false
                                    Toast.makeText(context, "Item Saved!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Please enter product name", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .weight(1.5f)
                                .testTag("save_item_button")
                        ) {
                            Text("Save Item")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GroceryItemCard(
    item: GroceryItem,
    onDelete: () -> Unit,
    onTransferToShopping: () -> Unit
) {
    // Expiry warnings
    val now = System.currentTimeMillis()
    val daysLeft = ((item.expiryTimestamp - now) / (1000 * 60 * 60 * 24)).toInt()

    val (badgeColor, textColor, textLabel, borderLineColor) = when {
        daysLeft < 0 -> Quadruple(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, "Expired!", MaterialTheme.colorScheme.error)
        daysLeft < 2 -> Quadruple(Color(0xFFFCE4E4), Color(0xFFC62828), "Critical! (${daysLeft.daysLeftd} left)", Color(0xFFBA1A1A))
        daysLeft < 5 -> Quadruple(Color(0xFFFFFDE7), Color(0xFFF57F17), "Warning (${daysLeft.daysLeftd} left)", Color(0xFFE9B800))
        else -> Quadruple(Color(0xFFE8F5E9), Color(0xFF2E7D32), "$daysLeft days left", Color.Transparent)
    }

    val categoryIcon = when (item.category) {
        "Dairy" -> Icons.Default.LocalCafe
        "Staples" -> Icons.Default.Kitchen
        "Snacks" -> Icons.Default.Fastfood
        "Spices" -> Icons.Default.Whatshot
        else -> Icons.Default.ShoppingCart
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("grocery_item_${item.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Visual dynamic vertical indicator border decoration on extreme left:
            if (borderLineColor != Color.Transparent) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(borderLineColor)
                )
            } else {
                Spacer(modifier = Modifier.width(4.dp))
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category Icon Indicator background
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = categoryIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${item.quantity} • ${item.category}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (item.barcode != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                Icons.Default.QrCodeScanner,
                                contentDescription = "Scanned",
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    // Expiry Badge label
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(badgeColor)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = textLabel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = textColor
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Copy to Shopping List action
                IconButton(
                    onClick = onTransferToShopping,
                    modifier = Modifier.testTag("transfer_shopping_button_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.AddShoppingCart,
                        contentDescription = "Transfer Low Stock to Shopping List",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.82f)
                    )
                }

                // Delete action
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("delete_item_button_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Consume / Delete Item",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.82f)
                    )
                }
            }
        }
    }
}

// Data class for Quadruple configuration
data class Quadruple<out A, out B, out C, out D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

// Help text labels formatting helper
private val Int.daysLeftd: String
    get() = if (this == 1) "1 day" else "$this days"

@Composable
fun PantryStatsSection(viewModel: GroceryViewModel) {
    val items by viewModel.allItems.collectAsStateWithLifecycle()
    val pref by viewModel.userPreference.collectAsStateWithLifecycle()
    val currencyChar = if (pref?.defaultCurrency?.contains("INR") == true) "₹" else "₹"
    val threshold = pref?.reminderThresholdDays ?: 3

    val totalCount = items.size
    val now = System.currentTimeMillis()
    val expiringSoonCount = items.count {
        val days = ((it.expiryTimestamp - now) / (86400000)).toInt()
        days in 0..threshold
    }
    val expiredCount = items.count { it.expiryTimestamp < now }
    val safeCount = totalCount - expiringSoonCount - expiredCount

    val categoryCounts = items.groupBy { it.category }.mapValues { it.value.size }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Pantry Analytics Breakdown",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary
        )

        // Health meter card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Pantry Health Index",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = when {
                                totalCount == 0 -> "Pantry is empty"
                                expiredCount > 0 -> "Needs attention"
                                expiringSoonCount > 0 -> "Good, use expiring items"
                                else -> "Excellent pantry condition!"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (expiredCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (totalCount == 0) "100%" else "${((safeCount.toFloat() / totalCount.toFloat()) * 100).toInt()}%",
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Progress indicators
                Text("Stock Expiry Health", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                ) {
                    if (totalCount == 0) {
                        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)))
                    } else {
                        val expiredRatio = expiredCount.toFloat() / totalCount
                        val expiringRatio = expiringSoonCount.toFloat() / totalCount
                        val safeRatio = safeCount.toFloat() / totalCount

                        if (expiredRatio > 0f) {
                            Box(modifier = Modifier.weight(expiredRatio).fillMaxHeight().background(MaterialTheme.colorScheme.error))
                        }
                        if (expiringRatio > 0f) {
                            Box(modifier = Modifier.weight(expiringRatio).fillMaxHeight().background(Color(0xFFFFB300)))
                        }
                        if (safeRatio > 0f) {
                            Box(modifier = Modifier.weight(safeRatio).fillMaxHeight().background(Color(0xFF4CAF50)))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Legend row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF4CAF50)))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Safe: $safeCount", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFFFB300)))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Soon: $expiringSoonCount", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Expired: $expiredCount", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Category graph cards
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Pantry Categories Distribution",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))

                val customCategories = pref?.categoryOrderJson?.split(",") ?: listOf("Staples", "Dairy", "Snacks", "Spices", "Other")

                if (totalCount == 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No inventory data available. Register products to track category volumes.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    customCategories.forEach { category ->
                        val count = categoryCounts[category] ?: 0
                        val percent = if (totalCount > 0) (count.toFloat() / totalCount * 100).toInt() else 0
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(category, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text("$count items ($percent%)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { if (totalCount > 0) count.toFloat() / totalCount else 0f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Suggestions card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Smart Kitchen Insights",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = when {
                        expiredCount > 0 -> "You have $expiredCount expired items. Clean them out and make space in your kitchen!"
                        expiringSoonCount > 0 -> "You have $expiringSoonCount items expiring in less than $threshold days. Consider adding them to today's dinner recipe!"
                        totalCount < 3 -> "Your pantry inventory is running low. Visit the Shopping List tab to list replenishment items as soon as possible!"
                        else -> "Your pantry is well stocked and all items are fresh. Keep scanning purchases to stay ahead of waste."
                    },
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Spacer(modifier = Modifier.height(60.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListSection(viewModel: GroceryViewModel) {
    val shoppingItems by viewModel.allShoppingItems.collectAsStateWithLifecycle()
    val pref by viewModel.userPreference.collectAsStateWithLifecycle()
    val currency = pref?.defaultCurrency ?: "INR (₹)"
    val currencyChar = if (currency.contains("INR")) "₹" else "₹"
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Local manual entry state
    var newName by remember { mutableStateOf("") }
    var newCategory by remember { mutableStateOf("Staples") }
    var newQuantity by remember { mutableStateOf("") }
    var showAddRow by remember { mutableStateOf(false) }

    // Quick Commerce Simulation States
    var showCheckoutSheet by remember { mutableStateOf(false) }
    var checkOutInApp by remember { mutableStateOf("Zepto") }
    var isPlacingOrder by remember { mutableStateOf(false) }

    val categoriesList = pref?.categoryOrderJson?.split(",") ?: listOf("Staples", "Dairy", "Snacks", "Spices", "Other")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Shopping & Replenishments",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Check off items as you buy them in the market.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontWeight = FontWeight.SemiBold
                )
            }
            IconButton(
                onClick = { showAddRow = !showAddRow },
                colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Icon(
                    imageVector = if (showAddRow) Icons.Default.Close else Icons.Default.Add,
                    contentDescription = "New item",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        // Animated input drawer
        AnimatedVisibility(
            visible = showAddRow,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Add Grocery To Buy", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Item Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("shopping_input_name")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newQuantity,
                            onValueChange = { newQuantity = it },
                            label = { Text("Qty (e.g. 500g, 2 units)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("shopping_input_qty")
                        )

                        // Selector
                        var dropdownExpanded by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = newCategory,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Category") },
                                trailingIcon = {
                                    IconButton(onClick = { dropdownExpanded = true }) {
                                        Icon(Icons.Default.ArrowDropDown, null)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            DropdownMenu(
                                expanded = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false }
                            ) {
                                categoriesList.forEach { category ->
                                    DropdownMenuItem(
                                        text = { Text(category) },
                                        onClick = {
                                            newCategory = category
                                            dropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            if (newName.trim().isNotEmpty()) {
                                viewModel.addShoppingItem(newName, newCategory, newQuantity)
                                newName = ""
                                newQuantity = ""
                                showAddRow = false
                                Toast.makeText(context, "$newName added to shopping list!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("shopping_add_submit")
                    ) {
                        Text("Add Item To List", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Actions toolbar
        if (shoppingItems.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${shoppingItems.count { it.isChecked }} of ${shoppingItems.size} purchased",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { viewModel.deleteCheckedShoppingItems() }
                    ) {
                        Text("Clear Bought", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    TextButton(
                        onClick = { viewModel.clearShoppingList() }
                    ) {
                        Text("Clear All", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        // List
        if (shoppingItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.PlaylistAdd,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Your shopping list is pristine!",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Add items manually or tap the transfer basket icon directly from your pantry inventory.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("shopping_items_list"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(shoppingItems, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (item.isChecked) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            else MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = item.isChecked,
                                onCheckedChange = { viewModel.toggleShoppingItemChecked(item) },
                                modifier = Modifier.testTag("shopping_item_checkbox_${item.id}")
                            )
                            Spacer(modifier = Modifier.width(8.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    fontWeight = FontWeight.Bold,
                                    color = if (item.isChecked) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    else MaterialTheme.colorScheme.onSurface,
                                    style = if (item.isChecked) MaterialTheme.typography.bodyMedium.copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)
                                    else MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "${item.quantity} • ${item.category}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }

                            IconButton(
                                onClick = { viewModel.deleteShoppingItem(item) },
                                modifier = Modifier.testTag("delete_shopping_item_${item.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Quick Commerce Direct Checkout Trigger button!
            Button(
                onClick = { showCheckoutSheet = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF386A20)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .testTag("qcom_convert_order_button")
            ) {
                Icon(
                    imageVector = Icons.Default.FlashOn,
                    contentDescription = null,
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Convert to Q-Commerce Express Order", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(72.dp))
    }

    // Express Q-Commerce Simulation Bottom Drawer
    if (showCheckoutSheet) {
        Dialog(onDismissRequest = { if (!isPlacingOrder) showCheckoutSheet = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .testTag("qcom_checkout_drawer"),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(2.dp, Color(0xFF386A20))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        "Instant Delivery Order Dispatch",
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF386A20)
                    )

                    Text(
                        "Simulates transferring your current KiranaSync shopping list into external instant checkout baskets.",
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Partner selection pills
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Zepto", "Blinkit", "Swiggy").forEach { partner ->
                            val isSelected = checkOutInApp == partner
                            val pColor = when (partner) {
                                "Zepto" -> Color(0xFF5E2B96)
                                "Blinkit" -> Color(0xFFF7EC13)
                                else -> Color(0xFFFF5200)
                            }
                            val pTextColor = if (partner == "Blinkit") Color.Black else Color.White

                            FilterChip(
                                selected = isSelected,
                                onClick = { checkOutInApp = partner },
                                label = { Text(partner, fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = pColor,
                                    selectedLabelColor = pTextColor,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("checkout_partner_$partner")
                            )
                        }
                    }

                    // Basket receipt preview
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Basket Count", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                                Text("${shoppingItems.size} items", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Delivery Address", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                                Text("Aakash's Home Flat 402", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Estimated ETA", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF386A20))
                                Text("9 - 13 minutes", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFF386A20))
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Simulated Bill", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text("$currencyChar ${shoppingItems.size * 55 + 20}.00", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                            }
                        }
                    }

                    // Bottom sheet actions
                    if (isPlacingOrder) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            CircularProgressIndicator(color = Color(0xFF386A20))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Assembling products & finalizing webhook payload...", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showCheckoutSheet = false },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Cancel")
                            }

                            Button(
                                onClick = {
                                    isPlacingOrder = true
                                    scope.launch {
                                        kotlinx.coroutines.delay(2000)
                                        isPlacingOrder = false
                                        showCheckoutSheet = false
                                        
                                        // Auto transfer checked-items or all list items to Pantry on order placement simulation!
                                        shoppingItems.forEach { sItem ->
                                            viewModel.addGroceryItem(
                                                name = sItem.name,
                                                category = sItem.category,
                                                qty = sItem.quantity,
                                                expiryDays = 7,
                                                barcode = "Sim-$checkOutInApp"
                                            )
                                        }

                                        // Auto clear checkout list!
                                        viewModel.clearShoppingList()

                                        // Fire a congratulations status push notification!
                                        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                                        val cId = "kiranasync_checkout_success"
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            val chan = NotificationChannel(cId, "Q-Commerce Checkout Confirmations", NotificationManager.IMPORTANCE_HIGH)
                                            notificationManager.createNotificationChannel(chan)
                                        }
                                        val notification = NotificationCompat.Builder(context, cId)
                                            .setSmallIcon(android.R.drawable.stat_sys_download_done)
                                            .setContentTitle("$checkOutInApp Order Dispatched!")
                                            .setContentText("Your instant order is arriving in 11 minutes! Items synced automatically.")
                                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                                            .setAutoCancel(true)
                                            .build()
                                        notificationManager.notify(4002, notification)

                                        Toast.makeText(context, "$checkOutInApp Order Dispatched! Check status notifications.", Toast.LENGTH_LONG).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF386A20)),
                                modifier = Modifier.weight(1.5f).testTag("checkout_order_submit")
                            ) {
                                Text("Place Order Instantly", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSection(viewModel: GroceryViewModel) {
    val prefState by viewModel.userPreference.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val pref = prefState ?: com.example.data.UserPreference()

    // Preferences states
    var username by remember(prefState) { mutableStateOf(pref.username) }
    var currency by remember(prefState) { mutableStateOf(pref.defaultCurrency) }
    var weightUnit by remember(prefState) { mutableStateOf(pref.preferredWeightUnit) }
    var volumeUnit by remember(prefState) { mutableStateOf(pref.preferredVolumeUnit) }
    var alertThreshold by remember(prefState) { mutableFloatStateOf(pref.reminderThresholdDays.toFloat()) }

    // Reorder categories state
    var categoryStringList by remember(prefState) {
        val list = pref.categoryOrderJson.split(",").toMutableList()
        mutableStateOf(list)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Profile & Settings",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary
        )

        // Profile Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
            shape = RoundedCornerShape(24.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    val initials = username.split(" ").mapNotNull { it.firstOrNull() }.joinToString("").take(2).uppercase()
                    Text(
                        text = if (initials.isEmpty()) "AP" else initials,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = username.ifEmpty { "Aakash Nath" },
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        "Pantry Manager Premium Account",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Basic Profile Editing Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Profile Details", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Manager Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("settings_username_input")
                )

                OutlinedTextField(
                    value = currency,
                    onValueChange = { currency = it },
                    label = { Text("Default Currency Symbol") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("settings_currency_input")
                )
            }
        }

        // Measurements & Expiry Alarm threshold card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Measurement & Alarms", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                // Weight units
                Text("Default Mass/Weight Unit", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    listOf("grams (g)", "kilograms (kg)").forEach { unit ->
                        val cleanUnit = if (unit.contains("kg")) "kg" else "g"
                        val isSelected = weightUnit == cleanUnit
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { weightUnit = cleanUnit },
                                modifier = Modifier.testTag("radio_weight_$cleanUnit")
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(unit, fontSize = 12.sp)
                        }
                    }
                }

                // Volume units
                Text("Default Liquid/Volume Unit", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    listOf("milliliters (ml)", "liters (L)").forEach { unit ->
                        val cleanUnit = if (unit.contains("L")) "L" else "ml"
                        val isSelected = volumeUnit == cleanUnit
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { volumeUnit = cleanUnit },
                                modifier = Modifier.testTag("radio_volume_$cleanUnit")
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(unit, fontSize = 12.sp)
                        }
                    }
                }

                HorizontalDivider()

                // Warning threshold slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Expiry Alerts Reminder Threshold", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("${alertThreshold.toInt()} Days", fontSize = 12.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                }

                Slider(
                    value = alertThreshold,
                    onValueChange = { alertThreshold = it },
                    valueRange = 1f..14f,
                    steps = 13,
                    modifier = Modifier.testTag("alert_threshold_slider")
                )
                Text(
                    text = "Automatically runs daily checks. KiranaSync will post a local notification for items expiring in ${alertThreshold.toInt()} days.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }

        // Reorder list categories card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Customize Dashboard Category Display Priority",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Reorder categories below. Tapping directional arrows will reorder category chips immediately on the main dashboard screen.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    lineHeight = 14.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                categoryStringList.forEachIndexed { index, cat ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(cat, fontWeight = FontWeight.Bold, fontSize = 12.sp)

                            Row {
                                if (index > 0) {
                                    IconButton(
                                        onClick = {
                                            val newList = categoryStringList.toMutableList()
                                            val temp = newList[index]
                                            newList[index] = newList[index - 1]
                                            newList[index - 1] = temp
                                            categoryStringList = newList
                                        },
                                        modifier = Modifier.size(32.dp).testTag("category_up_$cat")
                                    ) {
                                        Icon(Icons.Default.ArrowUpward, null, modifier = Modifier.size(16.dp))
                                    }
                                }
                                if (index < categoryStringList.size - 1) {
                                    IconButton(
                                        onClick = {
                                            val newList = categoryStringList.toMutableList()
                                            val temp = newList[index]
                                            newList[index] = newList[index + 1]
                                            newList[index + 1] = temp
                                            categoryStringList = newList
                                        },
                                        modifier = Modifier.size(32.dp).testTag("category_down_$cat")
                                    ) {
                                        Icon(Icons.Default.ArrowDownward, null, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Settings confirmation save button
        Button(
            onClick = {
                val csvCategories = categoryStringList.joinToString(",")
                viewModel.saveUserPreference(
                    username = username.trim(),
                    currency = currency.trim(),
                    weightUnit = weightUnit,
                    volumeUnit = volumeUnit,
                    threshold = alertThreshold.toInt(),
                    categoryOrder = csvCategories
                )
                Toast.makeText(context, "Preferences & category priority orders saved successfully!", Toast.LENGTH_SHORT).show()
                
                // Immediately trigger simulated daily alarm right now to demonstrate push working!
                viewModel.scheduleExpiryCheckAlarm(context, alertThreshold.toInt())
            },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().testTag("save_settings_button")
        ) {
            Icon(Icons.Default.Check, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Save Preferences & Test Alerts", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(72.dp))
    }
}

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onBarcodeScanned: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val previewView = remember { PreviewView(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val analyzer = remember { BarcodeAnalyzer(onBarcodeScanned) }

    AndroidView(
        factory = { previewView },
        modifier = modifier,
        update = {
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().apply {
                    setSurfaceProvider(previewView.surfaceProvider)
                }
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .apply {
                        setAnalyzer(executor, analyzer)
                    }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    Log.e("CameraPreview", "Use case binding failed", e)
                }
            }, ContextCompat.getMainExecutor(context))
        }
    )

    DisposableEffect(Unit) {
        onDispose {
            executor.shutdown()
        }
    }
}

fun isNotificationListenerEnabled(context: Context): Boolean {
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    return flat != null && flat.contains(context.packageName)
}

fun simulateDeliveryNotification(context: Context, provider: String) {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channelId = "kiranasync_sim_channel"
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            channelId,
            "Simulated Deliveries (Testing)",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        notificationManager.createNotificationChannel(channel)
    }

    val notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.stat_sys_download_done)
        .setContentTitle("$provider Order Delivered")
        .setContentText("Your $provider order has been delivered successfully! Tap to sync.")
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .build()

    notificationManager.notify(2002, notification)
}

enum class AppTab {
    Home,
    Stats,
    ShoppingList,
    Settings
}

@Composable
fun KiranaElegantBottomBar(
    activeTab: AppTab,
    onTabSelect: (AppTab) -> Unit,
    onScanClick: () -> Unit
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Safe navigation bar backing
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Home Selector
                val isHomeSelected = activeTab == AppTab.Home
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onTabSelect(AppTab.Home) }
                ) {
                    Box(
                        modifier = Modifier
                            .width(64.dp)
                            .height(32.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isHomeSelected) MaterialTheme.colorScheme.primaryContainer 
                                else Color.Transparent
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = "Home",
                            tint = if (isHomeSelected) MaterialTheme.colorScheme.onPrimaryContainer 
                                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        "Home",
                        fontSize = 11.sp,
                        fontWeight = if (isHomeSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isHomeSelected) MaterialTheme.colorScheme.onSurface 
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }

                // Stats Selector
                val isStatsSelected = activeTab == AppTab.Stats
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onTabSelect(AppTab.Stats) }
                ) {
                    Box(
                        modifier = Modifier
                            .width(64.dp)
                            .height(32.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isStatsSelected) MaterialTheme.colorScheme.primaryContainer 
                                else Color.Transparent
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.TrendingUp,
                            contentDescription = "Stats",
                            tint = if (isStatsSelected) MaterialTheme.colorScheme.onPrimaryContainer 
                                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        "Stats",
                        fontSize = 11.sp,
                        fontWeight = if (isStatsSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isStatsSelected) MaterialTheme.colorScheme.onSurface 
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }

                // Placeholder space for the central offset FAB style
                Spacer(modifier = Modifier.width(72.dp))

                // Cart / Shopping List Selector
                val isCartSelected = activeTab == AppTab.ShoppingList
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onTabSelect(AppTab.ShoppingList) }
                ) {
                    Box(
                        modifier = Modifier
                            .width(64.dp)
                            .height(32.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isCartSelected) MaterialTheme.colorScheme.primaryContainer 
                                else Color.Transparent
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShoppingCart,
                            contentDescription = "Cart",
                            tint = if (isCartSelected) MaterialTheme.colorScheme.onPrimaryContainer 
                                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        "Cart",
                        fontSize = 11.sp,
                        fontWeight = if (isCartSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isCartSelected) MaterialTheme.colorScheme.onSurface 
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }

                // Settings Selector
                val isSettingsSelected = activeTab == AppTab.Settings
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onTabSelect(AppTab.Settings) }
                ) {
                    Box(
                        modifier = Modifier
                            .width(64.dp)
                            .height(32.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isSettingsSelected) MaterialTheme.colorScheme.primaryContainer 
                                else Color.Transparent
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = if (isSettingsSelected) MaterialTheme.colorScheme.onPrimaryContainer 
                                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        "Settings",
                        fontSize = 11.sp,
                        fontWeight = if (isSettingsSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSettingsSelected) MaterialTheme.colorScheme.onSurface 
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }

        // Beautifully Floating Primary Scan Barcode Icon Button (Rounded 2XL style)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-20).dp)
        ) {
            FloatingActionButton(
                onClick = onScanClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(20.dp),
                elevation = FloatingActionButtonDefaults.elevation(8.dp),
                modifier = Modifier
                    .size(56.dp)
                    .testTag("scan_barcode_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = "Scan Barcode",
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}


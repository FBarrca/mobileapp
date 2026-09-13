package coredevices.ring.pluskey.setup

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlusKeySetupCard(
    state: PlusKeySetupState,
    startPairing: () -> Unit,
    grant: () -> Unit,
    refresh: () -> Unit,
    stop: () -> Unit,
    openRequest: Int = 0
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scrollState = rememberScrollState()
    var showingSetup by rememberSaveable { mutableStateOf(false) }
    var step by rememberSaveable { mutableIntStateOf(0) }
    var settingsError by remember { mutableStateOf("") }
    var notificationsEnabled by remember { mutableStateOf(PlusKeyPairingService.notificationsEnabled(context)) }
    fun networkAllowed() = Build.VERSION.SDK_INT < 37 || ContextCompat.checkSelfPermission(
        context, "android.permission.ACCESS_LOCAL_NETWORK") == PackageManager.PERMISSION_GRANTED
    var networkEnabled by remember { mutableStateOf(networkAllowed()) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        notificationsEnabled = PlusKeyPairingService.notificationsEnabled(context)
        networkEnabled = networkAllowed()
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsEnabled = PlusKeyPairingService.notificationsEnabled(context)
                networkEnabled = networkAllowed()
                refresh()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(step) { scrollState.scrollTo(0) }
    LaunchedEffect(state.phase) { if (state.phase == SetupPhase.Paired && step == 2) step = 3 }
    LaunchedEffect(openRequest) {
        if (openRequest > 0) { showingSetup = true; step = if (state.paired || state.granted) 3 else 2 }
    }
    val supported = Build.MANUFACTURER.lowercase(Locale.ROOT) in setOf("oneplus", "oppo", "realme")
    fun openSettings(action: String) {
        try {
            val intent = Intent(action)
            if (action == Settings.ACTION_APP_NOTIFICATION_SETTINGS) intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            context.startActivity(intent)
            settingsError = ""
        } catch (_: ActivityNotFoundException) { settingsError = "Open Android Settings manually to continue." }
    }
    fun allowSetupPermissions() {
        val missing = buildList {
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                add(Manifest.permission.POST_NOTIFICATIONS)
            if (!networkEnabled) add("android.permission.ACCESS_LOCAL_NETWORK")
        }
        if (missing.isNotEmpty()) permissions.launch(missing.toTypedArray())
        else openSettings(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
    }
    fun dismiss() { showingSetup = false; stop() }
    Row(
        Modifier.fillMaxWidth().heightIn(min=56.dp)
            .clickable(role=Role.Button, onClickLabel="Open key setup") { showingSetup = true },
        verticalAlignment=Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Key setup", style=MaterialTheme.typography.labelLarge)
            Text(when {
                !supported -> "Not supported on this phone"
                state.busy || state.searching -> "Setup in progress…"
                state.granted -> "Ready"
                else -> "Permission needed"
            }, style=MaterialTheme.typography.bodySmall,
                color=if(state.granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription=null,
            tint=MaterialTheme.colorScheme.onSurfaceVariant, modifier=Modifier.size(24.dp))
    }
    if (showingSetup) ModalBottomSheet(
        modifier=Modifier.statusBarsPadding(), onDismissRequest=::dismiss,
        sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),
        containerColor=MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max=(LocalConfiguration.current.screenHeightDp*.85f).dp).imePadding()) {
            Row(Modifier.fillMaxWidth().heightIn(min=48.dp).padding(horizontal=24.dp), verticalAlignment=Alignment.CenterVertically) {
                Text("Key setup", Modifier.weight(1f), style=MaterialTheme.typography.titleLarge)
            }
            Column(Modifier.weight(1f,fill=false).verticalScroll(scrollState).padding(24.dp),
                verticalArrangement=Arrangement.spacedBy(16.dp)) {
                when {
                    !supported -> Text("The Plus Key is available on supported OnePlus, OPPO and realme phones.")
                    state.granted -> {
                        Text("Plus Key is ready", style=MaterialTheme.typography.titleMedium)
                        Text("Return to Oneplus integration, tap Prepare Index, then Enable key. Allow Android log access if prompted.", style=MaterialTheme.typography.bodyMedium)
                        Text("Turn off Wireless debugging and restore System optimization in Developer options.", style=MaterialTheme.typography.bodyMedium)
                        OutlinedButton(onClick={ openSettings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS) }, modifier=Modifier.fillMaxWidth()) { Text("Open Developer options") }
                    }
                    Build.VERSION.SDK_INT < 30 -> Text("On-phone setup requires Android 11 or later. On Android 10, grant READ_LOGS using ADB from a computer.")
                    else -> {
                        Text("Step ${step+1} of 4", style=MaterialTheme.typography.labelMedium, color=MaterialTheme.colorScheme.primary)
                        when (step) {
                            0 -> {
                                Text("Disable system optimization", style=MaterialTheme.typography.titleMedium)
                                Text("In Developer options, disable System optimization. If the switch is named “Disable system optimization”, turn it on.", style=MaterialTheme.typography.bodyMedium)
                                Text("If Developer options is hidden, open Settings → About device → Version and tap Build number seven times. Enter your phone PIN if asked.", style=MaterialTheme.typography.bodyMedium)
                                OutlinedButton(onClick={ openSettings(Settings.ACTION_DEVICE_INFO_SETTINGS) }, modifier=Modifier.fillMaxWidth()) { Text("Open About device") }
                                OutlinedButton(onClick={ openSettings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS) }, modifier=Modifier.fillMaxWidth()) { Text("Open Developer options") }
                            }
                            1 -> {
                                Text("Enable wireless debugging", style=MaterialTheme.typography.titleMedium)
                                Text("Connect to Wi-Fi. In Developer options, turn on Wireless debugging and accept the Wi-Fi prompt.", style=MaterialTheme.typography.bodyMedium)
                                Text("Index uses temporary access to enable the Plus Key. The key listener filters for key events; the Android permission grants access to device logs.", style=MaterialTheme.typography.bodyMedium)
                                OutlinedButton(onClick={ openSettings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS) }, modifier=Modifier.fillMaxWidth()) { Text("Open Developer options") }
                            }
                            2 -> {
                                Text("Pair with this phone", style=MaterialTheme.typography.titleMedium)
                                if (!notificationsEnabled || !networkEnabled) {
                                    Text("Allow notifications so you can enter the pairing code while Android Settings stays open.", style=MaterialTheme.typography.bodyMedium)
                                    if (!networkEnabled) Text("Allow local network access so Index can find this phone.", style=MaterialTheme.typography.bodyMedium)
                                    TextButton(onClick={ openSettings(Settings.ACTION_APP_NOTIFICATION_SETTINGS) }) { Text("Notification settings") }
                                } else {
                                    Text("Open the Wireless debugging row, then tap Pair device with pairing code.", style=MaterialTheme.typography.bodyMedium)
                                    Text("Keep the dialog open. Pull down your notifications, tap Enter pairing code on Index, and send the six-digit code.", style=MaterialTheme.typography.bodyMedium)
                                    if (state.searching) {
                                        SetupStatus(
                                            if (state.phase == SetupPhase.PairingCode) "Pairing service found" else "Searching for pairing service",
                                            state.message, progress=state.phase == SetupPhase.SearchingPairing
                                        )
                                        TextButton(onClick=stop) { Text("Stop searching") }
                                    }
                                }
                                if (state.paired) Text("This phone has been paired before. You can continue if Index is still listed in its paired devices.", style=MaterialTheme.typography.bodyMedium)
                                TextButton(onClick={ stop(); step=3 }, enabled=!state.busy) { Text("Already paired? Continue") }
                            }
                            3 -> {
                                Text("Enable the Plus Key", style=MaterialTheme.typography.titleMedium)
                                Text("Keep Wireless debugging on. Index will find this phone and enable the key automatically.", style=MaterialTheme.typography.bodyMedium)
                                Text("After setup, turn off Wireless debugging and restore System optimization.", style=MaterialTheme.typography.bodyMedium)
                                if (state.phase == SetupPhase.Error) OutlinedButton(
                                    onClick={ openSettings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS) }, modifier=Modifier.fillMaxWidth()
                                ) { Text("Open Developer options") }
                            }
                        }
                        if (state.busy) SetupStatus(
                            when(state.phase) {
                                SetupPhase.Pairing -> "Pairing with this phone"
                                SetupPhase.SearchingConnection -> "Finding this phone"
                                else -> "Enabling the Plus Key"
                            }, state.message, progress=true
                        )
                        if (state.phase == SetupPhase.Error) SetupStatus("Setup needs attention", state.message, error=true)
                    }
                }
                if (settingsError.isNotEmpty()) Text(settingsError, color=MaterialTheme.colorScheme.error)
            }
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=12.dp),
                horizontalArrangement=Arrangement.spacedBy(16.dp), verticalAlignment=Alignment.CenterVertically) {
                if (step > 0 && !state.granted && supported && Build.VERSION.SDK_INT >= 30) TextButton(
                    onClick={ stop(); step-- }, enabled=!state.busy, modifier=Modifier.heightIn(min=48.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription=null, modifier=Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp)); Text("Back")
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick={
                        when {
                            state.granted || !supported || Build.VERSION.SDK_INT < 30 -> dismiss()
                            step < 2 -> step++
                            step >= 2 && (!notificationsEnabled || !networkEnabled) -> allowSetupPermissions()
                            step == 2 -> { startPairing(); openSettings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS) }
                            else -> grant()
                        }
                    }, enabled=!state.busy, modifier=Modifier.heightIn(min=48.dp)
                ) {
                    Text(when {
                        state.granted || !supported || Build.VERSION.SDK_INT < 30 -> "Done"
                        step < 2 -> "Next"
                        step >= 2 && !notificationsEnabled -> "Allow notifications"
                        step >= 2 && !networkEnabled -> "Allow access"
                        step == 2 && state.searching -> "Open settings"
                        step == 2 -> "Start pairing"
                        else -> "Enable Plus Key"
                    })
                    if (step < 2 && !state.granted && supported && Build.VERSION.SDK_INT >= 30) {
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription=null, modifier=Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupStatus(title: String, message: String, progress: Boolean = false, error: Boolean = false) {
    Surface(
        color=if(error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape=MaterialTheme.shapes.medium, modifier=Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(title, style=MaterialTheme.typography.labelLarge)
            if (message.isNotEmpty()) Text(message, style=MaterialTheme.typography.bodySmall)
            if (progress) LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
        }
    }
}

@Composable
internal fun WirelessKeySetup() {
    val context = LocalContext.current
    val model = remember { PlusKeySetupModel(context.applicationContext as android.app.Application) }
    val state by model.state.collectAsState()
    Box(Modifier.padding(horizontal = 16.dp)) {
        PlusKeySetupCard(state, model::startPairing, model::grant, model::refresh, model::stop)
    }
}

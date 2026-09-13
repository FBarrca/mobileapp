package coredevices.ring.pluskey

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import android.widget.*
import coredevices.util.CoreConfigHolder
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Visible entry point for permission consent and arming background microphone access. */
class IndexPlusKeyActivity : Activity(), KoinComponent {
    private val config: CoreConfigHolder by inject()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var stateLabel: TextView
    private lateinit var setupLabel: TextView
    private lateinit var enable: Button
    private val refresh = object : Runnable {
        override fun run() { render(); handler.postDelayed(this, 300) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Index Plus Key"
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (24 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        fun text(value: String, size: Float = 17f) = TextView(this).apply {
            text = value; textSize = size; setPadding(0, 12, 0, 12); layout.addView(this)
        }
        text("Index from your Plus Key", 26f)
        text("Hold the OnePlus key to record with this phone. Release to run your Index Hold & Talk action. Quick presses do nothing. Maximum recording: two minutes.")
        text("Disable DeskLink's PC microphone before enabling Index mode. The phone's assigned system key action still runs; choose an action that does not use the microphone.")
        stateLabel = text("")
        setupLabel = text("")
        Button(this).apply {
            text = "Prepare on-device Index"
            setOnClickListener { IndexPlusKeySetup.prepareLocal() }
            layout.addView(this)
        }
        text("Downloads the speech model and uses Index's on-device agent. No online account is needed for local processing. Use Wi-Fi for model setup.", 14f)
        enable = Button(this).apply {
            text = "Enable Index key"
            setOnClickListener {
                if (!IndexPlusKeySetup.state.value.ready || IndexPlusKeySetup.state.value.busy) {
                    Toast.makeText(this@IndexPlusKeyActivity, "Prepare the on-device models first", Toast.LENGTH_LONG).show()
                } else if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
                } else if (checkSelfPermission(Manifest.permission.READ_LOGS) != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this@IndexPlusKeyActivity, "Complete key permission setup first", Toast.LENGTH_LONG).show()
                } else {
                    config.update(config.config.value.copy(enableIndex = true))
                    runCatching { startForegroundService(Intent(this@IndexPlusKeyActivity, IndexPlusKeyService::class.java)) }
                        .onFailure { Toast.makeText(this@IndexPlusKeyActivity, it.message, Toast.LENGTH_LONG).show() }
                }
            }
            layout.addView(this)
        }
        Button(this).apply {
            text = "Disable Index key"
            setOnClickListener { stopService(Intent(this@IndexPlusKeyActivity, IndexPlusKeyService::class.java)); render() }
            layout.addView(this)
        }
        Button(this).apply {
            text = "Open Index app"
            setOnClickListener {
                config.update(config.config.value.copy(enableIndex = true))
                startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("pebble://navbar/index")).setClassName(packageName, "coredevices.coreapp.MainActivity"))
            }
            layout.addView(this)
        }
        text("First-time key setup: grant log access using ADB, then enable while this screen is visible and the key is released. Approve Android's log-access dialog if shown.")
        text("adb shell pm grant --user 0 $packageName android.permission.READ_LOGS", 14f).setTextIsSelectable(true)
        text("Index requires its own configured speech and language models, or a signed-in online account. Enablement keeps a notification visible; Disable cancels an unfinished recording. Reopen this screen to re-enable after Android stops the app.")
        setContentView(ScrollView(this).apply { addView(layout) })
    }

    private fun render() {
        val state = IndexPlusKeyService.status.value
        val logs = checkSelfPermission(Manifest.permission.READ_LOGS) == PackageManager.PERMISSION_GRANTED
        setupLabel.text = IndexPlusKeySetup.state.value.message
        stateLabel.text = "${state.message}\nRecordings sent to Index: ${state.queued}" + if (logs) "" else "\nKey log permission is missing"
        enable.isEnabled = !state.armed && IndexPlusKeySetup.state.value.ready && !IndexPlusKeySetup.state.value.busy
    }
    override fun onResume() { super.onResume(); handler.post(refresh) }
    override fun onPause() { handler.removeCallbacks(refresh); super.onPause() }
}

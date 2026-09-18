package com.hybridremote.app.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.hybridremote.app.adb.AdbClient
import com.hybridremote.app.adb.AdbCrypto
import com.hybridremote.app.databinding.ActivityMainBinding
import com.hybridremote.app.ir.IrController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.KeyPair
import kotlin.math.abs

enum class Mode { WIFI, IR }

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var currentMode = Mode.WIFI
    private var adbClient: AdbClient? = null
    private lateinit var irController: IrController
    private lateinit var keyPair: KeyPair
    private lateinit var gestureDetector: GestureDetector
    private val scope = CoroutineScope(Dispatchers.Main)

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        irController = IrController(this)
        keyPair = AdbCrypto.generateKeyPair()

        setMode(Mode.WIFI)

        binding.btnModeWifi.setOnClickListener { setMode(Mode.WIFI) }
        binding.btnModeIr.setOnClickListener { setMode(Mode.IR) }

        binding.btnConnect.setOnClickListener {
            val ip = binding.etTvIp.text.toString().trim()
            if (ip.isNotEmpty()) connectAdb(ip)
        }

        // Panel táctil (modo Wi-Fi): deslizar = mover, tocar = OK
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                sendKey(AdbClient.KeyEvent.OK, irController.hasIrHardware())
                return true
            }
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                if (abs(dx) > abs(dy)) {
                    sendKey(if (dx > 0) AdbClient.KeyEvent.RIGHT else AdbClient.KeyEvent.LEFT, irController.hasIrHardware())
                } else {
                    sendKey(if (dy > 0) AdbClient.KeyEvent.DOWN else AdbClient.KeyEvent.UP, irController.hasIrHardware())
                }
                return true
            }
        })
        binding.touchpad.setOnTouchListener { v, event ->
            val handled = gestureDetector.onTouchEvent(event)
            if (event.action == MotionEvent.ACTION_UP) v.performClick()
            handled
        }

        // D-pad (modo IR)
        binding.btnUp.setOnClickListener { sendKey(AdbClient.KeyEvent.UP, irController.hasIrHardware()) }
        binding.btnDown.setOnClickListener { sendKey(AdbClient.KeyEvent.DOWN, irController.hasIrHardware()) }
        binding.btnLeft.setOnClickListener { sendKey(AdbClient.KeyEvent.LEFT, irController.hasIrHardware()) }
        binding.btnRight.setOnClickListener { sendKey(AdbClient.KeyEvent.RIGHT, irController.hasIrHardware()) }
        binding.btnOk.setOnClickListener { sendKey(AdbClient.KeyEvent.OK, irController.hasIrHardware()) }

        // Home / atrás (Wi-Fi)
        binding.btnHome.setOnClickListener { sendKey(AdbClient.KeyEvent.HOME, irController.hasIrHardware()) }
        binding.btnBack.setOnClickListener { sendKey(AdbClient.KeyEvent.BACK, irController.hasIrHardware()) }

        // Power
        binding.btnPowerWifi.setOnClickListener { sendPower() }
        binding.btnPowerIr.setOnClickListener { sendPower() }

        // Volumen / Canal / Mute — Wi-Fi
        binding.btnVolUpWifi.setOnClickListener { sendKey(AdbClient.KeyEvent.VOLUME_UP, irController.hasIrHardware()) }
        binding.btnVolDownWifi.setOnClickListener { sendKey(AdbClient.KeyEvent.VOLUME_DOWN, irController.hasIrHardware()) }
        binding.btnChUpWifi.setOnClickListener { sendKey(AdbClient.KeyEvent.CHANNEL_UP, irController.hasIrHardware()) }
        binding.btnChDownWifi.setOnClickListener { sendKey(AdbClient.KeyEvent.CHANNEL_DOWN, irController.hasIrHardware()) }
        binding.btnMuteWifi.setOnClickListener { sendKey(AdbClient.KeyEvent.MUTE, irController.hasIrHardware()) }

        // Volumen / Canal / Mute — IR
        binding.btnVolUpIr.setOnClickListener { sendKey(AdbClient.KeyEvent.VOLUME_UP, irController.hasIrHardware()) }
        binding.btnVolDownIr.setOnClickListener { sendKey(AdbClient.KeyEvent.VOLUME_DOWN, irController.hasIrHardware()) }
        binding.btnChUpIr.setOnClickListener { sendKey(AdbClient.KeyEvent.CHANNEL_UP, irController.hasIrHardware()) }
        binding.btnChDownIr.setOnClickListener { sendKey(AdbClient.KeyEvent.CHANNEL_DOWN, irController.hasIrHardware()) }
        binding.btnMuteIr.setOnClickListener { sendKey(AdbClient.KeyEvent.MUTE, irController.hasIrHardware()) }

        // Botones de colores (IR)
        binding.btnColorRed.setOnClickListener { sendKey(AdbClient.KeyEvent.PROG_RED, irController.hasIrHardware()) }
        binding.btnColorGreen.setOnClickListener { sendKey(AdbClient.KeyEvent.PROG_GREEN, irController.hasIrHardware()) }
        binding.btnColorYellow.setOnClickListener { sendKey(AdbClient.KeyEvent.PROG_YELLOW, irController.hasIrHardware()) }
        binding.btnColorBlue.setOnClickListener { sendKey(AdbClient.KeyEvent.PROG_BLUE, irController.hasIrHardware()) }

        // Teclado numérico (IR) — KEYCODE_0=7 ... KEYCODE_9=16
        val numberButtons = listOf(
            binding.btnNum0 to 0, binding.btnNum1 to 1, binding.btnNum2 to 2,
            binding.btnNum3 to 3, binding.btnNum4 to 4, binding.btnNum5 to 5,
            binding.btnNum6 to 6, binding.btnNum7 to 7, binding.btnNum8 to 8,
            binding.btnNum9 to 9
        )
        numberButtons.forEach { (button, digit) ->
            button.setOnClickListener { sendKey(digit + 7, irController.hasIrHardware()) }
        }

        // Botones aún sin función asignada
        val placeholderButtons = listOf(
            binding.btnSettings, binding.btnShortcut1, binding.btnShortcut2,
            binding.btnShortcut3, binding.btnShortcut4, binding.btnKeyboard,
            binding.btnNumpad, binding.btnMic, binding.btnIndex,
            binding.btnChList, binding.btnMenu, binding.btnInput
        )
        placeholderButtons.forEach { view ->
            view.setOnClickListener { Toast.makeText(this, "Función no configurada todavía", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun setMode(mode: Mode) {
        currentMode = mode
        val wifiActive = mode == Mode.WIFI
        binding.btnModeWifi.setBackgroundColor(if (wifiActive) Color.parseColor("#4285F4") else Color.parseColor("#22242C"))
        binding.btnModeIr.setBackgroundColor(if (!wifiActive) Color.parseColor("#4285F4") else Color.parseColor("#22242C"))
        binding.tvModeTitle.text = if (wifiActive) "Modo: Android TV (Wi-Fi)" else "Modo: Infrarrojo (IR)"
        binding.wifiPanel.visibility = if (wifiActive) View.VISIBLE else View.GONE
        binding.irPanel.visibility = if (wifiActive) View.GONE else View.VISIBLE

        if (mode == Mode.IR && !irController.hasIrHardware()) {
            Toast.makeText(this, "Tu teléfono no tiene emisor de infrarrojos físico. El modo IR no funcionará en este dispositivo.", Toast.LENGTH_LONG).show()
        }
    }

    private fun connectAdb(ip: String) {
        binding.tvStatus.text = "● Conectando..."
        scope.launch {
            try {
                val client = AdbClient(ip)
                withContext(Dispatchers.IO) { client.connect(keyPair) }
                adbClient = client
                binding.tvStatus.text = "● Conectado"
                binding.tvStatus.setTextColor(Color.parseColor("#4CAF50"))
                Toast.makeText(this@MainActivity, "Revisa tu TV: acepta la autorización si aparece.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                binding.tvStatus.text = "● Error de conexión"
                binding.tvStatus.setTextColor(Color.parseColor("#FF5555"))
                Toast.makeText(this@MainActivity, "No se pudo conectar: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun sendKey(keyCode: Int, hasIr: Boolean) {
        if (currentMode == Mode.WIFI) {
            val client = adbClient
            if (client == null) {
                Toast.makeText(this, "Primero conéctate a tu TV/TV Box", Toast.LENGTH_SHORT).show()
                return
            }
            scope.launch(Dispatchers.IO) { client.shell("input keyevent $keyCode") }
        } else {
            if (!hasIr) return
            irController.transmit(IrController.SamsungCodes.FREQUENCY, IrController.SamsungCodes.POWER)
        }
    }

    private fun sendPower() {
        if (currentMode == Mode.WIFI) {
            sendKey(AdbClient.KeyEvent.POWER, false)
        } else {
            if (!irController.hasIrHardware()) {
                Toast.makeText(this, "Sin hardware IR en este teléfono", Toast.LENGTH_SHORT).show()
                return
            }
            irController.transmit(IrController.SamsungCodes.FREQUENCY, IrController.SamsungCodes.POWER)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        adbClient?.close()
    }
}

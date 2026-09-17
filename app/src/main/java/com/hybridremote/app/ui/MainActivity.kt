package com.hybridremote.app.ui

import android.graphics.Color
import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.hybridremote.app.R
import com.hybridremote.app.adb.AdbClient
import com.hybridremote.app.adb.AdbCrypto
import com.hybridremote.app.databinding.ActivityMainBinding
import com.hybridremote.app.ir.IrController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.KeyPair

enum class Mode { WIFI, IR }

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var currentMode = Mode.WIFI
    private var adbClient: AdbClient? = null
    private lateinit var irController: IrController
    private lateinit var keyPair: KeyPair
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        irController = IrController(this)
        keyPair = AdbCrypto.generateKeyPair() // en producción: guardar y reutilizar la clave

        setMode(Mode.WIFI)

        binding.btnModeWifi.setOnClickListener { setMode(Mode.WIFI) }
        binding.btnModeIr.setOnClickListener { setMode(Mode.IR) }

        binding.btnConnect.setOnClickListener {
            val ip = binding.etTvIp.text.toString().trim()
            if (ip.isNotEmpty()) connectAdb(ip)
        }

        binding.btnUp.setOnClickListener { sendKey(AdbClient.KeyEvent.UP, irController.hasIrHardware()) }
        binding.btnDown.setOnClickListener { sendKey(AdbClient.KeyEvent.DOWN, irController.hasIrHardware()) }
        binding.btnLeft.setOnClickListener { sendKey(AdbClient.KeyEvent.LEFT, irController.hasIrHardware()) }
        binding.btnRight.setOnClickListener { sendKey(AdbClient.KeyEvent.RIGHT, irController.hasIrHardware()) }
        binding.btnOk.setOnClickListener { sendKey(AdbClient.KeyEvent.OK, irController.hasIrHardware()) }
        binding.btnHome.setOnClickListener { sendKey(AdbClient.KeyEvent.HOME, irController.hasIrHardware()) }
        binding.btnBack.setOnClickListener { sendKey(AdbClient.KeyEvent.BACK, irController.hasIrHardware()) }
        binding.btnPower.setOnClickListener { sendPower() }
    }

    private fun setMode(mode: Mode) {
        currentMode = mode
        val wifiActive = mode == Mode.WIFI
        binding.btnModeWifi.setBackgroundColor(if (wifiActive) Color.parseColor("#4285F4") else Color.parseColor("#22242C"))
        binding.btnModeIr.setBackgroundColor(if (!wifiActive) Color.parseColor("#4285F4") else Color.parseColor("#22242C"))
        binding.tvModeTitle.text = if (wifiActive) "Modo: Android TV (Wi-Fi)" else "Modo: Infrarrojo (IR)"
        binding.etTvIp.visibility = if (wifiActive) android.view.View.VISIBLE else android.view.View.GONE
        binding.btnConnect.visibility = if (wifiActive) android.view.View.VISIBLE else android.view.View.GONE

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
            // Modo IR: aquí se dispara el código IR correspondiente (según marca elegida)
            if (!hasIr) return
            // Ejemplo con tabla Samsung; añade selector de marca según lo necesites
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

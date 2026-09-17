package com.hybridremote.app.ir

import android.content.Context
import android.hardware.ConsumerIrManager

/**
 * Controla el emisor de infrarrojos físico del teléfono (si existe).
 * La mayoría de los Android modernos NO tienen este chip; solo algunos
 * Xiaomi, Huawei y Samsung antiguos lo incluyen. Por eso siempre hay
 * que comprobar hasIrEmitter() antes de intentar transmitir.
 */
class IrController(context: Context) {

    private val irManager: ConsumerIrManager? =
        context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager

    /** true solo si el hardware físico de IR existe en este teléfono */
    fun hasIrHardware(): Boolean =
        irManager?.hasIrEmitter() == true

    /**
     * Envía un código IR en formato NEC estándar.
     * frequency: frecuencia portadora en Hz (normalmente 38000)
     * pattern: array de duraciones en microsegundos, alternando ON/OFF
     */
    fun transmit(frequency: Int, pattern: IntArray) {
        if (!hasIrHardware()) return
        irManager?.transmit(frequency, pattern)
    }

    /** Códigos de ejemplo en formato NEC. Amplía esta tabla con tu propia
     * base de datos de códigos IR (por marca) según lo que necesites,
     * por ejemplo desde bases públicas como https://github.com/probonopd/irdb */
    object SamsungCodes {
        const val FREQUENCY = 38000
        // Código NEC público, encendido/apagado genérico Samsung
        val POWER = intArrayOf(
            4500, 4500, 560, 560, 560, 1690, 560, 560, 560, 1690,
            560, 560, 560, 560, 560, 1690, 560, 1690, 560, 560,
            560, 560, 560, 1690, 560, 560, 560, 560, 560, 1690,
            560, 1690, 560, 1690, 560, 1690, 560, 1690, 560, 39000
        )
    }
}

package com.flxrs.earableassistant.data

import kotlin.math.absoluteValue

data class GyroData(val x: Double = 0.0, val y: Double = 0.0, val z: Double = 0.0) {

    private val isNod: Boolean = z.absoluteValue > THRESHOLD_HIGH_NOD && x.absoluteValue < THRESHOLD_LOW && y.absoluteValue < THRESHOLD_LOW
    private val isShake: Boolean = (x.absoluteValue > THRESHOLD_HIGH_SHAKE || y.absoluteValue > THRESHOLD_HIGH_SHAKE) && z.absoluteValue < THRESHOLD_LOW
    val event = when {
        isNod -> MotionEvent.Nod
        isShake -> MotionEvent.Shake
        else -> MotionEvent.Unknown
    }

    companion object {
        fun fromIMUBytes(bytes: ByteArray): GyroData {
            val x = bytes.combineBytes(4, 5) / 32.8
            val y = bytes.combineBytes(6, 7) / 32.8
            val z = bytes.combineBytes(8, 9) / 32.8

            return GyroData(x, y, z)
        }

        private const val THRESHOLD_HIGH_NOD = 100
        private const val THRESHOLD_HIGH_SHAKE = 150
        private const val THRESHOLD_LOW = 50
    }
}
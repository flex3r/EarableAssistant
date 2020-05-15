package com.flxrs.earableassistant.data

data class AccelerationData(val x: Double, val y: Double, val z: Double) {
    companion object {
        fun fromIMUBytes(bytes: ByteArray, offset: Triple<Int, Int, Int>): AccelerationData {
            val x = (bytes.combineBytes(10, 11) + offset.first) / 8192
            val y = (bytes.combineBytes(12, 13) + offset.second) / 8192
            val z = (bytes.combineBytes(14, 15) + offset.third) / 8192

            return AccelerationData(x, y, z)
        }
    }
}
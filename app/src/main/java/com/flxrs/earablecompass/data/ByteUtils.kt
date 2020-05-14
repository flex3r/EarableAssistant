package com.flxrs.earablecompass.data

fun ByteArray.combineBytes(msbIndex: Int, lsbIndex: Int): Double = (get(msbIndex).toInt() shl 8).toDouble() + get(lsbIndex)
package com.flxrs.earableassistant.data

sealed class MotionEvent {
    open val msg = ""
    open val threshold = 0

    object Unknown : MotionEvent()
    object Nod : MotionEvent() {
        override val msg = "Nod"
        override val threshold = 8
    }

    object Shake : MotionEvent() {
        override val msg = "Shake"
        override val threshold = 15
    }
}
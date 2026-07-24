package com.potato.player.engine

sealed class MpvEvent {
    data class PropertyLong(val name: String, val value: Long) : MpvEvent()
    data class PropertyBool(val name: String, val value: Boolean) : MpvEvent()
    data class PropertyDouble(val name: String, val value: Double) : MpvEvent()
    data class PropertyString(val name: String, val value: String) : MpvEvent()
    data class Id(val id: Int) : MpvEvent()
}

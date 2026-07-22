package com.potato.player.engine

enum class TrackType { AUDIO, SUBTITLE }

data class TrackInfo(
    val id: Int,            // MPV track id (used for aid/sid)
    val type: TrackType,    // audio or subtitle
    val title: String?,     // track-list/N/title (nullable)
    val lang: String?,      // track-list/N/lang (nullable)
    val isExternal: Boolean // track-list/N/external
) {
    fun displayLabel(): String = when {
        !title.isNullOrBlank() -> title
        !lang.isNullOrBlank()  -> lang
        else                   -> "Track $id"
    }
}

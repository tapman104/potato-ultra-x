package com.potato.player.engine

data class TrackInfo(
    val id: Int,            // MPV track id (used for aid/sid)
    val type: String,       // "audio" or "sub"
    val title: String?,     // track-list/N/title (nullable)
    val lang: String?,      // track-list/N/lang (nullable)
    val isExternal: Boolean // track-list/N/external
) {
    fun displayLabel(): String {
        return when {
            !title.isNullOrBlank() -> title
            !lang.isNullOrBlank() -> lang
            else -> "Track $id"
        }
    }
}

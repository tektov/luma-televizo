package com.lumatv.player

data class Channel(
    val number: Int,
    val name: String,
    val category: String,
    val url: String,
    val tvgId: String = "",
    val catchupDays: Int = 0,
    val catchupSource: String = "",
    var isFavorite: Boolean = false
)

data class EpgProgram(
    val channelId: String,
    val title: String,
    val startMs: Long,
    val endMs: Long
) {
    fun isLive(nowMs: Long): Boolean = nowMs in startMs until endMs
    fun isPast(nowMs: Long): Boolean = endMs <= nowMs
    fun durationMinutes(): Long = ((endMs - startMs) / 60000L).coerceAtLeast(1L)
}

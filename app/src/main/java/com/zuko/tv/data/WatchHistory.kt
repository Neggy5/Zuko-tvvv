package com.zuko.tv.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class WatchItem(
    val id: Int,
    val title: String = "",
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val releaseDate: String? = null,
    val voteAverage: Double = 0.0,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val updatedAt: Long = 0L,
) {
    val progress: Float
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    fun toSummary() = MovieSummary(
        id = id,
        title = title,
        posterPath = posterPath,
        backdropPath = backdropPath,
        releaseDate = releaseDate,
        voteAverage = voteAverage,
    )
}

/** Local "Continue watching" store (SharedPreferences, newest first). */
class WatchHistory(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("zuko_watch", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<WatchItem>> = _items.asStateFlow()

    private fun load(): List<WatchItem> = runCatching {
        json.decodeFromString<List<WatchItem>>(prefs.getString(KEY, null) ?: "[]")
    }.getOrDefault(emptyList())

    fun get(id: Int): WatchItem? = _items.value.firstOrNull { it.id == id }

    @Synchronized
    fun record(item: WatchItem) {
        if (item.positionMs < MIN_MS) return
        val finished = item.durationMs > 0L && item.positionMs >= item.durationMs * FINISHED_AT
        val rest = _items.value.filterNot { it.id == item.id }
        persist(if (finished) rest else (listOf(item) + rest).take(MAX_ITEMS))
    }

    @Synchronized
    fun clear() { persist(emptyList()) }

    @Synchronized
    fun remove(id: Int) {
        persist(_items.value.filterNot { it.id == id })
    }

    private fun persist(next: List<WatchItem>) {
        _items.value = next
        runCatching { prefs.edit().putString(KEY, json.encodeToString(next)).apply() }
    }

    private companion object {
        const val KEY = "items"
        const val MIN_MS = 15_000L
        const val FINISHED_AT = 0.95
        const val MAX_ITEMS = 20
    }
}

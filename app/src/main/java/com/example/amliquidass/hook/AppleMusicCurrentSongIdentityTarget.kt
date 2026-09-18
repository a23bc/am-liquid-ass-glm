package com.example.amliquidass.hook

/**
 * Lightweight current-song identity cache.
 *
 * The upstream file bundled `CurrentSongIdentityCache` with the
 * `AppleMusicCurrentSongIdentityTarget` install target (the latter was
 * tied to custom-lyrics replacement). Only the cache type is needed by
 * HookEntry, so the target class has been dropped.
 */
data class TargetCurrentSong(
    val appleMusicId: String? = null,
    val title: String? = null,
    val artist: String? = null,
)

class CurrentSongIdentityCache {
    @Volatile
    private var current: TargetCurrentSong = TargetCurrentSong()

    fun update(song: TargetCurrentSong) {
        current = song
    }

    fun current(): TargetCurrentSong = current
}

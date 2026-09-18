package com.example.amliquidass.hook

import com.example.amliquidass.CurrentSongDetails

/**
 * Lightweight current-song identity cache.
 *
 * The upstream file bundled `CurrentSongIdentityCache` with the
 * `AppleMusicCurrentSongIdentityTarget` install target (the latter was
 * tied to custom-lyrics replacement). Only the cache type is needed by
 * HookEntry, so the target class has been dropped.
 *
 * `TargetCurrentSong.details` is preserved so HookEntry's existing
 * `currentSong = { currentSong.current()?.details }` call site stays
 * source-compatible. Both classes are `internal` because
 * `CurrentSongDetails` itself is internal — a public class would
 * leak an internal type through its members.
 */
internal data class TargetCurrentSong(
    val details: CurrentSongDetails? = null,
)

internal class CurrentSongIdentityCache {
    @Volatile
    private var current: TargetCurrentSong = TargetCurrentSong()

    fun update(song: TargetCurrentSong) {
        current = song
    }

    fun current(): TargetCurrentSong = current
}

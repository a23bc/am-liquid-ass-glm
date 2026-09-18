package com.example.amliquidass.hook

/**
 * Target adaptation shim.
 *
 * The upstream AM-plus-plus TargetAdaptation wired every Apple Music hook
 * target (dual-pane, editorial video, lyric blur, CJK karaoke, typeface,
 * custom lyrics, current-song identity, catalog language, HLE metadata).
 * AMLiquidAss ships only the liquid-glass feature, so this shim simply
 * records the target identity and exposes a no-op install surface.
 *
 * FeatureInstallation constructs TargetAdaptation directly; no
 * `appleMusic(...)` companion factory is needed any more.
 */
data class TargetAdaptation(
    val identity: String,
) {
    companion object {
        fun appleMusic(): TargetAdaptation = TargetAdaptation(identity = "AppleMusic")
    }
}

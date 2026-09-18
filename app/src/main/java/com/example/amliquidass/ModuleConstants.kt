package com.example.amliquidass

/**
 * Minimal module constants for the AMLiquidAss Xposed module.
 *
 * Only the liquid-glass feature is shipped; all lyric / font / catalog /
 * dual-pane / DPI / title-correction / editorial-video feature keys that
 * the upstream AM-plus-plus module exposed have been removed.
 */
object ModuleConstants {
    const val MODULE_PACKAGE = "com.example.amliquidass"
    const val TARGET_PACKAGE = "com.apple.android.music"
    const val REMOTE_PREFERENCES_GROUP = "settings"
    const val CONFIG_SCHEMA_VERSION = 13

    const val FEATURE_PHONE_LIQUID_GLASS = "phone_liquid_glass"
}

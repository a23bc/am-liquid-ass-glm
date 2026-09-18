package com.example.amliquidass.model

/**
 * Slim module data models for the AMLiquidAss Xposed module.
 *
 * Lyric / font / custom-lyrics / catalog-language / title-correction models
 * have been removed; only the liquid-glass toggle + feature health surface
 * remains.
 */
data class ModuleSettings(
    val phoneLiquidGlassEnabled: Boolean = false,
    val schemaVersion: Int = 0,
)

enum class FeatureState {
    ACTIVE,
    DISABLED,
    UNSUPPORTED,
    DEGRADED,
    FAILED,
}

data class FeatureHealth(
    val feature: String,
    val state: FeatureState,
    val message: String,
    val targetVersion: String,
)

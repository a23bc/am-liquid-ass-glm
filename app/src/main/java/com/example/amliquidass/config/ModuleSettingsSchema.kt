package com.example.amliquidass.config

import com.example.amliquidass.ModuleConstants
import com.example.amliquidass.model.ModuleSettings

/**
 * Trimmed settings schema for AMLiquidAss.
 *
 * Only the liquid-glass toggle and schema-version keys survive; all
 * lyric / font / catalog / dual-pane / DPI / title-correction keys from
 * upstream AM-plus-plus have been removed.
 */
internal object ModuleSettingsSchema {

    fun decode(values: Map<String, *>): ModuleSettings = ModuleSettings(
        phoneLiquidGlassEnabled = values.boolean(KEY_PHONE_LIQUID_GLASS, default = true),
        schemaVersion = values.number(KEY_SCHEMA_VERSION) ?: ModuleConstants.CONFIG_SCHEMA_VERSION,
    )

    fun encodeOrdinarySettings(settings: ModuleSettings): Map<String, Any> = linkedMapOf(
        KEY_PHONE_LIQUID_GLASS to settings.phoneLiquidGlassEnabled,
        KEY_SCHEMA_VERSION to ModuleConstants.CONFIG_SCHEMA_VERSION,
    )

    /** Returns true when the map carries at least one AMLiquidAss setting. */
    fun hasMigratableValues(values: Map<String, *>): Boolean =
        values.keys.any { it in settingKeys }

    private val settingKeys = setOf(KEY_PHONE_LIQUID_GLASS, KEY_SCHEMA_VERSION)

    private fun Map<String, *>.boolean(key: String, default: Boolean): Boolean =
        this[key] as? Boolean ?: default

    private fun Map<String, *>.number(key: String): Int? = (this[key] as? Number)?.toInt()

    private const val KEY_PHONE_LIQUID_GLASS = "phone_liquid_glass_enabled"
    private const val KEY_SCHEMA_VERSION = "schema_version"
}

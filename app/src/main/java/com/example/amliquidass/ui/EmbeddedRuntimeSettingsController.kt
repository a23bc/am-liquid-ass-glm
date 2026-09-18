package com.example.amliquidass.ui

import android.content.Context
import com.example.amliquidass.CurrentSongDetails
import com.example.amliquidass.config.EmbeddedConfigurationSession
import com.example.amliquidass.model.ModuleSettings

/**
 * Minimal EmbeddedRuntimeSettingsController — the upstream version drove
 * the custom-lyrics / font / online-import / SAF / backup-restore UI
 * surface. AMLiquidAss ships only the liquid-glass toggle, so the
 * controller reads/writes ordinary settings and reports the current
 * song details — that's all the trimmed EmbeddedSettingsHost needs.
 */
internal class EmbeddedRuntimeSettingsController(
    @Suppress("unused") private val context: Context,
    private val session: EmbeddedConfigurationSession,
    private val currentSong: () -> CurrentSongDetails?,
) : EmbeddedSettingsController {

    override fun currentSettings(): ModuleSettings = session.settings()

    override fun saveOrdinarySettings(settings: ModuleSettings): Boolean =
        session.saveSettings(settings)

    override fun currentSongDetails(): CurrentSongDetails? = currentSong()
}

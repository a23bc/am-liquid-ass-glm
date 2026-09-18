package com.example.amliquidass.ui

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.view.View
import com.example.amliquidass.CurrentSongDetails
import com.example.amliquidass.model.ModuleSettings

/**
 * Minimal EmbeddedSettingsHost — the upstream 4500-line host shipped an
 * in-app custom-lyrics / font / online-import / SAF / backup-restore
 * editor surface. AMLiquidAss ships only the liquid-glass feature, so the
 * host is reduced to a no-op stub that the libxposed HookEntry still
 * constructs and forwards lifecycle callbacks into.
 *
 * The activity-result bridge, settings-fragment hook, and lifecycle
 * notifications remain as no-ops. When you re-introduce a settings UI,
 * replace this stub with a real host implementation.
 */
internal interface EmbeddedSettingsController {
    fun currentSettings(): ModuleSettings
    fun saveOrdinarySettings(settings: ModuleSettings): Boolean
    fun currentSongDetails(): CurrentSongDetails?
}

internal class EmbeddedSettingsHost private constructor(
    private val controller: EmbeddedSettingsController,
) {
    fun onActivityResult(
        @Suppress("UNUSED_PARAMETER") activity: Activity,
        @Suppress("UNUSED_PARAMETER") requestCode: Int,
        @Suppress("UNUSED_PARAMETER") resultCode: Int,
        @Suppress("UNUSED_PARAMETER") data: Intent?,
    ) {
        // No-op: liquid-glass feature does not observe activity results.
    }

    fun onSettingsPreferencesReady(
        @Suppress("UNUSED_PARAMETER") fragment: Any,
        @Suppress("UNUSED_PARAMETER") activity: Activity,
    ) {
        // No-op: liquid-glass feature has no preferences fragment to bind.
    }

    fun onSettingsFragmentResumed(
        @Suppress("UNUSED_PARAMETER") fragment: Any,
        @Suppress("UNUSED_PARAMETER") activity: Activity,
    ) {
        // No-op.
    }

    fun onSettingsFragmentViewCreated(
        @Suppress("UNUSED_PARAMETER") fragment: Any,
        @Suppress("UNUSED_PARAMETER") activity: Activity,
        @Suppress("UNUSED_PARAMETER") view: View?,
    ) {
        // No-op.
    }

    companion object {
        const val PLAYER_ACTIVITY_NAME = "com.apple.android.music.common.activity.PlayerActivity"

        fun install(
            @Suppress("UNUSED_PARAMETER") application: Application,
            controller: EmbeddedSettingsController,
            @Suppress("UNUSED_PARAMETER") playerActivityClass: Class<*>?,
        ): EmbeddedSettingsHost = EmbeddedSettingsHost(controller)
    }
}

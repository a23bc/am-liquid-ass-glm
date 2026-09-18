package com.example.amliquidass.config

import android.content.SharedPreferences
import android.os.ParcelFileDescriptor
import com.example.amliquidass.hook.ModernXposedRuntime
import com.example.amliquidass.model.FeatureHealth
import com.example.amliquidass.model.ModuleSettings
import java.io.InputStream

/**
 * Trimmed TargetConfigClient for AMLiquidAss.
 *
 * Only ordinary settings + raw file access + health reporting remain;
 * the custom-lyrics manifest cache / index pointer plumbing from
 * upstream AM-plus-plus has been removed.
 */
class TargetConfigClient private constructor(
    private val valuesProvider: () -> Map<String, *>,
    private val fileOpener: ((String) -> InputStream)?,
    private val remoteFileOpener: ((String) -> ParcelFileDescriptor)?,
) {
    constructor(
        preferences: SharedPreferences,
        remoteFileOpener: ((String) -> ParcelFileDescriptor)? = null,
    ) : this(
        valuesProvider = preferences::getAll,
        fileOpener = remoteFileOpener?.let { opener ->
            { name -> ParcelFileDescriptor.AutoCloseInputStream(opener(name)) }
        },
        remoteFileOpener = remoteFileOpener,
    )

    internal constructor(reader: ConfigurationReader) : this(
        valuesProvider = reader::values,
        fileOpener = { name -> reader.openFile(name) ?: error("Configuration file is unavailable: $name") },
        remoteFileOpener = { name ->
            reader.openFileDescriptor(name)
                ?: error("Configuration file descriptor is unavailable: $name")
        },
    )

    init {
        active = this
    }

    fun settings(): ModuleSettings = ModuleSettingsSchema.decode(valuesProvider())

    fun openRemoteFile(name: String): ParcelFileDescriptor? =
        runCatching { remoteFileOpener?.invoke(name) }.getOrNull()

    fun openFile(name: String): InputStream? =
        runCatching { fileOpener?.invoke(name) }.getOrNull()

    fun openFileDescriptor(name: String): ParcelFileDescriptor? =
        runCatching { remoteFileOpener?.invoke(name) }.getOrNull()

    fun reportHealth(health: FeatureHealth) {
        ModernXposedRuntime.log(
            "${health.feature}: ${health.state} - ${health.message} [${health.targetVersion}]",
        )
    }

    companion object {
        @Volatile
        private var active: TargetConfigClient? = null

        fun currentSettings(): ModuleSettings = active?.settings()
            ?: ModuleSettings(phoneLiquidGlassEnabled = true)
    }
}

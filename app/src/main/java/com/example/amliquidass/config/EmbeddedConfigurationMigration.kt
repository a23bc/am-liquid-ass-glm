package com.example.amliquidass.config

import android.content.SharedPreferences
import android.os.ParcelFileDescriptor
import java.io.InputStream

/**
 * Trimmed EmbeddedConfigurationMigration for AMLiquidAss.
 *
 * Only the high-level migrate/destination/write plumbing survives;
 * upstream's custom-lyrics manifest / font policy consultation inside
 * buildPlanUnsafe has been removed (only ordinary settings are migrated).
 */
internal object EmbeddedConfigurationMigration {
    const val MIGRATION_MARKER_KEY = "embedded_storage_migration_v1"
    const val MIGRATION_IN_PROGRESS = "in_progress"
    const val MIGRATION_COMPLETE = "complete"

    fun destinationAlreadyInitialized(destination: EmbeddedConfigurationStorage): Boolean =
        synchronized(destination) {
            when (destinationState(destination)) {
                DestinationState.COMPLETE,
                DestinationState.OCCUPIED,
                -> true
                DestinationState.EMPTY,
                DestinationState.IN_PROGRESS,
                -> false
            }
        }

    fun migrate(
        remotePreferences: SharedPreferences,
        remoteFileOpener: (String) -> ParcelFileDescriptor?,
        destination: EmbeddedConfigurationStorage,
    ): EmbeddedConfigurationMigrationResult {
        val remoteValues = runCatching { remotePreferences.all }.getOrNull()
            ?: return EmbeddedConfigurationMigrationResult.Failed("无法读取远程配置")
        return migrate(
            remoteValues = remoteValues,
            openRemoteFile = { name ->
                runCatching { remoteFileOpener(name) }
                    .getOrNull()
                    ?.let { descriptor -> ParcelFileDescriptor.AutoCloseInputStream(descriptor) }
            },
            destination = destination,
        )
    }

    fun migrate(
        remoteValues: Map<String, *>,
        @Suppress("UNUSED_PARAMETER") openRemoteFile: (String) -> InputStream?,
        destination: EmbeddedConfigurationStorage,
    ): EmbeddedConfigurationMigrationResult = synchronized(destination) {
        when (destinationState(destination)) {
            DestinationState.COMPLETE -> return@synchronized EmbeddedConfigurationMigrationResult.SkippedAlreadyComplete
            DestinationState.OCCUPIED -> return@synchronized EmbeddedConfigurationMigrationResult.SkippedDestinationOccupied
            DestinationState.EMPTY, DestinationState.IN_PROGRESS -> Unit
        }

        if (remoteValues.isEmpty() || !ModuleSettingsSchema.hasMigratableValues(remoteValues)) {
            return@synchronized EmbeddedConfigurationMigrationResult.SkippedNoRemoteConfiguration
        }

        if (!writeValues(
                destination,
                mapOf(MIGRATION_MARKER_KEY to MIGRATION_IN_PROGRESS),
            )
        ) {
            return@synchronized EmbeddedConfigurationMigrationResult.Failed(
                message = "无法标记宿主存储迁移状态",
            )
        }

        // AMLiquidAss carries only ordinary settings; just copy them through.
        val ordinary = ModuleSettingsSchema.encodeOrdinarySettings(
            ModuleSettingsSchema.decode(remoteValues),
        )
        if (!writeValues(destination, ordinary)) {
            return@synchronized EmbeddedConfigurationMigrationResult.Failed(
                message = "无法写入迁移后的设置",
            )
        }

        if (!writeValues(destination, mapOf(MIGRATION_MARKER_KEY to MIGRATION_COMPLETE))) {
            return@synchronized EmbeddedConfigurationMigrationResult.Failed(
                message = "无法标记迁移完成",
            )
        }

        EmbeddedConfigurationMigrationResult.Migrated(copiedFileIds = emptyList())
    }

    private fun destinationState(destination: EmbeddedConfigurationStorage): DestinationState {
        val marker = destination.values()[MIGRATION_MARKER_KEY] as? String
        return when (marker) {
            MIGRATION_COMPLETE -> DestinationState.COMPLETE
            MIGRATION_IN_PROGRESS -> DestinationState.IN_PROGRESS
            null -> if (destination.hasAnyFiles()) DestinationState.OCCUPIED else DestinationState.EMPTY
            else -> DestinationState.EMPTY
        }
    }

    private fun writeValues(
        destination: EmbeddedConfigurationStorage,
        values: Map<String, Any>,
    ): Boolean = runCatching { destination.writeValues(values, synchronous = true) }
        .getOrDefault(false)

    private enum class DestinationState {
        EMPTY,
        IN_PROGRESS,
        COMPLETE,
        OCCUPIED,
    }
}

internal sealed interface EmbeddedConfigurationMigrationResult {
    data class Migrated(val copiedFileIds: List<String>) : EmbeddedConfigurationMigrationResult
    data class Failed(val message: String) : EmbeddedConfigurationMigrationResult

    object SkippedAlreadyComplete : EmbeddedConfigurationMigrationResult
    object SkippedDestinationOccupied : EmbeddedConfigurationMigrationResult
    object SkippedNoRemoteConfiguration : EmbeddedConfigurationMigrationResult
}

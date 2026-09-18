package com.example.amliquidass.hook

/**
 * Target build descriptor — kept as a tiny standalone file after the
 * upstream TargetSymbols.kt symbol-discovery machinery was removed.
 *
 * Used by EmbeddedBootstrap.supports(...) and FeatureInstallation.targetBuild(...).
 */
data class TargetBuild(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
) {
    /**
     * Human-readable build identifier for log lines — matches the
     * upstream AM-plus-plus format `versionName (versionCode)`.
     */
    val displayName: String get() = "$versionName ($versionCode)"

    companion object {
        val UNKNOWN = TargetBuild("", "", 0L)
    }
}

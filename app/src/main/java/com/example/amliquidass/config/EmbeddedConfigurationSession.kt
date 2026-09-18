package com.example.amliquidass.config

import com.example.amliquidass.model.ModuleSettings
import java.io.InputStream
import android.os.ParcelFileDescriptor
import java.security.MessageDigest

/** Read-only configuration surface consumed by target-process features. */
internal interface ConfigurationReader {
    fun values(): Map<String, *>
    fun openFile(name: String): InputStream?
    fun openFileDescriptor(name: String): ParcelFileDescriptor? = null
}

/** Host-private storage adapter used only by the embedded artifact. */
internal interface EmbeddedConfigurationStorage : ConfigurationReader {
    fun writeValues(values: Map<String, Any>, synchronous: Boolean): Boolean
    fun removeValues(keys: Set<String>, synchronous: Boolean = true): Boolean = true
    fun writeFile(name: String, bytes: ByteArray): Boolean
    fun deleteFile(name: String): Boolean

    fun copyFile(
        name: String,
        input: InputStream,
        expectedSizeBytes: Long,
        expectedSha256: String,
    ): Boolean = runCatching {
        val bytes = input.use(InputStream::readBytes)
        bytes.size.toLong() == expectedSizeBytes &&
            sha256(bytes).equals(expectedSha256, ignoreCase = true) &&
            writeFile(name, bytes)
    }.getOrDefault(false)

    fun fileMatches(
        name: String,
        expectedSizeBytes: Long,
        expectedSha256: String,
    ): Boolean = runCatching {
        val bytes = openFile(name)?.use(InputStream::readBytes) ?: return@runCatching false
        bytes.size.toLong() == expectedSizeBytes &&
            sha256(bytes).equals(expectedSha256, ignoreCase = true)
    }.getOrDefault(false)

    fun hasAnyFiles(): Boolean = false

    companion object {
        private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}

/**
 * Owns the embedded configuration contract while hiding the host storage
 * implementation from settings workflows and target hooks.
 *
 * Trimmed for AMLiquidAss — custom-lyrics index mutation APIs and legacy
 * title-correction migration have been removed; only ordinary settings +
 * raw file/value access remain.
 */
internal class EmbeddedConfigurationSession(
    private val storage: EmbeddedConfigurationStorage,
    private val writable: Boolean = true,
) : ConfigurationReader {

    fun settings(): ModuleSettings = ModuleSettingsSchema.decode(storage.values())

    fun saveSettings(settings: ModuleSettings): Boolean = writable && storage.writeValues(
        ModuleSettingsSchema.encodeOrdinarySettings(settings),
        synchronous = true,
    )

    fun writeFile(name: String, bytes: ByteArray): Boolean = writable && storage.writeFile(name, bytes)

    fun deleteFile(name: String): Boolean = writable && storage.deleteFile(name)

    override fun values(): Map<String, *> = storage.values()

    override fun openFile(name: String): InputStream? = storage.openFile(name)

    override fun openFileDescriptor(name: String): ParcelFileDescriptor? =
        storage.openFileDescriptor(name)
}

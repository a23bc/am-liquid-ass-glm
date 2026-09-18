package com.example.amliquidass.hook

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import com.example.amliquidass.ModuleConstants
import com.example.amliquidass.config.TargetConfigClient
import com.example.amliquidass.model.FeatureHealth
import com.example.amliquidass.model.FeatureState
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Trimmed feature-installation module for AMLiquidAss.
 *
 * Only the PhoneLiquidGlassFeature plan survives — every lyric / font /
 * dual-pane / CJK / title-correction / DPI / editorial-video plan from
 * upstream AM-plus-plus has been dropped.
 */
internal object FeatureInstallation {
    private val module: FeatureInstallationModule = productionFeatureInstallationModule()

    fun registerResources(config: TargetConfigClient) {
        module.registerResources(config)
    }

    fun install(
        config: TargetConfigClient,
        targetClassLoader: ClassLoader,
    ) {
        module.install(config, targetClassLoader)
    }

    fun installEmbedded(
        config: TargetConfigClient,
        application: Application,
        targetClassLoader: ClassLoader,
        @Suppress("UNUSED_PARAMETER") currentSong: CurrentSongIdentityCache,
    ) {
        module.installNow(config) {
            HookContext(
                config = config,
                target = TargetAdaptation.appleMusic(),
            )
        }
    }
}

internal class FeatureInstallationModule(
    private val plans: List<FeatureInstallationPlan>,
    private val installLayoutInflationHooks: () -> Unit,
    private val registerApplicationCreated: (
        TargetConfigClient,
        ClassLoader,
        (() -> HookContext) -> Unit,
    ) -> Unit,
    private val reportHealth: (HookContext, FeatureHealth) -> Unit,
    private val reportError: (String, Throwable) -> Unit,
) {
    @Volatile
    private var activeSession: FeatureInstallationSession? = null
    private var resourceRegistrationAttempted = false
    private var resourceRegistrationFailure: Throwable? = null

    fun registerResources(config: TargetConfigClient) = synchronized(this) {
        registerResourcesIfNeeded(config)
    }

    fun install(
        config: TargetConfigClient,
        targetClassLoader: ClassLoader,
    ): FeatureInstallationSession = synchronized(this) {
        activeSession?.let { return@synchronized it }
        registerResourcesIfNeeded(config)
        val session = newSession()
        registerApplicationCreated(config, targetClassLoader, session::install)
        activeSession = session
        session
    }

    fun installNow(
        config: TargetConfigClient,
        contextFactory: () -> HookContext,
    ): FeatureInstallationSession = synchronized(this) {
        activeSession?.let { return@synchronized it }
        registerResourcesIfNeeded(config)
        val session = newSession()
        session.install(contextFactory)
        activeSession = session
        session
    }

    private fun registerResourcesIfNeeded(config: TargetConfigClient) {
        resourceRegistrationFailure?.let { throw it }
        if (resourceRegistrationAttempted) return
        resourceRegistrationAttempted = true
        try {
            plans.forEach { plan -> plan.registerResources(config) }
            installLayoutInflationHooks()
        } catch (error: Throwable) {
            resourceRegistrationFailure = error
            throw error
        }
    }

    private fun newSession(): FeatureInstallationSession = FeatureInstallationSession(
        features = plans.map(FeatureInstallationPlan::feature),
        reportHealth = reportHealth,
        reportError = reportError,
    )
}

internal data class FeatureInstallationPlan(
    val feature: FeatureHook,
    val registerResources: (TargetConfigClient) -> Unit = {},
)

internal enum class FeatureInstallationPhase {
    RESOURCES_REGISTERED,
    FEATURES_INSTALLING,
    COMPLETE,
}

internal data class FeatureInstallationSnapshot(
    val phase: FeatureInstallationPhase,
    val health: List<FeatureHealth>,
)

internal class FeatureInstallationSession(
    private val features: List<FeatureHook>,
    private val reportHealth: (HookContext, FeatureHealth) -> Unit,
    private val reportError: (String, Throwable) -> Unit,
) {
    private val installed = AtomicBoolean(false)
    private val snapshot = AtomicReference(
        FeatureInstallationSnapshot(
            phase = FeatureInstallationPhase.RESOURCES_REGISTERED,
            health = emptyList(),
        ),
    )

    fun snapshot(): FeatureInstallationSnapshot = snapshot.get()

    fun install(contextFactory: () -> HookContext) {
        if (installed.get()) return
        val context = contextFactory()
        if (!installed.compareAndSet(false, true)) return
        val installedHealth = mutableListOf<FeatureHealth>()
        snapshot.set(
            FeatureInstallationSnapshot(
                phase = FeatureInstallationPhase.FEATURES_INSTALLING,
                health = emptyList(),
            ),
        )

        features.forEach { feature ->
            val result = feature.installSafely(context, reportError)
            val health = FeatureHealth(
                feature = feature.key,
                state = result.state,
                message = result.message,
                targetVersion = context.target.identity,
            )
            reportHealth(context, health)
            installedHealth += health
            snapshot.set(
                FeatureInstallationSnapshot(
                    phase = FeatureInstallationPhase.FEATURES_INSTALLING,
                    health = installedHealth.toList(),
                ),
            )
        }

        snapshot.set(
            FeatureInstallationSnapshot(
                phase = FeatureInstallationPhase.COMPLETE,
                health = installedHealth.toList(),
            ),
        )
    }
}

internal data class HookContext(
    val config: TargetConfigClient,
    val target: TargetAdaptation,
)

internal interface FeatureHook {
    val key: String
    fun install(context: HookContext): FeatureInstallResult
}

internal fun FeatureHook.installSafely(
    context: HookContext,
    reportError: (String, Throwable) -> Unit = ModernXposedRuntime::log,
): FeatureInstallResult = runCatching { install(context) }
    .getOrElse { error ->
        reportError("$key failed", error)
        FeatureInstallResult.failed(error.shortMessage())
    }

internal class FeatureInstallResult private constructor(
    val state: FeatureState,
    val message: String,
) {
    init {
        require(message.isNotBlank()) { "Feature install diagnostic must not be blank" }
    }

    companion object {
        fun active(message: String): FeatureInstallResult =
            FeatureInstallResult(FeatureState.ACTIVE, message)

        fun disabled(message: String = "Disabled in module settings"): FeatureInstallResult =
            FeatureInstallResult(FeatureState.DISABLED, message)

        fun unsupported(message: String): FeatureInstallResult =
            FeatureInstallResult(FeatureState.UNSUPPORTED, message)

        fun degraded(message: String): FeatureInstallResult =
            FeatureInstallResult(FeatureState.DEGRADED, message)

        fun failed(message: String): FeatureInstallResult =
            FeatureInstallResult(FeatureState.FAILED, message)
    }
}

internal fun targetBuild(context: Context): TargetBuild = runCatching {
    val packageInfo = context.packageManager.getPackageInfo(ModuleConstants.TARGET_PACKAGE, 0)
    val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
        packageInfo.longVersionCode
    } else {
        @Suppress("DEPRECATION") packageInfo.versionCode.toLong()
    }
    TargetBuild(
        packageName = ModuleConstants.TARGET_PACKAGE,
        versionName = packageInfo.versionName.orEmpty(),
        versionCode = versionCode,
    )
}.getOrDefault(TargetBuild.UNKNOWN)

private fun productionFeatureInstallationModule(): FeatureInstallationModule =
    FeatureInstallationModule(
        plans = listOf(
            FeatureInstallationPlan(
                feature = PhoneLiquidGlassFeature(),
                registerResources = PhoneLiquidGlassResourceHook::install,
            ),
        ),
        installLayoutInflationHooks = LayoutInflationRegistry::install,
        registerApplicationCreated = { config, targetClassLoader, onCreated ->
            val onCreate = Application::class.java.getDeclaredMethod("onCreate")
            ModernXposedRuntime.hookMethod(onCreate, object : ModernMethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val application = param.thisObject as Application
                    onCreated {
                        HookContext(
                            config = config,
                            target = TargetAdaptation.appleMusic(),
                        )
                    }
                }
            })
        },
        reportHealth = { context, health -> context.config.reportHealth(health) },
        reportError = ModernXposedRuntime::log,
    )

private fun Throwable.shortMessage(): String = buildString {
    append(javaClass.simpleName.ifBlank { javaClass.name })
    message?.takeIf(String::isNotBlank)?.let { append(": ").append(it.take(180)) }
}

package com.example.amliquidass.hook

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.example.amliquidass.ModuleConstants
import com.example.amliquidass.config.TargetConfigClient
import com.example.amliquidass.model.FeatureState

/**
 * Real liquid-glass feature for Apple Music on phones.
 *
 * Re-implemented from scratch using the backdrop library's AGSL shaders
 * (vendored in [LiquidGlassShaders]) applied via Android's native
 * `RenderEffect.createRuntimeShaderEffect` + `RuntimeShader` (see
 * [LiquidGlassEffect]). The upstream BlurView-based implementation has
 * been removed entirely.
 *
 * Two Xposed resource-time hooks remain unchanged from upstream:
 *   - `bottom_navigation` layout inflation -> install glass on the tabs
 *   - `mini_player` layout inflation -> install glass on the mini player
 *
 * The mini-player lookup-with-retry logic is preserved since it has to
 * resolve the view id after the navigation root attaches.
 */
internal class PhoneLiquidGlassFeature : FeatureHook {
    override val key: String = ModuleConstants.FEATURE_PHONE_LIQUID_GLASS

    override fun install(context: HookContext): FeatureInstallResult {
        val settings = context.config.settings()
        if (!settings.phoneLiquidGlassEnabled) {
            return FeatureInstallResult.disabled()
        }
        return FeatureInstallResult.active(
            "liquid glass registered (resource hooks live); API ${android.os.Build.VERSION.SDK_INT}",
        )
    }
}

internal object PhoneLiquidGlassResourceHook {
    fun install(config: TargetConfigClient) {
        LayoutInflationRegistry.register("bottom_navigation") { view ->
            val root = view as? ViewGroup ?: return@register
            if (!PhoneLiquidGlassQualifier.isEligible(root.context, config)) return@register
            PhoneLiquidGlassStyler.installBottomNavigation(root)
            installMiniPlayerWhenAvailable(root, config, attempt = 0)
        }
        LayoutInflationRegistry.register("mini_player") { view ->
            val root = view as? FrameLayout ?: return@register
            if (!PhoneLiquidGlassQualifier.isEligible(root.context, config)) return@register
            PhoneLiquidGlassStyler.installMiniPlayer(root)
        }
    }

    private fun installMiniPlayerWhenAvailable(
        navigationRoot: ViewGroup,
        config: TargetConfigClient,
        attempt: Int,
    ) {
        navigationRoot.post {
            if (!PhoneLiquidGlassQualifier.isEligible(navigationRoot.context, config)) return@post
            val miniPlayerId = navigationRoot.resources.getIdentifier(
                "mini_player",
                "id",
                ModuleConstants.TARGET_PACKAGE,
            )
            val miniPlayer = miniPlayerId.takeIf { it != 0 }
                ?.let { navigationRoot.findViewById<FrameLayout>(it) }
            if (miniPlayer != null) {
                PhoneLiquidGlassStyler.installMiniPlayer(miniPlayer)
                ModernXposedRuntime.log("phone liquid-glass mini player installed from navigation root")
            } else if (attempt < MINI_PLAYER_LOOKUP_RETRIES) {
                navigationRoot.postDelayed(
                    { installMiniPlayerWhenAvailable(navigationRoot, config, attempt + 1) },
                    MINI_PLAYER_LOOKUP_DELAY_MS,
                )
            }
        }
    }

    private const val MINI_PLAYER_LOOKUP_RETRIES = 15
    private const val MINI_PLAYER_LOOKUP_DELAY_MS = 80L
}

internal object PhoneLiquidGlassQualifier {
    fun isEligible(context: Context, config: TargetConfigClient): Boolean =
        !isOfficialTablet(context) && config.settings().phoneLiquidGlassEnabled

    /**
     * Inline tablet check — upstream AM-plus-plus pulled this out of
     * `AppleMusicDualPaneTarget.kt` (which has been deleted). The check
     * just excludes large-screen layouts so the phone-only effect
     * doesn't fire on tablets.
     */
    private fun isOfficialTablet(context: Context): Boolean {
        val configuration = context.resources.configuration
        return configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK >=
            Configuration.SCREENLAYOUT_SIZE_LARGE &&
            (configuration.screenLayout and Configuration.SCREENLAYOUT_LONG_MASK ==
                Configuration.SCREENLAYOUT_LONG_YES ||
                configuration.smallestScreenWidthDp >= 600)
    }
}

private object PhoneLiquidGlassStyler {
    private const val BOTTOM_NAVIGATION = "bottom_navigation"
    private const val BOTTOM_NAVIGATION_TABS_FRAME = "bottom_navigation_tabs_frame"
    private const val MINI_PLAYER_CONTENT = "mini_player_content"

    fun installBottomNavigation(root: ViewGroup) {
        val tabsFrame = findByIdRecursive(root, BOTTOM_NAVIGATION_TABS_FRAME)
            ?: findByIdRecursive(root, BOTTOM_NAVIGATION)
        if (tabsFrame == null) {
            ModernXposedRuntime.log("phone liquid-glass: bottom_navigation not resolved")
            return
        }
        applyToViewTree(tabsFrame)
    }

    fun installMiniPlayer(root: ViewGroup) {
        val content = findByIdRecursive(root, MINI_PLAYER_CONTENT) ?: root
        applyToViewTree(content)
    }

    /**
     * Walks the target's subtree and applies the liquid-glass effect to
     * every direct child that is not itself a ViewGroup with children —
     * i.e. the leaf-ish surfaces that we want to render as "glass".
     *
     * This is intentionally broader than upstream's per-id stylist, which
     * required knowing each Apple Music resource id. The backdrop AGSL
     * refraction shader doesn't care about id; it cares about bounds.
     */
    private fun applyToViewTree(target: View) {
        target.post {
            LiquidGlassEffect.applyTo(target)
            (target as? ViewGroup)?.let { group ->
                for (i in 0 until group.childCount) {
                    val child = group.getChildAt(i) ?: continue
                    LiquidGlassEffect.applyTo(child)
                }
            }
        }
    }

    private fun findByIdRecursive(root: View, name: String): View? {
        val id = root.resources.getIdentifier(name, "id", ModuleConstants.TARGET_PACKAGE)
        if (id != 0 && root.id == id) return root
        val view = if (id != 0) root.findViewById<View?>(id) else null
        if (view != null) return view
        val group = root as? ViewGroup ?: return null
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i) ?: continue
            val found = findByIdRecursive(child, name)
            if (found != null) return found
        }
        return null
    }
}

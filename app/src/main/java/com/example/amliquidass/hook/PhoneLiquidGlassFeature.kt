package com.example.amliquidass.hook

import android.content.Context
import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.amliquidass.ModuleConstants
import com.example.amliquidass.compose.LiquidBottomTab
import com.example.amliquidass.compose.LiquidBottomTabs
import com.example.amliquidass.config.TargetConfigClient
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import java.lang.ref.WeakReference

/**
 * Real liquid-glass feature for Apple Music on phones.
 *
 * Replaces AM's native `bottom_navigation_tabs_frame` with a [ComposeView]
 * hosting `Backdrop { LiquidBottomTabs(...) }` from the upstream
 * AndroidLiquidGlass library (via `io.github.kyant0:backdrop:2.0.1`).
 * The previous BlurView-based implementation from AM-plus-plus and the
 * hand-rolled `LiquidGlassEffect` shim are both gone.
 *
 * The original AM tab Views are kept alive (just hidden via INVISIBLE)
 * so that `performClick()` still drives AM's real navigation — clicking
 * a glass tab forwards to the matching original tab.
 */
internal class PhoneLiquidGlassFeature : FeatureHook {
    override val key: String = ModuleConstants.FEATURE_PHONE_LIQUID_GLASS

    override fun install(context: HookContext): FeatureInstallResult {
        // Always on — AMLiquidAss ships no GUI; the feature is the
        // entire reason the module exists.
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
        }
        // mini_player inflation hook is intentionally NOT registered in
        // this iteration — the user's complaint is specifically about the
        // bottom bar; mini player replacement needs its own component.
    }
}

internal object PhoneLiquidGlassQualifier {
    fun isEligible(context: Context, @Suppress("UNUSED_PARAMETER") config: TargetConfigClient): Boolean =
        !isOfficialTablet(context)

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
    private const val BOTTOM_NAVIGATION_TABS_FRAME = "bottom_navigation_tabs_frame"
    private const val BOTTOM_NAVIGATION = "bottom_navigation"

    /**
     * Apple Music's known tab strip — 5 tabs in this order:
     *   Listen Now, Browse, Radio, Library, Search
     * Hardcoded labels + Material icons for v1. Each tab's onClick
     * forwards to the matching AM original tab View's `performClick()`
     * so AM's actual navigation is preserved.
     */
    internal data class TabSpec(val label: String, val icon: ImageVector)
    internal val APPLE_MUSIC_TABS = listOf(
        TabSpec("Listen Now", Icons.Filled.Home),
        TabSpec("Browse", Icons.Filled.Star),
        TabSpec("Radio", Icons.Filled.PlayArrow),
        TabSpec("Library", Icons.Filled.AccountBox),
        TabSpec("Search", Icons.Filled.Search),
    )

    fun installBottomNavigation(root: ViewGroup) {
        root.post {
            try {
                installBottomNavigationUnsafe(root)
            } catch (error: Throwable) {
                // Inline the exception type + message into the log string
                // itself — LSPosed prepends a "[com.example.amliquidass,...]"
                // tag to every log line, so the actual exception line
                // (which doesn't contain "amliquidass") gets filtered out
                // by `adb logcat | findstr amliquidass`. Folding the type
                // and message into our own string keeps it visible.
                ModernXposedRuntime.log(
                    "phone liquid-glass: install failed: " +
                        "${error.javaClass.name}: ${error.message.orEmpty()}"
                )
            }
        }
    }

    private fun installBottomNavigationUnsafe(root: ViewGroup) {
        val tabsFrame = findByIdRecursive(root, BOTTOM_NAVIGATION_TABS_FRAME)
            ?: findByIdRecursive(root, BOTTOM_NAVIGATION)
        if (tabsFrame == null) {
            ModernXposedRuntime.log("phone liquid-glass: bottom_navigation_tabs_frame not resolved")
            return
        }
        // Use the root (bottom_navigation) as the ComposeView's parent.
        // We previously tried tabsFrame.parent but AM's custom view tree
        // may reject non-AM children inside the tabs frame's parent.
        if (findExistingComposeView(root) != null) return

        val amTabs = collectOriginalTabs(tabsFrame)
        tabsFrame.visibility = View.INVISIBLE

        val composeView = createComposeView(root.context, amTabs)
        // Use the simplest possible LayoutParams (base ViewGroup.LayoutParams)
        // and MATCH_PARENT x WRAP_CONTENT — the parent will convert via
        // generateLayoutParams() if it needs a more specific type.
        val lp = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        root.addView(composeView, lp)
        ModernXposedRuntime.log(
            "phone liquid-glass: installed ComposeView bottom bar (amTabs=${amTabs.size})",
        )
    }

    private fun findExistingComposeView(root: ViewGroup): ComposeView? {
        // Use a String tag (set in createComposeView) instead of a numeric ID,
        // to avoid any collision with AM's resource IDs.
        val found = root.findViewWithTag(COMPOSE_TAG) as? ComposeView
        return found
    }

    /**
     * Walks the original tabs frame and collects any View that looks like
     * a tab (i.e. clickable + has children). Best-effort: if extraction
     * fails we just return an empty list and rely on the hardcoded 5
     * Material icons; click forwarding still works for indices < amTabs.size.
     */
    private fun collectOriginalTabs(tabsFrame: View): List<WeakReference<View>> {
        val out = mutableListOf<WeakReference<View>>()
        val group = tabsFrame as? ViewGroup ?: return out
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i) ?: continue
            if (child.isClickable) out.add(WeakReference(child))
        }
        if (out.isEmpty() && group.childCount > 0) {
            val inner = group.getChildAt(0) as? ViewGroup
            if (inner != null) {
                for (i in 0 until inner.childCount) {
                    val child = inner.getChildAt(i) ?: continue
                    out.add(WeakReference(child))
                }
            }
        }
        return out
    }

    private fun createComposeView(
        context: android.content.Context,
        amTabs: List<WeakReference<View>>,
    ): ComposeView {
        val owner = ComposeLifecycleOwner()
        return ComposeView(context).apply {
            tag = COMPOSE_TAG
            // Install the ViewTree owners via the Kotlin extension functions.
            // The static ViewTree*Owner classes were removed in lifecycle 2.8.0;
            // the extensions are the canonical API now.
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                MaterialTheme {
                    AmLiquidBottomBar(amTabs = amTabs)
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

    private const val COMPOSE_TAG = "amliquidass.compose.bottom_bar"
}

/**
 * Minimal lifecycle/viewmodel/savedstate owner so a [ComposeView] can
 * live inside Apple Music's view tree without an AppCompatActivity host.
 *
 * AM's host activity is a plain `Activity`; Compose needs a
 * [LifecycleOwner] + [ViewModelStoreOwner] + [SavedStateRegistryOwner]
 * attached to the view tree, so we synthesize one that is always
 * RESUMED. This is the standard pattern documented for embedding
 * Compose inside non-Compose Android hosts.
 */
private class ComposeLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry: LifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val savedStateController: SavedStateRegistryController =
        SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    override val viewModelStore: ViewModelStore = ViewModelStore()

    init {
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }
}

/**
 * The Compose hierarchy that replaces AM's bottom nav strip.
 *
 * - `rememberCanvasBackdrop` provides a translucent dark backdrop
 *   surface that the backdrop library's `drawBackdrop` modifier then
 *   transforms with `vibrancy` + `blur` + `lens` effects.
 * - `LiquidBottomTabs` lays out `tabsCount` `LiquidBottomTab` slots in
 *   a `Row` with capsule-shaped glass surfaces.
 * - Each tab's `onClick` forwards to the corresponding original AM tab
 *   view (if still alive).
 */
@Composable
private fun AmLiquidBottomBar(amTabs: List<WeakReference<View>>) {
    val tabsCount = PhoneLiquidGlassStyler.APPLE_MUSIC_TABS.size
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val backdrop = rememberCanvasBackdrop {
        // Translucent dark canvas — the backdrop library applies the
        // vibrancy / blur / lens pipeline on top of this.
        drawRect(Color.Black.copy(alpha = 0.18f))
    }
    Surface(color = Color.Transparent) {
        LiquidBottomTabs(
            selectedTabIndex = { selectedTabIndex },
            onTabSelected = { idx ->
                selectedTabIndex = idx
                amTabs.getOrNull(idx)?.get()?.performClick()
            },
            backdrop = backdrop,
            tabsCount = tabsCount,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            for (idx in 0 until tabsCount) {
                val spec = PhoneLiquidGlassStyler.APPLE_MUSIC_TABS[idx]
                LiquidBottomTab(
                    onClick = {
                        selectedTabIndex = idx
                        amTabs.getOrNull(idx)?.get()?.performClick()
                    },
                ) {
                    Box(
                        Modifier.size(28.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = spec.icon,
                            contentDescription = spec.label,
                            tint = if (selectedTabIndex == idx)
                                MaterialTheme.colors.primary
                            else MaterialTheme.colors.onSurface,
                        )
                    }
                    Text(
                        spec.label,
                        fontSize = 11.sp,
                        color = if (selectedTabIndex == idx)
                            MaterialTheme.colors.primary
                        else MaterialTheme.colors.onSurface,
                    )
                }
            }
        }
    }
}

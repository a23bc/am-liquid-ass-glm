package com.example.amliquidass.hook

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.graphics.Outline

/**
 * Liquid glass effect applied to a target View using Android's native
 * `RenderEffect` + `RuntimeShader` (API 33+) and the AGSL shaders vendored
 * from backdrop's `RoundedRectRefractionWithDispersionShaderString`.
 *
 * On API < 33 the helpers no-op (logged once per call). Apple Music ships
 * with `minSdk` well below 33, so we degrade gracefully instead of crashing.
 *
 * The effect:
 *   1. Force the view to a transparent background so the refracted
 *      backdrop pixels are visible.
 *   2. Apply a rounded-rectangle outline so the SDF coordinates line up.
 *   3. Layer `RenderEffect` chain: blur the backing content (cheap Android
 *      native blur) THEN run the refraction shader against the blurred
 *      backdrop. This produces the "frosted glass with chromatic edges"
 *      look.
 *
 * Tunables: [refractionHeight], [refractionAmount], [depthEffect],
 * [chromaticAberration], [blurRadiusPx], [cornerRadiusPx]. Tweak these
 * to taste — defaults match backdrop's playground look.
 */
internal object LiquidGlassEffect {

    private const val MIN_API = Build.VERSION_CODES.TIRAMISU // API 33

    private val refractionShader by lazy { RuntimeShader(LiquidGlassShaders.RefractionWithDispersionShader) }
    private val highlightShader by lazy { RuntimeShader(LiquidGlassShaders.HighlightShader) }

    /** Tunables — adjust to taste. */
    private const val DEFAULT_CORNER_RADIUS_PX = 32f
    private const val DEFAULT_REFRACTION_HEIGHT = 60f
    private const val DEFAULT_REFRACTION_AMOUNT = 18f
    private const val DEFAULT_DEPTH_EFFECT = 0.25f
    private const val DEFAULT_CHROMATIC_ABERRATION = 8f
    private const val DEFAULT_BLUR_RADIUS_PX = 8f

    /**
     * Applies the liquid-glass effect to [view]. Safe to call repeatedly;
     * re-installs the chain on every call (Android `setRenderEffect`
     * replaces, not chains, on subsequent calls without an explicit chain).
     */
    fun applyTo(view: View) {
        if (Build.VERSION.SDK_INT < MIN_API) {
            ModernXposedRuntime.log("LiquidGlassEffect skipped: API ${Build.VERSION.SDK_INT} < 33")
            return
        }
        runCatching {
            view.background = null
            view.outlineProvider = RoundedOutlineProvider(DEFAULT_CORNER_RADIUS_PX)
            view.clipToOutline = true

            val blur = RenderEffect.createBlurEffect(
                DEFAULT_BLUR_RADIUS_PX,
                DEFAULT_BLUR_RADIUS_PX,
                Shader.TileMode.DECAL,
            )
            val refraction = buildRefractionEffect(view)
            view.setRenderEffect(RenderEffect.createChainEffect(refraction, blur))
        }.onFailure { ModernXposedRuntime.log("LiquidGlassEffect failed: $it") }
    }

    private fun buildRefractionEffect(view: View): RenderEffect {
        val w = view.width.takeIf { it > 0 }?.toFloat() ?: 0f
        val h = view.height.takeIf { it > 0 }?.toFloat() ?: 0f
        refractionShader.setFloatUniform("size", w, h)
        refractionShader.setFloatUniform("offset", 0f, 0f)
        refractionShader.setFloatUniform(
            "cornerRadii",
            DEFAULT_CORNER_RADIUS_PX,
            DEFAULT_CORNER_RADIUS_PX,
            DEFAULT_CORNER_RADIUS_PX,
            DEFAULT_CORNER_RADIUS_PX,
        )
        refractionShader.setFloatUniform("refractionHeight", DEFAULT_REFRACTION_HEIGHT)
        refractionShader.setFloatUniform("refractionAmount", DEFAULT_REFRACTION_AMOUNT)
        refractionShader.setFloatUniform("depthEffect", DEFAULT_DEPTH_EFFECT)
        refractionShader.setFloatUniform("chromaticAberration", DEFAULT_CHROMATIC_ABERRATION)
        return RenderEffect.createRuntimeShaderEffect(refractionShader, "content")
    }

    private class RoundedOutlineProvider(private val radius: Float) : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, radius)
        }
    }
}

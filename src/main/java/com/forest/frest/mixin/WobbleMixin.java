package com.forest.frest.mixin;

import com.forest.frest.modules.AntiWobbleModule;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import net.minecraft.client.renderer.GameRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Zeroes the warp intensity in renderLevel.
 *
 * The injection point mirrors the client's own AutismNoRenderNauseaMixin, which
 * is the verified location for this mapping set. require = 0 means a mapping
 * change degrades to "no effect" rather than crashing the game on launch.
 */
@Mixin(GameRenderer.class)
public abstract class WobbleMixin {

    @ModifyExpressionValue(
        method = "renderLevel",
        at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(FF)F", ordinal = 0),
        require = 0)
    private float frest$noWarp(float original) {
        return AntiWobbleModule.noWarp() ? 0.0F : original;
    }
}

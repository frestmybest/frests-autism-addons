package com.forest.frest.mixin;

import com.forest.frest.modules.AntiWobbleModule;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Suppresses the nausea haze and portal swirl overlays. */
@Mixin(Hud.class)
public abstract class WobbleOverlayMixin {

    @Inject(method = "extractConfusionOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private void frest$noConfusionOverlay(GuiGraphicsExtractor ctx, float amount, CallbackInfo ci) {
        if (AntiWobbleModule.noConfusionOverlay()) ci.cancel();
    }

    @Inject(method = "extractPortalOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private void frest$noPortalOverlay(GuiGraphicsExtractor ctx, float alpha, CallbackInfo ci) {
        if (AntiWobbleModule.noPortalOverlay()) ci.cancel();
    }
}

package com.forest.frest.mixin;

import com.forest.frest.modules.FrestSettingsModule;

import autismclient.gui.vanillaui.module.VanillaModuleMenuController;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Explains the purple tint, in the module menu's top-left corner. */
@Mixin(VanillaModuleMenuController.class)
public abstract class ModuleMenuLabelMixin {

    @Unique
    private static final String FREST_LABEL = "Purple Color is part of frest's autism addons";

    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void frest$drawMenuLabel(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                     float delta, int width, int height, CallbackInfo ci) {
        if (!FrestSettingsModule.labelEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.font == null) return;

        // TAIL, so this lands on top of the panels rather than under them.
        graphics.text(mc.font, FREST_LABEL, 4, 4, FrestSettingsModule.baseColor(), true);
    }
}

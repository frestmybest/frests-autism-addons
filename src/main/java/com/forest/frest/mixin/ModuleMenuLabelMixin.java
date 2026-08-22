package com.forest.frest.mixin;

import autismclient.gui.vanillaui.module.VanillaModuleMenuController;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Explains the purple rows, in the module menu's top-left corner. */
@Mixin(VanillaModuleMenuController.class)
public abstract class ModuleMenuLabelMixin {

    @Unique
    private static final String FREST_LABEL = "Purple Color is part of frest's autism addons";

    /** Matches the row outline, so the note reads as part of the same set. */
    @Unique
    private static final int FREST_LABEL_COLOR = 0xFFB794F4;

    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void frest$drawMenuLabel(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                     float delta, int width, int height, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.font == null) return;

        graphics.text(mc.font, FREST_LABEL, 4, 4, FREST_LABEL_COLOR, true);
    }
}

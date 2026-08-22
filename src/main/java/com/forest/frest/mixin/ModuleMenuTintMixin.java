package com.forest.frest.mixin;

import com.forest.frest.modules.FrestSettingsModule;

import autismclient.gui.vanillaui.UiTextRenderer;
import autismclient.gui.vanillaui.module.VanillaModuleMenuController;
import autismclient.modules.Module;

import com.llamalad7.mixinextras.sugar.Local;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Tints this addon's module names in the module menu.
 *
 * renderModuleRow draws every label with a theme colour — text when enabled,
 * muted when not — and there's no per-module colour hook, so the colour argument
 * gets replaced here instead. @Local grabs the Module parameter so only rows
 * belonging to this addon are touched; everything else keeps the theme colour.
 *
 * require = 0: if the menu is restructured this silently stops tinting rather
 * than breaking the whole menu.
 */
@Mixin(VanillaModuleMenuController.class)
public abstract class ModuleMenuTintMixin {

    @ModifyArg(
        method = "renderModuleRow",
        at = @At(
            value = "INVOKE",
            target = "Lautismclient/gui/vanillaui/UiTextRenderer;drawFitted("
                + "Lnet/minecraft/client/gui/GuiGraphicsExtractor;Ljava/lang/String;IIII)V"),
        index = 5,
        require = 0)
    private int frest$tintModuleLabel(int color, @Local(argsOnly = true) Module module) {
        if (module == null) return color;
        if (!FrestSettingsModule.tintEnabled()) return color;
        if (!FrestSettingsModule.isOurs(module.id())) return color;

        return module.isEnabled()
            ? FrestSettingsModule.enabledColor()
            : FrestSettingsModule.baseColor();
    }
}

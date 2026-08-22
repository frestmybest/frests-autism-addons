package com.forest.frest.mixin;

import com.forest.frest.FrestAddon;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContext;
import autismclient.modules.Module;
import autismclient.modules.ModuleCategory;
import autismclient.gui.vanillaui.module.VanillaModuleMenuController;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives this addon's rows a purple treatment in the module menu.
 *
 * The first version tinted the label text, which was a mistake: the client
 * paints coloured rows under white text, so purple-on-dark text was both
 * unreadable and unlike everything around it. Text is now left entirely alone
 * and the row background, enabled fill and enabled outline are tinted instead.
 *
 * Row colours are blended rather than replaced, so the menu's own hover
 * lightening and enable animation still come through.
 *
 * The current module is stashed at the head of renderModuleRow rather than
 * captured with MixinExtras @Local. drawAnimatedEnabledOutline is called from
 * inside renderModuleRow on the same thread, so the field is valid for both.
 */
@Mixin(VanillaModuleMenuController.class)
public abstract class ModuleMenuTintMixin {

    /** Row background tint. Dark enough to sit quietly on a black theme. */
    @Unique private static final int FREST_ROW = 0xFF2A1145;
    /** Enabled fill and the left marker bar. */
    @Unique private static final int FREST_ACCENT = 0xFF4A15A1;
    /** Light lavender edge on an enabled row. */
    @Unique private static final int FREST_OUTLINE = 0xFFB794F4;

    @Unique private static Module frest$row;

    @Inject(method = "renderModuleRow", at = @At("HEAD"), require = 0)
    private void frest$captureRow(UiContext context, Module module, UiBounds row,
                                  ModuleCategory category, int index, CallbackInfo ci) {
        frest$row = module;
    }

    /** Row background: blended so hover still reads. */
    @ModifyArg(
        method = "renderModuleRow",
        at = @At(value = "INVOKE",
            target = "Lautismclient/gui/vanillaui/UiRenderer;rect("
                + "Lnet/minecraft/client/gui/GuiGraphicsExtractor;"
                + "Lautismclient/gui/vanillaui/UiBounds;I)V",
            ordinal = 0),
        index = 2,
        require = 0)
    private int frest$tintRowBackground(int color) {
        return frest$ours() ? frest$blend(color, FREST_ROW, 0.72f) : color;
    }

    /** Enabled fill: the accent wash that sweeps across as a module turns on. */
    @ModifyArg(
        method = "renderModuleRow",
        at = @At(value = "INVOKE",
            target = "Lautismclient/gui/vanillaui/UiRenderer;rect("
                + "Lnet/minecraft/client/gui/GuiGraphicsExtractor;"
                + "Lautismclient/gui/vanillaui/UiBounds;I)V",
            ordinal = 1),
        index = 2,
        require = 0)
    private int frest$tintEnabledFill(int color) {
        // Keep the client's own alpha so the animation still fades in.
        return frest$ours() ? (color & 0xFF000000) | (FREST_ACCENT & 0x00FFFFFF) : color;
    }

    /** Outline drawn around an enabled row, plus its left marker bar. */
    @ModifyVariable(method = "drawAnimatedEnabledOutline", at = @At("STORE"), ordinal = 0, require = 0)
    private int frest$tintOutline(int color) {
        return frest$ours() ? (color & 0xFF000000) | (FREST_OUTLINE & 0x00FFFFFF) : color;
    }

    @Unique
    private static boolean frest$ours() {
        Module module = frest$row;
        return module != null && module.id() != null
            && module.id().startsWith(FrestAddon.ID + ":");
    }

    /** Mixes {@code from} toward {@code to}, preserving from's alpha. */
    @Unique
    private static int frest$blend(int from, int to, float amount) {
        float t = Math.max(0.0f, Math.min(1.0f, amount));
        int a = (from >>> 24) & 0xFF;
        int r = Math.round((((from >> 16) & 0xFF) * (1 - t)) + (((to >> 16) & 0xFF) * t));
        int g = Math.round((((from >> 8) & 0xFF) * (1 - t)) + (((to >> 8) & 0xFF) * t));
        int b = Math.round(((from & 0xFF) * (1 - t)) + ((to & 0xFF) * t));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}

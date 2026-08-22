package com.forest.frest.mixin;

import com.forest.frest.FrestAddon;

import autismclient.gui.screen.AutismTitleScreen;
import autismclient.util.AutismUiScale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the addon name between the client wordmark and the menu buttons.
 *
 * Targets AutismTitleScreen, not TitleScreen. The client swaps the vanilla
 * title screen out for its own class in AutismGuiSetScreenMixin, and that class
 * extends Screen rather than TitleScreen — so two earlier attempts failed for
 * two separate reasons: an `instanceof TitleScreen` check that was never true,
 * and a @Mixin(Screen.class) injection that never fired because
 * AutismTitleScreen.extractRenderState doesn't call super.
 *
 * Coordinates are virtual, matching layout(): the custom menu draws inside
 * AutismUiScale's overlay scale, which is popped again before this runs, so the
 * scale has to be re-pushed here to land in the same space as the buttons.
 *
 * The AUTISM CLIENT wordmark is a PNG, not a font, so this can't reuse that
 * lettering. Scaled, letter-spaced game font is the closest available match.
 */
@Mixin(AutismTitleScreen.class)
public abstract class TitleBrandingMixin {

    @Unique private static final String FREST_TITLE = "FREST'S AUTISM ADDON " + FrestAddon.VERSION;
    @Unique private static final String FREST_BYLINE = "by frestmybest";

    @Unique private static final float FREST_TITLE_SCALE = 1.15F;
    @Unique private static final float FREST_BYLINE_SCALE = 0.6F;

    @Unique private static final int FREST_TITLE_COLOR = 0xFFFFFFFF;
    @Unique private static final int FREST_BYLINE_COLOR = 0xFFB4B4B4;
    @Unique private static final int FREST_TRACKING = 1;

    @Inject(method = "extractRenderState", at = @At("TAIL"), require = 0)
    private void frest$drawBranding(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.font == null) return;

        int screenW = AutismUiScale.getVirtualScreenWidth();
        int screenH = AutismUiScale.getVirtualScreenHeight();
        if (screenW <= 0 || screenH <= 0) return;

        // Same arithmetic as AutismTitleScreen.layout(), so the text tracks the
        // hero panel instead of guessing from raw screen height.
        int panelW = Math.max(1, Math.min(356, Math.max(1, screenW - 24)));
        int pad = screenH < 360 ? 8 : 12;
        int rowH = screenH < 360 ? 18 : 21;
        int gap = screenH < 360 ? 4 : 5;
        int heroH = screenH < 360 ? 58 : 76;
        int rowsH = rowH * 5 + gap * 4;
        int panelH = Math.max(1, Math.min(Math.max(1, screenH - 18), pad * 2 + heroH + 14 + rowsH));
        int panelY = Math.max(6, (screenH - panelH) / 2);
        int heroY = panelY + pad;
        int rowTop = heroY + heroH + 14;

        // rowTop is the first button. The title renders ~10px tall at 1.15x, so
        // the two lines need more than the 8px they had or the byline lands in
        // the title's descenders.
        int titleY = rowTop - 20;
        int bylineY = rowTop - 8;
        if (titleY < 2) return;

        AutismUiScale.pushOverlayScale(graphics);
        try {
            frest$drawCentred(graphics, mc, FREST_TITLE, screenW, titleY,
                FREST_TITLE_SCALE, FREST_TITLE_COLOR, FREST_TRACKING);
            frest$drawCentred(graphics, mc, FREST_BYLINE, screenW, bylineY,
                FREST_BYLINE_SCALE, FREST_BYLINE_COLOR, 0);
        } finally {
            AutismUiScale.popOverlayScale(graphics);
        }
    }

    @Unique
    private void frest$drawCentred(GuiGraphicsExtractor graphics, Minecraft mc, String text,
                                   int screenWidth, int y, float scale, int color, int tracking) {
        graphics.pose().pushMatrix();
        graphics.pose().scale(scale, scale);

        int scaledWidth = Math.round(screenWidth / scale);
        int scaledY = Math.round(y / scale);

        int totalWidth = mc.font.width(text) + tracking * Math.max(0, text.length() - 1);
        int x = scaledWidth / 2 - totalWidth / 2;

        if (tracking <= 0) {
            graphics.text(mc.font, text, x, scaledY, color, true);
        } else {
            int cursor = x;
            for (int i = 0; i < text.length(); i++) {
                String ch = String.valueOf(text.charAt(i));
                graphics.text(mc.font, ch, cursor, scaledY, color, true);
                cursor += mc.font.width(ch) + tracking;
            }
        }

        graphics.pose().popMatrix();
    }
}

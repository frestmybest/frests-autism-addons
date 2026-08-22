package com.forest.frest.modules;

import com.forest.frest.FrestAddon;

import autismclient.api.module.BoolSetting;
import autismclient.modules.Module;

/**
 * Removes the screen distortion from nausea and nether portals.
 *
 * Purely a rendering change — nothing about movement, packets or server state
 * is touched. The nausea warp is a common motion-sickness trigger, which is why
 * vanilla exposes a Distortion Effects slider under Accessibility.
 *
 * Note: the base client's NoRender module already offers this under
 * Overlay -> Nausea. This is the standalone equivalent, using the same
 * injection points.
 */
public final class AntiWobbleModule extends Module {

    private static AntiWobbleModule instance;

    private final BoolSetting warp = add(new BoolSetting("warp", "Disable Screen Warp", true)
        .description("Removes the world-bending distortion. Covers nausea and portals together — vanilla computes them at one point, so they can't be split.")
        .group("Distortion"));

    private final BoolSetting confusionOverlay = add(new BoolSetting("confusion-overlay", "Hide Nausea Overlay", true)
        .description("Removes the green haze drawn over the screen while nauseated.")
        .group("Overlays"));

    private final BoolSetting portalOverlay = add(new BoolSetting("portal-overlay", "Hide Portal Overlay", false)
        .description("Removes the purple swirl while standing in a portal. Off by default — it's a useful cue that you're actually in one.")
        .group("Overlays"));

    public AntiWobbleModule() {
        super(FrestAddon.ID + ":anti-wobble", "AntiWobble", FrestAddon.CATEGORY,
            "Disables the wobble effect from nausea and portals.");
        instance = this;
    }

    private static boolean on() {
        return instance != null && instance.isEnabled();
    }

    public static boolean noWarp() {
        return on() && instance.warp.get();
    }

    public static boolean noConfusionOverlay() {
        return on() && instance.confusionOverlay.get();
    }

    public static boolean noPortalOverlay() {
        return on() && instance.portalOverlay.get();
    }
}

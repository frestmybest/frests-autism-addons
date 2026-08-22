package com.forest.frest.modules;

import com.forest.frest.FrestAddon;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.ColorSetting;
import autismclient.api.module.IntSetting;
import autismclient.modules.Module;
import autismclient.modules.ModuleCategory;

/**
 * Appearance settings for the rest of the addon.
 *
 * The modules themselves now live in the client's own categories, so this is
 * what keeps them identifiable: it owns the tint applied to their names in the
 * module menu, and the note drawn in the menu's top-left corner.
 *
 * Enabling or disabling this module doesn't change what any other module does —
 * it only controls how they're drawn.
 */
public final class FrestSettingsModule extends Module {

    /** #4a15a1 */
    public static final int DEFAULT_COLOR = 0xFF4A15A1;

    private static FrestSettingsModule instance;

    private final BoolSetting tint = add(new BoolSetting("tint", "Tint Addon Modules", true)
        .description("Colour this addon's module names so you can tell them apart from builtins.")
        .group("Colour"));

    private final ColorSetting color = add(new ColorSetting("color", "Module Colour", DEFAULT_COLOR)
        .description("Applied to this addon's module names while they're switched off.")
        .group("Colour")
        .visibleWhen(tint::get));

    private final IntSetting enabledDarken = add(new IntSetting("darken", "Darken When On (%)", 45, 0, 80, 5)
        .description("How much to darken the colour once a module is enabled.")
        .group("Colour")
        .visibleWhen(tint::get));

    private final BoolSetting showLabel = add(new BoolSetting("show-label", "Show Menu Label", true)
        .description("Draw a note in the module menu's top-left explaining what the purple means.")
        .group("Label"));

    public FrestSettingsModule() {
        super(FrestAddon.ID + ":settings", "Frest Addons", ModuleCategory.MISC,
            "Appearance settings for frest's autism addons.");
        instance = this;
    }

    // ------------------------------------------------------------ shared access

    /** True when this module id belongs to this addon. */
    public static boolean isOurs(String moduleId) {
        return moduleId != null && moduleId.startsWith(FrestAddon.ID + ":");
    }

    public static boolean tintEnabled() {
        return instance != null && instance.isEnabled() && instance.tint.get();
    }

    public static boolean labelEnabled() {
        return instance != null && instance.isEnabled() && instance.showLabel.get();
    }

    public static int baseColor() {
        return instance == null ? DEFAULT_COLOR : instance.color.get();
    }

    /** The same hue, darkened, for modules that are switched on. */
    public static int enabledColor() {
        int argb = baseColor();
        int percent = instance == null ? 45 : instance.enabledDarken.get();
        float keep = 1.0f - Math.max(0, Math.min(80, percent)) / 100.0f;

        int a = (argb >>> 24) & 0xFF;
        int r = Math.round(((argb >> 16) & 0xFF) * keep);
        int g = Math.round(((argb >> 8) & 0xFF) * keep);
        int b = Math.round((argb & 0xFF) * keep);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}

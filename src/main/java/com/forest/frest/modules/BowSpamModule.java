package com.forest.frest.modules;

import com.forest.frest.FrestAddon;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.IntSetting;
import autismclient.modules.Module;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Releases the bow the moment it reaches a chosen draw, then redraws.
 *
 * This handles release timing only — you still aim it yourself, and nothing
 * tracks or selects a target. The draw is genuine: arrows leave at whatever
 * power the draw time actually earned, so a short draw really does fire weak
 * arrows. There's no packet trickery to fake a full-power shot.
 */
public final class BowSpamModule extends Module {

    private final IntSetting drawTicks = add(new IntSetting("draw", "Draw Time (ticks)", 4, 1, 20, 1)
        .formatter(BowSpamModule::describeDraw)
        .description("Ticks to hold before releasing. Short draws fire fast but weak \u2014 20 is a full-power shot.")
        .group("Timing"));

    private final IntSetting between = add(new IntSetting("between", "Gap Between Shots (ticks)", 1, 0, 20, 1)
        .description("Pause after each shot before redrawing.")
        .group("Timing"));

    private final BoolSetting requireHold = add(new BoolSetting("require-hold", "Only While Holding Use", true)
        .description("Only fire while you're actually holding right-click. Off makes it fire continuously on its own.")
        .group("Behaviour"));

    private final BoolSetting requireArrows = add(new BoolSetting("require-arrows", "Stop Without Arrows", true)
        .description("Don't keep drawing an empty bow.")
        .group("Behaviour"));

    private final BoolSetting pauseInGui = add(new BoolSetting("pause-in-gui", "Pause In GUI", true)
        .group("Behaviour"));

    private int cooldown;

    public BowSpamModule() {
        super(FrestAddon.ID + ":bow-spam", "BowSpam", FrestAddon.CATEGORY,
            "Releases the bow at a set draw time and redraws.");
    }

    @Override
    public void onDisable() {
        cooldown = 0;
    }

    @Override
    public void tick() {
        LocalPlayer player = MC.player;
        if (player == null || MC.level == null || MC.gameMode == null) return;
        if (pauseInGui.get() && MC.gui.screen() != null) return;

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        InteractionHand hand = bowHand(player);
        if (hand == null) return;
        if (requireHold.get() && !MC.options.keyUse.isDown()) return;
        if (requireArrows.get() && !hasAmmo(player)) return;

        if (player.isUsingItem()) {
            // Drawing. Let it go once it's held long enough.
            if (player.getTicksUsingItem() >= drawTicks.get()) {
                MC.gameMode.releaseUsingItem(player);
                cooldown = between.get();
            }
            return;
        }

        // Not drawing yet, so start.
        MC.gameMode.useItem(player, hand);
    }

    /**
     * Setting.formatter is Function&lt;String, String&gt; — it receives the value
     * already stringified, so parse it back before asking vanilla for the power.
     */
    private static String describeDraw(String value) {
        try {
            int ticks = Integer.parseInt(value.trim());
            int percent = Math.round(BowItem.getPowerForTime(ticks) * 100.0f);
            return ticks + "t (" + percent + "% power)";
        } catch (NumberFormatException e) {
            return value;
        }
    }

    private static InteractionHand bowHand(LocalPlayer player) {
        if (player.getMainHandItem().getItem() instanceof BowItem) return InteractionHand.MAIN_HAND;
        if (player.getOffhandItem().getItem() instanceof BowItem) return InteractionHand.OFF_HAND;
        return null;
    }

    /**
     * Creative always has ammo; otherwise look for anything the bow will accept,
     * which covers tipped and spectral arrows as well as plain ones.
     */
    private static boolean hasAmmo(LocalPlayer player) {
        if (player.getAbilities().instabuild) return true;

        for (int slot = 0; slot < 41; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty()) continue;
            if (stack.is(Items.ARROW) || stack.is(Items.TIPPED_ARROW) || stack.is(Items.SPECTRAL_ARROW)) {
                return true;
            }
        }
        return false;
    }
}

package com.forest.frest.modules;

import com.forest.frest.FrestAddon;

import autismclient.api.module.BoolSetting;
import autismclient.modules.Module;

import net.minecraft.client.player.LocalPlayer;

/**
 * Keeps you sprinting whenever you're moving forward.
 *
 * Note: the base client already ships a "sprint" module wired through
 * ModuleMovementUtil with mixins, which handles omnidirectional sprint and
 * collision cases this one does not. This is the simple version.
 */
public final class AutoSprintModule extends Module {

    private final BoolSetting whenHungry = add(new BoolSetting("when-hungry", "Sprint Below 6 Hunger", false)
        .description("Vanilla blocks sprinting under 6 hunger. Forcing it on is a well-known desync flag.")
        .group("Conditions"));

    private final BoolSetting inWater = add(new BoolSetting("in-water", "Sprint In Water", false)
        .description("Also sprint while swimming.")
        .group("Conditions"));

    private final BoolSetting whileSneaking = add(new BoolSetting("while-sneaking", "Sprint While Sneaking", false)
        .description("Off by default — sneak-sprinting is not something a real player can do.")
        .group("Conditions"));

    private final BoolSetting requireForward = add(new BoolSetting("require-forward", "Require Forward Input", true)
        .description("Only sprint when actually pressing forward, rather than strafing.")
        .group("Conditions"));

    public AutoSprintModule() {
        super(FrestAddon.ID + ":auto-sprint", "AutoSprint", FrestAddon.CATEGORY,
            "Automatically sprints whenever you walk.");
    }

    @Override
    public void onDisable() {
        if (MC.player != null) MC.player.setSprinting(false);
    }

    @Override
    public void tick() {
        LocalPlayer player = MC.player;
        if (player == null || MC.level == null) return;
        if (MC.gui.screen() != null) return;

        if (!canSprint(player)) {
            return;
        }
        if (!player.isSprinting()) player.setSprinting(true);
    }

    private boolean canSprint(LocalPlayer player) {
        float forward = player.input.getMoveVector().y;
        if (requireForward.get() ? forward <= 0.0f : forward == 0.0f && player.input.getMoveVector().x == 0.0f) {
            return false;
        }
        if (!whileSneaking.get() && player.isShiftKeyDown()) return false;
        if (!inWater.get() && player.isInWater()) return false;
        if (!whenHungry.get() && player.getFoodData().getFoodLevel() <= 6) return false;
        if (player.isUsingItem()) return false;
        if (player.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS)) return false;
        return !player.isFallFlying();
    }
}

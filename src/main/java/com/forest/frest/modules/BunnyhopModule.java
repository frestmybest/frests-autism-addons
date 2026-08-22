package com.forest.frest.modules;

import com.forest.frest.FrestAddon;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.EnumSetting;
import autismclient.api.module.IntSetting;
import autismclient.modules.Module;

import net.minecraft.client.player.LocalPlayer;

/**
 * Jumps for you, on a condition you choose.
 */
public final class BunnyhopModule extends Module {

    public enum JumpIf {
        SPRINTING,
        WALKING,
        ALWAYS
    }

    private final EnumSetting<JumpIf> jumpIf = add(new EnumSetting<>("jump-if", "Jump If", JumpIf.SPRINTING, JumpIf.values())
        .description("When to jump: only while sprinting, whenever moving, or unconditionally.")
        .group("Conditions"));

    private final BoolSetting requireGround = add(new BoolSetting("require-ground", "Only On Ground", true)
        .description("Leave on. Jumping mid-air is not something vanilla lets you do.")
        .group("Conditions"));

    private final BoolSetting pauseInLiquid = add(new BoolSetting("pause-liquid", "Pause In Liquid", true)
        .description("Don't bob up and down in water or lava.")
        .group("Conditions"));

    private final BoolSetting pauseOnLadder = add(new BoolSetting("pause-ladder", "Pause On Ladder", true)
        .description("Don't jump off ladders and vines.")
        .group("Conditions"));

    private final BoolSetting pauseInGui = add(new BoolSetting("pause-in-gui", "Pause In GUI", true)
        .group("Conditions"));

    private final IntSetting delay = add(new IntSetting("delay", "Delay (ticks)", 0, 0, 20, 1)
        .description("Extra ticks to wait between jumps, on top of the landing.")
        .group("Behaviour"));

    private int cooldown;

    public BunnyhopModule() {
        super(FrestAddon.ID + ":bunnyhop", "Bunnyhop", FrestAddon.CATEGORY,
            "Automatically jumps based on the Jump If condition.");
    }

    @Override
    public void tick() {
        LocalPlayer player = MC.player;
        if (player == null || MC.level == null) return;
        if (pauseInGui.get() && MC.gui.screen() != null) return;

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        if (requireGround.get() && !player.onGround()) return;
        if (pauseInLiquid.get() && (player.isInWater() || player.isInLava())) return;
        if (pauseOnLadder.get() && player.onClimbable()) return;
        if (player.isPassenger() || player.isFallFlying()) return;
        if (player.isShiftKeyDown()) return;

        if (!conditionMet(player)) return;

        player.jumpFromGround();
        cooldown = delay.get();
    }

    private boolean conditionMet(LocalPlayer player) {
        return switch (jumpIf.get()) {
            case ALWAYS -> true;
            case SPRINTING -> player.isSprinting() && isMoving(player);
            case WALKING -> isMoving(player);
        };
    }

    private boolean isMoving(LocalPlayer player) {
        var move = player.input.getMoveVector();
        return move.lengthSquared() > 0.0f;
    }
}

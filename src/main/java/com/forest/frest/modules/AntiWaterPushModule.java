package com.forest.frest.modules;

import com.forest.frest.FrestAddon;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.IntSetting;
import autismclient.modules.Module;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Stops flowing water and lava from carrying you around.
 *
 * The first version mixed into Entity.isPushedByFluid, which does not exist in
 * this mapping set — the mixin silently no-opped and the module did nothing.
 * This version needs no mixin: it damps horizontal drift in preMovementTick(),
 * the same hook the client's own Flight module uses for velocity work, which
 * runs after vanilla has applied the current but before movement is committed.
 *
 * Vertical velocity is never touched, so bubble columns, sinking and swimming
 * up all behave normally. By default nothing happens while you are actively
 * swimming, so this resists being dragged without making you any faster —
 * it removes an effect rather than granting movement vanilla wouldn't allow.
 */
public final class AntiWaterPushModule extends Module {

    private final BoolSetting water = add(new BoolSetting("water", "Resist Water", true)
        .description("Ignore the current from flowing water.")
        .group("Fluids"));

    private final BoolSetting lava = add(new BoolSetting("lava", "Resist Lava", true)
        .description("Ignore the current from flowing lava.")
        .group("Fluids"));

    private final IntSetting strength = add(new IntSetting("strength", "Resistance", 90, 10, 100, 5)
        .description("How much horizontal drift to cancel each tick. 100 stops you dead.")
        .group("Fluids"));

    private final BoolSetting whileSwimming = add(new BoolSetting("while-swimming", "Resist While Swimming", false)
        .description("Also damp while you're holding a movement key. Off by default \u2014 on, it fights your own swimming.")
        .group("Fluids"));

    public AntiWaterPushModule() {
        super(FrestAddon.ID + ":anti-water-push", "AntiWaterPush", FrestAddon.CATEGORY,
            "Prevents flowing water and lava from pushing you.");
    }

    @Override
    public void preMovementTick() {
        LocalPlayer player = MC.player;
        if (player == null || MC.level == null) return;
        if (player.isSpectator() || player.getAbilities().flying) return;

        boolean inLava = player.isInLava();
        boolean inWater = player.isInWater();
        if (!inWater && !inLava) return;

        if (inLava ? !lava.get() : !water.get()) return;

        // Leave deliberate swimming alone unless asked otherwise.
        if (!whileSwimming.get() && hasMovementInput(player)) return;

        Vec3 velocity = player.getDeltaMovement();
        double keep = 1.0 - (strength.get() / 100.0);

        // Vertical is preserved: bubble columns and sinking must still work.
        player.setDeltaMovement(velocity.x * keep, velocity.y, velocity.z * keep);
    }

    private static boolean hasMovementInput(LocalPlayer player) {
        return player.input.getMoveVector().lengthSquared() > 0.0f
            || MC.options.keyJump.isDown();
    }
}

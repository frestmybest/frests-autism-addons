package com.forest.frest.modules;

import com.forest.frest.FrestAddon;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.IntSetting;
import autismclient.modules.Module;
import autismclient.util.AutismKeyMappingBridge;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Raises your shield when something is about to hit you.
 *
 * Defensive and self-directed: it holds up a shield you're already carrying.
 * It doesn't attack, aim, or track players — the trigger is an incoming
 * projectile's trajectory, or your own health dropping.
 */
public final class AutoShieldModule extends Module {

    private final BoolSetting onProjectile = add(new BoolSetting("on-projectile", "Block Incoming Projectiles", true)
        .description("Raise the shield when an arrow or similar is heading toward you.")
        .group("Triggers"));

    private final IntSetting projectileRange = add(new IntSetting("projectile-range", "Detection Range", 12, 2, 32, 1)
        .description("How far out to watch for projectiles.")
        .group("Triggers")
        .visibleWhen(onProjectile::get));

    private final BoolSetting onDamage = add(new BoolSetting("on-damage", "Block After Taking Damage", false)
        .description("Also raise it briefly whenever you get hit.")
        .group("Triggers"));

    private final IntSetting holdTicks = add(new IntSetting("hold", "Hold For (ticks)", 20, 5, 100, 5)
        .description("How long to keep it up once raised.")
        .group("Behaviour"));

    private final BoolSetting offhandOnly = add(new BoolSetting("offhand-only", "Offhand Only", true)
        .description("Only act when the shield is in your offhand, so it never steals your weapon hand.")
        .group("Behaviour"));

    private final BoolSetting releaseOnAttack = add(new BoolSetting("release-on-attack", "Drop Guard To Swing", true)
        .description("Lower it the moment you attack, so blocking never eats your hit.")
        .group("Behaviour"));

    private int holding;
    private float lastHealth = -1.0f;
    private boolean forcedUse;

    public AutoShieldModule() {
        super(FrestAddon.ID + ":auto-shield", "AutoShield", FrestAddon.CATEGORY,
            "Raises your shield against incoming projectiles.");
    }

    @Override
    public void onDisable() {
        release();
        holding = 0;
        lastHealth = -1.0f;
    }

    @Override
    public void tick() {
        LocalPlayer player = MC.player;
        if (player == null || MC.level == null || MC.gameMode == null) {
            release();
            return;
        }
        if (MC.gui.screen() != null) {
            release();
            return;
        }

        boolean hurt = tookDamage(player);

        if (holding > 0) {
            holding--;
            if (releaseOnAttack.get() && MC.options.keyAttack.isDown()) {
                release();
                holding = 0;
                return;
            }
            hold(player);
            return;
        }

        release();

        if (!hasShield(player)) return;
        if ((onDamage.get() && hurt) || (onProjectile.get() && projectileIncoming(player))) {
            holding = holdTicks.get();
            hold(player);
        }
    }

    private boolean tookDamage(LocalPlayer player) {
        float health = player.getHealth();
        boolean hurt = lastHealth >= 0.0f && health < lastHealth;
        lastHealth = health;
        return hurt;
    }

    private boolean hasShield(LocalPlayer player) {
        if (player.getOffhandItem().is(Items.SHIELD)) return true;
        return !offhandOnly.get() && player.getMainHandItem().is(Items.SHIELD);
    }

    /** An arrow whose velocity is carrying it toward us and which is still close. */
    private boolean projectileIncoming(LocalPlayer player) {
        double range = projectileRange.get();
        Vec3 self = player.position();

        for (Entity entity : MC.level.entitiesForRendering()) {
            if (!(entity instanceof AbstractArrow arrow)) continue;

            Vec3 toSelf = self.subtract(arrow.position());
            double distance = toSelf.length();
            if (distance > range || distance < 0.01) continue;

            Vec3 velocity = arrow.getDeltaMovement();
            if (velocity.lengthSqr() < 0.01) continue;

            // Positive dot product means it's travelling our way.
            if (velocity.normalize().dot(toSelf.normalize()) > 0.7) return true;
        }
        return false;
    }

    private void hold(LocalPlayer player) {
        ItemStack offhand = player.getOffhandItem();
        InteractionHand hand = offhand.is(Items.SHIELD) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;

        if (!forcedUse) {
            AutismKeyMappingBridge.of(MC.options.keyUse).autism$simulatePress(true);
            forcedUse = true;
        }
        if (!player.isUsingItem()) MC.gameMode.useItem(player, hand);
    }

    private void release() {
        if (!forcedUse) return;
        // Only drop it if the player isn't genuinely holding use themselves.
        if (!AutismKeyMappingBridge.of(MC.options.keyUse).autism$isActuallyDown()) {
            AutismKeyMappingBridge.of(MC.options.keyUse).autism$simulatePress(false);
        }
        forcedUse = false;
    }
}

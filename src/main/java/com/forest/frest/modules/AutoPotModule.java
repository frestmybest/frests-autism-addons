package com.forest.frest.modules;

import com.forest.frest.FrestAddon;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.IntSetting;
import autismclient.modules.Module;
import autismclient.util.AutismInventoryHelper;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;

import java.util.Optional;

/**
 * Throws a splash healing potion at your own feet when you get low.
 *
 * This is a combat module and it will win you fights — but it is entirely
 * self-directed: your potion, your hand, aimed at your own feet. It automates
 * a sequence you'd otherwise do by hand under pressure. Nothing targets
 * another player and nothing is spoofed.
 */
public final class AutoPotModule extends Module {

    private final IntSetting healthPercent = add(new IntSetting("health", "Throw Below Health (%)", 45, 5, 95, 5)
        .description("Health percentage that triggers a throw.")
        .group("Triggers"));

    private final BoolSetting onlyInCombat = add(new BoolSetting("only-combat", "Only After Taking Damage", true)
        .description("Requires a recent hit, so you don't burn potions while starving or falling.")
        .group("Triggers"));

    private final IntSetting combatWindow = add(new IntSetting("combat-window", "Damage Window (ticks)", 60, 10, 200, 10)
        .description("How recently you must have been hurt to count as in combat.")
        .group("Triggers")
        .visibleWhen(onlyInCombat::get));

    private final BoolSetting requireGround = add(new BoolSetting("require-ground", "Only On Ground", true)
        .description("A splash potion thrown mid-air often misses you entirely.")
        .group("Triggers"));

    private final BoolSetting lookDown = add(new BoolSetting("look-down", "Look Down To Throw", true)
        .description("Aims at your feet for the throw. Leave on \u2014 without it the potion sails off and heals nobody.")
        .group("Behaviour"));

    private final BoolSetting restoreAim = add(new BoolSetting("restore-aim", "Restore Aim", true)
        .description("Put your camera back where it was afterwards.")
        .group("Behaviour")
        .visibleWhen(lookDown::get));

    private final BoolSetting restoreSlot = add(new BoolSetting("restore-slot", "Restore Held Slot", true)
        .group("Behaviour"));

    private final IntSetting cooldown = add(new IntSetting("cooldown", "Cooldown (ticks)", 30, 5, 200, 5)
        .description("Gap between throws, so one bad moment doesn't empty your inventory.")
        .group("Behaviour"));

    private float savedYaw;
    private float savedPitch;
    private int deferredRestoreSlot = -1;
    private boolean restoreAimNext;
    private int wait;
    private float lastHealth = -1.0f;
    private int sinceDamage = 9999;

    public AutoPotModule() {
        super(FrestAddon.ID + ":auto-pot", "AutoPot", FrestAddon.CATEGORY,
            "Throws a splash healing potion at your feet when low.");
    }

    @Override
    public void onDisable() {
        deferredRestoreSlot = -1;
        restoreAimNext = false;
        lastHealth = -1.0f;
    }

    @Override
    public void tick() {
        LocalPlayer player = MC.player;
        if (player == null || MC.level == null || MC.gameMode == null) return;

        // Restore a tick later: the use packet must go out while still aimed down.
        if (restoreAimNext) {
            if (restoreAim.get()) {
                player.setYRot(savedYaw);
                player.setXRot(savedPitch);
            }
            restoreAimNext = false;
        }
        if (deferredRestoreSlot >= 0) {
            AutismInventoryHelper.selectHotbarSlot(MC, deferredRestoreSlot);
            deferredRestoreSlot = -1;
        }

        trackDamage(player);

        if (wait > 0) {
            wait--;
            return;
        }
        if (MC.gui.screen() != null) return;
        if (!player.isAlive() || player.isSpectator()) return;
        if (requireGround.get() && !player.onGround()) return;
        if (onlyInCombat.get() && sinceDamage > combatWindow.get()) return;

        float ratio = player.getHealth() / Math.max(1.0f, player.getMaxHealth());
        if (ratio * 100.0f > healthPercent.get()) return;

        int slot = findSplashHealing(player);
        if (slot < 0) return;

        throwPot(player, slot);
    }

    private void trackDamage(LocalPlayer player) {
        float health = player.getHealth();
        if (lastHealth >= 0.0f && health < lastHealth) sinceDamage = 0;
        else if (sinceDamage < 100000) sinceDamage++;
        lastHealth = health;
    }

    private void throwPot(LocalPlayer player, int slot) {
        int previous = player.getInventory().getSelectedSlot();
        if (previous != slot) {
            AutismInventoryHelper.selectHotbarSlot(MC, slot);
            if (restoreSlot.get()) deferredRestoreSlot = previous;
        }

        if (lookDown.get()) {
            savedYaw = player.getYRot();
            savedPitch = player.getXRot();
            player.setXRot(90.0f);
            restoreAimNext = true;
        }

        MC.gameMode.useItem(player, InteractionHand.MAIN_HAND);
        player.swing(InteractionHand.MAIN_HAND);
        wait = cooldown.get();
    }

    /** A splash potion whose contents include an instant-health effect. */
    private static int findSplashHealing(LocalPlayer player) {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty() || !stack.is(Items.SPLASH_POTION)) continue;

            PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
            if (contents == null) continue;

            // Registry path rather than the Potions constants: BuiltInRegistries
            // .getKey is used all over the client, Potions.HEALING is not.
            // "healing" also matches "strong_healing" in one test.
            Optional<Holder<Potion>> potion = contents.potion();
            if (potion.isEmpty()) continue;
            Identifier id = BuiltInRegistries.POTION.getKey(potion.get().value());
            if (id != null && id.getPath().contains("healing")) return slot;
        }
        return -1;
    }
}

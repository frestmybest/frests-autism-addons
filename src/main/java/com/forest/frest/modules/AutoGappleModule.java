package com.forest.frest.modules;

import com.forest.frest.FrestAddon;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.IntSetting;
import autismclient.modules.Module;
import autismclient.util.AutismInventoryHelper;
import autismclient.util.AutismKeyMappingBridge;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Eats a golden apple when you drop low, then hands your slot back.
 *
 * Self-directed: your apple, your hand, your health. Complements AutoEat,
 * which handles hunger — this one is about surviving a fight.
 */
public final class AutoGappleModule extends Module {

    private final IntSetting healthPercent = add(new IntSetting("health", "Eat Below Health (%)", 40, 5, 95, 5)
        .description("Health percentage that triggers a gapple.")
        .group("Triggers"));

    private final BoolSetting useEnchanted = add(new BoolSetting("use-enchanted", "Allow Enchanted Gapples", false)
        .description("Off by default \u2014 notch apples are usually worth saving for something worse.")
        .group("Triggers"));

    private final IntSetting emergencyPercent = add(new IntSetting("emergency", "Enchanted Below (%)", 15, 5, 50, 5)
        .description("Only reach for an enchanted apple this low.")
        .group("Triggers")
        .visibleWhen(useEnchanted::get));

    private final BoolSetting onlyInCombat = add(new BoolSetting("only-combat", "Only After Taking Damage", true)
        .description("Requires a recent hit, so you don't eat gapples while starving or falling.")
        .group("Triggers"));

    private final IntSetting combatWindow = add(new IntSetting("combat-window", "Damage Window (ticks)", 60, 10, 200, 10)
        .group("Triggers")
        .visibleWhen(onlyInCombat::get));

    private final BoolSetting restoreSlot = add(new BoolSetting("restore-slot", "Restore Held Slot", true)
        .group("Behaviour"));

    private final IntSetting cooldown = add(new IntSetting("cooldown", "Cooldown (ticks)", 40, 10, 200, 5)
        .group("Behaviour"));

    private int deferredRestore = -1;
    private int wait;
    private float lastHealth = -1.0f;
    private int sinceDamage = 9999;
    private boolean eating;

    public AutoGappleModule() {
        super(FrestAddon.ID + ":auto-gapple", "AutoGapple", FrestAddon.CATEGORY,
            "Eats a golden apple when your health drops.");
    }

    @Override
    public void onDisable() {
        releaseUse();
        deferredRestore = -1;
        lastHealth = -1.0f;
        eating = false;
    }

    @Override
    public void tick() {
        LocalPlayer player = MC.player;
        if (player == null || MC.level == null || MC.gameMode == null) return;

        trackDamage(player);

        if (eating) {
            // Keep the use key held until the apple actually goes down.
            if (!player.isUsingItem() || !isGapple(player.getMainHandItem())) {
                releaseUse();
                eating = false;
                wait = cooldown.get();
                if (deferredRestore >= 0) {
                    AutismInventoryHelper.selectHotbarSlot(MC, deferredRestore);
                    deferredRestore = -1;
                }
            }
            return;
        }

        if (wait > 0) {
            wait--;
            return;
        }
        if (MC.gui.screen() != null) return;
        if (!player.isAlive() || player.isSpectator()) return;
        if (player.isUsingItem()) return;
        if (onlyInCombat.get() && sinceDamage > combatWindow.get()) return;

        float ratio = player.getHealth() / Math.max(1.0f, player.getMaxHealth()) * 100.0f;
        if (ratio > healthPercent.get()) return;

        boolean emergency = useEnchanted.get() && ratio <= emergencyPercent.get();
        int slot = findGapple(player, emergency);
        if (slot < 0) return;

        int previous = player.getInventory().getSelectedSlot();
        if (previous != slot) {
            AutismInventoryHelper.selectHotbarSlot(MC, slot);
            if (restoreSlot.get()) deferredRestore = previous;
        }

        AutismKeyMappingBridge.of(MC.options.keyUse).autism$simulatePress(true);
        MC.gameMode.useItem(player, InteractionHand.MAIN_HAND);
        eating = true;
    }

    private void releaseUse() {
        if (MC.options == null) return;
        if (AutismKeyMappingBridge.of(MC.options.keyUse).autism$isActuallyDown()) return;
        AutismKeyMappingBridge.of(MC.options.keyUse).autism$simulatePress(false);
    }

    private void trackDamage(LocalPlayer player) {
        float health = player.getHealth();
        if (lastHealth >= 0.0f && health < lastHealth) sinceDamage = 0;
        else if (sinceDamage < 100000) sinceDamage++;
        lastHealth = health;
    }

    private int findGapple(LocalPlayer player, boolean allowEnchanted) {
        int enchanted = -1;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty()) continue;
            if (stack.is(Items.GOLDEN_APPLE)) return slot;
            if (stack.is(Items.ENCHANTED_GOLDEN_APPLE)) enchanted = slot;
        }
        return allowEnchanted ? enchanted : -1;
    }

    private static boolean isGapple(ItemStack stack) {
        return stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE);
    }
}

package com.forest.frest.modules;

import com.forest.frest.FrestAddon;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.EnumSetting;
import autismclient.api.module.IntSetting;
import autismclient.modules.Module;
import autismclient.util.AutismInventoryHelper;
import autismclient.util.AutismKeyMappingBridge;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Set;

/**
 * Automatically eats to keep hunger and saturation topped up.
 *
 * Uses only ordinary item-use actions: select a slot, hold use, release when done.
 * Nothing is spoofed and no packets are hand-built — the server sees the same
 * sequence it would see from a player right-clicking a steak.
 */
public final class AutoEatModule extends Module {

    /** Vanilla hunger regen requires food level >= 18. */
    private static final int REGEN_FOOD_LEVEL = 18;
    private static final int MAX_FOOD_LEVEL = 20;

    /** Foods that poison, starve, or teleport you. Never auto-eaten unless allowed below. */
    private static final Set<Item> HARMFUL = Set.of(
        Items.ROTTEN_FLESH,
        Items.SPIDER_EYE,
        Items.POISONOUS_POTATO,
        Items.PUFFERFISH,
        Items.CHORUS_FRUIT,
        Items.SUSPICIOUS_STEW,
        Items.CHICKEN,
        Items.BEEF,
        Items.PORKCHOP,
        Items.MUTTON,
        Items.RABBIT,
        Items.COD,
        Items.SALMON
    );

    /** Foods worth protecting from being burned on a routine top-up. */
    private static final Set<Item> PRECIOUS = Set.of(
        Items.GOLDEN_APPLE,
        Items.ENCHANTED_GOLDEN_APPLE,
        Items.GOLDEN_CARROT,
        Items.HONEY_BOTTLE
    );

    public enum Pick {
        /** Highest saturation — best for long trips. */
        SATURATION,
        /** Highest raw hunger restore. */
        NUTRITION,
        /** Least overflow past 20 hunger — stretches your food stack furthest. */
        EFFICIENT,
        /** Lowest value first — burns junk before the good stuff. */
        WORST_FIRST
    }

    // ---------------------------------------------------------------- triggers

    private final IntSetting hungerAt = add(new IntSetting("hunger-at", "Eat At Hunger", 16, 0, 19, 1)
        .description("Start eating when hunger drops to or below this many half-shanks.")
        .group("Triggers"));

    private final BoolSetting keepRegen = add(new BoolSetting("keep-regen", "Keep Regen Active", true)
        .description("Also eat below 18 hunger so natural health regen never stops.")
        .group("Triggers"));

    private final IntSetting satAt = add(new IntSetting("sat-at", "Eat At Saturation", 0, 0, 20, 1)
        .description("Also eat when saturation drops below this. 0 disables.")
        .group("Triggers"));

    private final IntSetting emergencyHealth = add(new IntSetting("emergency-health", "Emergency Health %", 40, 0, 100, 5)
        .description("Eat immediately when health falls below this percent, regardless of hunger. 0 disables.")
        .group("Triggers"));

    private final IntSetting stopAt = add(new IntSetting("stop-at", "Stop At Hunger", 20, 1, 20, 1)
        .description("Stop eating once hunger reaches this. Keeping it at 20 wastes the least food.")
        .group("Triggers"));

    // ----------------------------------------------------------- food selection

    private final EnumSetting<Pick> pick = add(new EnumSetting<>("pick", "Choose Food By", Pick.EFFICIENT, Pick.values())
        .description("How to rank the food in your inventory.")
        .group("Food"));

    private final BoolSetting avoidHarmful = add(new BoolSetting("avoid-harmful", "Avoid Harmful Food", true)
        .description("Never auto-eat raw meat, rotten flesh, spider eyes, pufferfish or chorus fruit.")
        .group("Food"));

    private final BoolSetting savePrecious = add(new BoolSetting("save-precious", "Save Gapples & Gold Carrots", true)
        .description("Skip golden apples and golden carrots during routine eating.")
        .group("Food"));

    private final BoolSetting allowPreciousEmergency = add(new BoolSetting("precious-emergency", "…Except In Emergency", true)
        .description("Allow them anyway when the emergency health trigger fires.")
        .group("Food")
        .visibleWhen(savePrecious::get));

    private final BoolSetting searchInventory = add(new BoolSetting("search-inventory", "Search Full Inventory", true)
        .description("Swap food up from the main inventory when the hotbar has none. Requires an inventory click.")
        .group("Food"));

    // --------------------------------------------------------------- behaviour

    private final BoolSetting pauseMining = add(new BoolSetting("pause-mining", "Pause While Mining", true)
        .description("Do not interrupt an in-progress block break.")
        .group("Behaviour"));

    private final BoolSetting pauseUsing = add(new BoolSetting("pause-using", "Pause While Using Item", true)
        .description("Do not interrupt drawing a bow, blocking, or placing blocks.")
        .group("Behaviour"));

    private final BoolSetting restoreSlot = add(new BoolSetting("restore-slot", "Restore Held Slot", true)
        .description("Switch back to whatever you were holding once the meal finishes.")
        .group("Behaviour"));

    private final IntSetting cooldown = add(new IntSetting("cooldown", "Cooldown (ms)", 250, 0, 3000, 50)
        .description("Minimum gap between meals.")
        .group("Behaviour"));

    // ------------------------------------------------------------------- state

    private int savedSlot = -1;
    private int swappedInventorySlot = -1;
    private int swappedHotbarSlot = -1;
    private boolean eating;
    private boolean forcedUseKey;
    private long lastMealEnded;

    public AutoEatModule() {
        super(FrestAddon.ID + ":auto-eat", "AutoEat", FrestAddon.CATEGORY,
            "Automatically eats to keep hunger and saturation topped up.");
    }

    @Override
    public void onDisable() {
        cancelMeal();
    }

    @Override
    public void tick() {
        LocalPlayer player = MC.player;
        if (player == null || MC.level == null || MC.gameMode == null) {
            cancelMeal();
            return;
        }
        if (player.isSpectator() || player.getAbilities().invulnerable) {
            cancelMeal();
            return;
        }

        // Mid-meal: hold the use key until the animation completes or the goal is met.
        if (eating) {
            continueMeal(player);
            return;
        }

        if (System.currentTimeMillis() - lastMealEnded < cooldown.get()) return;
        if (pauseMining.get() && MC.options.keyAttack.isDown()) return;
        if (pauseUsing.get() && (MC.options.keyUse.isDown() || player.isUsingItem())) return;

        boolean emergency = isEmergency(player);
        if (!emergency && !wantsFood(player)) return;

        int slot = findBestFood(player, emergency);
        if (slot < 0) return;

        beginMeal(player, slot);
    }

    // ---------------------------------------------------------------- triggers

    private boolean isEmergency(LocalPlayer player) {
        int pct = emergencyHealth.get();
        if (pct <= 0) return false;
        if (player.getFoodData().getFoodLevel() >= MAX_FOOD_LEVEL) return false;
        float ratio = player.getHealth() / Math.max(1.0f, player.getMaxHealth());
        return ratio * 100.0f <= pct;
    }

    private boolean wantsFood(LocalPlayer player) {
        FoodData data = player.getFoodData();
        int hunger = data.getFoodLevel();

        if (hunger <= hungerAt.get()) return true;
        if (keepRegen.get() && hunger < REGEN_FOOD_LEVEL) return true;

        int satFloor = satAt.get();
        return satFloor > 0 && data.getSaturationLevel() < satFloor && hunger < MAX_FOOD_LEVEL;
    }

    // ----------------------------------------------------------- food scanning

    /**
     * Returns a user-visible slot index (0-8 hotbar, 9-35 main inventory), or -1.
     * The hotbar is preferred so we can eat without touching the inventory at all.
     */
    private int findBestFood(LocalPlayer player, boolean emergency) {
        int best = -1;
        int bestScore = Integer.MIN_VALUE;
        int limit = searchInventory.get() ? 36 : 9;

        for (int slot = 0; slot < limit; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            FoodProperties props = foodOf(stack);
            if (props == null) continue;
            if (!isEdibleHere(stack, props, player, emergency)) continue;

            int score = score(props, player);
            // Tie-break toward the hotbar: no inventory click needed.
            if (slot < 9) score += 1;

            if (score > bestScore) {
                bestScore = score;
                best = slot;
            }
        }
        return best;
    }

    private FoodProperties foodOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        return stack.get(DataComponents.FOOD);
    }

    private boolean isEdibleHere(ItemStack stack, FoodProperties props, LocalPlayer player, boolean emergency) {
        Item item = stack.getItem();

        if (avoidHarmful.get() && HARMFUL.contains(item)) return false;

        if (savePrecious.get() && PRECIOUS.contains(item)) {
            if (!emergency || !allowPreciousEmergency.get()) return false;
        }

        // Vanilla refuses non-canAlwaysEat food at full hunger; don't bother trying.
        return props.canAlwaysEat() || player.getFoodData().getFoodLevel() < MAX_FOOD_LEVEL;
    }

    private int score(FoodProperties props, LocalPlayer player) {
        int nutrition = props.nutrition();
        float saturation = props.saturation();

        return switch (pick.get()) {
            case SATURATION -> Math.round(saturation * 100.0f);
            case NUTRITION -> nutrition * 100;
            case WORST_FIRST -> -(nutrition * 100 + Math.round(saturation * 10.0f));
            case EFFICIENT -> {
                int missing = MAX_FOOD_LEVEL - player.getFoodData().getFoodLevel();
                int wasted = Math.max(0, nutrition - missing);
                // Fill the gap without overflow; break ties on saturation.
                yield (nutrition - wasted * 2) * 100 + Math.round(saturation * 10.0f);
            }
        };
    }

    // ------------------------------------------------------------- meal driver

    private void beginMeal(LocalPlayer player, int slot) {
        savedSlot = player.getInventory().getSelectedSlot();

        if (slot >= 9) {
            // Park it in the currently held hotbar slot, remember it so we can undo the swap.
            int target = savedSlot;
            if (!AutismInventoryHelper.swapInventoryWithHotbar(MC, slot, target)) return;
            swappedInventorySlot = slot;
            swappedHotbarSlot = target;
        } else {
            AutismInventoryHelper.selectHotbarSlot(MC, slot);
        }

        eating = true;
        holdUseKey(true);
        MC.gameMode.useItem(player, InteractionHand.MAIN_HAND);
    }

    private void continueMeal(LocalPlayer player) {
        ItemStack held = player.getMainHandItem();
        FoodProperties props = foodOf(held);

        // Ran out mid-bite, or something swapped the stack out from under us.
        if (props == null) {
            finishMeal();
            return;
        }

        if (player.getFoodData().getFoodLevel() >= stopAt.get() && !isEmergency(player)) {
            finishMeal();
            return;
        }

        // Player took over — yield rather than fight them for the hand.
        if (pauseMining.get() && MC.options.keyAttack.isDown()) {
            finishMeal();
            return;
        }

        holdUseKey(true);
        if (!player.isUsingItem()) {
            MC.gameMode.useItem(player, InteractionHand.MAIN_HAND);
        }
    }

    private void finishMeal() {
        LocalPlayer player = MC.player;
        holdUseKey(false);
        if (player != null && MC.gameMode != null) {
            MC.gameMode.releaseUsingItem(player);
        }
        undoSwap();
        if (restoreSlot.get() && savedSlot >= 0 && savedSlot < 9) {
            AutismInventoryHelper.selectHotbarSlot(MC, savedSlot);
        }
        savedSlot = -1;
        eating = false;
        lastMealEnded = System.currentTimeMillis();
    }

    private void cancelMeal() {
        if (!eating && savedSlot < 0 && swappedInventorySlot < 0) return;
        finishMeal();
    }

    private void undoSwap() {
        if (swappedInventorySlot < 0 || swappedHotbarSlot < 0) return;
        AutismInventoryHelper.restoreInventoryHotbarSwap(MC, swappedInventorySlot, swappedHotbarSlot);
        swappedInventorySlot = -1;
        swappedHotbarSlot = -1;
    }

    /**
     * Eating is driven by the use key, not by repeated useItem calls — vanilla
     * releases the active item on any tick where the key is up.
     */
    private void holdUseKey(boolean down) {
        if (down == forcedUseKey) return;

        // autism$isActuallyDown() reports the real key state, ignoring anything
        // simulated. Checking keyUse.isDown() here was a bug: after we forced it
        // down it always read true, so release never fired and the key stuck.
        if (!down && AutismKeyMappingBridge.of(MC.options.keyUse).autism$isActuallyDown()) {
            forcedUseKey = false;
            return;
        }
        AutismKeyMappingBridge.of(MC.options.keyUse).autism$simulatePress(down);
        forcedUseKey = down;
    }
}

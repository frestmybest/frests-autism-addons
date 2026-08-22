package com.forest.frest.modules;

import com.forest.frest.FrestAddon;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.IntSetting;
import autismclient.modules.Module;
import autismclient.util.AutismInventoryHelper;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Tops up running-low hotbar stacks from the main inventory.
 *
 * Pairs with AutoFarm (seeds), AutoMine (torches, blocks) and AutoEat (food),
 * all of which otherwise just stop being useful once the hotbar stack empties.
 * Uses ordinary inventory swaps, the same clicks you'd make by hand.
 */
public final class AutoRefillModule extends Module {

    private static final int HOTBAR_END = 9;
    private static final int INVENTORY_END = 36;

    private final IntSetting threshold = add(new IntSetting("threshold", "Refill Below", 8, 1, 64, 1)
        .description("Refill a hotbar stack once it drops to this many items.")
        .group("Triggers"));

    private final BoolSetting refillEmpty = add(new BoolSetting("refill-empty", "Refill Emptied Slots", true)
        .description("Also refill a hotbar slot that ran out completely, if a matching stack remains.")
        .group("Triggers"));

    private final BoolSetting onlyHeld = add(new BoolSetting("only-held", "Held Slot Only", false)
        .description("Only maintain the slot you're actually holding.")
        .group("Triggers"));

    private final BoolSetting skipTools = add(new BoolSetting("skip-tools", "Skip Tools & Armor", true)
        .description("Never try to stack durability items.")
        .group("Behaviour"));

    private final IntSetting delay = add(new IntSetting("delay", "Delay (ticks)", 5, 1, 40, 1)
        .description("Ticks between refills. Inventory clicks are rate-limited server-side, so don't go too low.")
        .group("Behaviour"));

    private final BoolSetting pauseInGui = add(new BoolSetting("pause-in-gui", "Pause In GUI", true)
        .description("Leave on. Swapping while a chest is open targets that container instead.")
        .group("Behaviour"));

    /** Remembers what each hotbar slot held, so an emptied slot can be refilled. */
    private final ItemStack[] lastSeen = new ItemStack[HOTBAR_END];

    private int cooldown;

    public AutoRefillModule() {
        super(FrestAddon.ID + ":auto-refill", "AutoRefill", FrestAddon.CATEGORY,
            "Refills running-low hotbar stacks from your inventory.");
    }

    @Override
    public void onEnable() {
        java.util.Arrays.fill(lastSeen, null);
    }

    @Override
    public void tick() {
        LocalPlayer player = MC.player;
        if (player == null || MC.level == null) return;
        if (pauseInGui.get() && MC.gui.screen() != null) return;

        rememberHotbar(player);

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        int target = findSlotNeedingRefill(player);
        if (target < 0) return;

        ItemStack want = wantedFor(player, target);
        if (want == null) return;

        int source = findSourceStack(player, want, target);
        if (source < 0) return;

        if (AutismInventoryHelper.swapInventoryWithHotbar(MC, source, target)) {
            cooldown = delay.get();
        }
    }

    private void rememberHotbar(LocalPlayer player) {
        for (int slot = 0; slot < HOTBAR_END; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty()) lastSeen[slot] = stack.copy();
        }
    }

    private int findSlotNeedingRefill(LocalPlayer player) {
        int held = player.getInventory().getSelectedSlot();

        if (onlyHeld.get()) {
            return needsRefill(player, held) ? held : -1;
        }
        // Held slot first: it's the one actively being drained.
        if (needsRefill(player, held)) return held;

        for (int slot = 0; slot < HOTBAR_END; slot++) {
            if (needsRefill(player, slot)) return slot;
        }
        return -1;
    }

    private boolean needsRefill(LocalPlayer player, int slot) {
        if (slot < 0 || slot >= HOTBAR_END) return false;
        ItemStack stack = player.getInventory().getItem(slot);

        if (stack.isEmpty()) {
            return refillEmpty.get() && lastSeen[slot] != null;
        }
        if (skipTools.get() && stack.isDamageableItem()) return false;
        if (stack.getMaxStackSize() <= 1) return false;

        return stack.getCount() <= threshold.get();
    }

    /** The item a slot should be topped up with: what's there, or what used to be. */
    private ItemStack wantedFor(LocalPlayer player, int slot) {
        ItemStack current = player.getInventory().getItem(slot);
        if (!current.isEmpty()) return current;
        return lastSeen[slot];
    }

    private int findSourceStack(LocalPlayer player, ItemStack want, int excludeHotbarSlot) {
        int best = -1;
        int bestCount = Integer.MAX_VALUE;

        for (int slot = HOTBAR_END; slot < INVENTORY_END; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty()) continue;
            if (!ItemStack.isSameItemSameComponents(stack, want)) continue;

            // Prefer the smallest matching stack, so full stacks stay intact.
            if (stack.getCount() < bestCount) {
                bestCount = stack.getCount();
                best = slot;
            }
        }
        return best;
    }
}

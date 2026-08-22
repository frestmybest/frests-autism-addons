package com.forest.frest.modules;

import com.forest.frest.FrestAddon;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.IntSetting;
import autismclient.modules.Module;
import autismclient.util.AutismInventoryClickHelper;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Keeps an open furnace loaded and empties its output.
 *
 * Furnace-family menus put input at slot 0, fuel at 1 and output at 2, with the
 * player's inventory after. Everything here is QUICK_MOVE clicks on a container
 * you opened yourself.
 */
public final class AutoSmeltModule extends Module {

    private static final int INPUT = 0;
    private static final int FUEL = 1;
    private static final int OUTPUT = 2;
    private static final int FURNACE_SLOTS = 3;

    private final BoolSetting takeOutput = add(new BoolSetting("take-output", "Collect Output", true)
        .description("Pull finished items out into your inventory.")
        .group("Actions"));

    private final BoolSetting refuel = add(new BoolSetting("refuel", "Add Fuel", true)
        .description("Top up the fuel slot from your inventory.")
        .group("Actions"));

    private final BoolSetting reload = add(new BoolSetting("reload", "Add Input", true)
        .description("Shift-click smeltable items in when the input slot empties.")
        .group("Actions"));

    private final IntSetting delay = add(new IntSetting("delay", "Delay (ticks)", 4, 1, 40, 1)
        .description("Ticks between clicks.")
        .group("Behaviour"));

    private int cooldown;

    public AutoSmeltModule() {
        super(FrestAddon.ID + ":auto-smelt", "AutoSmelt", FrestAddon.CATEGORY,
            "Keeps an open furnace fed and collects its output.");
    }

    @Override
    public void tick() {
        LocalPlayer player = MC.player;
        if (player == null || MC.gameMode == null) return;

        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null || menu == player.inventoryMenu) return;
        if (!looksLikeFurnace(menu)) return;

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        if (takeOutput.get() && menu.slots.get(OUTPUT).hasItem()) {
            AutismInventoryClickHelper.click(MC, OUTPUT, 0, ContainerInput.QUICK_MOVE);
            cooldown = delay.get();
            return;
        }

        if (refuel.get() && !menu.slots.get(FUEL).hasItem()) {
            int slot = findInPlayerHalf(menu, true);
            if (slot >= 0) {
                AutismInventoryClickHelper.click(MC, slot, 0, ContainerInput.QUICK_MOVE);
                cooldown = delay.get();
                return;
            }
        }

        if (reload.get() && !menu.slots.get(INPUT).hasItem()) {
            int slot = findInPlayerHalf(menu, false);
            if (slot >= 0) {
                AutismInventoryClickHelper.click(MC, slot, 0, ContainerInput.QUICK_MOVE);
                cooldown = delay.get();
            }
        }
    }

    /** Furnace, blast furnace and smoker all expose exactly three container slots. */
    private static boolean looksLikeFurnace(AbstractContainerMenu menu) {
        return menu.slots.size() == FURNACE_SLOTS + 36;
    }

    private int findInPlayerHalf(AbstractContainerMenu menu, boolean wantFuel) {
        for (int i = FURNACE_SLOTS; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (!slot.hasItem()) continue;
            ItemStack stack = slot.getItem();

            if (wantFuel ? isFuel(stack) : !isFuel(stack)) return i;
        }
        return -1;
    }

    /**
     * A deliberately conservative fuel list. Guessing wrong here shift-clicks
     * something valuable into a furnace, so only obvious fuels count.
     */
    private static boolean isFuel(ItemStack stack) {
        return stack.is(Items.COAL) || stack.is(Items.CHARCOAL)
            || stack.is(Items.COAL_BLOCK) || stack.is(Items.LAVA_BUCKET)
            || stack.is(Items.BLAZE_ROD) || stack.is(Items.DRIED_KELP_BLOCK);
    }
}

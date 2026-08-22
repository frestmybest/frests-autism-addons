package com.forest.frest.modules;

import com.forest.frest.FrestAddon;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.ChoiceSetting;
import autismclient.api.module.IntSetting;
import autismclient.api.module.RegistryListSetting;
import autismclient.modules.Module;
import autismclient.modules.ModuleCategory;
import autismclient.util.AutismInventoryClickHelper;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Empties or fills a container you already have open.
 *
 * Pure shift-clicking through QUICK_MOVE — the same click the vanilla UI sends.
 * It never opens anything, so it only ever touches a container you walked up to
 * and opened yourself.
 */
public final class ChestStealerModule extends Module {

    private final ChoiceSetting direction = add(new ChoiceSetting("direction", "Direction", "Take", "Take", "Deposit")
        .description("Take empties the container into you. Deposit puts your items in.")
        .group("Behaviour")
        .build());

    private final RegistryListSetting filter = add(RegistryListSetting
        .items("filter", "Item Filter", "")
        .description("Leave empty to move everything. Otherwise only these items.")
        .group("Filter")
        .build());

    private final ChoiceSetting filterMode = add(new ChoiceSetting("filter-mode", "Filter Mode", "WhiteList", "WhiteList", "BlackList")
        .group("Filter")
        .build());

    private final BoolSetting skipDamaged = add(new BoolSetting("skip-damaged", "Skip Tools & Armor", false)
        .description("Leave anything with a durability bar alone.")
        .group("Filter"));

    private final IntSetting delay = add(new IntSetting("delay", "Delay (ticks)", 2, 0, 20, 1)
        .description("Ticks between clicks. Too fast and the server will drop some.")
        .group("Behaviour"));

    private final BoolSetting closeWhenDone = add(new BoolSetting("close-when-done", "Close When Finished", false)
        .description("Shut the screen once there's nothing left to move.")
        .group("Behaviour"));

    private int cooldown;

    public ChestStealerModule() {
        super(FrestAddon.ID + ":chest-stealer", "ChestStealer", ModuleCategory.PLAYER,
            "Empties or fills an open container with shift-clicks.");
    }

    @Override
    public void tick() {
        LocalPlayer player = MC.player;
        if (player == null || MC.gameMode == null) return;

        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null || menu == player.inventoryMenu) return;

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        int slot = findSlot(player, menu);
        if (slot < 0) {
            if (closeWhenDone.get()) player.closeContainer();
            return;
        }

        AutismInventoryClickHelper.click(MC, slot, 0, ContainerInput.QUICK_MOVE);
        cooldown = delay.get();
    }

    /**
     * Container slots come first in the menu, the player's own inventory last.
     * Taking scans the container half; depositing scans the player half.
     */
    private int findSlot(LocalPlayer player, AbstractContainerMenu menu) {
        int total = menu.slots.size();
        int playerSlots = 36;
        int containerEnd = Math.max(0, total - playerSlots);

        boolean taking = "Take".equals(direction.get());
        int from = taking ? 0 : containerEnd;
        int to = taking ? containerEnd : total;

        for (int i = from; i < to; i++) {
            Slot slot = menu.slots.get(i);
            if (!slot.hasItem()) continue;
            if (shouldMove(slot.getItem())) return i;
        }
        return -1;
    }

    private boolean shouldMove(ItemStack stack) {
        if (skipDamaged.get() && stack.isDamageableItem()) return false;

        List<String> list = filter.get();
        if (list.isEmpty()) return true;

        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) return false;
        boolean listed = list.contains(id.toString());

        return "BlackList".equals(filterMode.get()) ? !listed : listed;
    }
}

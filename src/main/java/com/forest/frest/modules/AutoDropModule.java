package com.forest.frest.modules;

import com.forest.frest.FrestAddon;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.ChoiceSetting;
import autismclient.api.module.IntSetting;
import autismclient.api.module.RegistryListSetting;
import autismclient.modules.Module;
import autismclient.modules.ModuleCategory;
import autismclient.util.AutismDropHelper;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Throws away configured junk so mining and farming don't stall on a full pack.
 *
 * Uses the client's own drop helper, which issues the same DROP_ITEM action a
 * manual Q press produces.
 */
public final class AutoDropModule extends Module {

    /** Slots 9-35 are the main inventory; 0-8 the hotbar. */
    private static final int INVENTORY_END = 36;

    private final RegistryListSetting items = add(RegistryListSetting
        .items("items", "Items", "minecraft:cobblestone|minecraft:dirt|minecraft:rotten_flesh|minecraft:gravel")
        .description("Items to throw away. Pick from the list in this menu.")
        .group("Items")
        .build());

    private final ChoiceSetting mode = add(new ChoiceSetting("mode", "List Mode", "WhiteList", "WhiteList", "BlackList")
        .description("WhiteList drops only what's listed. BlackList drops everything except what's listed \u2014 be careful.")
        .group("Items")
        .build());

    private final ChoiceSetting when = add(new ChoiceSetting("when", "Drop When", "Inventory Full", "Always", "Inventory Full")
        .description("Always drops on sight. Inventory Full waits until you're nearly out of space.")
        .group("Behaviour")
        .build());

    private final IntSetting freeSlots = add(new IntSetting("free-slots", "Full Below (free slots)", 3, 0, 20, 1)
        .description("How few empty slots counts as full.")
        .group("Behaviour"));

    private final BoolSetting skipHotbar = add(new BoolSetting("skip-hotbar", "Skip Hotbar", true)
        .description("Never drop from the hotbar, so your tools stay put.")
        .group("Behaviour"));

    private final BoolSetting skipDamaged = add(new BoolSetting("skip-damaged", "Skip Tools & Armor", true)
        .description("Never drop anything with a durability bar, whatever the list says.")
        .group("Behaviour"));

    private final BoolSetting skipNamed = add(new BoolSetting("skip-named", "Skip Renamed Items", true)
        .description("Never drop items with a custom name \u2014 usually a sign they matter.")
        .group("Behaviour"));

    private final IntSetting delay = add(new IntSetting("delay", "Delay (ticks)", 4, 0, 40, 1)
        .description("Ticks between drops.")
        .group("Behaviour"));

    private final BoolSetting pauseInGui = add(new BoolSetting("pause-in-gui", "Pause In GUI", true)
        .group("Behaviour"));

    private int cooldown;

    public AutoDropModule() {
        super(FrestAddon.ID + ":auto-drop", "AutoDrop", ModuleCategory.PLAYER,
            "Throws away configured junk items.");
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

        if (isFullTrigger() && countFreeSlots(player) > freeSlots.get()) return;

        int slot = findDroppable(player);
        if (slot < 0) return;

        // count 0 means the whole stack, matching the client's DROP_STACK path.
        AutismDropHelper.dropFromInventorySlot(MC, slot, 0);
        cooldown = delay.get();
    }

    private boolean isFullTrigger() {
        return "Inventory Full".equals(when.get());
    }

    private int countFreeSlots(LocalPlayer player) {
        int free = 0;
        for (int slot = 0; slot < INVENTORY_END; slot++) {
            if (player.getInventory().getItem(slot).isEmpty()) free++;
        }
        return free;
    }

    private int findDroppable(LocalPlayer player) {
        int start = skipHotbar.get() ? 9 : 0;
        for (int slot = start; slot < INVENTORY_END; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty()) continue;
            if (shouldDrop(stack)) return slot;
        }
        return -1;
    }

    private boolean shouldDrop(ItemStack stack) {
        if (skipDamaged.get() && stack.isDamageableItem()) return false;
        if (skipNamed.get() && stack.get(DataComponents.CUSTOM_NAME) != null) return false;

        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) return false;

        List<String> list = items.get();
        boolean listed = list.contains(id.toString());

        return "BlackList".equals(mode.get()) ? !listed : listed;
    }
}

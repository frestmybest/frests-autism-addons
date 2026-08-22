package com.forest.frest.modules;

import com.forest.frest.FrestAddon;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.KeybindSetting;
import autismclient.modules.Module;
import autismclient.util.AutismInventoryClickHelper;
import autismclient.util.AutismInventoryHelper;
import autismclient.util.AutismNotifications;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Hotswaps elytra and chestplate on a keybind.
 *
 * Same shape as the client's builtin AutoTotem: a swap click between your chest
 * slot and an inventory slot you already own. Nothing is spoofed and it does
 * nothing you couldn't do by opening your inventory.
 */
public final class ElytraSwapModule extends Module {

    /** Chest armour is menu slot 6 in the player inventory, as AutoArmorModule maps it. */
    private static final int CHEST_MENU_SLOT = 6;

    private final KeybindSetting key = add(new KeybindSetting("key", "Swap Key", -1)
        .description("Press to swap between elytra and chestplate.")
        .group("Binding"));

    private final BoolSetting autoElytraOnFall = add(new BoolSetting("auto-fall", "Auto Elytra When Falling", false)
        .description("Swap to elytra automatically past the fall distance below.")
        .group("Automatic"));

    private final BoolSetting autoChestOnGround = add(new BoolSetting("auto-ground", "Auto Chestplate On Landing", false)
        .description("Swap back once you're on the ground again.")
        .group("Automatic"));

    private final BoolSetting notify = add(new BoolSetting("notify", "Notify On Swap", true)
        .group("Behaviour"));

    private boolean keyWasDown;
    private int cooldown;

    public ElytraSwapModule() {
        super(FrestAddon.ID + ":elytra-swap", "ElytraSwap", FrestAddon.CATEGORY,
            "Swaps between elytra and chestplate.");
    }

    @Override
    public void tick() {
        LocalPlayer player = MC.player;
        if (player == null || MC.level == null) return;
        if (MC.gui.screen() != null) return;

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        boolean down = key.get() >= 0 && isKeyDown();
        if (down && !keyWasDown) {
            swap(player);
        }
        keyWasDown = down;

        if (autoElytraOnFall.get() && !wearingElytra(player)
            && player.fallDistance > 3.0F && !player.onGround()) {
            swap(player);
        } else if (autoChestOnGround.get() && wearingElytra(player)
            && player.onGround() && !player.isFallFlying()) {
            swap(player);
        }
    }

    private boolean isKeyDown() {
        long window = MC.getWindow().handle();
        return org.lwjgl.glfw.GLFW.glfwGetKey(window, key.get()) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
    }

    private static boolean wearingElytra(LocalPlayer player) {
        return player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA);
    }

    private void swap(LocalPlayer player) {
        boolean wantChestplate = wearingElytra(player);
        int source = findReplacement(player, wantChestplate);
        if (source < 0) {
            if (notify.get()) {
                AutismNotifications.warning(wantChestplate
                    ? "ElytraSwap: no chestplate in inventory"
                    : "ElytraSwap: no elytra in inventory");
            }
            cooldown = 20;
            return;
        }

        if (source < 9) {
            // Hotbar source: one SWAP click, button = the hotbar index.
            AutismInventoryClickHelper.click(MC, CHEST_MENU_SLOT, source, ContainerInput.SWAP);
        } else {
            // Main inventory: pick up the new piece, drop it into the chest slot
            // (which puts the worn one on the cursor), then park that in the
            // now-empty source slot. Three clicks, in this order.
            int sourceMenuSlot = AutismInventoryHelper.toHandlerSlot(MC, source);
            if (sourceMenuSlot < 0) return;
            AutismInventoryClickHelper.click(MC, sourceMenuSlot, 0, ContainerInput.PICKUP);
            AutismInventoryClickHelper.click(MC, CHEST_MENU_SLOT, 0, ContainerInput.PICKUP);
            AutismInventoryClickHelper.click(MC, sourceMenuSlot, 0, ContainerInput.PICKUP);
        }

        if (notify.get()) {
            AutismNotifications.success(wantChestplate ? "Chestplate equipped" : "Elytra equipped");
        }
        cooldown = 10;
    }

    private static int findReplacement(LocalPlayer player, boolean wantChestplate) {
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty()) continue;

            if (wantChestplate) {
                if (isChestplate(stack)) return slot;
            } else if (stack.is(Items.ELYTRA)) {
                return slot;
            }
        }
        return -1;
    }

    private static boolean isChestplate(ItemStack stack) {
        return stack.is(Items.NETHERITE_CHESTPLATE) || stack.is(Items.DIAMOND_CHESTPLATE)
            || stack.is(Items.IRON_CHESTPLATE) || stack.is(Items.GOLDEN_CHESTPLATE)
            || stack.is(Items.CHAINMAIL_CHESTPLATE) || stack.is(Items.LEATHER_CHESTPLATE);
    }
}

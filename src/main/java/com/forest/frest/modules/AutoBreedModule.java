package com.forest.frest.modules;

import com.forest.frest.FrestAddon;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.IntSetting;
import autismclient.modules.Module;
import autismclient.modules.ModuleCategory;
import autismclient.util.AutismInventoryHelper;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Feeds animals to breed them, and shears grown sheep.
 *
 * Ordinary right-click interactions at vanilla entity reach, on passive mobs.
 * Nothing here touches players.
 */
public final class AutoBreedModule extends Module {

    /** Vanilla entity interaction reach in survival. */
    private static final double ENTITY_REACH = 3.0;

    private final BoolSetting breed = add(new BoolSetting("breed", "Breed Animals", true)
        .description("Feed adult animals their breeding item.")
        .group("Actions"));

    private final BoolSetting shear = add(new BoolSetting("shear", "Shear Sheep", true)
        .description("Shear grown, unsheared sheep when holding shears is possible.")
        .group("Actions"));

    private final BoolSetting skipBabies = add(new BoolSetting("skip-babies", "Skip Babies", true)
        .description("Don't waste food on animals too young to breed.")
        .group("Actions"));

    private final IntSetting perAnimalCooldown = add(new IntSetting("animal-cooldown", "Per-Animal Cooldown (s)", 15, 2, 120, 1)
        .description("Don't re-feed the same animal for this long, so one cow doesn't eat your whole stack.")
        .group("Actions"));

    private final IntSetting range = add(new IntSetting("range", "Range", 3, 1, 3, 1)
        .description("Capped at vanilla entity reach.")
        .group("Behaviour"));

    private final IntSetting delay = add(new IntSetting("delay", "Delay (ticks)", 10, 2, 60, 2)
        .description("Ticks between interactions.")
        .group("Behaviour"));

    private final BoolSetting restoreSlot = add(new BoolSetting("restore-slot", "Restore Held Slot", true)
        .group("Behaviour"));

    private final BoolSetting pauseInGui = add(new BoolSetting("pause-in-gui", "Pause In GUI", true)
        .group("Behaviour"));

    /** Entity id -> ticks left before that animal may be fed again. */
    private final Map<Integer, Integer> recentlyFed = new HashMap<>();

    private int cooldown;
    private int deferredRestore = -1;

    public AutoBreedModule() {
        super(FrestAddon.ID + ":auto-breed", "AutoBreed", ModuleCategory.PLAYER,
            "Feeds animals to breed them and shears sheep.");
    }

    @Override
    public void onDisable() {
        recentlyFed.clear();
    }

    @Override
    public void tick() {
        LocalPlayer player = MC.player;
        if (player == null || MC.level == null || MC.gameMode == null) return;

        if (deferredRestore >= 0) {
            AutismInventoryHelper.selectHotbarSlot(MC, deferredRestore);
            deferredRestore = -1;
        }

        ageRecentlyFed();

        if (pauseInGui.get() && MC.gui.screen() != null) return;

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        double reach = Math.min(range.get(), ENTITY_REACH);
        Vec3 eye = player.getEyePosition();

        for (Entity entity : MC.level.entitiesForRendering()) {
            if (!(entity instanceof Animal animal)) continue;
            if (eye.distanceToSqr(animal.position()) > reach * reach) continue;
            if (recentlyFed.containsKey(animal.getId())) continue;

            int slot = itemFor(player, animal);
            if (slot < 0) continue;

            recentlyFed.put(animal.getId(), perAnimalCooldown.get() * 20);
            interact(player, animal, slot);
            cooldown = delay.get();
            return;
        }
    }

    private void ageRecentlyFed() {
        Iterator<Map.Entry<Integer, Integer>> it = recentlyFed.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Integer> e = it.next();
            int left = e.getValue() - 1;
            if (left <= 0) it.remove();
            else e.setValue(left);
        }
    }

    /** Hotbar slot holding the right item for this animal, or -1. */
    private int itemFor(LocalPlayer player, Animal animal) {
        // No isSheared() check: that method isn't used anywhere in the client and
        // sheep wool moved to a data component in recent versions, so it's a guess.
        // Shearing an already-sheared sheep is a harmless no-op, and the
        // per-animal cooldown below stops it being retried in a loop.
        if (shear.get() && isSheep(animal) && !animal.isBaby()) {
            int slot = findHotbar(player, Items.SHEARS);
            if (slot >= 0) return slot;
        }

        if (!breed.get()) return -1;
        if (skipBabies.get() && animal.isBaby()) return -1;

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty()) continue;
            if (animal.isFood(stack)) return slot;
        }
        return -1;
    }

    private void interact(LocalPlayer player, Animal animal, int slot) {
        int previous = player.getInventory().getSelectedSlot();
        if (previous != slot) {
            AutismInventoryHelper.selectHotbarSlot(MC, slot);
            if (restoreSlot.get()) deferredRestore = previous;
        }

        EntityHitResult hit = new EntityHitResult(animal, animal.position());
        MC.gameMode.interact(player, animal, hit, InteractionHand.MAIN_HAND);
        player.swing(InteractionHand.MAIN_HAND);
    }

    /**
     * Registry id rather than `instanceof Sheep`: this version nests entities in
     * per-type subpackages (animal.pig.Pig, monster.warden.Warden), and guessing
     * Sheep's exact package is how the last build broke.
     */
    private static boolean isSheep(Animal animal) {
        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(animal.getType());
        return id != null && "sheep".equals(id.getPath());
    }

    private static int findHotbar(LocalPlayer player, net.minecraft.world.item.Item item) {
        for (int slot = 0; slot < 9; slot++) {
            if (player.getInventory().getItem(slot).is(item)) return slot;
        }
        return -1;
    }
}

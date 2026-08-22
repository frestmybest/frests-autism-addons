package com.forest.frest.modules;

import com.forest.frest.FrestAddon;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.IntSetting;
import autismclient.modules.Module;
import autismclient.util.AutismInventoryHelper;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Set;

/**
 * Drinks milk to clear a bad effect.
 *
 * Self-directed: your bucket, your hand. Milk clears every effect including
 * good ones, so this deliberately holds off while you have buffs worth keeping
 * unless you say otherwise.
 */
public final class AutoMilkModule extends Module {

    /** Registry paths rather than MobEffectCategory, which this mapping set doesn't expose. */
    private static final Set<String> BAD = Set.of(
        "poison", "wither", "blindness", "nausea", "weakness", "mining_fatigue",
        "slowness", "hunger", "levitation", "unluck", "darkness", "infested",
        "oozing", "weaving", "bad_omen", "trial_omen", "raid_omen");

    private final IntSetting minDuration = add(new IntSetting("min-duration", "Only If Longer Than (s)", 5, 0, 120, 1)
        .description("Ignore effects about to expire on their own.")
        .group("Triggers"));

    private final IntSetting minAmplifier = add(new IntSetting("min-amplifier", "Minimum Level", 1, 1, 5, 1)
        .description("Only react at this effect level or above.")
        .group("Triggers"));

    private final BoolSetting keepBuffs = add(new BoolSetting("keep-buffs", "Don't Waste Buffs", true)
        .description("Hold off while you have beneficial effects, since milk clears those too.")
        .group("Triggers"));

    private final BoolSetting restoreSlot = add(new BoolSetting("restore-slot", "Restore Held Slot", true)
        .group("Behaviour"));

    private final IntSetting cooldown = add(new IntSetting("cooldown", "Cooldown (ticks)", 40, 5, 200, 5)
        .description("Gap between drinks, so a lingering effect doesn't drain your buckets.")
        .group("Behaviour"));

    private int wait;
    private int deferredRestore = -1;

    public AutoMilkModule() {
        super(FrestAddon.ID + ":auto-milk", "AutoMilk", FrestAddon.CATEGORY,
            "Drinks milk when a harmful effect lands on you.");
    }

    @Override
    public void tick() {
        LocalPlayer player = MC.player;
        if (player == null || MC.level == null || MC.gameMode == null) return;

        if (deferredRestore >= 0 && !player.isUsingItem()) {
            AutismInventoryHelper.selectHotbarSlot(MC, deferredRestore);
            deferredRestore = -1;
        }

        if (wait > 0) {
            wait--;
            return;
        }
        if (player.isUsingItem()) return;
        if (MC.gui.screen() != null) return;
        if (!hasBadEffect(player)) return;
        if (keepBuffs.get() && hasGoodEffect(player)) return;

        int slot = findMilk(player);
        if (slot < 0) return;

        int previous = player.getInventory().getSelectedSlot();
        if (previous != slot) {
            AutismInventoryHelper.selectHotbarSlot(MC, slot);
            if (restoreSlot.get()) deferredRestore = previous;
        }

        // Milk needs a held use, so drive it through useItem and let the
        // deferred restore above wait for isUsingItem() to finish.
        MC.gameMode.useItem(player, InteractionHand.MAIN_HAND);
        wait = cooldown.get();
    }

    private boolean hasBadEffect(LocalPlayer player) {
        for (MobEffectInstance effect : player.getActiveEffects()) {
            if (!isBad(effect)) continue;
            if (effect.getAmplifier() + 1 < minAmplifier.get()) continue;
            if (effect.getDuration() >= 0 && effect.getDuration() < minDuration.get() * 20) continue;
            return true;
        }
        return false;
    }

    private boolean hasGoodEffect(LocalPlayer player) {
        for (MobEffectInstance effect : player.getActiveEffects()) {
            if (!isBad(effect)) return true;
        }
        return false;
    }

    private static boolean isBad(MobEffectInstance effect) {
        Identifier id = BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value());
        return id != null && BAD.contains(id.getPath());
    }

    private static int findMilk(LocalPlayer player) {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.is(Items.MILK_BUCKET)) return slot;
        }
        return -1;
    }
}

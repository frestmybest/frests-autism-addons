package com.forest.frest.modules;

import com.forest.frest.FrestAddon;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.IntSetting;
import autismclient.modules.Module;
import autismclient.modules.ModuleCategory;
import autismclient.util.AutismInventoryHelper;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Places water beneath you during a long fall, then picks it back up.
 *
 * Unlike the client's builtin NoFall, which works by lying to the server about
 * your ground state, this places an actual bucket you own. If you have no water
 * bucket, or there's no solid block to place against, it fails and you take the
 * fall — which is the honest version of the same save.
 */
public final class AutoBucketModule extends Module {

    private final IntSetting minFall = add(new IntSetting("min-fall", "Place After Falling (blocks)", 5, 3, 40, 1)
        .description("Fall distance before it acts.")
        .group("Triggers"));

    private final IntSetting placeHeight = add(new IntSetting("place-height", "Place Within (blocks)", 3, 1, 5, 1)
        .description("How close the ground must be before placing. Too early and the water is gone by the time you land.")
        .group("Triggers"));

    private final BoolSetting onlyIfLethal = add(new BoolSetting("only-lethal", "Only If Fall Would Hurt Badly", false)
        .description("Hold off unless the fall would take more than half your health.")
        .group("Triggers"));

    private final BoolSetting pickUp = add(new BoolSetting("pick-up", "Pick Water Back Up", true)
        .description("Collect the water again after landing.")
        .group("Behaviour"));

    private final IntSetting pickUpDelay = add(new IntSetting("pickup-delay", "Pickup Delay (ticks)", 6, 1, 40, 1)
        .description("Wait this long after landing before collecting.")
        .group("Behaviour")
        .visibleWhen(pickUp::get));

    private final BoolSetting restoreSlot = add(new BoolSetting("restore-slot", "Restore Held Slot", true)
        .group("Behaviour"));

    private BlockPos placedAt;
    private int landedTicks = -1;
    private int deferredRestore = -1;
    private int cooldown;

    public AutoBucketModule() {
        super(FrestAddon.ID + ":auto-bucket", "AutoBucket", ModuleCategory.MOVEMENT,
            "Water-clutches a long fall with a bucket you actually own.");
    }

    @Override
    public void onDisable() {
        placedAt = null;
        landedTicks = -1;
    }

    @Override
    public void tick() {
        LocalPlayer player = MC.player;
        if (player == null || MC.level == null || MC.gameMode == null) return;

        if (deferredRestore >= 0) {
            AutismInventoryHelper.selectHotbarSlot(MC, deferredRestore);
            deferredRestore = -1;
        }
        if (cooldown > 0) cooldown--;

        // Retrieve the water once we're safely down.
        if (placedAt != null) {
            if (player.onGround()) {
                if (landedTicks < 0) landedTicks = 0;
                else landedTicks++;

                if (!pickUp.get()) {
                    placedAt = null;
                    landedTicks = -1;
                } else if (landedTicks >= pickUpDelay.get()) {
                    collectWater(player);
                }
            }
            return;
        }

        if (cooldown > 0) return;
        if (player.onGround() || player.isFallFlying() || player.getAbilities().flying) return;
        if (player.isInWater() || player.isInLava()) return;
        if (player.fallDistance < minFall.get()) return;
        if (onlyIfLethal.get() && !wouldHurtBadly(player)) return;

        BlockPos ground = groundBelow(player);
        if (ground == null) return;

        placeWater(player, ground);
    }

    private boolean wouldHurtBadly(LocalPlayer player) {
        // fallDistance is a double here, so keep the whole calculation in double.
        double damage = Math.max(0.0, player.fallDistance - 3.0);
        return damage > player.getHealth() / 2.0;
    }

    /** First solid block within placeHeight below the player, or null. */
    private BlockPos groundBelow(LocalPlayer player) {
        BlockPos feet = player.blockPosition();
        for (int dy = 1; dy <= placeHeight.get(); dy++) {
            BlockPos pos = feet.below(dy);
            if (!MC.level.getBlockState(pos).isAir()) return pos;
        }
        return null;
    }

    private void placeWater(LocalPlayer player, BlockPos ground) {
        int slot = findHotbar(player, Items.WATER_BUCKET);
        if (slot < 0) return;

        int previous = player.getInventory().getSelectedSlot();
        if (previous != slot) {
            AutismInventoryHelper.selectHotbarSlot(MC, slot);
            if (restoreSlot.get()) deferredRestore = previous;
        }

        Vec3 hitVec = new Vec3(ground.getX() + 0.5, ground.getY() + 1.0, ground.getZ() + 0.5);
        BlockHitResult hit = new BlockHitResult(hitVec, Direction.UP, ground, false);
        MC.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
        player.swing(InteractionHand.MAIN_HAND);

        placedAt = ground.above().immutable();
        landedTicks = -1;
    }

    private void collectWater(LocalPlayer player) {
        int slot = findHotbar(player, Items.BUCKET);
        if (slot < 0) {
            placedAt = null;
            landedTicks = -1;
            return;
        }

        int previous = player.getInventory().getSelectedSlot();
        if (previous != slot) {
            AutismInventoryHelper.selectHotbarSlot(MC, slot);
            if (restoreSlot.get()) deferredRestore = previous;
        }

        Vec3 hitVec = Vec3.atCenterOf(placedAt);
        BlockHitResult hit = new BlockHitResult(hitVec, Direction.UP, placedAt, false);
        MC.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
        player.swing(InteractionHand.MAIN_HAND);

        placedAt = null;
        landedTicks = -1;
        cooldown = 20;
    }

    private static int findHotbar(LocalPlayer player, net.minecraft.world.item.Item item) {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.is(item)) return slot;
        }
        return -1;
    }
}

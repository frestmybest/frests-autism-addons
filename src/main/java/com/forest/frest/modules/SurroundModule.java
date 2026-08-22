package com.forest.frest.modules;

import com.forest.frest.FrestAddon;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.IntSetting;
import autismclient.api.module.KeybindSetting;
import autismclient.api.module.RegistryListSetting;
import autismclient.modules.Module;
import autismclient.util.AutismInventoryHelper;
import autismclient.util.AutismNotifications;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Walls the four blocks around your own feet.
 *
 * Defensive and entirely self-directed — it places blocks you're carrying, in
 * the four cardinal positions touching you, at vanilla reach. It never places
 * anything near anyone else, and it won't trap another player.
 */
public final class SurroundModule extends Module {

    private static final double VANILLA_REACH = 4.5;

    private static final Direction[] CARDINALS = {
        Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };

    private final RegistryListSetting blocks = add(RegistryListSetting
        .placeableBlocks("blocks", "Blocks", "minecraft:obsidian|minecraft:crying_obsidian|minecraft:ender_chest")
        .description("What to place, in preference order.")
        .group("Blocks")
        .build());

    private final KeybindSetting key = add(new KeybindSetting("key", "Surround Key", -1)
        .description("Press to place the ring once.")
        .group("Blocks"));

    private final BoolSetting includeCorners = add(new BoolSetting("corners", "Include Corners", false)
        .description("Also fill the four diagonals, for eight blocks instead of four.")
        .group("Blocks"));

    private final BoolSetting alsoBelow = add(new BoolSetting("below", "Fill Gap Below", true)
        .description("Place under your feet too if there's a hole there.")
        .group("Blocks"));

    private final IntSetting perTick = add(new IntSetting("per-tick", "Blocks Per Cycle", 2, 1, 8, 1)
        .description("How many to place each cycle. High values are obvious and may be rejected.")
        .group("Behaviour"));

    private final IntSetting delay = add(new IntSetting("delay", "Delay (ticks)", 1, 0, 20, 1)
        .group("Behaviour"));

    private final BoolSetting restoreSlot = add(new BoolSetting("restore-slot", "Restore Held Slot", true)
        .group("Behaviour"));

    private final BoolSetting notifyMissing = add(new BoolSetting("notify-missing", "Warn If Out Of Blocks", true)
        .group("Behaviour"));

    private boolean keyWasDown;
    private int cooldown;
    private int deferredRestore = -1;
    private boolean warned;

    public SurroundModule() {
        super(FrestAddon.ID + ":surround", "Surround", FrestAddon.CATEGORY,
            "Places blocks around your own feet.");
    }

    @Override
    public void onDisable() {
        cooldown = 0;
        warned = false;
    }

    @Override
    public void tick() {
        LocalPlayer player = MC.player;
        if (player == null || MC.level == null || MC.gameMode == null) return;

        if (deferredRestore >= 0) {
            AutismInventoryHelper.selectHotbarSlot(MC, deferredRestore);
            deferredRestore = -1;
        }
        if (MC.gui.screen() != null) return;

        boolean down = key.get() >= 0 && keyDown();
        boolean triggered = down && !keyWasDown;
        keyWasDown = down;

        // While the module is on it maintains the ring; the key forces a refresh.
        if (cooldown > 0 && !triggered) {
            cooldown--;
            return;
        }

        List<BlockPos> gaps = findGaps(player);
        if (gaps.isEmpty()) {
            warned = false;
            return;
        }

        int slot = findBlockSlot(player);
        if (slot < 0) {
            if (notifyMissing.get() && !warned) {
                AutismNotifications.warning("Surround: no listed blocks in hotbar");
                warned = true;
            }
            cooldown = 20;
            return;
        }
        warned = false;

        int previous = player.getInventory().getSelectedSlot();
        if (previous != slot) {
            AutismInventoryHelper.selectHotbarSlot(MC, slot);
            if (restoreSlot.get()) deferredRestore = previous;
        }

        int placed = 0;
        for (BlockPos pos : gaps) {
            if (placed >= perTick.get()) break;
            if (place(player, pos)) placed++;
        }
        cooldown = delay.get();
    }

    private boolean keyDown() {
        return org.lwjgl.glfw.GLFW.glfwGetKey(MC.getWindow().handle(), key.get())
            == org.lwjgl.glfw.GLFW.GLFW_PRESS;
    }

    /** Empty positions touching the player that we could fill. */
    private List<BlockPos> findGaps(LocalPlayer player) {
        List<BlockPos> out = new ArrayList<>();
        BlockPos feet = player.blockPosition();

        for (Direction dir : CARDINALS) {
            addIfEmpty(out, feet.relative(dir), player);
        }
        if (includeCorners.get()) {
            addIfEmpty(out, feet.north().east(), player);
            addIfEmpty(out, feet.north().west(), player);
            addIfEmpty(out, feet.south().east(), player);
            addIfEmpty(out, feet.south().west(), player);
        }
        if (alsoBelow.get()) {
            addIfEmpty(out, feet.below(), player);
        }
        return out;
    }

    private void addIfEmpty(List<BlockPos> out, BlockPos pos, LocalPlayer player) {
        if (!MC.level.getBlockState(pos).isAir()) return;
        if (!inReach(player, pos)) return;
        if (!hasSupport(pos)) return;
        out.add(pos.immutable());
    }

    private boolean inReach(LocalPlayer player, BlockPos pos) {
        return player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos))
            <= VANILLA_REACH * VANILLA_REACH;
    }

    /** Needs a solid neighbour to click against; floating placement isn't vanilla. */
    private boolean hasSupport(BlockPos pos) {
        for (Direction dir : Direction.values()) {
            if (!MC.level.getBlockState(pos.relative(dir)).isAir()) return true;
        }
        return false;
    }

    private Direction supportFace(BlockPos pos) {
        for (Direction dir : Direction.values()) {
            if (!MC.level.getBlockState(pos.relative(dir)).isAir()) return dir;
        }
        return null;
    }

    private boolean place(LocalPlayer player, BlockPos pos) {
        Direction toSupport = supportFace(pos);
        if (toSupport == null) return false;

        BlockPos support = pos.relative(toSupport);
        Direction face = toSupport.getOpposite();
        Vec3 hitVec = Vec3.atCenterOf(support).add(
            face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);

        BlockHitResult hit = new BlockHitResult(hitVec, face, support, false);
        MC.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
        player.swing(InteractionHand.MAIN_HAND);

        return !MC.level.getBlockState(pos).isAir();
    }

    private int findBlockSlot(LocalPlayer player) {
        List<String> wanted = blocks.get();
        if (wanted.isEmpty()) return -1;

        // Preference order follows the list, not hotbar order.
        for (String id : wanted) {
            for (int slot = 0; slot < 9; slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) continue;

                Identifier blockId = BuiltInRegistries.BLOCK.getKey(blockItem.getBlock());
                if (blockId != null && blockId.toString().equals(id)) return slot;
            }
        }
        return -1;
    }
}

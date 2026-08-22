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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Harvests mature crops in range and replants them.
 *
 * Everything goes through ordinary {@code destroyBlock} / {@code useItemOn}
 * calls at vanilla reach, so it is a fast farmhand rather than a reach exploit.
 * Stalk crops (cane, cactus, bamboo, kelp) are cut above the base block so they
 * regrow on their own; stem crops (pumpkin, melon) regrow from the stem, so
 * neither category needs replanting.
 */
public final class AutoFarmModule extends Module {

    /** Vanilla survival block reach. Going past this is what anticheats flag. */
    private static final double VANILLA_REACH = 4.5;

    // ------------------------------------------------------------------ toggles

    private final BoolSetting doWheat = add(new BoolSetting("wheat", "Wheat", true).group("Crops"));
    private final BoolSetting doCarrots = add(new BoolSetting("carrots", "Carrots", true).group("Crops"));
    private final BoolSetting doPotatoes = add(new BoolSetting("potatoes", "Potatoes", true).group("Crops"));
    private final BoolSetting doBeetroot = add(new BoolSetting("beetroot", "Beetroot", true).group("Crops"));
    private final BoolSetting doNetherWart = add(new BoolSetting("nether-wart", "Nether Wart", true).group("Crops"));

    private final BoolSetting doPumpkin = add(new BoolSetting("pumpkin", "Pumpkins", true).group("Stem & Stalk"));
    private final BoolSetting doMelon = add(new BoolSetting("melon", "Melons", true).group("Stem & Stalk"));
    private final BoolSetting doCane = add(new BoolSetting("cane", "Sugar Cane", true).group("Stem & Stalk"));
    private final BoolSetting doCactus = add(new BoolSetting("cactus", "Cactus", true).group("Stem & Stalk"));
    private final BoolSetting doBamboo = add(new BoolSetting("bamboo", "Bamboo", true).group("Stem & Stalk"));
    private final BoolSetting doKelp = add(new BoolSetting("kelp", "Kelp", false).group("Stem & Stalk"));

    // ---------------------------------------------------------------- behaviour

    private final IntSetting radius = add(new IntSetting("radius", "Radius", 4, 1, 5, 1)
        .description("Horizontal search radius. Capped at vanilla reach — larger values do nothing useful.")
        .group("Behaviour"));

    private final IntSetting vertical = add(new IntSetting("vertical", "Vertical Range", 2, 0, 4, 1)
        .description("How far above and below to search.")
        .group("Behaviour"));

    private final BoolSetting replant = add(new BoolSetting("replant", "Replant", true)
        .description("Place seeds back after harvesting. Stems and stalks regrow on their own.")
        .group("Behaviour"));

    private final BoolSetting replantOnly = add(new BoolSetting("replant-only", "Replant Empty Farmland", true)
        .description("Also fill bare farmland you walk past, not just what was just harvested.")
        .group("Behaviour")
        .visibleWhen(replant::get));

    private final IntSetting delay = add(new IntSetting("delay", "Delay (ticks)", 2, 0, 20, 1)
        .description("Ticks between actions. 0 is instant and very obvious.")
        .group("Behaviour"));

    private final IntSetting perTick = add(new IntSetting("per-tick", "Actions Per Cycle", 1, 1, 8, 1)
        .description("How many blocks to handle each time the delay elapses.")
        .group("Behaviour"));

    private final IntSetting blockCooldown = add(new IntSetting("block-cooldown", "Per-Block Cooldown", 20, 5, 100, 5)
        .description("Ticks before the same position may be touched again. This is what stops break/replant loops \u2014 do not set it low.")
        .group("Behaviour"));

    private final BoolSetting pauseInGui = add(new BoolSetting("pause-in-gui", "Pause In GUI", true)
        .group("Behaviour"));

    /** Positions acted on recently, mapped to ticks remaining before retry. */
    private final Map<BlockPos, Integer> recentlyTouched = new HashMap<>();

    private int cooldown;
    private int deferredRestoreSlot = -1;

    public AutoFarmModule() {
        super(FrestAddon.ID + ":auto-farm", "AutoFarm", ModuleCategory.PLAYER,
            "Harvests and replants crops around you.");
    }

    @Override
    public void onDisable() {
        recentlyTouched.clear();
        cooldown = 0;
        if (deferredRestoreSlot >= 0) {
            AutismInventoryHelper.selectHotbarSlot(MC, deferredRestoreSlot);
            deferredRestoreSlot = -1;
        }
    }

    @Override
    public void tick() {
        LocalPlayer player = MC.player;
        if (player == null || MC.level == null || MC.gameMode == null) return;

        // Restore the hotbar a tick after planting, never in the same tick \u2014 the
        // server must see the use packet while the seed is still equipped.
        if (deferredRestoreSlot >= 0) {
            AutismInventoryHelper.selectHotbarSlot(MC, deferredRestoreSlot);
            deferredRestoreSlot = -1;
        }

        ageRecentlyTouched();

        if (pauseInGui.get() && MC.gui.screen() != null) return;

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        List<BlockPos> harvest = new ArrayList<>();
        List<BlockPos> plant = new ArrayList<>();
        scan(player, harvest, plant);

        int budget = perTick.get();
        int used = 0;

        for (BlockPos pos : harvest) {
            if (used >= budget) break;
            markTouched(pos);
            if (breakBlock(player, pos)) used++;
        }

        if (replant.get()) {
            for (BlockPos pos : plant) {
                if (used >= budget) break;
                // Mark before acting: a rejected placement must not retry next
                // tick, or we are back to the spam loop.
                markTouched(pos);
                if (plantAt(player, pos)) used++;
            }
        }

        if (used > 0) cooldown = delay.get();
    }

    // -------------------------------------------------------- position cooldown

    private void ageRecentlyTouched() {
        Iterator<Map.Entry<BlockPos, Integer>> it = recentlyTouched.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Integer> entry = it.next();
            int left = entry.getValue() - 1;
            if (left <= 0) it.remove();
            else entry.setValue(left);
        }
    }

    private void markTouched(BlockPos pos) {
        recentlyTouched.put(pos.immutable(), blockCooldown.get());
    }

    private boolean isTouched(BlockPos pos) {
        return recentlyTouched.containsKey(pos);
    }

    // ------------------------------------------------------------------- search

    private void scan(LocalPlayer player, List<BlockPos> harvest, List<BlockPos> plant) {
        BlockPos origin = player.blockPosition();
        int r = radius.get();
        int v = vertical.get();
        Vec3 eye = player.getEyePosition();

        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -v; dy <= v; dy++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (isTouched(pos)) continue;
                    if (!inReach(eye, pos)) continue;

                    BlockState state = MC.level.getBlockState(pos);

                    if (isHarvestable(state, pos)) {
                        harvest.add(pos.immutable());
                    } else if (replantOnly.get() && state.isAir() && isPlantableGround(pos)) {
                        plant.add(pos.immutable());
                    }
                }
            }
        }

        // Nearest first — keeps the pattern tight and reduces mid-action range failures.
        Comparator<BlockPos> byDistance = Comparator.comparingDouble(p -> eye.distanceToSqr(Vec3.atCenterOf(p)));
        harvest.sort(byDistance);
        plant.sort(byDistance);
    }

    private boolean inReach(Vec3 eye, BlockPos pos) {
        return eye.distanceToSqr(Vec3.atCenterOf(pos)) <= VANILLA_REACH * VANILLA_REACH;
    }

    // -------------------------------------------------------------- crop tests

    private boolean isHarvestable(BlockState state, BlockPos pos) {
        Block block = state.getBlock();

        // Age-based crops: only take them at full growth.
        if (block instanceof CropBlock crop) {
            if (!crop.isMaxAge(state)) return false;
            if (block == Blocks.WHEAT) return doWheat.get();
            if (block == Blocks.CARROTS) return doCarrots.get();
            if (block == Blocks.POTATOES) return doPotatoes.get();
            if (block == Blocks.BEETROOTS) return doBeetroot.get();
            return false;
        }

        if (block instanceof NetherWartBlock) {
            if (!doNetherWart.get()) return false;
            return state.getValue(NetherWartBlock.AGE) >= 3;
        }

        if (block == Blocks.PUMPKIN) return doPumpkin.get();
        if (block == Blocks.MELON) return doMelon.get();

        // Stalks: cut above the base so the plant survives and regrows.
        if (block == Blocks.SUGAR_CANE) return doCane.get() && hasSameBlockBelow(pos, block);
        if (block == Blocks.CACTUS) return doCactus.get() && hasSameBlockBelow(pos, block);
        if (block == Blocks.BAMBOO) return doBamboo.get() && hasSameBlockBelow(pos, block);
        if (block == Blocks.KELP_PLANT || block == Blocks.KELP) {
            return doKelp.get() && isKelpAboveBase(pos);
        }

        return false;
    }

    private boolean hasSameBlockBelow(BlockPos pos, Block block) {
        return MC.level.getBlockState(pos.below()).is(block);
    }

    private boolean isKelpAboveBase(BlockPos pos) {
        BlockState below = MC.level.getBlockState(pos.below());
        return below.is(Blocks.KELP_PLANT) || below.is(Blocks.KELP);
    }

    // ---------------------------------------------------------------- planting

    private boolean isPlantableGround(BlockPos pos) {
        BlockState ground = MC.level.getBlockState(pos.below());
        if (ground.is(Blocks.FARMLAND)) return true;
        return ground.is(Blocks.SOUL_SAND) && doNetherWart.get();
    }

    /** Which seed belongs on the ground under {@code pos}, or null if we have none. */
    private int findSeedSlot(LocalPlayer player, BlockPos pos) {
        BlockState ground = MC.level.getBlockState(pos.below());

        List<Item> wanted = new ArrayList<>();
        if (ground.is(Blocks.FARMLAND)) {
            if (doWheat.get()) wanted.add(Items.WHEAT_SEEDS);
            if (doCarrots.get()) wanted.add(Items.CARROT);
            if (doPotatoes.get()) wanted.add(Items.POTATO);
            if (doBeetroot.get()) wanted.add(Items.BEETROOT_SEEDS);
        } else if (ground.is(Blocks.SOUL_SAND)) {
            if (doNetherWart.get()) wanted.add(Items.NETHER_WART);
        }
        if (wanted.isEmpty()) return -1;

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && wanted.contains(stack.getItem())) return slot;
        }
        return -1;
    }

    private boolean plantAt(LocalPlayer player, BlockPos pos) {
        int seedSlot = findSeedSlot(player, pos);
        if (seedSlot < 0) return false;

        int previous = player.getInventory().getSelectedSlot();
        if (previous != seedSlot) {
            AutismInventoryHelper.selectHotbarSlot(MC, seedSlot);
            deferredRestoreSlot = previous;
        }

        // Place onto the top face of the soil block below the empty crop space.
        BlockPos soil = pos.below();
        Vec3 hitVec = new Vec3(soil.getX() + 0.5, soil.getY() + 1.0, soil.getZ() + 0.5);
        BlockHitResult hit = new BlockHitResult(hitVec, Direction.UP, soil, false);

        MC.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
        player.swing(InteractionHand.MAIN_HAND);

        // The client predicts placement immediately. Still air means it was
        // rejected locally, so report failure instead of claiming success.
        return !MC.level.getBlockState(pos).isAir();
    }

    // ---------------------------------------------------------------- breaking

    private boolean breakBlock(LocalPlayer player, BlockPos pos) {
        // Crops and stalks are all instant-break, so a single destroyBlock is enough.
        boolean ok = MC.gameMode.destroyBlock(pos);
        if (ok) player.swing(InteractionHand.MAIN_HAND);
        return ok;
    }

}

package com.forest.frest.modules;

import com.forest.frest.FrestAddon;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.ChoiceSetting;
import autismclient.api.module.IntSetting;
import autismclient.api.module.RegistryListSetting;
import autismclient.modules.Module;
import autismclient.modules.ModuleCategory;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Finishes an ore vein you've already started breaking.
 *
 * Seeded by onStartBreakingBlock — you swing at one ore, and the connected ones
 * get queued. Breaking runs through continueDestroyBlock, the same progressive
 * loop vanilla uses when you hold the mouse button, so tool speed, hardness and
 * server-side break validation all apply normally. Nothing is instant and
 * nothing reaches further than you could reach yourself.
 */
public final class VeinMinerModule extends Module {

    /** Vanilla survival block reach. */
    private static final double VANILLA_REACH = 4.5;

    private static final String DEFAULT_ORES = String.join("|",
        "minecraft:coal_ore", "minecraft:deepslate_coal_ore",
        "minecraft:iron_ore", "minecraft:deepslate_iron_ore",
        "minecraft:copper_ore", "minecraft:deepslate_copper_ore",
        "minecraft:gold_ore", "minecraft:deepslate_gold_ore",
        "minecraft:redstone_ore", "minecraft:deepslate_redstone_ore",
        "minecraft:lapis_ore", "minecraft:deepslate_lapis_ore",
        "minecraft:diamond_ore", "minecraft:deepslate_diamond_ore",
        "minecraft:emerald_ore", "minecraft:deepslate_emerald_ore",
        "minecraft:nether_gold_ore", "minecraft:nether_quartz_ore",
        "minecraft:ancient_debris");

    // ------------------------------------------------------------------ targets

    private final RegistryListSetting blocks = add(RegistryListSetting
        .blocks("blocks", "Blocks", DEFAULT_ORES)
        .description("Which blocks count as a vein. Defaults to every vanilla ore; add logs for tree felling.")
        .group("Targets")
        .build());

    private final ChoiceSetting mode = add(new ChoiceSetting("mode", "List Mode", "WhiteList", "WhiteList", "BlackList")
        .description("WhiteList mines only listed blocks. BlackList mines anything except them.")
        .group("Targets")
        .build());

    private final BoolSetting sameTypeOnly = add(new BoolSetting("same-type", "Same Block Only", true)
        .description("Only follow blocks identical to the one you started on, so a coal seam doesn't drag in the iron next to it.")
        .group("Targets"));

    private final BoolSetting deepslateVariants = add(new BoolSetting("deepslate-variants", "Treat Deepslate As Same", true)
        .description("Count deepslate_X and X as one ore, so a vein spanning the boundary still completes.")
        .group("Targets")
        .visibleWhen(sameTypeOnly::get));

    private final BoolSetting diagonals = add(new BoolSetting("diagonals", "Follow Diagonals", true)
        .description("Include the 26 surrounding blocks rather than only the 6 faces. Vanilla veins are usually diagonal.")
        .group("Targets"));

    // ---------------------------------------------------------------- behaviour

    private final IntSetting maxBlocks = add(new IntSetting("max-blocks", "Max Blocks", 64, 1, 256, 1)
        .description("Stop after this many blocks in one vein.")
        .group("Behaviour"));

    private final IntSetting delay = add(new IntSetting("delay", "Delay (ticks)", 0, 0, 20, 1)
        .description("Extra ticks between blocks, on top of the time each takes to break.")
        .group("Behaviour"));

    private final BoolSetting onlyPickaxe = add(new BoolSetting("only-pickaxe", "Pickaxe Only", true)
        .description("Only run while holding a pickaxe. Turn off if you've added logs to the list.")
        .group("Behaviour"));

    private final BoolSetting requireTool = add(new BoolSetting("require-tool", "Require Correct Tool", true)
        .description("Skip blocks your held item can't actually harvest, so you don't mine ore for nothing.")
        .group("Behaviour"));

    private final BoolSetting stopOnMove = add(new BoolSetting("stop-on-move", "Cancel If Out Of Reach", true)
        .description("Abandon the vein if you walk away, rather than queueing blocks you can no longer reach.")
        .group("Behaviour"));

    private final BoolSetting pauseInGui = add(new BoolSetting("pause-in-gui", "Pause In GUI", true)
        .group("Behaviour"));

    // ------------------------------------------------------------------- state

    private final Deque<BlockPos> queue = new ArrayDeque<>();
    private final Set<BlockPos> seen = new HashSet<>();

    private Block seedBlock;
    private Direction face = Direction.UP;
    private BlockPos current;
    private int mined;
    private int cooldown;

    public VeinMinerModule() {
        super(FrestAddon.ID + ":vein-miner", "VeinMiner", ModuleCategory.PLAYER,
            "Finishes the whole vein once you start breaking one ore.");
    }

    @Override
    public void onDisable() {
        clearJob();
    }

    @Override
    public void onStartBreakingBlock(BlockPos pos, Direction direction) {
        if (pos == null || MC.level == null || MC.player == null) return;
        if (!queue.isEmpty() || current != null) return;

        BlockState state = MC.level.getBlockState(pos);
        if (!isTarget(state)) return;
        if (onlyPickaxe.get() && !MC.player.getMainHandItem().is(ItemTags.PICKAXES)) return;

        seedBlock = state.getBlock();
        face = direction == null ? Direction.UP : direction;
        mined = 0;
        cooldown = 0;
        seen.clear();
        queue.clear();

        seen.add(pos.immutable());
        // The player breaks the seed block themselves; we take the neighbours.
        enqueueNeighbours(pos);
    }

    @Override
    public void tick() {
        LocalPlayer player = MC.player;
        if (player == null || MC.level == null || MC.gameMode == null) {
            clearJob();
            return;
        }
        if (queue.isEmpty() && current == null) return;

        if (pauseInGui.get() && MC.gui.screen() != null) return;
        if (onlyPickaxe.get() && !player.getMainHandItem().is(ItemTags.PICKAXES)) {
            clearJob();
            return;
        }

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        if (current == null) {
            current = nextValidTarget(player);
            if (current == null) {
                clearJob();
                return;
            }
        }

        BlockState state = MC.level.getBlockState(current);
        if (state.isAir()) {
            // Finished this one; fan out from it and move on.
            mined++;
            enqueueNeighbours(current);
            current = null;
            cooldown = delay.get();
            if (mined >= maxBlocks.get()) clearJob();
            return;
        }

        if (stopOnMove.get() && !inReach(player, current)) {
            current = null;
            return;
        }

        // Progressive break: same loop vanilla runs while you hold the button,
        // so hardness and server validation apply exactly as normal.
        MC.gameMode.continueDestroyBlock(current, face);
        player.swing(InteractionHand.MAIN_HAND);
    }

    // ------------------------------------------------------------------- search

    private BlockPos nextValidTarget(LocalPlayer player) {
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            BlockState state = MC.level.getBlockState(pos);
            if (!isTarget(state)) continue;
            if (!matchesSeed(state)) continue;
            if (!inReach(player, pos)) continue;
            if (requireTool.get() && state.requiresCorrectToolForDrops()
                && !player.getMainHandItem().isCorrectToolForDrops(state)) continue;
            return pos;
        }
        return null;
    }

    private void enqueueNeighbours(BlockPos origin) {
        if (mined >= maxBlocks.get()) return;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    if (!diagonals.get() && Math.abs(dx) + Math.abs(dy) + Math.abs(dz) != 1) continue;

                    BlockPos pos = origin.offset(dx, dy, dz).immutable();
                    if (!seen.add(pos)) continue;

                    BlockState state = MC.level.getBlockState(pos);
                    if (isTarget(state) && matchesSeed(state)) queue.add(pos);
                }
            }
        }
    }

    private boolean inReach(LocalPlayer player, BlockPos pos) {
        return player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos))
            <= VANILLA_REACH * VANILLA_REACH;
    }

    // -------------------------------------------------------------- block tests

    private boolean isTarget(BlockState state) {
        if (state == null || state.isAir()) return false;

        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (id == null) return false;

        List<String> list = new ArrayList<>(blocks.get());
        boolean listed = list.contains(id.toString());

        return "BlackList".equals(mode.get()) ? !listed : listed;
    }

    private boolean matchesSeed(BlockState state) {
        if (!sameTypeOnly.get() || seedBlock == null) return true;

        Block block = state.getBlock();
        if (block == seedBlock) return true;
        if (!deepslateVariants.get()) return false;

        return stripDeepslate(block).equals(stripDeepslate(seedBlock));
    }

    /** "deepslate_iron_ore" and "iron_ore" collapse to the same name. */
    private static String stripDeepslate(Block block) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        if (id == null) return "";
        String path = id.getPath();
        return path.startsWith("deepslate_") ? path.substring("deepslate_".length()) : path;
    }

    private void clearJob() {
        if (current != null && MC.gameMode != null) MC.gameMode.stopDestroyBlock();
        queue.clear();
        seen.clear();
        current = null;
        seedBlock = null;
        mined = 0;
        cooldown = 0;
    }
}

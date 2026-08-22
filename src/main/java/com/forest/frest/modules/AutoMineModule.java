package com.forest.frest.modules;

import com.forest.frest.FrestAddon;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.IntSetting;
import autismclient.modules.Module;
import autismclient.util.AutismKeyMappingBridge;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Mines whatever the crosshair is on, as if attack were held down.
 *
 * Rather than driving {@code continueDestroyBlock} by hand, this simulates the
 * attack key and lets vanilla run its normal break loop. That keeps break
 * progress, tool speed, swing animation and the START/STOP_DESTROY_BLOCK packet
 * pair exactly as they'd be if you were holding the button yourself.
 */
public final class AutoMineModule extends Module {

    private final BoolSetting blocksOnly = add(new BoolSetting("blocks-only", "Blocks Only", true)
        .description("Only swing when the crosshair is on a block. Off also attacks entities.")
        .group("Targeting"));

    private final BoolSetting skipUnbreakable = add(new BoolSetting("skip-unbreakable", "Skip Unbreakable", true)
        .description("Don't bother swinging at bedrock, barriers, portal frames and the like.")
        .group("Targeting"));

    private final BoolSetting requireTool = add(new BoolSetting("require-tool", "Require Correct Tool", false)
        .description("Only mine when the held item can actually harvest the block, so you don't waste durability.")
        .group("Targeting"));

    private final BoolSetting pauseInGui = add(new BoolSetting("pause-in-gui", "Pause In GUI", true)
        .description("Stop while a screen or chat is open.")
        .group("Behaviour"));

    private final BoolSetting pauseWhileEating = add(new BoolSetting("pause-eating", "Pause While Using Item", true)
        .description("Stop while eating, drawing a bow, or blocking, so AutoEat isn't fought for the hand.")
        .group("Behaviour"));

    private final IntSetting startDelay = add(new IntSetting("start-delay", "Start Delay (ms)", 0, 0, 2000, 50)
        .description("Wait this long after the module turns on before the first swing.")
        .group("Behaviour"));

    private boolean holding;
    private long enabledAt;

    public AutoMineModule() {
        super(FrestAddon.ID + ":auto-mine", "AutoMine", FrestAddon.CATEGORY,
            "Mines the block under your crosshair as if attack were held down.");
    }

    @Override
    public void onEnable() {
        enabledAt = System.currentTimeMillis();
    }

    @Override
    public void onDisable() {
        releaseAttack();
    }

    @Override
    public void tick() {
        LocalPlayer player = MC.player;
        if (player == null || MC.level == null || MC.gameMode == null) {
            releaseAttack();
            return;
        }
        if (System.currentTimeMillis() - enabledAt < startDelay.get()) {
            releaseAttack();
            return;
        }
        if (pauseInGui.get() && MC.gui.screen() != null) {
            releaseAttack();
            return;
        }
        if (pauseWhileEating.get() && player.isUsingItem()) {
            releaseAttack();
            return;
        }
        if (!hasValidTarget(player)) {
            releaseAttack();
            return;
        }
        holdAttack();
    }

    private boolean hasValidTarget(LocalPlayer player) {
        HitResult hit = MC.hitResult;
        if (hit == null || hit.getType() == HitResult.Type.MISS) return false;

        if (hit.getType() == HitResult.Type.ENTITY) return !blocksOnly.get();
        if (!(hit instanceof BlockHitResult block)) return false;

        BlockState state = MC.level.getBlockState(block.getBlockPos());
        if (state.isAir() || state.is(Blocks.WATER) || state.is(Blocks.LAVA)) return false;

        if (skipUnbreakable.get()) {
            // Negative hardness means "never breaks in survival".
            float hardness = state.getDestroySpeed(MC.level, block.getBlockPos());
            if (hardness < 0.0f) return false;
        }

        if (requireTool.get() && state.requiresCorrectToolForDrops()
            && !player.getMainHandItem().isCorrectToolForDrops(state)) {
            return false;
        }

        return true;
    }

    private void holdAttack() {
        if (holding) return;
        AutismKeyMappingBridge.of(MC.options.keyAttack).autism$simulatePress(true);
        holding = true;
    }

    private void releaseAttack() {
        if (!holding) return;
        AutismKeyMappingBridge.of(MC.options.keyAttack).autism$simulatePress(false);
        holding = false;
        // Cancel any half-finished break so the server doesn't hold stale progress.
        if (MC.gameMode != null) MC.gameMode.stopDestroyBlock();
    }
}

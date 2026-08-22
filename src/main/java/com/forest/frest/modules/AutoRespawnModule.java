package com.forest.frest.modules;

import com.forest.frest.FrestAddon;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.IntSetting;
import autismclient.modules.Module;

import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;

/** Clicks through the death screen for you. */
public final class AutoRespawnModule extends Module {

    private final IntSetting delay = add(new IntSetting("delay", "Delay (ticks)", 10, 0, 200, 5)
        .description("Wait before respawning. Some anticheats dislike an instant respawn packet.")
        .group("Behaviour"));

    private final BoolSetting onlyIfSafe = add(new BoolSetting("only-if-safe", "Skip If Screen Changed", true)
        .description("Abort if you dismiss the death screen yourself during the delay.")
        .group("Behaviour"));

    private int waited;
    private boolean armed;

    public AutoRespawnModule() {
        super(FrestAddon.ID + ":auto-respawn", "AutoRespawn", FrestAddon.CATEGORY,
            "Respawns you automatically after death.");
    }

    @Override
    public void onDisable() {
        waited = 0;
        armed = false;
    }

    @Override
    public void tick() {
        if (MC.player == null || MC.getConnection() == null) {
            armed = false;
            waited = 0;
            return;
        }

        boolean dead = !MC.player.isAlive();
        if (!dead) {
            armed = false;
            waited = 0;
            return;
        }

        if (!armed) {
            armed = true;
            waited = 0;
        }

        if (waited++ < delay.get()) return;
        if (onlyIfSafe.get() && MC.player.isAlive()) return;

        MC.getConnection().send(
            new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
        armed = false;
        waited = 0;
    }
}

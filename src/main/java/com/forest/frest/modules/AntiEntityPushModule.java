package com.forest.frest.modules;

import com.forest.frest.FrestAddon;

import autismclient.api.module.BoolSetting;
import autismclient.modules.Module;
import autismclient.modules.ModuleCategory;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Stops other entities from shoving you around.
 *
 * This is purely local physics: the collision push is never applied on your
 * client, and your client then honestly reports the position it actually
 * reached. Nothing is spoofed. The flip side is that it only affects soft
 * collision push — server-driven knockback from hits and explosions still
 * lands, because that arrives as a velocity packet rather than a local shove.
 */
public final class AntiEntityPushModule extends Module {

    private static AntiEntityPushModule instance;

    private final BoolSetting fromPlayers = add(new BoolSetting("players", "From Players", true)
        .description("Ignore push from other players.")
        .group("Sources"));

    private final BoolSetting fromMobs = add(new BoolSetting("mobs", "From Mobs", true)
        .description("Ignore push from mobs and other non-player entities.")
        .group("Sources"));

    public AntiEntityPushModule() {
        super(FrestAddon.ID + ":anti-entity-push", "AntiEntityPush", ModuleCategory.MOVEMENT,
            "Prevents other players and mobs from pushing you around.");
        instance = this;
    }

    /** Called from EntityPushMixin. Returns true when the shove should be dropped. */
    public static boolean shouldCancel(Entity pushed, Entity pusher) {
        AntiEntityPushModule module = instance;
        if (module == null || !module.isEnabled()) return false;
        if (pushed == null || pusher == null) return false;

        return pusher instanceof Player ? module.fromPlayers.get() : module.fromMobs.get();
    }
}

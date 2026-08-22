package com.forest.frest.mixin;

import com.forest.frest.modules.AntiEntityPushModule;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Drops entity-on-player collision push before it reaches the local player.
 *
 * doPush(Entity) is invoked on the entity doing the shoving, with the shoved
 * entity as the argument — so the guard checks the argument against the local
 * player, not {@code this}.
 */
@Mixin(LivingEntity.class)
public abstract class EntityPushMixin {

    @Inject(method = "doPush", at = @At("HEAD"), cancellable = true, require = 0)
    private void frest$cancelPush(Entity pushed, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || pushed != mc.player) return;

        Entity pusher = (Entity) (Object) this;
        if (AntiEntityPushModule.shouldCancel(pushed, pusher)) ci.cancel();
    }
}

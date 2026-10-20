package com.teslamaps.adm.leap.mixin;

import com.teslamaps.adm.leap.AdmWorld;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public class AdmClientLevelMixin {

    @Inject(method = "addEntity", at = @At("TAIL"))
    private void adm$add(Entity entity, CallbackInfo ci) {
        AdmWorld.onEntityAdd(entity);
    }

    @Inject(method = "removeEntity", at = @At("HEAD"))
    private void adm$remove(int id, Entity.RemovalReason reason, CallbackInfo ci) {
        if (!AdmWorld.listening()) return;
        Entity e = ((ClientLevel) (Object) this).getEntity(id);
        if (e != null) AdmWorld.onEntityRemove(e);
    }
}

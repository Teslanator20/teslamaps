package com.teslamaps.mixin;

// Ported from Skyblocker's SocialInteractionsPlayerListMixin
import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.teslamaps.features.SocialScreenFilter;
import net.minecraft.client.gui.screens.social.PlayerEntry;
import net.minecraft.client.gui.screens.social.SocialInteractionsPlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Map;

@Mixin(SocialInteractionsPlayerList.class)
public class SocialInteractionsPlayerListMixin {
    @WrapWithCondition(method = "addOnlinePlayers", at = @At(value = "INVOKE",
            target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
    private boolean teslamaps$hideFakePlayers(Map<Object, Object> map, Object uuid, Object entry) {
        return !SocialScreenFilter.hide(((PlayerEntry) entry).getPlayerName());
    }
}

package com.teslamaps.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.customize.Customize;
import com.teslamaps.injected.CustomizableStack;
import net.minecraft.core.component.DataComponentHolder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/// Caches Hypixel's per-item uuid on the stack and applies the custom name from `/tmap customize`.
@Mixin(ItemStack.class)
public abstract class ItemStackUuidMixin implements DataComponentHolder, CustomizableStack {

    @Unique
    private @Nullable String teslamaps$cachedUuid;

    @Override
    @Unique
    public String teslamaps$uuid() {
        if (teslamaps$cachedUuid != null) return teslamaps$cachedUuid;
        CustomData data = get(DataComponents.CUSTOM_DATA);
        return teslamaps$cachedUuid = data == null ? "" : data.copyTag().getString("uuid").orElse("");
    }

    @Inject(method = "set(Lnet/minecraft/core/component/DataComponentType;Ljava/lang/Object;)Ljava/lang/Object;", at = @At("TAIL"))
    private <T> void teslamaps$invalidateUuid(DataComponentType<T> type, T value, CallbackInfoReturnable<T> cir) {
        if (type == DataComponents.CUSTOM_DATA) teslamaps$cachedUuid = null;
    }

    @ModifyReturnValue(method = "getHoverName", at = @At("RETURN"))
    private Component teslamaps$customItemName(Component original) {
        if (!Customize.hasAnyName()) return original;
        String uuid = teslamaps$uuid();
        if (uuid.isEmpty()) return original;
        return TeslaMapsConfig.get().customItemNames.getOrDefault(uuid, original);
    }
}

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
import net.minecraft.world.item.component.ItemLore;
import com.teslamaps.utils.StringUtil;
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
        // straight off the map: CustomComponentMixin injects into get() and asks for the uuid,
        // so going through get() here is infinite recursion
        CustomData data = getComponents().get(DataComponents.CUSTOM_DATA);
        return teslamaps$cachedUuid = data == null ? "" : data.copyTag().getString("uuid").orElse("");
    }

    // keyed on the component instance: components are immutable, any change swaps the object
    @Unique
    private @Nullable com.teslamaps.injected.StackCache<String> teslamaps$idCache;
    @Unique
    private @Nullable com.teslamaps.injected.StackCache<java.util.List<String>> teslamaps$loreCache;

    @Override
    @Unique
    public String teslamaps$skyblockId() {
        CustomData data = getComponents().get(DataComponents.CUSTOM_DATA);
        if (data == null) return "";
        com.teslamaps.injected.StackCache<String> c = teslamaps$idCache;
        if (c != null && c.src() == data) return c.value();
        String id = data.copyTag().getString("id").orElse("");
        teslamaps$idCache = new com.teslamaps.injected.StackCache<>(data, id);
        return id;
    }

    @Override
    @Unique
    public java.util.List<String> teslamaps$loreLines() {
        ItemLore lore = getComponents().get(DataComponents.LORE);
        if (lore == null) return java.util.List.of();
        com.teslamaps.injected.StackCache<java.util.List<String>> c = teslamaps$loreCache;
        if (c != null && c.src() == lore) return c.value();
        java.util.List<Component> lines = lore.lines();
        String[] out = new String[lines.size()];
        for (int i = 0; i < out.length; i++) out[i] = StringUtil.strip(lines.get(i).getString());
        java.util.List<String> value = java.util.List.of(out);
        teslamaps$loreCache = new com.teslamaps.injected.StackCache<>(lore, value);
        return value;
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

package com.teslamaps.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.customize.ArmorTrimId;
import com.teslamaps.features.customize.CustomAnimatedHelmetTextures;
import com.teslamaps.features.customize.CustomArmorTrims;
import com.teslamaps.features.customize.Customize;
import net.minecraft.core.component.DataComponentHolder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/// Swaps in the trim / head texture / glint / item model picked in `/tmap customize`.
@Mixin(DataComponentHolder.class)
public interface CustomComponentMixin {

    @SuppressWarnings("unchecked")
    @ModifyReturnValue(method = "get", at = @At("RETURN"))
    private <T> T teslamaps$customComponents(T original, DataComponentType<? extends T> type) {
        if (!Customize.active() || !((Object) this instanceof ItemStack stack)) return original;
        if (type != DataComponents.TRIM && type != DataComponents.PROFILE
                && type != DataComponents.ENCHANTMENT_GLINT_OVERRIDE && type != DataComponents.ITEM_MODEL) return original;

        String uuid = Customize.uuid(stack);
        if (uuid.isEmpty()) return original;
        TeslaMapsConfig config = TeslaMapsConfig.get();

        if (type == DataComponents.TRIM) {
            ArmorTrimId trimKey = config.customArmorTrims.get(uuid);
            if (trimKey != null) {
                return (T) CustomArmorTrims.get(trimKey, (ArmorTrim) original);
            }
        } else if (type == DataComponents.PROFILE && (stack.is(Items.PLAYER_HEAD)
                || Customize.PLAYER_HEAD_MODEL.equals(config.customItemModel.get(uuid)))) {
            String texture = config.customHelmetTextures.get(uuid);
            if (texture != null) {
                return (T) com.teslamaps.features.customize.CustomHelmetTextures.getProfile(texture);
            }
            String animatedId = config.customAnimatedHelmetTextures.get(uuid);
            ResolvableProfile frame = animatedId == null ? null : CustomAnimatedHelmetTextures.animateHeadTexture(animatedId);
            if (frame != null) return (T) frame;
        } else if (type == DataComponents.ENCHANTMENT_GLINT_OVERRIDE) {
            Boolean glint = config.customGlint.get(uuid);
            if (glint != null) return (T) glint;
        } else if (type == DataComponents.ITEM_MODEL) {
            Identifier id = config.customItemModel.get(uuid);
            if (id != null) return (T) id;
            if (Customize.hasDye(uuid)) {
                Identifier leather = Customize.leatherModel(stack);
                if (leather != null) return (T) leather;
            }
        }

        return original;
    }
}

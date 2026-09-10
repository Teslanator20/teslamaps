package com.teslamaps.mixin;

import com.teslamaps.features.BackpackPreview;
import com.teslamaps.features.ScrollableTooltip;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

@Mixin(GuiGraphicsExtractor.class)
public class ScrollableTooltipMixin {
    // wrap the positioner so the offset lands after vanilla's on-screen clamp
    @ModifyVariable(method = "tooltip", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private ClientTooltipPositioner teslamaps$scrollTooltip(ClientTooltipPositioner positioner) {
        return ScrollableTooltip.wrap(positioner);
    }

    @ModifyVariable(
        method = "setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Ljava/util/List;Ljava/util/Optional;IILnet/minecraft/resources/Identifier;)V",
        at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private List<Component> teslamaps$prepareBackpackTooltip(List<Component> lines) {
        return BackpackPreview.prepareTooltip(lines);
    }

    @ModifyVariable(method = "setTooltipForNextFrameInternal", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private List<ClientTooltipComponent> teslamaps$insertBackpackGrid(List<ClientTooltipComponent> components) {
        BackpackPreview.applyGrid(components);
        return components;
    }
}

package com.teslamaps.mixin;

import com.teslamaps.features.BackpackPreview;
import com.teslamaps.features.ScrollableTooltip;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

@Mixin(GuiGraphicsExtractor.class)
public class ScrollableTooltipMixin {
    // tooltip(Font, List, int mouseX, int mouseY, positioner, Identifier) -> offset mouseY (2nd int arg)
    @ModifyVariable(method = "tooltip", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private int teslamaps$scrollTooltip(int mouseY) {
        return ScrollableTooltip.applyY(mouseY);
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

package com.teslamaps.mixin;

import com.teslamaps.features.BackpackPreview;
import com.teslamaps.features.ScrollableTooltip;
import com.teslamaps.features.TooltipFit;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(GuiGraphicsExtractor.class)
public class ScrollableTooltipMixin {
    // wrap the positioner so the offset lands after vanilla's on-screen clamp
    @ModifyVariable(method = "tooltip", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private ClientTooltipPositioner teslamaps$scrollTooltip(ClientTooltipPositioner positioner) {
        return TooltipFit.wrap(ScrollableTooltip.wrap(positioner));
    }

    // shrink tooltips taller than the screen - vanilla only clamps their bottom edge
    @Inject(method = "tooltip", at = @At("HEAD"))
    private void teslamaps$fitPush(Font font, List<ClientTooltipComponent> lines, int x, int y,
                                   ClientTooltipPositioner positioner, Identifier background, CallbackInfo ci) {
        GuiGraphicsExtractor self = (GuiGraphicsExtractor) (Object) this;
        float f = TooltipFit.begin(font, lines, self.guiHeight());
        if (f < 1f) self.pose().pushMatrix().scale(f, f);
    }

    @Inject(method = "tooltip", at = @At("RETURN"))
    private void teslamaps$fitPop(Font font, List<ClientTooltipComponent> lines, int x, int y,
                                  ClientTooltipPositioner positioner, Identifier background, CallbackInfo ci) {
        if (TooltipFit.scale() < 1f) ((GuiGraphicsExtractor) (Object) this).pose().popMatrix();
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

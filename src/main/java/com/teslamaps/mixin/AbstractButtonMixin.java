package com.teslamaps.mixin;

import com.teslamaps.render.gui.TeslaGui;
import com.teslamaps.render.gui.TeslaGuiStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Swaps the vanilla button sprite for a glass panel on the restyled screens.
 * extractDefaultSprite is the sprite-only step, so the label still comes from
 * vanilla on top of our shapes.
 */
@Mixin(AbstractButton.class)
public abstract class AbstractButtonMixin extends AbstractWidget {

    protected AbstractButtonMixin() {
        super(0, 0, 0, 0, null);
    }

    @Inject(method = "extractDefaultSprite", at = @At("HEAD"), cancellable = true)
    private void teslamaps$glassSprite(GuiGraphicsExtractor ctx, CallbackInfo ci) {
        if (!TeslaGuiStyle.active()) return;
        TeslaGui.glassButton(ctx, getX(), getY(), getWidth(), getHeight(),
                isHoveredOrFocused(), this.active);
        ci.cancel();
    }
}

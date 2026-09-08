package com.teslamaps.mixin;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.TeslaGui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws vanilla buttons in the mod's glass style - the pause menu, options screens and every other
 * plain button.
 * <p>
 * Only the background sprite is replaced. The label, the action, the tooltip and the active state
 * all stay vanilla, which is why this hooks {@code extractDefaultSprite} instead of swapping the
 * widgets: {@code onPress} takes an input object, so re-creating buttons would mean guessing what
 * to hand it.
 * <p>
 * Geometry is read through a plain cast, not {@code @Shadow}: getX/getY/getWidth/getHeight and
 * isActive live on AbstractWidget, and a shadow only resolves against the target class itself -
 * shadowing them here crashed the game during bootstrap.
 */
@Mixin(AbstractButton.class)
public abstract class VanillaButtonStyleMixin {

    @Inject(method = "extractDefaultSprite", at = @At("HEAD"), cancellable = true)
    private void teslamaps$glassButton(GuiGraphicsExtractor ctx, CallbackInfo ci) {
        if (!TeslaMapsConfig.get().modernVanillaButtons) return;
        AbstractButton self = (AbstractButton) (Object) this;
        TeslaGui.glassButton(ctx, self.getX(), self.getY(), self.getWidth(), self.getHeight(),
                self.isHoveredOrFocused(), self.isActive());
        ci.cancel();
    }
}

package com.teslamaps.mixin;

import com.teslamaps.features.SlotLock;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class SlotLockRenderMixin {
    @Inject(method = "extractContents", at = @At("TAIL"))
    private void teslamaps$slotLockRender(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        SlotLock.render(ctx);
        com.teslamaps.features.ItemProtection.renderSlots(ctx);
        com.teslamaps.features.CompactorPreview.renderStatus(ctx);
        com.teslamaps.features.WardrobeKeys.render(ctx);
        com.teslamaps.features.HoveredSlot.render(ctx, mouseX, mouseY);
        com.teslamaps.features.SkillLevelStack.render(ctx);
        com.teslamaps.features.DungeonLevelSlotText.render(ctx);
        com.teslamaps.features.MenuSlotText.render(ctx);
        com.teslamaps.features.garden.ComposterMenu.renderTips(ctx);
        com.teslamaps.features.MenuHighlights.renderTips(ctx);
        com.teslamaps.features.AttributeOverlay.render(ctx);
        com.teslamaps.features.BrewingStandOverlay.render(ctx);
        com.teslamaps.features.MagicalPowerStack.render(ctx);
        com.teslamaps.features.TeleportPads.render(ctx);
        com.teslamaps.features.qol.TiaRelayHelper.render(ctx);
    }
}

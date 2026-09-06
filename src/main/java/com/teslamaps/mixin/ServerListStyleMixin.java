package com.teslamaps.mixin;

import com.teslamaps.render.gui.TeslaGui;
import com.teslamaps.render.gui.TeslaGuiStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Turns the server list into glass cards: no vanilla list background, no row
 * separators, one rounded card per entry and an accent outline on the selected
 * one. Scoped to the server list so every other vanilla list is untouched.
 */
@Mixin(AbstractSelectionList.class)
public abstract class ServerListStyleMixin {

    @Unique private static final float CARD_RADIUS = 5.0f;
    @Unique private static final float CARD_INSET = 1.0f;

    @Unique
    private boolean teslamaps$styled() {
        return TeslaGuiStyle.active() && (Object) this instanceof ServerSelectionList;
    }

    @Inject(method = "extractListBackground", at = @At("HEAD"), cancellable = true)
    private void teslamaps$noListBackground(GuiGraphicsExtractor ctx, CallbackInfo ci) {
        if (teslamaps$styled()) ci.cancel();
    }

    @Inject(method = "extractListSeparators", at = @At("HEAD"), cancellable = true)
    private void teslamaps$noSeparators(GuiGraphicsExtractor ctx, CallbackInfo ci) {
        if (teslamaps$styled()) ci.cancel();
    }

    @Inject(method = "extractItem", at = @At("HEAD"))
    private void teslamaps$cardBehindItem(GuiGraphicsExtractor ctx, int mouseX, int mouseY,
                                           float partialTick,
                                           AbstractSelectionList.Entry<?> entry, CallbackInfo ci) {
        if (!teslamaps$styled()) return;

        boolean hovered = entry.isMouseOver(mouseX, mouseY);
        TeslaGui.glassPanel(ctx,
                entry.getX() + CARD_INSET, entry.getY() + CARD_INSET,
                entry.getWidth() - CARD_INSET * 2.0f, entry.getHeight() - CARD_INSET * 2.0f,
                CARD_RADIUS,
                hovered ? 18 : 10,
                hovered ? 26 : 14,
                10,
                8);
    }

    @Inject(method = "extractSelection", at = @At("HEAD"), cancellable = true)
    private void teslamaps$accentSelection(GuiGraphicsExtractor ctx,
                                            AbstractSelectionList.Entry<?> entry,
                                            int index, CallbackInfo ci) {
        if (!teslamaps$styled()) return;

        TeslaGui.roundedOutline(ctx,
                entry.getX() + CARD_INSET, entry.getY() + CARD_INSET,
                entry.getWidth() - CARD_INSET * 2.0f, entry.getHeight() - CARD_INSET * 2.0f,
                CARD_RADIUS, 1.5f, TeslaGui.withAlpha(0x5AC8FA, 200));
        ci.cancel();
    }
}

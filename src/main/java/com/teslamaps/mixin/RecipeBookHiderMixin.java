package com.teslamaps.mixin;

// Ported from Skysoft's AbstractRecipeBookScreenMixin
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractRecipeBookScreen.class)
public class RecipeBookHiderMixin {

    @Shadow @Final private RecipeBookComponent<?> recipeBookComponent;

    @Inject(method = "init", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeBookComponent;updateScreenPosition(II)I"))
    private void teslamaps$hidePanel(CallbackInfo ci) {
        if (teslamaps$hide()) ((RecipeBookComponentAccessor) recipeBookComponent).teslamaps$setVisible(false);
    }

    @Inject(method = "initButton", at = @At("HEAD"), cancellable = true)
    private void teslamaps$skipButton(CallbackInfo ci) {
        if (teslamaps$hide()) ci.cancel();
    }

    @WrapOperation(method = "extractSlots", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeBookComponent;extractGhostRecipe(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Z)V"))
    private void teslamaps$skipGhostRecipe(RecipeBookComponent<?> component, GuiGraphicsExtractor graphics, boolean bigResultSlot, Operation<Void> original) {
        if (!teslamaps$hide()) original.call(component, graphics, bigResultSlot);
    }

    private boolean teslamaps$hide() {
        return (Object) this instanceof InventoryScreen
                && TeslaMapsConfig.get().hideRecipeBook
                && SkyblockUtils.isInSkyblock();
    }
}

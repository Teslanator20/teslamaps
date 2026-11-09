package com.teslamaps.adm.misc.mixin;

import com.teslamaps.adm.misc.RandomStuff;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ShareToLanScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Clean Menus: Back to Game, Options | Mods, (Open to LAN), Disconnect
@Mixin(PauseScreen.class)
public abstract class AdmPauseMenuMixin extends Screen {
    @Shadow private Button disconnectButton;

    protected AdmPauseMenuMixin(Component title) {
        super(title);
    }

    @Inject(method = "createPauseMenu", at = @At("HEAD"), cancellable = true)
    private void teslamaps$admCleanPauseMenu(CallbackInfo ci) {
        if (!RandomStuff.cleansMenus()) return;
        ci.cancel();
        Screen self = this;
        GridLayout grid = new GridLayout();
        grid.defaultCellSetting().padding(4, 4, 4, 0);
        GridLayout.RowHelper helper = grid.createRowHelper(2);
        helper.addChild(Button.builder(Component.translatable("menu.returnToGame"), b -> {
            minecraft.gui.setScreen(null);
            minecraft.mouseHandler.grabMouse();
        }).width(204).build(), 2);
        Button.Builder options = Button.builder(Component.translatable("menu.options"),
                b -> minecraft.setScreen(new OptionsScreen(self, minecraft.options, true)));
        Screen mods = RandomStuff.modsScreen(self);
        if (mods != null) {
            helper.addChild(options.width(98).build());
            helper.addChild(Button.builder(Component.literal("Mods"), b -> minecraft.gui.setScreen(RandomStuff.modsScreen(self))).width(98).build());
        } else {
            helper.addChild(options.width(204).build(), 2);
        }
        if (minecraft.hasSingleplayerServer())
            helper.addChild(Button.builder(Component.translatable("menu.shareToLan"),
                    b -> minecraft.setScreen(new ShareToLanScreen(self))).width(204).build(), 2);
        disconnectButton = helper.addChild(Button.builder(CommonComponents.disconnectButtonLabel(minecraft.isLocalServer()), b -> {
            b.active = false;
            minecraft.getReportingContext().draftReportHandled(minecraft, self, () -> minecraft.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE), true);
        }).width(204).build(), 2);
        grid.arrangeElements();
        FrameLayout.alignInRectangle(grid, 0, 0, width, height, 0.5F, 0.5F);
        grid.visitWidgets(this::addRenderableWidget);
    }
}

package com.teslamaps.mixin;

// Ported from Skysoft's ChatScreenMixin tab hooks
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.teslamaps.features.chattabs.ChatTabButton;
import com.teslamaps.features.chattabs.ChatTabs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class ChatTabsScreenMixin extends Screen {

    @Shadow protected EditBox input;

    @Unique private boolean teslamaps$selectedTab = false;

    protected ChatTabsScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void teslamaps$addTabButtons(CallbackInfo ci) {
        if (!ChatTabs.enabled()) return;
        for (ChatTabs.TabBounds bounds : ChatTabs.layout(Minecraft.getInstance())) {
            addRenderableWidget(new ChatTabButton(bounds, () -> {
                ChatTabs.select(bounds.channel());
                teslamaps$selectedTab = true;
            }));
        }
    }

    @WrapOperation(method = "handleChatInput", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;sendChat(Ljava/lang/String;)V"))
    private void teslamaps$sendToActiveTab(ClientPacketListener connection, String message, Operation<Void> original) {
        String command = ChatTabs.prepareOutgoingCommand(message);
        if (command == null) {
            original.call(connection, message);
        } else {
            ChatTabs.recordOutgoingCommand(command);
            connection.sendCommand(command);
        }
    }

    @WrapOperation(method = "handleChatInput", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;sendCommand(Ljava/lang/String;)V"))
    private void teslamaps$recordTabCommand(ClientPacketListener connection, String command, Operation<Void> original) {
        ChatTabs.recordOutgoingCommand(command);
        original.call(connection, command);
    }

    @Inject(method = "keyPressed", at = @At("RETURN"))
    private void teslamaps$refocusAfterKey(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        teslamaps$restoreFocus();
    }

    @Inject(method = "mouseClicked", at = @At("RETURN"))
    private void teslamaps$refocusAfterClick(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        teslamaps$restoreFocus();
    }

    @Unique
    private void teslamaps$restoreFocus() {
        if (!teslamaps$selectedTab) return;
        setFocused(input);
        teslamaps$selectedTab = false;
    }
}

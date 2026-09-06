package com.teslamaps.mixin;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.MenuBackgroundRenderer;
import com.teslamaps.screen.TeslaMenuButton;
import com.teslamaps.screen.TeslaSettingsButton;
import com.teslamaps.utils.AnimClock;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    @Unique private static final String HYPIXEL_IP = "mc.hypixel.net";
    @Unique private static final int BUTTON_HEIGHT = 20;
    @Unique private static final int SPACING = 6;

    @Unique private int teslamaps$titleY;

    protected TitleScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void teslamaps$buildMenu(CallbackInfo ci) {
        // The two corner buttons belong to the mod, not to the custom menu, so they
        // stay put even when the vanilla title screen is showing.
        if (!TeslaMapsConfig.get().customTitleScreen) {
            teslamaps$addCornerButtons();
            return;
        }

        clearWidgets();

        int buttonWidth = Math.clamp(Math.round(this.width * 0.38f), 190, 220);
        int titleHeight = 24;
        int titleGap = 12;
        int rows = 3;
        int menuHeight = BUTTON_HEIGHT * rows + SPACING * (rows - 1);
        int top = Math.max(20, (this.height - (titleHeight + titleGap + menuHeight)) / 2);

        this.teslamaps$titleY = top;

        int x = this.width / 2 - buttonWidth / 2;
        int startY = top + titleHeight + titleGap;

        addRenderableWidget(new TeslaMenuButton(x, startY, buttonWidth, BUTTON_HEIGHT,
                Component.literal("Hypixel"), this::teslamaps$connectToHypixel));

        TeslaMenuButton multiplayer = new TeslaMenuButton(x, startY + BUTTON_HEIGHT + SPACING,
                buttonWidth, BUTTON_HEIGHT, Component.translatable("menu.multiplayer"),
                () -> this.minecraft.setScreen(new JoinMultiplayerScreen(this)));
        multiplayer.active = this.minecraft.allowsMultiplayer();
        addRenderableWidget(multiplayer);

        int half = (buttonWidth - SPACING) / 2;
        int bottomY = startY + (BUTTON_HEIGHT + SPACING) * 2;

        addRenderableWidget(new TeslaMenuButton(x, bottomY, half, BUTTON_HEIGHT,
                Component.literal("Options"),
                () -> this.minecraft.setScreen(new OptionsScreen(this, this.minecraft.options, false))));

        addRenderableWidget(new TeslaMenuButton(x + half + SPACING, bottomY, half, BUTTON_HEIGHT,
                Component.translatable("menu.quit"), this.minecraft::stop));

        addRenderableWidget(new TeslaMenuButton(x, bottomY + BUTTON_HEIGHT + SPACING,
                buttonWidth, BUTTON_HEIGHT, Component.translatable("menu.singleplayer"),
                () -> this.minecraft.setScreen(new SelectWorldScreen(this))));

        teslamaps$addCornerButtons();
    }

    @Unique
    private void teslamaps$addCornerButtons() {
        int size = 20;
        addRenderableWidget(new TeslaSettingsButton(this.width - size - 8, 8, size));
        addRenderableWidget(new com.teslamaps.screen.TeslaLookButton(this.width - size * 2 - 12, 8, size));
    }

    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
    private void teslamaps$skipVanillaBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY,
                                                  float partialTick, CallbackInfo ci) {
        if (TeslaMapsConfig.get().customTitleScreen) ci.cancel();
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void teslamaps$renderCustomMenu(GuiGraphicsExtractor ctx, int mouseX, int mouseY,
                                             float partialTick, CallbackInfo ci) {
        if (!TeslaMapsConfig.get().customTitleScreen) return;

        AnimClock.update();

        MenuBackgroundRenderer.render(ctx);

        super.extractRenderState(ctx, mouseX, mouseY, partialTick);
        teslamaps$drawLabels(ctx);
        ci.cancel();
    }

    @Unique
    private void teslamaps$drawLabels(GuiGraphicsExtractor ctx) {
        int accent = MenuBackgroundRenderer.accentColor();
        // Halo in the opposite direction of the accent's brightness, so the title
        // keeps its edge whatever the background tint is set to.
        int halo = teslamaps$isDark(accent) ? 0x59FFFFFF : 0x8C000000;
        int brandX = -this.font.width("TESLAMAPS") / 2;

        ctx.pose().pushMatrix();
        ctx.pose().translate(this.width / 2.0f, this.teslamaps$titleY);
        ctx.pose().scale(2.5f, 2.5f);
        ctx.pose().pushMatrix();
        ctx.pose().translate(0.4f, 0.4f);
        ctx.text(this.font, "TESLAMAPS", brandX, 0, halo, false);
        ctx.pose().popMatrix();
        ctx.text(this.font, "TESLAMAPS", brandX, 0, accent, false);
        ctx.pose().popMatrix();

        String info = "TeslaMaps " + teslamaps$version() + " | " + this.minecraft.getUser().getName();
        ctx.text(this.font, info, 8, 8, 0xFFF4FAFF, true);
    }

    @Unique
    private static boolean teslamaps$isDark(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return (r * 299 + g * 587 + b * 114) / 1000 < 128;
    }

    @Unique
    private static String teslamaps$version() {
        return net.fabricmc.loader.api.FabricLoader.getInstance()
                .getModContainer(com.teslamaps.TeslaMaps.MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("dev");
    }

    @Unique
    private void teslamaps$connectToHypixel() {
        ServerData data = new ServerData("Hypixel", HYPIXEL_IP, ServerData.Type.OTHER);
        data.setResourcePackStatus(ServerData.ServerPackStatus.ENABLED);
        ConnectScreen.startConnecting(this, this.minecraft,
                ServerAddress.parseString(data.ip), data, false, null);
    }
}

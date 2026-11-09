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
            if (TeslaMapsConfig.get().quickJoinButton) teslamaps$addQuickJoin();
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
                () -> this.minecraft.gui.setScreen(new JoinMultiplayerScreen(this)));
        multiplayer.active = this.minecraft.allowsMultiplayer();
        addRenderableWidget(multiplayer);

        int half = (buttonWidth - SPACING) / 2;
        int bottomY = startY + (BUTTON_HEIGHT + SPACING) * 2;

        addRenderableWidget(new TeslaMenuButton(x, bottomY, half, BUTTON_HEIGHT,
                Component.literal("Options"),
                () -> this.minecraft.gui.setScreen(new OptionsScreen(this, this.minecraft.options))));

        addRenderableWidget(new TeslaMenuButton(x + half + SPACING, bottomY, half, BUTTON_HEIGHT,
                Component.translatable("menu.quit"), this.minecraft::stop));

        addRenderableWidget(new TeslaMenuButton(x, bottomY + BUTTON_HEIGHT + SPACING,
                buttonWidth, BUTTON_HEIGHT, Component.translatable("menu.singleplayer"),
                () -> this.minecraft.gui.setScreen(new SelectWorldScreen(this))));

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

        // Letter-spaced wordmark with a hairline rule instead of the old 2.5x font with a drop
        // halo - the halo read as a game logo and fought whatever background is set.
        String brand = "TESLAMAPS";
        float scale = 1.9f;
        int gap = 3;
        int plain = this.font.width(brand);
        int spaced = plain + gap * (brand.length() - 1);

        ctx.pose().pushMatrix();
        ctx.pose().translate(this.width / 2.0f, this.teslamaps$titleY);
        ctx.pose().scale(scale, scale);

        int x = -spaced / 2;
        for (int i = 0; i < brand.length(); i++) {
            String ch = String.valueOf(brand.charAt(i));
            ctx.text(this.font, ch, x, 0, accent, false);
            x += this.font.width(ch) + gap;
        }

        // the rule sits under the glyphs, a touch narrower so it reads as an underline
        int ruleY = this.font.lineHeight + 3;
        int inset = 2;
        ctx.fill(-spaced / 2 + inset, ruleY, spaced / 2 - inset, ruleY + 1,
                teslamaps$withAlpha(accent, 170));
        ctx.pose().popMatrix();

        String info = "TeslaMaps " + teslamaps$version() + " | " + this.minecraft.getUser().getName();
        ctx.text(this.font, info, 8, 8, 0xFFF4FAFF, true);
    }

    @Unique
    private static int teslamaps$withAlpha(int argb, int alpha) {
        return (argb & 0x00FFFFFF) | (alpha << 24);
    }


    @Unique
    private static String teslamaps$version() {
        return net.fabricmc.loader.api.FabricLoader.getInstance()
                .getModContainer(com.teslamaps.TeslaMaps.MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("dev");
    }

    @Unique
    private void teslamaps$addQuickJoin() {
        for (var child : children()) {
            if (!(child instanceof net.minecraft.client.gui.components.Button button)) continue;
            if (!(button.getMessage().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t)
                    || !"menu.multiplayer".equals(t.getKey())) continue;
            int half = (button.getWidth() - 4) / 2;
            int full = button.getWidth();
            button.setWidth(half);
            addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                            Component.literal("Join " + teslamaps$ip()), b -> this.teslamaps$connectToHypixel())
                    .bounds(button.getX() + full - half, button.getY(), half, button.getHeight()).build());
            return;
        }
    }

    @Unique
    private static String teslamaps$ip() {
        String ip = TeslaMapsConfig.get().quickJoinIp;
        return ip == null || ip.isBlank() ? HYPIXEL_IP : ip.trim();
    }

    @Unique
    private void teslamaps$connectToHypixel() {
        ServerData data = new ServerData("Hypixel", TeslaMapsConfig.get().quickJoinButton ? teslamaps$ip() : HYPIXEL_IP, ServerData.Type.OTHER);
        data.setResourcePackStatus(ServerData.ServerPackStatus.ENABLED);
        ConnectScreen.startConnecting(this, this.minecraft,
                ServerAddress.parseString(data.ip), data, false, null);
    }
}

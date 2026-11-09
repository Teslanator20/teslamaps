package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.ChatComponentAccessor;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.world.entity.player.ChatVisiblity;

/**
 * Draws shared pictures into the gap of empty lines under their message.
 * <p>
 * This runs as a hud element on purpose. Inside ChatComponent's own render the pose is still
 * translated into chat space and the x it hands over is not a screen position, so anything
 * drawn there lands in the wrong place and drifts.
 */
public class SharedImageChatRenderer {

    private static final int CHAT_LEFT = 4;
    private static final int CHAT_BOTTOM_OFFSET = 40;
    private static final int MARGIN = 8;

    /** Where each picture ended up this frame, so a click can find it again. */
    public record Hit(String url, String sender, int x, int y, int width, int height) {
        public boolean contains(double mx, double my) {
            return mx >= x && mx < x + width && my >= y && my < y + height;
        }
    }

    private static final java.util.List<Hit> HITS = new java.util.ArrayList<>();

    // the transform the chat was last drawn with, see ChatGeometryMixin
    private static final org.joml.Matrix3x2f CHAT_POSE = new org.joml.Matrix3x2f();
    private static int chatGuiHeight = -1;

    public static void captureGeometry(org.joml.Matrix3x2f pose, int guiHeight) {
        CHAT_POSE.set(pose);
        chatGuiHeight = guiHeight;
    }

    public static Hit hitAt(double mouseX, double mouseY) {
        for (Hit hit : HITS) {
            if (hit.contains(mouseX, mouseY)) return hit;
        }
        return null;
    }

    private static Hit screenHit(String url, String sender, int x, int y, int w, int h) {
        org.joml.Vector2f min = CHAT_POSE.transformPosition(new org.joml.Vector2f(x, y));
        org.joml.Vector2f max = CHAT_POSE.transformPosition(new org.joml.Vector2f(x + w, y + h));
        int sx = (int) Math.min(min.x, max.x);
        int sy = (int) Math.min(min.y, max.y);
        int sw = (int) Math.abs(max.x - min.x);
        int sh = (int) Math.abs(max.y - min.y);
        return new Hit(url, sender, sx, sy, Math.max(1, sw), Math.max(1, sh));
    }

    private static String senderOf(String line) {
        int colon = line.indexOf(':');
        return colon > 0 ? line.substring(0, colon) : line;
    }

    public static void render(GuiGraphicsExtractor ctx, DeltaTracker delta) {
        render(ctx, false, -1, -1);
        peek(ctx);
    }

    public static void render(GuiGraphicsExtractor ctx, boolean fromChatScreen, int mouseX, int mouseY) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.imageShareReceive) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.hud.isHidden()) return;
        if (mc.options.chatVisibility().get() == ChatVisiblity.HIDDEN) return;
        // with the chat open the screen dims everything the hud drew, so ChatScreenMixin
        // repeats this on top instead and the hud pass steps aside
        if (!fromChatScreen && mc.gui.screen() instanceof net.minecraft.client.gui.screens.ChatScreen) return;

        ChatComponent chat = mc.gui.hud.getChat();
        ChatComponentAccessor acc = (ChatComponentAccessor) chat;
        List<GuiMessage.Line> lines = acc.teslamaps$getTrimmedMessages();
        if (lines.isEmpty()) return;

        double scale = acc.teslamaps$getScale();
        int lineHeight = acc.teslamaps$getLineHeight();
        if (scale <= 0 || lineHeight <= 0) return;

        // the height the chat itself laid out with, not the window's - a chat mod can hand it a
        // different one when it draws the chat into its own hud
        int guiHeight = chatGuiHeight > 0 ? chatGuiHeight : mc.getWindow().getGuiScaledHeight();
        int scroll = acc.teslamaps$getChatScrollbarPos();
        int visible = chat.getLinesPerPage();
        int gap = Math.max(1, c.imageShareChatLines);

        float rowHeight = (float) (lineHeight * scale);
        // The instance getter on purpose, not ChatComponent.getWidth(options): chat mods like
        // Polyfrost's Chatting cancel getWidth() at HEAD and return their own width, and reading
        // the vanilla option instead put the images at a width the chat no longer uses.
        float maxWidth = (float) (chat.getWidth() * scale);

        boolean focused = chat.isChatFocused();
        int guiTicks = mc.gui.hud.getGuiTicks();
        float chatBottom = guiHeight - CHAT_BOTTOM_OFFSET;
        float chatTop = chatBottom - visible * rowHeight;

        // vanilla clips the chat to its own box, so the images have to stay inside it too
        HITS.clear();
        ctx.pose().pushMatrix();
        ctx.pose().mul(CHAT_POSE);
        ctx.enableScissor(0, (int) chatTop, mc.getWindow().getGuiScaledWidth(), (int) chatBottom);
        try {
        for (int i = scroll; i < Math.min(scroll + visible + gap, lines.size()); i++) {
            GuiMessage.Line line = lines.get(i);
            // closed chat fades its messages out after a while, do not leave the picture behind
            if (!focused && guiTicks - line.addedTime() >= 200) continue;
            // the padding rows share the message's parent, so they would match it as well
            if (SharedImageChat.isPad(line)) continue;

            String text = line.parent().content().getString();
            String url = SharedImageChat.urlForLine(text);
            if (url == null) continue;
            // a wrapped message matches on every row, the picture belongs under the last one
            if (i > 0 && lines.get(i - 1).parent() == line.parent() && !SharedImageChat.isPad(lines.get(i - 1))) continue;

            // Rows are counted up from the chat baseline, so a lower index sits further down.
            // The padding was appended after the message and therefore lives below it: the gap
            // starts at this row's bottom edge and runs downwards.
            float top = guiHeight - CHAT_BOTTOM_OFFSET - (i - scroll) * rowHeight;
            float bottom = top + gap * rowHeight;
            if (bottom < 0 || top > guiHeight) continue;

            float boxHeight = bottom - top;
            SharedImageLoader.Loaded image = SharedImageLoader.cached(url);

            if (image == null) {
                String note = SharedImageLoader.error(url);
                ctx.fill(CHAT_LEFT, (int) top, CHAT_LEFT + (int) maxWidth, (int) bottom, 0x60000000);
                ctx.text(mc.font, note != null ? "§c" + note : "§7Loading image...",
                        CHAT_LEFT + 3, (int) (top + boxHeight / 2 - mc.font.lineHeight / 2f), 0xFFFFFFFF);
                continue;
            }

            float fit = Math.min(maxWidth / image.width(), boxHeight / image.height());
            int w = Math.max(1, (int) (image.width() * fit));
            int h = Math.max(1, (int) (image.height() * fit));
            int iy = (int) (top + (boxHeight - h) / 2);

            ctx.fill(CHAT_LEFT - 1, iy - 1, CHAT_LEFT + w + 1, iy + h + 1, 0x60000000);
            ctx.blit(image.id(), CHAT_LEFT, iy, CHAT_LEFT + w, iy + h, 0f, 1f, 0f, 1f);
            // clicks arrive in screen space, so the box has to leave chat space behind
            HITS.add(screenHit(url, senderOf(text), CHAT_LEFT, iy, w, h));
        }
        } finally {
            ctx.disableScissor();
            ctx.pose().popMatrix();
        }

        if (fromChatScreen) {
            Hit hovered = hitAt(mouseX, mouseY);
            if (hovered != null) large(ctx, mc, hovered.url(), hovered.sender(), fullSize());
        }
    }

    /** Hold the configured key to blow up the newest picture, no chat needed. */
    private static void peek(GuiGraphicsExtractor ctx) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (c.imagePeekKey == -1) return;

        Minecraft mc = Minecraft.getInstance();
        if (com.teslamaps.utils.HudVisibility.suppressed()) return;
        if (!com.teslamaps.utils.KeyNames.isDown(c.imagePeekKey)) return;

        String url = SharedImageChat.newestUrl();
        if (url != null) large(ctx, mc, url, SharedImageChat.newestSender(), fullSize());
    }

    private static boolean fullSize() {
        long window = Minecraft.getInstance().getWindow().handle();
        return com.mojang.blaze3d.platform.InputConstants.isKeyDown(com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT)
                || com.mojang.blaze3d.platform.InputConstants.isKeyDown(com.mojang.blaze3d.platform.InputConstants.KEY_RSHIFT);
    }

    /** Centered. A quarter of the screen normally, everything it can get while shift is held. */
    private static void large(GuiGraphicsExtractor ctx, Minecraft mc, String url, String caption, boolean full) {
        SharedImageLoader.Loaded image = SharedImageLoader.cached(url);
        if (image == null) return;

        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        // a quarter of the screen area, so half the width and half the height
        float boxW = full ? screenW - 40f : screenW / 2f;
        float boxH = full ? screenH - 60f : screenH / 2f;
        float fit = Math.min(boxW / image.width(), boxH / image.height());
        int w = Math.max(1, (int) (image.width() * fit));
        int h = Math.max(1, (int) (image.height() * fit));
        // the small one sits in the top right corner, out of the way of the chat
        int x = full ? (screenW - w) / 2 : screenW - w - MARGIN;
        int y = full ? (screenH - h) / 2 : MARGIN + 11;

        if (full) ctx.fill(0, 0, screenW, screenH, 0xC0000000);
        ctx.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFF000000);
        ctx.blit(image.id(), x, y, x + w, y + h, 0f, 1f, 0f, 1f);
        if (caption != null) ctx.text(mc.font, "§f" + caption, x, y - 11, 0xFFFFFFFF);
        if (!full) ctx.text(mc.font, "§8Shift", x, y + h + 3, 0xFFFFFFFF);
    }
}

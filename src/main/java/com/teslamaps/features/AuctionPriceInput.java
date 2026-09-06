// Adapted from NoammAddons (CC0 1.0), see NOTICE.md
package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.AbstractSignEditScreenAccessor;
import com.teslamaps.render.gui.TeslaGui;
import com.teslamaps.screen.AppleColors;
import com.teslamaps.utils.ItemPrice;
import com.teslamaps.utils.ItemUtil;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;
import java.util.Set;

/** Replaces the auction starting-bid sign with a real textbox, plus an undercut mode. */
public class AuctionPriceInput {

    private static final Set<String> GUI_NAMES = Set.of("Create BIN Auction", "Create Auction");
    private static final Set<String> CONFIRM_NAMES = Set.of("Confirm BIN Auction", "Confirm Auction");

    private static final int NORMAL = 0;
    private static final int UNDERCUT = 1;

    private static ItemStack item = null;
    private static int mode = -1;
    private static String input = "";

    public static void init() {
        ScreenEvents.AFTER_INIT.register((mc, screen, width, height) -> {
            if (!TeslaMapsConfig.get().auctionPriceInput) return;
            if (!(screen instanceof AbstractSignEditScreen)) return;

            ItemStack stack = item;
            if (stack == null) return;

            SignBlockEntity sign = ((AbstractSignEditScreenAccessor) screen).getSign();
            if (sign == null) return;

            String[] lines = new String[4];
            for (int i = 0; i < 4; i++) lines[i] = sign.getFrontText().getMessage(i, false).getString();

            if (!lines[1].equals("^^^^^^^^^^^^^^^") || !lines[2].equals("Your auction") || !lines[3].equals("starting bid")) return;

            // set the screen by hand so the sign gui does not close
            mc.execute(() -> {
                AuctionInputScreen next = new AuctionInputScreen(sign, lines, stack);
                mc.screen = next;
                next.init(width, height);
            });
        });
    }

    /** Called from HandledScreenClickMixin - remembers the item being sold. */
    public static void onSlotClick(AbstractContainerScreen<?> screen, Slot slot) {
        if (!TeslaMapsConfig.get().auctionPriceInput) return;
        if (slot == null || slot.index != 31) return;
        if (!GUI_NAMES.contains(strip(screen.getTitle().getString()))) return;

        ItemStack stack = screen.getMenu().getSlot(13).getItem();
        item = ItemUtil.skyblockId(stack).isEmpty() ? null : stack;
    }

    /** Called from ContainerKeyMixin - enter confirms the auction. Returns true if handled. */
    public static boolean onContainerKey(AbstractContainerScreen<?> screen, int key) {
        if (!TeslaMapsConfig.get().auctionPriceInput) return false;
        if (key != GLFW.GLFW_KEY_ENTER && key != GLFW.GLFW_KEY_KP_ENTER) return false;

        String title = strip(screen.getTitle().getString());
        int slotId;
        Set<String> valid;
        if (GUI_NAMES.contains(title)) {
            slotId = 29;
            valid = GUI_NAMES;
        } else if (CONFIRM_NAMES.contains(title)) {
            slotId = 11;
            valid = CONFIRM_NAMES;
        } else {
            return false;
        }

        if (slotId >= screen.getMenu().slots.size()) return false;
        ItemStack stack = screen.getMenu().getSlot(slotId).getItem();
        if (!stack.is(Blocks.GREEN_TERRACOTTA.asItem())) return false;
        if (!valid.contains(strip(stack.getHoverName().getString()))) return false;

        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null || mc.player == null) return false;
        mc.gameMode.handleContainerInput(screen.getMenu().containerId, slotId, 0, ContainerInput.PICKUP, mc.player);
        return true;
    }

    private static String strip(String s) {
        return s.replaceAll("(?i)§[0-9A-FK-OR]", "");
    }

    /** "10m" / "1.5b" / "250k" -> coins. Null when the text is not a number. */
    static Long parseCompact(String value) {
        if (value == null || value.isBlank()) return null;
        String clean = value.toLowerCase(Locale.ROOT).replace(",", "").trim();
        if (clean.isEmpty()) return null;

        try {
            return Long.parseLong(clean);
        } catch (NumberFormatException ignored) {
        }

        long multiplier = switch (clean.charAt(clean.length() - 1)) {
            case 'k' -> 1_000L;
            case 'm' -> 1_000_000L;
            case 'b' -> 1_000_000_000L;
            case 't' -> 1_000_000_000_000L;
            case 'p' -> 1_000_000_000_000_000L;
            case 'e' -> 1_000_000_000_000_000_000L;
            default -> 0L;
        };
        if (multiplier == 0L) return null;

        try {
            return (long) (Double.parseDouble(clean.substring(0, clean.length() - 1)) * multiplier);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static class AuctionInputScreen extends Screen {

        private static final int PANEL_W = 220;
        private static final int PANEL_H = 176;
        private static final int BUTTON_H = 20;

        private final SignBlockEntity sign;
        private final String[] originalText;
        private final ItemStack stack;

        private EditBox inputField;
        private Long parsedValue = null;
        private long lowestBin = 0L;

        private int panelX, panelY;
        private boolean wasMouseDown = false;

        AuctionInputScreen(SignBlockEntity sign, String[] originalText, ItemStack stack) {
            super(Component.literal("Auction Price Input"));
            this.sign = sign;
            this.originalText = originalText;
            this.stack = stack;
        }

        @Override
        protected void init() {
            super.init();

            TeslaMapsConfig config = TeslaMapsConfig.get();
            if (!config.auctionRememberText) input = "";
            if (!config.auctionRememberMode || mode < 0) {
                mode = "Undercut".equals(config.auctionDefaultMode) ? UNDERCUT : NORMAL;
            }

            ItemPrice.resolve(ItemUtil.skyblockId(stack), price -> {
                lowestBin = price.longValue();
                recalculateValue();
            });

            panelX = (width - PANEL_W) / 2;
            panelY = (height - PANEL_H) / 2;

            inputField = new EditBox(font, panelX + 22, inputBoxY() + 5, PANEL_W - 44, 12, Component.literal("Price"));
            inputField.setBordered(false);
            inputField.setMaxLength(32);
            inputField.setHint(Component.literal("10m, 5k, 1.5b"));
            inputField.setValue(input);
            recalculateValue();
            inputField.moveCursorToEnd(false);
            inputField.setHighlightPos(0);
            inputField.setResponder(text -> {
                input = text;
                recalculateValue();
            });

            addRenderableWidget(inputField);
            setInitialFocus(inputField);
        }

        private int inputBoxY() { return panelY + 82; }
        private int modeButtonY() { return panelY + 118; }
        private int doneButtonY() { return panelY + 144; }

        @Override
        public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
            boolean isMouseDown = GLFW.glfwGetMouseButton(minecraft.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
            boolean clicked = isMouseDown && !wasMouseDown;

            ctx.fill(0, 0, width, height, 0xC0000000);
            TeslaGui.hudPanel(ctx, panelX, panelY, PANEL_W, PANEL_H, 6);
            ctx.fill(panelX + 10, panelY, panelX + PANEL_W - 10, panelY + 3, AppleColors.ACCENT_GREEN);

            int centerX = panelX + PANEL_W / 2;

            ctx.text(font, "Auction Price", panelX + 16, panelY + 14, AppleColors.TEXT_PRIMARY);
            ctx.text(font, mode == UNDERCUT ? "Undercut mode" : "Starting bid",
                    panelX + 16, panelY + 26, AppleColors.TEXT_SECONDARY);

            int itemX = centerX - 8;
            int itemY = panelY + 44;
            ctx.item(stack, itemX, itemY);
            ctx.itemDecorations(font, stack, itemX, itemY);

            ctx.centeredText(font, "Lowest BIN: " + ItemPrice.format(lowestBin),
                    centerX, panelY + 66, AppleColors.TEXT_SECONDARY);

            int boxX = panelX + 16;
            int boxW = PANEL_W - 32;
            boolean focused = inputField != null && inputField.isFocused();
            ctx.fill(boxX, inputBoxY(), boxX + boxW, inputBoxY() + 22, 0xFF242426);
            drawBorder(ctx, boxX, inputBoxY(), boxW, 22, focused ? AppleColors.ACCENT_GREEN : 0xFF3A3A3C);

            String display;
            int displayColor;
            if (parsedValue != null) {
                display = formatComma(parsedValue) + " coins";
                displayColor = AppleColors.ACCENT_GREEN;
            } else if (input.isEmpty()) {
                display = "Enter a value";
                displayColor = AppleColors.TEXT_TERTIARY;
            } else {
                display = "Invalid format";
                displayColor = AppleColors.ACCENT_RED;
            }
            ctx.centeredText(font, display, centerX, panelY + 108, displayColor);

            boolean modeHover = button(ctx, boxX, modeButtonY(), boxW,
                    mode == UNDERCUT ? "Mode: Undercut" : "Mode: Normal", mouseX, mouseY);
            boolean doneHover = button(ctx, boxX, doneButtonY(), boxW, "Done", mouseX, mouseY);

            if (clicked && modeHover) {
                mode = mode == NORMAL ? UNDERCUT : NORMAL;
                recalculateValue();
            }

            super.extractRenderState(ctx, mouseX, mouseY, delta);

            // after the widgets, so the tooltip sits on top of everything
            if (mouseX >= itemX && mouseX <= itemX + 16 && mouseY >= itemY && mouseY <= itemY + 16) {
                ctx.setTooltipForNextFrame(font, stack, mouseX, mouseY);
            }

            wasMouseDown = isMouseDown;
            if (clicked && doneHover) finish();
        }

        /** Draws a row button in the config-screen style and reports whether it is hovered. */
        private boolean button(GuiGraphicsExtractor ctx, int x, int y, int w, String label, int mouseX, int mouseY) {
            boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + BUTTON_H;
            ctx.fill(x, y, x + w, y + BUTTON_H, hovered ? 0xFF3A3A3C : AppleColors.CARD_BACKGROUND);
            drawBorder(ctx, x, y, w, BUTTON_H, hovered ? 0xFF5A5A5C : AppleColors.INPUT_BORDER);
            ctx.centeredText(font, label, x + w / 2, y + 6, AppleColors.TEXT_PRIMARY);
            return hovered;
        }

        private void drawBorder(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int color) {
            ctx.fill(x, y, x + w, y + 1, color);
            ctx.fill(x, y + h - 1, x + w, y + h, color);
            ctx.fill(x, y, x + 1, y + h, color);
            ctx.fill(x + w - 1, y, x + w, y + h, color);
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
                finish();
                return true;
            }
            return super.keyPressed(event);
        }

        private void finish() {
            String line0 = parsedValue != null ? parsedValue.toString() : input;

            Minecraft mc = Minecraft.getInstance();
            if (mc.getConnection() != null) {
                mc.getConnection().send(new ServerboundSignUpdatePacket(
                        sign.getBlockPos(), true, line0, originalText[1], originalText[2], originalText[3]));
            }

            onClose();
        }

        private void recalculateValue() {
            Long textValue = parseCompact(input.replace(',', '.'));
            if (textValue == null) {
                parsedValue = null;
                return;
            }
            parsedValue = mode == UNDERCUT ? Math.max(0L, lowestBin - textValue) : textValue;
        }

        private static String formatComma(long value) {
            return String.format(Locale.US, "%,d", value);
        }

        @Override
        public boolean isPauseScreen() { return false; }
    }
}

package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.render.gui.TeslaGui;
import com.teslamaps.screen.AppleColors;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Farming and pest income, with a switchable time window. */
public class FarmProfitOverlay {

    private static final int PANEL_WIDTH = 166;
    private static final int PADDING = 10;
    private static final int LINE = 11;
    /** Space a divider takes: the hairline plus air on both sides. */
    private static final int DIVIDER = 7;


    /** Vanilla draws no panel, so the overlay goes bare: white, shadowed, no padding. */
    private static boolean plain() {
        return HudStyle.VANILLA.equals(HudStyle.current());
    }

    private static int pad() {
        return plain() ? 0 : PADDING;
    }

    private static int dividerSpace() {
        return plain() ? 0 : DIVIDER;
    }

    /** Muted greys need the panel behind them; without one everything goes white. */
    private static int col(int color) {
        if (!plain()) return color;
        return color == AppleColors.TEXT_SECONDARY || color == AppleColors.TEXT_TERTIARY
                ? AppleColors.TEXT_PRIMARY : color;
    }

    public static boolean shouldRender() {
        return TeslaMapsConfig.get().farmProfit && SkyblockUtils.isInGarden();
    }

    public static void render(GuiGraphicsExtractor context, DeltaTracker delta) {
        if (!shouldRender()) return;
        TeslaMapsConfig config = TeslaMapsConfig.get();
        draw(context, Minecraft.getInstance(), config.farmProfitX, config.farmProfitY,
                config.farmProfitScale);
    }

    private static int topItems() {
        return Math.max(0, TeslaMapsConfig.get().farmProfitRows);
    }

    private static int itemRows() {
        return Math.min(FarmProfit.entries().size(), topItems());
    }

    /** Y of the footer row, shared by the drawing and the reset hit area. */
    private static int footerY() {
        int items = itemRows();
        return pad() + LINE * 2 + dividerSpace() + LINE * items + (items > 0 ? dividerSpace() : 0);
    }

    private static int panelHeight() {
        return footerY() + LINE + pad() - 2;
    }

    public static int width(float scale) {
        return (int) (PANEL_WIDTH * scale);
    }

    public static int height(float scale) {
        return (int) (panelHeight() * scale);
    }

    private static void divider(GuiGraphicsExtractor context, int y) {
        if (plain()) return;
        TeslaGui.roundedRect(context, PADDING, y + DIVIDER / 2f - 0.5f,
                PANEL_WIDTH - PADDING * 2, 1, 0.5f, TeslaGui.withAlpha(0xFFFFFF, 28));
    }

    /** Cuts a label to the room the value leaves, marking it as shortened. */
    private static String fit(Minecraft mc, String label, int room) {
        if (mc.font.width(label) <= room) return label;
        while (label.length() > 1 && mc.font.width(label + "…") > room) {
            label = label.substring(0, label.length() - 1);
        }
        return label.stripTrailing() + "…";
    }

    public static void draw(GuiGraphicsExtractor context, Minecraft mc, int x, int y, float scale) {
        List<FarmProfit.Entry> entries = FarmProfit.entries();
        int items = itemRows();

        context.pose().pushMatrix();
        context.pose().translate(x, y);
        context.pose().scale(scale, scale);

        HudStyle.panel(context, 0, 0, PANEL_WIDTH, panelHeight(), 6);

        // Headline block: what was earned, and at what rate.
        int row = pad();
        drawRow(context, mc, "Profit", format(FarmProfit.totalCoins()),
                AppleColors.TEXT_SECONDARY, AppleColors.ACCENT_GREEN, row);

        row += LINE;
        double perHour = FarmProfit.coinsPerHour();
        drawRow(context, mc, "Per hour", perHour > 0 ? format(perHour) + "/h" : "-",
                AppleColors.TEXT_TERTIARY,
                perHour > 0 ? AppleColors.TEXT_PRIMARY : AppleColors.TEXT_TERTIARY, row);

        divider(context, row + LINE);
        row += LINE + dividerSpace();

        for (int i = 0; i < items; i++) {
            FarmProfit.Entry entry = entries.get(i);
            // Auction-only drops (vinyls and the like) have no bazaar price; show
            // that they are unpriced rather than implying they are worthless.
            String value = BazaarPrices.known(entry.item()) ? format(entry.coins()) : "?";
            String label = fit(mc, shortName(entry.item()),
                    PANEL_WIDTH - pad() * 2 - mc.font.width(value) - 6);
            boolean custom = TeslaMapsConfig.get().farmProfitItemOptions
                    && !"Default".equals(FarmProfit.itemSource(entry.item()));
            boolean armed = entry.item().equals(hideArmed) && System.currentTimeMillis() - hideArmedAt < CONFIRM_MS;
            drawRow(context, mc, armed ? fit(mc, "hide? click again", PANEL_WIDTH - pad() * 2 - mc.font.width(value) - 6) : label,
                    value, armed ? AppleColors.ACCENT_RED : AppleColors.TEXT_SECONDARY,
                    custom ? AppleColors.ACCENT_BLUE : AppleColors.TEXT_PRIMARY, row);
            row += LINE;
        }

        if (items > 0) {
            divider(context, row);
            row += dividerSpace();
        }

        // Footer doubles as the window selector and carries the reset button.
        boolean shadow = plain();
        String windowLabel = FarmProfit.window().label;
        context.text(mc.font, Component.literal(windowLabel), pad(), row,
                AppleColors.ACCENT_BLUE, shadow);

        String reset = resetArmed() ? RESET_SURE : RESET;
        int resetX = PANEL_WIDTH - pad() - mc.font.width(reset);
        context.text(mc.font, Component.literal(reset), resetX, row,
                AppleColors.ACCENT_RED, shadow);

        // Sits between the two, and gives way rather than running into them.
        String status = status();
        int statusX = pad() + mc.font.width(windowLabel) + 6;
        status = fit(mc, status, resetX - statusX - 6);
        context.text(mc.font, Component.literal(status), statusX, row,
                FarmProfit.idle() ? AppleColors.ACCENT_RED : col(AppleColors.TEXT_TERTIARY), shadow);

        context.pose().popMatrix();
    }

    private static float tooltipFade = 0f;

    private static final int TOOLTIP_PADDING = 8;

    /**
     * Full breakdown while the cursor rests on the panel. Only reachable with a
     * screen open, since gameplay has no cursor.
     */
    public static void drawTooltip(GuiGraphicsExtractor context, Minecraft mc,
                                    int mouseX, int mouseY) {
        if (!shouldRender()) return;

        TeslaMapsConfig config = TeslaMapsConfig.get();
        boolean hovered = mouseX >= config.farmProfitX
                && mouseX <= config.farmProfitX + width(config.farmProfitScale)
                && mouseY >= config.farmProfitY
                && mouseY <= config.farmProfitY + height(config.farmProfitScale);

        com.teslamaps.utils.AnimClock.update();
        tooltipFade += ((hovered ? 1f : 0f) - tooltipFade)
                * com.teslamaps.utils.AnimClock.lerpFactor(0.35f);
        if (tooltipFade < 0.02f) return;

        List<FarmProfit.Entry> entries = FarmProfit.entries();
        if (entries.isEmpty()) return;

        int rows = Math.min(entries.size(), 10);
        int lineHeight = mc.font.lineHeight + 2;

        // Every entry takes two lines, the name and the amount below it. The box
        // used to be sized for one, so everything past the first few items fell
        // out the bottom.
        int entryHeight = lineHeight * 2 - 3;
        int headerHeight = lineHeight + 2;
        int boxWidth = 170;
        boolean hasSplit = FarmProfit.pestCoins() >= 0;
        int boxHeight = TOOLTIP_PADDING + headerHeight + rows * entryHeight
                + (hasSplit ? lineHeight + 4 : 0) + TOOLTIP_PADDING;

        // Slide in slightly while fading, and keep the box on screen.
        int boxX = mouseX + 10;
        int boxY = mouseY - boxHeight / 2;
        boxX = Math.min(boxX, mc.getWindow().getGuiScaledWidth() - boxWidth - 4);
        boxY = Math.clamp(boxY, 4,
                Math.max(4, mc.getWindow().getGuiScaledHeight() - boxHeight - 4));

        // The fade only ever approaches 1, so anything scaled by it stayed
        // slightly see-through forever. Snap it once it is close enough.
        if (tooltipFade > 0.97f) tooltipFade = 1f;

        int alpha = (int) (tooltipFade * 255);
        context.pose().pushMatrix();
        context.pose().translate(boxX - (1f - tooltipFade) * 6f, boxY);

        TeslaGui.shadow(context, 0, 2, boxWidth, boxHeight, 6, 6,
                TeslaGui.withAlpha(0x000000, (int) (tooltipFade * 110)));
        TeslaGui.roundedRect(context, 0, 0, boxWidth, boxHeight, 6,
                TeslaGui.withAlpha(0x1C1C1E, alpha));
        TeslaGui.roundedOutline(context, 0.5f, 0.5f, boxWidth - 1, boxHeight - 1, 6, 1.0f,
                TeslaGui.withAlpha(0xFFFFFF, (int) (tooltipFade * 55)));

        int y = TOOLTIP_PADDING;
        context.text(mc.font, Component.literal(FarmProfit.window().label), 8, y,
                withAlpha(AppleColors.ACCENT_BLUE, alpha), false);
        y += lineHeight + 2;

        for (int i = 0; i < rows; i++) {
            FarmProfit.Entry entry = entries.get(i);
            double each = entry.amount() > 0 ? entry.coins() / entry.amount() : 0;

            String value = format(entry.coins());
            int valueWidth = mc.font.width(value);

            // Long drop names ran straight into the value; cut them to whatever
            // room the value leaves, with an ellipsis so it reads as shortened.
            String label = shortName(entry.item());
            int room = boxWidth - 16 - valueWidth - 4;
            if (mc.font.width(label) > room) {
                while (label.length() > 1 && mc.font.width(label + "…") > room) {
                    label = label.substring(0, label.length() - 1);
                }
                label = label.stripTrailing() + "…";
            }

            context.text(mc.font, Component.literal(label), 8, y,
                    withAlpha(AppleColors.TEXT_SECONDARY, alpha), false);
            context.text(mc.font, Component.literal(value),
                    boxWidth - 8 - valueWidth, y,
                    withAlpha(AppleColors.TEXT_PRIMARY, alpha), false);
            y += lineHeight;

            String detail = String.format("x%,d  @ %.1f", entry.amount(), each);
            context.text(mc.font, Component.literal(detail), 14, y - 1,
                    withAlpha(AppleColors.TEXT_TERTIARY, (int) (alpha * 0.8f)), false);
            y += lineHeight - 3;
        }

        // Pest-only trackers report the left number; the difference is farming.
        if (hasSplit) {
            double pest = FarmProfit.pestCoins();
            double farm = Math.max(0, FarmProfit.totalCoins() - pest);
            y += 4;
            context.text(mc.font, Component.literal("Pests " + format(pest)), 8, y,
                    withAlpha(AppleColors.TEXT_TERTIARY, alpha), false);
            String farmText = "Farm " + format(farm);
            context.text(mc.font, Component.literal(farmText),
                    boxWidth - 8 - mc.font.width(farmText), y,
                    withAlpha(AppleColors.TEXT_TERTIARY, alpha), false);
        }

        context.pose().popMatrix();
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.clamp(alpha, 0, 255) << 24) | (color & 0xFFFFFF);
    }

    private static final String RESET = "reset";
    private static final String RESET_SURE = "sure?";
    private static final long CONFIRM_MS = 3000L;
    private static long resetArmedAt = 0L;
    private static String hideArmed = null;
    private static long hideArmedAt = 0L;

    private static boolean resetArmed() {
        return TeslaMapsConfig.get().farmProfitResetConfirm && System.currentTimeMillis() - resetArmedAt < CONFIRM_MS;
    }

    private static void say(String msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §f" + msg));
    }

    /** Window label suffix: how long the session has really been farming. */
    private static String status() {
        if (!FarmProfit.started()) return "not started";
        if (FarmProfit.idle()) return "idle";
        if (!BazaarPrices.loaded()) return "no prices";
        return FarmProfit.activeTime();
    }

    /**
     * Left click steps the window forward, right click back, and a click on the
     * reset label clears the session.
     */
    public static void onClick(double mouseX, double mouseY, int button) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        float scale = config.farmProfitScale;
        Minecraft mc = Minecraft.getInstance();

        float localX = (float) (mouseX - config.farmProfitX) / scale;
        float localY = (float) (mouseY - config.farmProfitY) / scale;

        int footerY = footerY();
        boolean onReset = localY >= footerY - 2 && localY <= footerY + LINE
                && localX >= PANEL_WIDTH - pad() - mc.font.width(RESET) - 3;

        if (onReset) {
            if (config.farmProfitResetConfirm && !resetArmed()) {
                resetArmedAt = System.currentTimeMillis();
                return;
            }
            resetArmedAt = 0L;
            FarmProfit.resetSession();
            return;
        }

        // Skysoft's item panel, as clicks: left = price source, right twice = hide
        int itemsTop = pad() + LINE * 2 + dividerSpace();
        int index = (int) Math.floor((localY - itemsTop) / LINE);
        if (config.farmProfitItemOptions && localY >= itemsTop && index >= 0 && index < itemRows()) {
            String item = FarmProfit.entries().get(index).item();
            if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT) {
                long now = System.currentTimeMillis();
                if (item.equals(hideArmed) && now - hideArmedAt < CONFIRM_MS) {
                    hideArmed = null;
                    FarmProfit.toggleExcluded(item);
                    say("Hid §e" + item + "§f from the profit tracker · /tmap profit exclude " + item + " brings it back");
                } else {
                    hideArmed = item;
                    hideArmedAt = now;
                }
            } else {
                say("§e" + item + "§f priced by §b" + FarmProfit.cycleItemSource(item));
            }
            return;
        }
        if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT) {
            FarmProfit.cycleWindowBack();
        } else {
            FarmProfit.cycleWindow();
        }
    }

    private static void drawRow(GuiGraphicsExtractor context, Minecraft mc,
                                 String label, String value,
                                 int labelColor, int valueColor, int y) {
        boolean shadow = plain();
        context.text(mc.font, Component.literal(label), pad(), y, col(labelColor), shadow);
        int valueX = PANEL_WIDTH - pad() - mc.font.width(value);
        context.text(mc.font, Component.literal(value), valueX, y, col(valueColor), shadow);
    }

    /** "Enchanted Red Mushroom" -> "Ench. Red Mushroom", to fit the panel. */
    private static String shortName(String item) {
        return item.startsWith("Enchanted ") ? "Ench. " + item.substring(10) : item;
    }

    private static String format(double coins) {
        if (coins >= 1_000_000_000) return String.format("%.2fB", coins / 1_000_000_000);
        if (coins >= 1_000_000) return String.format("%.2fM", coins / 1_000_000);
        if (coins >= 1_000) return String.format("%.1fk", coins / 1_000);
        return String.format("%.0f", coins);
    }
}

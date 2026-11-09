package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.screen.AppleColors;
import com.teslamaps.utils.ChatText;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;

/** Visitors charmed with the Gift Vinyl Set, ported from SkyHanni. */
public class CharmedVisitors {

    private static final int INFO_SLOT = 13;
    private static final int ACCEPT_SLOT = 29;
    private static final int VINYL_SLOT = 48;

    private static final String CHARMED_NAME = "This Visitor has been Charmed!";

    private static final int PADDING = 8;
    private static final int LINE = 10;

    private static final String[] SAMPLE = {
            "§6Madame Eleanor Q. Goldsworth III", "§9Lazy Miner", "§aEmissary Carlton"
    };

    public static void tick() {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        if (!config.charmedVisitorList) return;
        if (!SkyblockUtils.isInGarden()) return;

        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs)) return;

        List<Slot> slots = cs.getMenu().slots;
        if (slots.size() <= VINYL_SLOT) return;

        ItemStack info = slots.get(INFO_SLOT).getItem();
        if (!isVisitorInfo(info)) return;
        ItemStack accept = slots.get(ACCEPT_SLOT).getItem();
        if (!StringUtil.strip(accept.getHoverName().getString()).equals("Accept Offer")) return;

        String name = visitorName(info);
        if (name.isEmpty()) return;

        if (isCharmedItem(slots.get(VINYL_SLOT).getItem())) add(config, name);
        else remove(config, name);
    }

    /** Slot 13 of a visitor menu: four lore lines, the last one the accept counter. */
    private static boolean isVisitorInfo(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null || lore.lines().size() != 4) return false;
        return StringUtil.strip(lore.lines().get(3).getString()).startsWith("Offers Accepted: ");
    }

    private static boolean isCharmedItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return StringUtil.strip(stack.getHoverName().getString()).startsWith(CHARMED_NAME);
    }

    /** Keeps the rarity colour, drops the resets Hypixel wraps the name in. */
    private static String visitorName(ItemStack stack) {
        String name = ChatText.formatted(stack.getHoverName()).replace("§r", "").trim();
        String plain = StringUtil.strip(name);
        if (plain.isEmpty()) return "";
        // a doubled colour code in front of the name, as in "§9§9Lazy Miner"
        if (name.length() == plain.length() + 4) name = name.substring(2);
        return name.contains("§") ? name : "§f" + name;
    }

    private static void add(TeslaMapsConfig config, String name) {
        if (config.charmedVisitorNames.contains(name)) return;
        config.charmedVisitorNames.add(name);
        TeslaMapsConfig.save();
    }

    private static void remove(TeslaMapsConfig config, String name) {
        if (!config.charmedVisitorNames.remove(name)) return;
        TeslaMapsConfig.save();
    }

    public static void clear() {
        TeslaMapsConfig.get().charmedVisitorNames.clear();
        TeslaMapsConfig.save();
    }

    public static boolean shouldRender() {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        return config.charmedVisitorList && SkyblockUtils.isInGarden()
                && !config.charmedVisitorNames.isEmpty();
    }

    public static void render(GuiGraphicsExtractor context, DeltaTracker delta) {
        if (!shouldRender()) return;
        TeslaMapsConfig config = TeslaMapsConfig.get();
        draw(context, Minecraft.getInstance(), config.charmedVisitorX, config.charmedVisitorY,
                config.charmedVisitorScale, false);
    }

    private static List<String> names(boolean preview) {
        List<String> names = TeslaMapsConfig.get().charmedVisitorNames;
        return preview && names.isEmpty() ? List.of(SAMPLE) : names;
    }

    private static int panelWidth(boolean preview) {
        Minecraft mc = Minecraft.getInstance();
        int longest = mc.font.width(title(names(preview).size()));
        for (String name : names(preview)) {
            longest = Math.max(longest, mc.font.width(" - " + name));
        }
        return longest + PADDING * 2;
    }

    private static int panelHeight(boolean preview) {
        return PADDING * 2 + LINE * (1 + names(preview).size());
    }

    private static String title(int count) {
        return "§dCharmed Visitors §7(" + count + "):";
    }

    public static int width(float scale) {
        return (int) (panelWidth(false) * scale);
    }

    public static int height(float scale) {
        return (int) (panelHeight(false) * scale);
    }

    public static int previewWidth(float scale) {
        return (int) (panelWidth(true) * scale);
    }

    public static int previewHeight(float scale) {
        return (int) (panelHeight(true) * scale);
    }

    public static void draw(GuiGraphicsExtractor context, Minecraft mc, int x, int y, float scale,
                            boolean preview) {
        List<String> names = names(preview);
        int width = panelWidth(preview);
        int height = panelHeight(preview);

        context.pose().pushMatrix();
        context.pose().translate(x, y);
        context.pose().scale(scale, scale);

        HudStyle.panel(context, 0, 0, width, height, 6);

        int row = PADDING;
        context.text(mc.font, Component.literal(title(names.size())), PADDING, row,
                AppleColors.TEXT_PRIMARY, true);
        for (String name : names) {
            row += LINE;
            context.text(mc.font, Component.literal(" - " + name), PADDING, row,
                    HudStyle.label(), true);
        }

        context.pose().popMatrix();
    }

    /** A click on a name takes it off the list, like SkyHanni's clickable line. */
    public static void onClick(double mouseX, double mouseY, int button) {
        if (!shouldRender()) return;
        TeslaMapsConfig config = TeslaMapsConfig.get();
        float scale = config.charmedVisitorScale;
        double localY = (mouseY - config.charmedVisitorY) / scale;

        int row = (int) Math.floor((localY - PADDING) / LINE);
        if (row < 1 || row > config.charmedVisitorNames.size()) return;
        config.charmedVisitorNames.remove(row - 1);
        TeslaMapsConfig.save();
    }
}

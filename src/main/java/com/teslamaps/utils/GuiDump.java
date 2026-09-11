package com.teslamaps.utils;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Writes the open container's slots to a file, for working out slot matching. */
public class GuiDump {

    private static final Path PATH = FabricLoader.getInstance().getConfigDir()
            .resolve("teslamaps").resolve("gui_dump.txt");

    public static String strip(String text) {
        return text == null ? "" : text.replaceAll("(?i)§[0-9A-FK-OR]", "");
    }

    private static boolean keyWasDown = false;

    /**
     * Polls the dump key. The hotkey system in KeybindMessage bails out whenever
     * a screen is open, so dumping a container needs its own raw poll.
     */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        int key = com.teslamaps.config.TeslaMapsConfig.get().dumpGuiKey;
        if (key < 0 || mc.getWindow() == null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen) return;

        boolean down = org.lwjgl.glfw.GLFW.glfwGetKey(mc.getWindow().handle(), key)
                == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        if (down && !keyWasDown) {
            boolean shift = org.lwjgl.glfw.GLFW.glfwGetKey(mc.getWindow().handle(),
                    org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
            if (shift) dumpTooltip(); else dump(null);
        }
        keyWasDown = down;
    }

    /** Writes the tab list and scoreboard to a file, for building parsers. */
    public static void dumpTab() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        List<String> lines = new ArrayList<>();
        lines.add("=== tab list ===");
        for (String line : com.teslamaps.utils.TabListUtils.getTabListLines()) {
            lines.add("[" + line + "]   ->   " + strip(line));
        }
        lines.add("");
        lines.add("=== scoreboard ===");
        for (String line : com.teslamaps.utils.ScoreboardUtils.getScoreboardLines()) {
            lines.add("[" + line + "]   ->   " + strip(line));
        }

        Path path = PATH.getParent().resolve("tab_dump.txt");
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, lines);
            mc.player.sendSystemMessage(Component.literal(
                    "§b[TeslaMaps] §fWrote §e" + lines.size() + "§f lines to §7config/teslamaps/tab_dump.txt"));
        } catch (Exception e) {
            mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §cDump failed: " + e));
        }
    }

    /** Raw lore vs the tooltip we actually render, so a dropped line is visible. */
    public static void dumpTooltip() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        ItemStack stack = ItemStack.EMPTY;
        if (mc.screen instanceof AbstractContainerScreen<?> screen) {
            Slot slot = ((com.teslamaps.mixin.HandledScreenAccessor) screen).getFocusedSlot();
            if (slot != null) stack = slot.getItem();
        }
        if (stack.isEmpty()) stack = mc.player.getMainHandItem();
        if (stack.isEmpty()) {
            mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §fHover an item or hold one"));
            return;
        }

        List<String> raw = new ArrayList<>();
        var lore = stack.get(DataComponents.LORE);
        if (lore != null) for (Component line : lore.lines()) raw.add(line.getString());

        List<String> shown = new ArrayList<>();
        for (Component line : stack.getTooltipLines(
                net.minecraft.world.item.Item.TooltipContext.of(mc.level), mc.player,
                net.minecraft.world.item.TooltipFlag.NORMAL)) {
            shown.add(line.getString());
        }

        List<String> out = new ArrayList<>();
        out.add("item:   " + strip(stack.getHoverName().getString()));
        out.add("id:     " + stack.getItem());
        out.add("lore:   " + raw.size() + " lines");
        out.add("shown:  " + shown.size() + " lines");
        out.add("");
        out.add("=== raw lore (server) ===");
        for (String line : raw) out.add("  " + line.replace('§', '&'));
        out.add("");
        out.add("=== rendered tooltip (after our hooks) ===");
        for (String line : shown) out.add("  " + line.replace('§', '&'));
        out.add("");
        out.add("=== lore lines missing from the tooltip ===");
        List<String> shownStripped = new ArrayList<>();
        for (String line : shown) shownStripped.add(strip(line).trim());
        int missing = 0;
        for (String line : raw) {
            String plain = strip(line).trim();
            if (plain.isEmpty() || shownStripped.contains(plain)) continue;
            out.add("  " + plain);
            missing++;
        }
        if (missing == 0) out.add("  none");

        Path path = PATH.getParent().resolve("tooltip_dump.txt");
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, out);
            mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §fLore §e" + raw.size()
                    + "§f, tooltip §e" + shown.size() + "§f, missing §e" + missing
                    + "§f -> §7config/teslamaps/tooltip_dump.txt"));
        } catch (Exception e) {
            mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §cDump failed: " + e));
        }
    }

    public static void dump(String highlight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (!(mc.screen instanceof AbstractContainerScreen<?> screen)) {
            mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §fNo container menu open"));
            return;
        }

        String title = strip(screen.getTitle().getString());
        List<String> lines = new ArrayList<>();
        lines.add("title:      " + title);
        lines.add("screen:     " + screen.getClass().getName());
        lines.add("menu:       " + screen.getMenu().getClass().getName());
        lines.add("containerId:" + screen.getMenu().containerId);
        lines.add("slots:      " + screen.getMenu().slots.size());
        lines.add("");

        List<Integer> matches = new ArrayList<>();
        String needle = highlight == null ? null : highlight.toLowerCase();

        for (Slot slot : screen.getMenu().slots) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;

            String name = strip(stack.getHoverName().getString());
            lines.add(String.format("slot %-3d container=%-3d item=%s", slot.index,
                    slot.container == screen.getMenu().slots.getFirst().container ? 0 : 1,
                    stack.getItem().toString()));
            lines.add("    name: " + name);

            var lore = stack.get(DataComponents.LORE);
            if (lore != null) {
                for (Component line : lore.lines()) {
                    lines.add("    lore: " + strip(line.getString()));
                }
            }

            if (needle != null && name.toLowerCase().contains(needle)) matches.add(slot.index);
        }

        try {
            Files.createDirectories(PATH.getParent());
            Files.write(PATH, lines);
            mc.player.sendSystemMessage(Component.literal(
                    "§b[TeslaMaps] §fWrote §e" + (lines.size()) + "§f lines to §7config/teslamaps/gui_dump.txt"));
        } catch (Exception e) {
            mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §cDump failed: " + e));
        }

        mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §fMenu §e" + title
                + "§f, §e" + screen.getMenu().slots.size() + "§f slots"));
        if (needle != null) {
            mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §fName matches for '"
                    + highlight + "': §e" + (matches.isEmpty() ? "none" : matches.toString())));
        }
    }
}

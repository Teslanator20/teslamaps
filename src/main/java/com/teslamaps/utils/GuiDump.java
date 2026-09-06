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
        if (down && !keyWasDown) dump(null);
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

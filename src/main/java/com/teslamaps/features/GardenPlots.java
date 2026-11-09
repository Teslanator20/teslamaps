package com.teslamaps.features;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Plot names and icons scraped from the "Configure Plots" menu, so the inventory
 * overlay can offer a teleport grid.
 */
public class GardenPlots {

    public static final int PLOT_COUNT = 25;
    public static final int BARN_SLOT = 12;

    /** Plot number as it appears in the tab list -> slot in the 5x5 grid. */
    private static final int[] PLOT_TO_SLOT = {
            12, 7, 11, 13, 17,
            6, 8, 16, 18, 2,
            10, 14, 22, 1, 3,
            5, 9, 15, 19, 21,
            23, 0, 4, 20, 24
    };

    /** Grid slot (row * 5 + column, row = z) of a plot number, or -1. */
    public static int slotOf(int plotNumber) {
        return plotNumber >= 0 && plotNumber < PLOT_TO_SLOT.length ? PLOT_TO_SLOT[plotNumber] : -1;
    }

    /**
     * Icon choices for the right-click picker. Skyblocker pulls these from the NEU
     * item repository; these are the closest vanilla stand-ins. DOUBLE_PLANT and
     * MOONFLOWER have no vanilla counterpart, so those two are approximations.
     */
    public static final Item[] CUSTOM_ICONS = {
            Items.BARRIER,        // none
            Items.WHEAT,
            Items.CARROT,
            Items.POTATO,
            Items.SUGAR_CANE,
            Items.SUNFLOWER,      // DOUBLE_PLANT
            Items.WHITE_TULIP,    // MOONFLOWER
            Items.ROSE_BUSH,      // WILD_ROSE
            Items.NETHER_WART,
            Items.RED_MUSHROOM,
            Items.CACTUS,
            Items.MELON_SLICE,
            Items.PUMPKIN,
            Items.COCOA_BEANS
    };

    public static class Plot {
        public String name;
        public String icon;        // SNBT of the stack the server sent
        public int customIcon = -1; // index into CUSTOM_ICONS, -1 = use icon
        public String customStack;  // SNBT of any item picked in the Configure Plots menu
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<Map<Integer, Plot>>() {}.getType();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir()
            .resolve("teslamaps").resolve("garden_plots.json");

    private static Map<Integer, Plot> plots = new HashMap<>();
    private static final Map<Integer, ItemStack> decodedIcons = new HashMap<>();
    private static boolean loaded = false;

    // Scraping once per screen instance instead of per frame keeps the SNBT
    // encoding out of the render path.
    private static Screen lastScanned = null;

    public static void load() {
        loaded = true;
        try {
            if (Files.exists(PATH)) {
                Map<Integer, Plot> read = GSON.fromJson(Files.readString(PATH), TYPE);
                if (read != null) plots = read;
            }
        } catch (Exception e) {
            com.teslamaps.TeslaMaps.LOGGER.error("Failed to load garden plots", e);
        }
    }

    private static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(plots));
        } catch (Exception e) {
            com.teslamaps.TeslaMaps.LOGGER.error("Failed to save garden plots", e);
        }
    }

    public static Plot get(int slot) {
        if (!loaded) load();
        return plots.get(slot);
    }

    public static boolean hasAny() {
        if (!loaded) load();
        return !plots.isEmpty();
    }

    public static void setCustomIcon(int slot, int iconIndex) {
        Plot plot = get(slot);
        if (plot == null) return;
        plot.customIcon = iconIndex;
        plot.customStack = null;
        decodedIcons.remove(slot);
        customIcons.remove(slot);
        save();
    }

    /** Any item as icon; null clears it. */
    public static void setCustomStack(int slot, ItemStack stack) {
        if (!loaded) load();
        Plot plot = plots.computeIfAbsent(slot, k -> new Plot());
        plot.customStack = stack == null || stack.isEmpty() ? null : StorageCache.encode(stack.copyWithCount(1));
        if (plot.customStack != null) plot.customIcon = -1;
        decodedIcons.remove(slot);
        customIcons.remove(slot);
        save();
    }

    private static final Map<Integer, ItemStack> customIcons = new HashMap<>();

    /** The picked icon alone (custom stack or fixed choice), or null when the plot keeps the server icon. */
    public static ItemStack customIcon(int slot) {
        if (slot == BARN_SLOT) return null;
        ItemStack cached = customIcons.get(slot);
        if (cached != null) return cached.isEmpty() ? null : cached;
        Plot plot = get(slot);
        ItemStack stack = ItemStack.EMPTY;
        if (plot != null && plot.customStack != null) stack = StorageCache.decodeStack(plot.customStack);
        else if (plot != null && plot.customIcon >= 0 && plot.customIcon < CUSTOM_ICONS.length)
            stack = new ItemStack(CUSTOM_ICONS[plot.customIcon]);
        customIcons.put(slot, stack);
        return stack.isEmpty() ? null : stack;
    }

    /** Decoded icon for a plot, cached so the SNBT is not re-parsed every frame. */
    public static ItemStack icon(int slot) {
        if (slot == BARN_SLOT) {
            ItemStack barn = new ItemStack(Items.LODESTONE);
            barn.set(DataComponents.ITEM_NAME, Component.literal("The Barn"));
            return barn;
        }

        ItemStack cached = decodedIcons.get(slot);
        if (cached != null) return cached;

        Plot plot = get(slot);
        if (plot == null) return ItemStack.EMPTY;

        ItemStack stack;
        if (plot.customStack != null) {
            stack = StorageCache.decodeStack(plot.customStack);
        } else if (plot.customIcon >= 0 && plot.customIcon < CUSTOM_ICONS.length) {
            stack = new ItemStack(CUSTOM_ICONS[plot.customIcon]);
        } else {
            stack = StorageCache.decodeStack(plot.icon);
        }
        if (stack.isEmpty()) stack = new ItemStack(Items.WHEAT);

        stack = stack.copy();
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(plot.name)
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        decodedIcons.put(slot, stack);
        return stack;
    }

    /**
     * Reads plot names and icons out of an open "Configure Plots" menu. The menu
     * lays the plots out in columns 2..6 of each row; slot 22 holds the barn icon
     * and the names come through as "<something> - <plot name>".
     */
    public static void scanConfigureScreen() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof ContainerScreen screen)) return;
        if (!screen.getTitle().getString().trim().equals("Configure Plots")) return;
        scanDeskPests(screen);
        if (lastScanned == screen) return;
        lastScanned = screen;

        if (!loaded) load();
        boolean changed = false;

        for (int row = 0; row < 5; row++) {
            for (int i = row * 9 + 2; i < row * 9 + 7; i++) {
                if (i == 22) continue; // barn icon, not a configurable plot
                Slot slot = screen.getMenu().slots.get(i);
                ItemStack stack = slot.getItem();
                if (stack.isEmpty()) continue;

                String name = ChatFormatting.stripFormatting(stack.getHoverName().getString());
                if (name == null) continue;
                String[] parts = name.split("-", 2);
                if (parts.length < 2) continue;

                int index = (i / 9) * 5 + (i % 9 - 2);
                Plot plot = plots.computeIfAbsent(index, k -> new Plot());
                plot.name = parts[1].trim();
                plot.icon = StorageCache.encode(stack);
                decodedIcons.remove(index);
                changed = true;
            }
        }

        if (changed) save();
    }

    // SkyOcean's DeskPestHighlight: "ൠ This plot has 3 ൠ Pests!" in the Configure Plots lore
    private static final Pattern DESK_PESTS = Pattern.compile("This plot has (\\d+)\\D*Pests?!");
    private static final Pattern SPAWN_AMOUNT = Pattern.compile("(\\d+) Pests? have spawned in (?:Plot - )?(?<plot>.*)!");
    private static final int[] pestCounts = new int[PLOT_COUNT];
    private static final int[] deskCounts = new int[54];
    private static final String[] countText = new String[PLOT_COUNT];
    private static Screen deskScreen = null;
    private static long deskScanAt = 0L;
    private static int pestTicks = 0;

    private static boolean pestCountOn() {
        com.teslamaps.config.TeslaMapsConfig c = com.teslamaps.config.TeslaMapsConfig.get();
        return c.gardenPlotsPestCount && c.section("Garden");
    }

    public static void register() {
        com.teslamaps.registry.Feature.of("GardenPlotPests").section("Garden")
                .when(() -> com.teslamaps.config.TeslaMapsConfig.get().gardenPlotsPestCount)
                .tick(GardenPlots::pestTick);
    }

    private static void scanDeskPests(ContainerScreen screen) {
        if (!pestCountOn()) return;
        long now = System.currentTimeMillis();
        if (deskScreen == screen && now - deskScanAt < 500) return;
        deskScreen = screen;
        deskScanAt = now;
        java.util.Arrays.fill(deskCounts, 0);
        for (int row = 0; row < 5; row++) {
            for (int i = row * 9 + 2; i < row * 9 + 7; i++) {
                if (i >= screen.getMenu().slots.size()) continue;
                ItemStack stack = screen.getMenu().slots.get(i).getItem();
                if (stack.isEmpty()) continue;
                int amount = 0;
                for (String line : com.teslamaps.utils.ItemUtil.loreLines(stack)) {
                    Matcher m = DESK_PESTS.matcher(com.teslamaps.utils.StringUtil.strip(line));
                    if (m.find()) {
                        amount = Integer.parseInt(m.group(1));
                        break;
                    }
                }
                deskCounts[i] = amount;
                setCount((i / 9) * 5 + (i % 9 - 2), amount);
            }
        }
    }

    private static void setCount(int slot, int amount) {
        if (slot < 0 || slot >= PLOT_COUNT) return;
        pestCounts[slot] = Math.max(0, amount);
        countText[slot] = amount > 0 ? Integer.toString(amount) : null;
    }

    /** Grid slot for a plot name from chat or the scoreboard, -1 when unknown. */
    private static int slotForName(String name) {
        if (name == null) return -1;
        if (name.equalsIgnoreCase("The Barn")) return BARN_SLOT;
        if (!loaded) load();
        for (Map.Entry<Integer, Plot> e : plots.entrySet()) {
            if (e.getValue() != null && name.equalsIgnoreCase(e.getValue().name)) return e.getKey();
        }
        try {
            int number = Integer.parseInt(name.trim());
            if (number >= 0 && number < PLOT_TO_SLOT.length) return PLOT_TO_SLOT[number];
        } catch (NumberFormatException ignored) {
        }
        return -1;
    }

    private static void pestTick() {
        if (++pestTicks % 20 != 0) return;
        if (!com.teslamaps.utils.SkyblockUtils.isInGarden()) return;
        if (!PestData.hasPestSection()) return;
        boolean[] infested = infestedSlots();
        for (int i = 0; i < PLOT_COUNT; i++) {
            if (!infested[i] && pestCounts[i] > 0) setCount(i, 0);
        }
        int here = slotForName(PestData.currentPlot());
        if (here >= 0) setCount(here, infested[here] ? PestData.pestsOnCurrentPlot() : 0);
    }

    /** Pest count label for a grid slot, null when none are known. */
    public static String pestCountText(int slot) {
        if (!pestCountOn() || slot < 0 || slot >= PLOT_COUNT) return null;
        return countText[slot];
    }

    /** Red backdrop for infested plots in the vanilla Configure Plots menu. */
    public static void drawDeskBackground(net.minecraft.client.gui.GuiGraphicsExtractor ctx, Slot slot) {
        if (deskScreen == null || slot.index >= deskCounts.length || deskCounts[slot.index] <= 0) return;
        if (!onDesk(slot)) return;
        ctx.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x99FF3333);
    }

    public static void drawDeskCount(net.minecraft.client.gui.GuiGraphicsExtractor ctx, Slot slot) {
        if (deskScreen == null || slot.index >= deskCounts.length || deskCounts[slot.index] <= 0) return;
        if (!onDesk(slot)) return;
        var font = Minecraft.getInstance().font;
        String text = Integer.toString(deskCounts[slot.index]);
        ctx.text(font, text, slot.x + 17 - font.width(text), slot.y + 9, 0xFFFFAA00, true);
    }

    private static boolean onDesk(Slot slot) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() != deskScreen || !pestCountOn()) {
            if (mc.gui.screen() != deskScreen) deskScreen = null;
            return false;
        }
        return !(slot.container instanceof net.minecraft.world.entity.player.Inventory);
    }

    // "YUCK! 5 Pests have spawned in Plot - 18!" — the name is whatever the plot
    // is called, so it goes straight into /plottp. A trailing "(2x)" from chat
    // stacking sits after the "!", so the capture stops there.
    private static final Pattern[] PEST_SPAWN = {
            Pattern.compile("Pests? have spawned in Plot - (?<plot>.*)!"),
            Pattern.compile("Pest has appeared in Plot - (?<plot>.*)!"),
            Pattern.compile("Pests? have spawned in (?<plot>The Barn)!"),
            Pattern.compile("Pest has appeared in (?<plot>The Barn)!"),
    };

    private static String lastPestPlot = null;
    private static long lastPestPlotTime = 0L;

    public static void onChatMessage(String raw) {
        String text = raw.replaceAll("(?i)§[0-9A-FK-OR]", "");
        for (Pattern pattern : PEST_SPAWN) {
            Matcher matcher = pattern.matcher(text);
            if (!matcher.find()) continue;
            lastPestPlot = matcher.group("plot").trim();
            lastPestPlotTime = System.currentTimeMillis();
            if (pestCountOn()) {
                Matcher amount = SPAWN_AMOUNT.matcher(text);
                int slot = slotForName(lastPestPlot);
                if (slot >= 0) setCount(slot, pestCounts[slot] + (amount.find() ? Integer.parseInt(amount.group(1)) : 1));
            }
            return;
        }
    }

    /**
     * Teleports to an infested plot. The tab list is preferred because it stays
     * current as pests are killed; the chat memory covers the case where the
     * plot names have not been scraped from /plots yet.
     */
    public static void teleportToPest() {
        boolean[] infested = infestedSlots();
        for (int slot = 0; slot < PLOT_COUNT; slot++) {
            if (!infested[slot]) continue;
            Plot plot = get(slot);
            if (plot == null || plot.name == null) continue;
            command("plottp " + plot.name);
            return;
        }

        // Only trust the chat memory for a minute; past that the plot has very
        // likely been cleared already.
        if (lastPestPlot != null && System.currentTimeMillis() - lastPestPlotTime < 60_000L) {
            command("plottp " + lastPestPlot);
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §fNo pests found"));
        }
    }

    public static void teleport(int slot) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;
        if (slot == BARN_SLOT) {
            mc.getConnection().sendCommand("plottp barn");
            return;
        }
        Plot plot = get(slot);
        if (plot != null) mc.getConnection().sendCommand("plottp " + plot.name);
    }

    public static void command(String command) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) mc.getConnection().sendCommand(command);
    }

    /** Grid slots of plots the tab list reports as infested. */
    public static boolean[] infestedSlots() {
        boolean[] infested = new boolean[PLOT_COUNT];
        for (String raw : com.teslamaps.utils.TabListUtils.getTabListLines()) {
            String line = raw.replaceAll("(?i)§[0-9A-FK-OR]", "").trim();
            if (!line.startsWith("Plots:")) continue;
            for (String part : line.substring("Plots:".length()).split(",")) {
                try {
                    int number = Integer.parseInt(part.strip());
                    if (number >= 0 && number < PLOT_TO_SLOT.length) {
                        infested[PLOT_TO_SLOT[number]] = true;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            break;
        }
        return infested;
    }
}

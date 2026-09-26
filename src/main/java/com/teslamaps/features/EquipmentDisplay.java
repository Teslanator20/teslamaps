package com.teslamaps.features;

// Ported from Skysoft's InventoryEquipment*.kt (slimmed: panel beside the inventory, no slot moving)
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.ProfiledFile;
import com.teslamaps.utils.SbProfile;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class EquipmentDisplay {

    private static final int COUNT = 4;
    private static final String[] NAMES = {"Necklace", "Cloak", "Belt", "Gloves"};
    private static final Pattern LOADOUTS = Pattern.compile("^\\(\\d+/\\d+\\) Loadouts$");
    private static final Pattern SETS = Pattern.compile("^\\(\\d+/\\d+\\) Equipment Sets$");
    private static final Pattern EQUIPPED = Pattern.compile("^Slot \\d+: Equipped$");
    private static final Type TYPE = new TypeToken<List<String>>() {}.getType();
    private static final Gson GSON = new Gson();
    private static final ProfiledFile FILE = new ProfiledFile("equipment.json", null);

    private static final int SLOT = 18;

    private static Feature feature;
    private static HudEditElement hud;
    private static List<String> snbt = new ArrayList<>();
    private static ItemStack[] stacks;
    private static boolean loaded;
    private static int lastSeenHash;
    private static long openedAt;
    private static Object lastScreen;

    private EquipmentDisplay() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("equipmentHud", "Equipment HUD").section("Inventory")
                .enabled(() -> cfg().equipmentHud)
                .size(() -> cfg().equipmentHudVertical ? SLOT : SLOT * COUNT, () -> cfg().equipmentHudVertical ? SLOT * COUNT : SLOT)
                .preview((g, mc, x, y, s) -> drawHud(g, x, y, s, stacks()));
        feature = Feature.of("EquipmentDisplay").section("Inventory").when(() -> cfg().equipmentDisplay || cfg().equipmentHud || cfg().zorroCapeProtection)
                .tick(EquipmentDisplay::tick)
                .hud("equipmenthud", EquipmentDisplay::renderHud)
                .config("Inventory", b -> {
                    b.toggle("Equipment In Inventory", () -> cfg().equipmentDisplay, v -> cfg().equipmentDisplay = v)
                            .desc("Necklace, cloak, belt and gloves next to the inventory. Open /equipment once to fill it; click a slot to open it.");
                    b.dropdown("  Side", new String[]{"Left", "Right"},
                            () -> cfg().equipmentDisplayPos, v -> cfg().equipmentDisplayPos = v);
                    b.toggle("Equipment HUD", () -> cfg().equipmentHud, v -> cfg().equipmentHud = v)
                            .desc("The same four slots as a movable HUD (/tmap gui). Open /equipment once to fill it.");
                    b.toggle("  Vertical", () -> cfg().equipmentHudVertical, v -> cfg().equipmentHudVertical = v);
                });
        SbProfile.onChange(() -> loaded = false);
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof InventoryScreen inv)) return;
            ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
                if (!visible(inv)) return true;
                int i = hit(inv, event.x(), event.y());
                if (i < 0) return true;
                if (inv.getMenu().getCarried().isEmpty() && Minecraft.getInstance().getConnection() != null) {
                    Minecraft.getInstance().getConnection().sendCommand("equipment");
                }
                return false;
            });
        });
    }

    // ---------------------------------------------------------------- capture

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> screen) || mc.screen instanceof InventoryScreen) {
            lastScreen = null;
            return;
        }
        if (screen != lastScreen) {
            lastScreen = screen;
            openedAt = System.currentTimeMillis();
        }
        if (System.currentTimeMillis() - openedAt < 400) return;
        String title = StringUtil.stripTrim(screen.getTitle().getString());
        List<Slot> slots = screen.getMenu().slots;
        int[] idx = null;
        if (title.equals("Your Equipment and Stats") || title.equals("Stats & Equipment") || LOADOUTS.matcher(title).matches()) {
            idx = new int[]{10, 19, 28, 37};
        } else if (SETS.matcher(title).matches()) {
            for (int s = 36; s < 45 && s < slots.size(); s++) {
                ItemStack st = slots.get(s).getItem();
                if (st.is(Items.LIME_DYE) && EQUIPPED.matcher(StringUtil.stripTrim(st.getHoverName().getString())).matches()) {
                    int col = s - 36;
                    idx = new int[]{col, 9 + col, 18 + col, 27 + col};
                    break;
                }
            }
        }
        if (idx == null || slots.size() <= idx[3]) return;

        List<String> out = new ArrayList<>(COUNT);
        int hash = 1;
        for (int i : idx) {
            ItemStack st = slots.get(i).getItem();
            String name = StringUtil.stripTrim(st.getHoverName().getString()).toLowerCase(java.util.Locale.ROOT);
            boolean empty = st.isEmpty() || name.startsWith("empty") || name.startsWith("slot ");
            if (!empty && st.getItem().toString().contains("stained_glass_pane")) return;
            String enc = empty ? "" : StorageCache.encode(st);
            if (enc == null) return;
            out.add(enc);
            hash = 31 * hash + enc.hashCode();
        }
        if (hash == lastSeenHash && loaded) return;
        lastSeenHash = hash;
        ensure();
        if (out.equals(snbt)) return;
        snbt = out;
        stacks = null;
        save();
    }

    private static void ensure() {
        if (loaded && !FILE.stale()) return;
        loaded = true;
        stacks = null;
        snbt = new ArrayList<>();
        Path p = FILE.path();
        if (p == null) return;
        try {
            if (Files.exists(p)) {
                List<String> l = GSON.fromJson(Files.readString(p), TYPE);
                if (l != null) snbt = l;
            }
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("equipment.json: {}", e.toString());
        }
    }

    private static void save() {
        Path p = FILE.path();
        if (p == null) return;
        try {
            Files.createDirectories(p.getParent());
            Files.writeString(p, GSON.toJson(snbt, TYPE));
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("equipment.json: {}", e.toString());
        }
    }

    /** Skyblock id of the captured cloak, "" when empty, null when /equipment was never opened. */
    public static String cloakId() {
        ItemStack[] st = stacks();
        if (snbt.isEmpty()) return null;
        return st[1].isEmpty() ? "" : com.teslamaps.utils.ItemUtil.skyblockId(st[1]);
    }

    /** Belt is The Primordial or blood shot; null when /equipment was never opened. */
    public static Boolean beltCanCocoon() {
        ItemStack[] st = stacks();
        if (snbt.isEmpty()) return null;
        ItemStack belt = st[2];
        if (belt.isEmpty()) return false;
        if ("THE_PRIMORDIAL".equals(com.teslamaps.utils.ItemUtil.skyblockId(belt))) return true;
        var d = belt.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        return d != null && "blood_shot".equals(d.copyTag().getStringOr("modifier", ""));
    }

    private static ItemStack[] stacks() {
        ensure();
        if (stacks == null) {
            stacks = new ItemStack[COUNT];
            for (int i = 0; i < COUNT; i++) {
                String s = i < snbt.size() ? snbt.get(i) : "";
                stacks[i] = s == null || s.isEmpty() ? ItemStack.EMPTY : StorageCache.decodeStack(s);
            }
        }
        return stacks;
    }

    // ---------------------------------------------------------------- render

    private static boolean visible(AbstractContainerScreen<?> screen) {
        return feature != null && feature.active() && cfg().equipmentDisplay && screen instanceof InventoryScreen && SkyblockUtils.isInSkyblock();
    }

    private static int panelX(AbstractContainerScreen<?> screen) {
        HandledScreenAccessor a = (HandledScreenAccessor) screen;
        return "Right".equals(cfg().equipmentDisplayPos) ? a.getX() + a.getImageWidth() + 2 : a.getX() - 24;
    }

    private static int panelY(AbstractContainerScreen<?> screen) {
        return ((HandledScreenAccessor) screen).getY() + 6;
    }

    private static int hit(AbstractContainerScreen<?> screen, double mx, double my) {
        int x = panelX(screen) + 3, y = panelY(screen) + 3;
        if (mx < x || mx >= x + 16) return -1;
        for (int i = 0; i < COUNT; i++) {
            int sy = y + i * 18;
            if (my >= sy && my < sy + 16) return i;
        }
        return -1;
    }

    private static void renderHud(GuiGraphicsExtractor g, net.minecraft.client.DeltaTracker d) {
        if (!cfg().equipmentHud || hud == null || !SkyblockUtils.isInSkyblock()) return;
        if (Minecraft.getInstance().options.hideGui) return;
        drawHud(g, hud.x(), hud.y(), hud.scale(), stacks());
    }

    private static void drawHud(GuiGraphicsExtractor g, int x, int y, float scale, ItemStack[] st) {
        boolean vertical = cfg().equipmentHudVertical;
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        com.teslamaps.render.gui.HudStyle.panel(g, 0, 0, vertical ? SLOT : SLOT * COUNT, vertical ? SLOT * COUNT : SLOT);
        for (int i = 0; i < COUNT; i++) {
            int sx = vertical ? 1 : i * SLOT + 1, sy = vertical ? i * SLOT + 1 : 1;
            g.fill(sx, sy, sx + 16, sy + 16, 0x40000000);
            if (!st[i].isEmpty()) g.item(st[i], sx, sy);
        }
        pose.popMatrix();
    }

    public static void render(AbstractContainerScreen<?> screen, GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (!(screen instanceof InventoryScreen) || !visible(screen)) return;
        int px = panelX(screen), py = panelY(screen);
        g.fill(px, py, px + 22, py + 4 + COUNT * 18, 0xFFC6C6C6);
        g.outline(px, py, 22, 4 + COUNT * 18, 0xFF555555);
        ItemStack[] st = stacks();
        Minecraft mc = Minecraft.getInstance();
        int hovered = hit(screen, mouseX, mouseY);
        for (int i = 0; i < COUNT; i++) {
            int x = px + 3, y = py + 3 + i * 18;
            g.fill(x - 1, y - 1, x + 17, y + 17, 0xFF373737);
            g.fill(x, y, x + 17, y + 17, 0xFFFFFFFF);
            g.fill(x, y, x + 16, y + 16, 0xFF8B8B8B);
            if (!st[i].isEmpty()) {
                g.item(st[i], x, y);
                g.itemDecorations(mc.font, st[i], x, y);
            }
            if (i == hovered) {
                g.fill(x, y, x + 16, y + 16, 0x80FFFFFF);
                if (!st[i].isEmpty()) g.setTooltipForNextFrame(mc.font, st[i], mouseX, mouseY);
                else g.setTooltipForNextFrame(mc.font, Component.literal("§7" + NAMES[i] + " §8- click for /equipment"), mouseX, mouseY);
            }
        }
    }
}

package com.teslamaps.adm.leap;

// Ported from Odin's LeapMenu.kt with ADM's extras (POV box shrink/fade, ADM leap order)
import com.teslamaps.adm.Adm;
import com.teslamaps.adm.leap.pov.PovPreviews;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.ClassColors;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.player.PlayerTracker;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** ADM's leap menu: Odin's four boxes, independent of ours, replacing LeapOverlay while ADM is on. */
public final class AdmLeapMenu {

    private AdmLeapMenu() {}

    public static final int BOX_WIDTH = 200, BOX_HEIGHT = 75;

    public record Mate(String name, String clazz, boolean dead, UUID uuid) {}

    private static final String[] SORTINGS = {"Odin Sorting", "A-Z Class", "A-Z Name", "Custom sorting", "No Sorting"};
    private static final String[] CLASSES = {"Archer", "Berserk", "Healer", "Mage", "Tank"};
    private static final Pattern LEAPED = Pattern.compile("^You have teleported to (\\w{1,16})!$");

    private static Mate[] mates = new Mate[4];
    private static int mateCount;
    private static final long[] hoverStart = new long[4];
    private static final boolean[] hovered = new boolean[4];

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static boolean overrides() { return Adm.overrides(cfg().admLeapMenu); }

    public static void register() {
        Feature.of("AdmLeapMenu").section(Adm.SECTION).when(() -> cfg().admLeapMenu)
                .tickAlways(AdmLeapMenu::refresh)
                .serverChat(AdmLeapMenu::onChat)
                .config("ADM Leap & Recore", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Leap Menu");
                    b.toggle("Leap Menu", () -> c.admLeapMenu, v -> c.admLeapMenu = v)
                            .desc("ADM's own leap menu (Odin's look) in the Spirit Leap gui. Replaces the TeslaMaps leap overlay while ADM is on.");
                    b.dropdown("Sorting", SORTINGS, () -> c.admLeapSorting, v -> c.admLeapSorting = v)
                            .desc("How to sort the leap menu. /lo to set the custom order.");
                    b.button("Edit Leap Order (/lo)", () -> Minecraft.getInstance().setScreen(new AdmLeapOrderScreen()))
                            .desc("Drag players between the four spots; setting it switches Sorting to Custom until the party changes.");
                    b.toggle("On Key Release", () -> c.admLeapOnRelease, v -> c.admLeapOnRelease = v)
                            .desc("Whether to trigger the leap on key release instead of key press.");
                    b.toggle("Only Classes", () -> c.admLeapOnlyClass, v -> c.admLeapOnlyClass = v).desc("Renders classes instead of names.");
                    b.toggle("Color Style", () -> c.admLeapColorStyle, v -> c.admLeapColorStyle = v).desc("Which color style to use.");
                    b.color("Background Color", () -> c.admLeapBgColor, v -> c.admLeapBgColor = v).desc("Color of the background of the leap menu.");
                    b.slider("Render Scale", 0.1f, 2f, () -> c.admLeapScale, v -> c.admLeapScale = Math.round(v * 10f) / 10f, 1).desc("Scale of the leap menu.");
                    b.dropdown("Mode", new String[]{"Corners", "Class"}, () -> c.admLeapKeyMode, v -> c.admLeapKeyMode = v).desc("How the keybinds should function.");
                    b.keybind("Top Left", () -> c.admLeapKeyTL, v -> c.admLeapKeyTL = v);
                    b.keybind("Top Right", () -> c.admLeapKeyTR, v -> c.admLeapKeyTR = v);
                    b.keybind("Bottom Left", () -> c.admLeapKeyBL, v -> c.admLeapKeyBL = v);
                    b.keybind("Bottom Right", () -> c.admLeapKeyBR, v -> c.admLeapKeyBR = v);
                    b.keybind("Archer", () -> c.admLeapKeyArcher, v -> c.admLeapKeyArcher = v);
                    b.keybind("Berserker", () -> c.admLeapKeyBerserk, v -> c.admLeapKeyBerserk = v);
                    b.keybind("Healer", () -> c.admLeapKeyHealer, v -> c.admLeapKeyHealer = v);
                    b.keybind("Mage", () -> c.admLeapKeyMage, v -> c.admLeapKeyMage = v);
                    b.keybind("Tank", () -> c.admLeapKeyTank, v -> c.admLeapKeyTank = v);
                    b.toggle("Leap Announce", () -> c.admLeapAnnounce, v -> c.admLeapAnnounce = v).desc("Announces when you leap to a player.");
                });
    }

    // ------------------------------------------------------------------ teammates

    /** The four leap slots in Sorting order; null = empty. */
    public static Mate[] teammates() { return mates; }

    private static int classOrdinal(String c) {
        for (int i = 0; i < CLASSES.length; i++) if (CLASSES[i].equalsIgnoreCase(c)) return i;
        return 5;
    }

    private static int priority(String c) {
        if (c == null) return 0;
        return switch (c) { case "Berserk" -> 0; case "Tank" -> 1; case "Archer", "Healer", "Mage" -> 2; default -> 0; };
    }

    private static int defaultQuadrant(String c) {
        if (c == null) return 0;
        return switch (c) { case "Berserk" -> 1; case "Healer" -> 2; case "Mage", "Tank" -> 3; default -> 0; };
    }

    private static void refresh() {
        if (!overrides()) return;
        String self = Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getName().getString();
        List<Mate> list = new ArrayList<>();
        for (PlayerTracker.DungeonPlayer dp : PlayerTracker.getPlayers()) {
            if (dp.getName() == null || dp.getName().equals(self)) continue;
            String clazz = "EMPTY".equals(dp.getDungeonClass()) ? null : dp.getDungeonClass();
            list.add(new Mate(dp.getName(), clazz, !dp.isAlive() || AdmWorld.isDead(dp.getName()), dp.getUuid()));
        }
        Mate[] out = new Mate[4];
        String sort = cfg().admLeapSorting == null ? SORTINGS[0] : cfg().admLeapSorting;
        if (sort.equals(SORTINGS[0])) {
            list.sort(Comparator.comparingInt(m -> priority(m.clazz)));
            List<Mate> second = new ArrayList<>();
            for (Mate m : list) {
                int q = defaultQuadrant(m.clazz);
                if (out[q] == null) out[q] = m; else second.add(m);
            }
            for (int i = 0; i < 4 && !second.isEmpty(); i++) if (out[i] == null) out[i] = second.remove(0);
        } else {
            if (sort.equals(SORTINGS[1])) list.sort(Comparator.comparingInt((Mate m) -> classOrdinal(m.clazz)).thenComparing(Mate::name));
            else if (sort.equals(SORTINGS[2])) list.sort(Comparator.comparing(Mate::name));
            else if (sort.equals(SORTINGS[3])) {
                List<String> order = AdmLeapOrder.order();
                list.sort(Comparator.comparingInt(m -> {
                    int i = order.indexOf(m.name.toLowerCase());
                    return i < 0 ? Integer.MAX_VALUE : i;
                }));
            }
            for (int i = 0; i < Math.min(4, list.size()); i++) out[i] = list.get(i);
        }
        mates = out;
        mateCount = list.size();
    }

    // ------------------------------------------------------------------ screen

    public static AbstractContainerScreen<?> leapScreen() {
        if (!overrides()) return null;
        Screen s = Minecraft.getInstance().screen;
        if (!(s instanceof AbstractContainerScreen<?> c)) return null;
        String t = c.getTitle().getString();
        if (!t.equals("Spirit Leap") && !t.equals("Teleport to Player")) return null;
        return c;
    }

    /** The menu takes over this screen: a leap screen with someone to show. */
    public static boolean active(Screen screen) {
        if (screen == null || screen != leapScreen()) return false;
        for (Mate m : mates) if (m != null) return true;
        return false;
    }

    public static void render(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        TeslaMapsConfig c = cfg();
        int halfW = mc.getWindow().getGuiScaledWidth() / 2;
        int halfH = mc.getWindow().getGuiScaledHeight() / 2;
        float overlayScale = PovPreviews.overlayScale;
        long now = System.currentTimeMillis();
        for (int i = 0; i < 4; i++) {
            Mate m = mates[i];
            if (m == null) continue;
            int col = i % 2, row = i / 2;
            int nearX = col == 0 ? halfW - 24 : halfW + 24;
            int nearY = row == 0 ? halfH - 24 : halfH + 24;
            int localX = col == 0 ? -BOX_WIDTH : 0;
            int localY = row == 0 ? -BOX_HEIGHT : 0;
            boolean hov = (col == 0 ? mouseX < halfW : mouseX >= halfW) && (row == 0 ? mouseY < halfH : mouseY >= halfH);
            if (hov != hovered[i]) {
                long elapsed = Math.min(200, now - hoverStart[i]);
                hoverStart[i] = now - (200 - elapsed);
                hovered[i] = hov;
            }
            float p = Math.min(1f, (now - hoverStart[i]) / 200f);
            float grow = (hovered[i] ? p : 1f - p) * 5f;

            int classColor = classColor(m.clazz);
            int bg = c.admLeapColorStyle ? classColor : TeslaMapsConfig.parseColor(c.admLeapBgColor);
            g.pose().pushMatrix();
            g.pose().translate(nearX, nearY);
            float s = c.admLeapScale * overlayScale;
            g.pose().scale(s * (BOX_WIDTH + grow * 2f) / BOX_WIDTH, s * (BOX_HEIGHT + grow * 2f) / BOX_HEIGHT);
            roundedFill(g, localX, localY, localX + BOX_WIDTH, localY + BOX_HEIGHT, PovPreviews.fade(bg), 9);
            int face = (int) (BOX_HEIGHT * 0.76);
            PlayerSkin skin = skin(m.name);
            PlayerFaceExtractor.extractRenderState(g, skin, localX + 9, localY + 9, face);
            String clazzName = m.clazz == null ? "EMPTY" : m.clazz.toUpperCase();
            g.text(mc.font, !c.admLeapOnlyClass ? m.name : clazzName, localX + 15 + face, localY + (int) (BOX_HEIGHT / 2.5),
                    !c.admLeapColorStyle ? classColor : TeslaMapsConfig.parseColor(c.admLeapBgColor), true);
            if (!c.admLeapOnlyClass || m.dead) {
                g.text(mc.font, m.dead ? "DEAD" : clazzName, localX + 15 + face, localY + (int) (BOX_HEIGHT / 1.7),
                        m.dead ? 0xFFFF5555 : 0xFFFFFFFF, true);
            }
            g.pose().popMatrix();
        }
    }

    private static int classColor(String clazz) {
        int def = switch (clazz == null ? "" : clazz) {
            case "Archer" -> 0xFFFFAA00;
            case "Berserk" -> 0xFFAA0000;
            case "Healer" -> 0xFFFF55FF;
            case "Mage" -> 0xFF55FFFF;
            case "Tank" -> 0xFF00AA00;
            default -> 0xFFFFFFFF;
        };
        return ClassColors.or(clazz, def);
    }

    public static PlayerSkin skin(String name) { return AdmLeapOrder.skin(name); }

    /** Rounded rect the way it reads at Odin's radius: chamfered corners. */
    public static void roundedFill(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int color, int r) {
        r = Math.max(0, Math.min(r, Math.min((x1 - x0) / 2, (y1 - y0) / 2)));
        g.fill(x0 + r, y0, x1 - r, y1, color);
        for (int k = 0; k < r; k++) {
            double dy = r - k - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
            g.fill(x0 + inset, y0 + k, x0 + r, y0 + k + 1, color);
            g.fill(x1 - r, y0 + k, x1 - inset, y0 + k + 1, color);
            g.fill(x0 + inset, y1 - k - 1, x0 + r, y1 - k, color);
            g.fill(x1 - r, y1 - k - 1, x1 - inset, y1 - k, color);
        }
        g.fill(x0, y0 + r, x0 + r, y1 - r, color);
        g.fill(x1 - r, y0 + r, x1, y1 - r, color);
    }

    // ------------------------------------------------------------------ input

    public static boolean click(AbstractContainerScreen<?> chest, double mx, double my, boolean release) {
        if (cfg().admLeapOnRelease != release) return true;
        int quadrant = (my >= chest.height / 2.0 ? 2 : 0) + (mx >= chest.width / 2.0 ? 1 : 0);
        String sort = cfg().admLeapSorting;
        boolean listSort = SORTINGS[1].equals(sort) || SORTINGS[2].equals(sort) || SORTINGS[3].equals(sort);
        if (listSort && mateCount < quadrant) return true;
        Mate m = mates[quadrant];
        if (m == null) return true;
        if (m.dead) { modMessage("This player is dead, can't leap."); return true; }
        leapTo(m.name, chest);
        return true;
    }

    public static boolean key(AbstractContainerScreen<?> chest, int key) {
        if (key < 0) return false;
        TeslaMapsConfig c = cfg();
        int index = -1;
        if (!"Class".equals(c.admLeapKeyMode)) {
            int[] keys = {c.admLeapKeyTL, c.admLeapKeyTR, c.admLeapKeyBL, c.admLeapKeyBR};
            for (int i = 0; i < 4; i++) if (keys[i] == key) { index = i; break; }
        } else {
            int[] keys = {c.admLeapKeyArcher, c.admLeapKeyBerserk, c.admLeapKeyHealer, c.admLeapKeyMage, c.admLeapKeyTank};
            int k = -1;
            for (int i = 0; i < keys.length; i++) if (keys[i] == key) { k = i; break; }
            if (k < 0) return false;
            for (int i = 0; i < 4; i++) if (mates[i] != null && CLASSES[k].equalsIgnoreCase(mates[i].clazz)) { index = i; break; }
            if (index < 0) return false;
        }
        if (index < 0) return false;
        Mate m = mates[index];
        if (m == null) return false;
        if (m.dead) { modMessage("This player is dead, can't leap."); return true; }
        leapTo(m.name, chest);
        return true;
    }

    private static void leapTo(String name, AbstractContainerScreen<?> chest) {
        Minecraft mc = Minecraft.getInstance();
        List<Slot> slots = chest.getMenu().slots;
        Slot target = null;
        for (int i = 11; i < Math.min(16, slots.size()); i++) {
            String n = StringUtil.strip(slots.get(i).getItem().getHoverName().getString());
            int sp = n.indexOf(' ');
            String after = sp >= 0 ? n.substring(sp + 1) : n;
            if (after.equalsIgnoreCase(name)) { target = slots.get(i); break; }
        }
        if (target == null || mc.gameMode == null || mc.player == null) return;
        mc.gameMode.handleContainerInput(chest.getMenu().containerId, target.index, 0, ContainerInput.PICKUP, mc.player);
        modMessage("Teleporting to " + name + ".");
    }

    private static void modMessage(String s) {
        var p = Minecraft.getInstance().player;
        if (p != null) p.sendSystemMessage(Component.literal("§8[§6ADM§8] §r" + s));
    }

    private static void onChat(String raw) {
        if (!cfg().admLeapAnnounce || !DungeonManager.isInDungeon() || !overrides()) return;
        Matcher m = LEAPED.matcher(StringUtil.strip(raw));
        if (m.matches()) com.teslamaps.features.ChatQueue.command("pc Leaped to " + m.group(1) + "!");
    }
}

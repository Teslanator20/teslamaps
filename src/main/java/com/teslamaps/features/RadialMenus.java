package com.teslamaps.features;

// Ported from Skyblocker's RadialMenuScreen / RadialMenuManager / menus/*
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Container menus drawn as a ring of item segments around the screen centre, picked by mouse angle. */
public final class RadialMenus {

    private RadialMenus() {}

    private static final int INNER = 55, OUTER = 110, TILE = 24;
    private static final String[] BASIC_NAV = {"Go Back", "Close"};

    private enum Menu {
        GENERAL("general", "SkyBlock Menu, Skills, Bags, Booster Cookie, Profiles",
                "skyblock menu|your skills|your bags|booster cookie|profile management", BASIC_NAV),
        WARDROBE("wardrobe", "Wardrobe", "wardrobe .*", new String[]{"Go Back", "Close", "Next Page", "Previous Page"}),
        FAST_TRAVEL("fastTravel", "Fast Travel", "(fast travel)|(.* warps)", BASIC_NAV),
        SACKS("sacks", "Sacks", ".* sack|sack of sacks", new String[]{"Rune Type Filter", "Close", "Go Back", "Item Tier"}),
        BANK("bank", "Bank", ".*bank.*", BASIC_NAV),
        STORAGE("storage", "Storage", "storage", BASIC_NAV),
        COLLECTIONS("collections", "Collections", ".*collections", BASIC_NAV),
        HUNTING("huntingToolkit", "Hunting Toolkit", "hunting toolkit", BASIC_NAV),
        FARMING("farmingToolkit", "Farming Toolkit", "farming toolkit", BASIC_NAV);

        final String id, label;
        final Pattern title;
        final String[] nav;

        Menu(String id, String label, String regex, String[] nav) {
            this.id = id;
            this.label = label;
            this.title = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            this.nav = nav;
        }

        boolean matches(String t) {
            if (!title.matcher(t).matches()) return false;
            return this != BANK || !t.contains("banker broadjaw");
        }

        boolean keep(int slot, ItemStack s) {
            String path = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
            boolean black = path.equals("black_stained_glass_pane");
            return switch (this) {
                case WARDROBE -> s.is(Items.ARROW) || s.is(Items.BARRIER) || !black && slot < 9;
                case FAST_TRAVEL -> slot != 50 && slot != 53 && !black && !s.is(Items.BEDROCK);
                case BANK -> !black && !s.is(Items.GOLD_BLOCK) && !s.is(Items.REDSTONE_TORCH) && !s.is(Items.FILLED_MAP);
                case STORAGE -> !black && !path.equals("red_stained_glass_pane") && !path.equals("brown_stained_glass_pane")
                        && slot != 4 && slot != 22;
                case COLLECTIONS -> slot != 4 && !black;
                case HUNTING, FARMING -> slot != 41 && !black && !path.equals("gray_dye");
                default -> !black;
            };
        }

        int slotOffset(int slot) {
            return this == WARDROBE && slot < 9 ? 36 : 0;
        }

        int button(int original, int slot, String title) {
            if (this == SACKS && title.equals("sack of sacks") && slot != 29 && slot != 30 && slot != 31) return 1;
            return original;
        }
    }

    private record Option(int slot, ItemStack stack, String name) {}

    private static List<Option> options = List.of();
    private static Menu menu;
    private static String titleText = "", titleLower = "";
    private static int containerId = -1, vanillaFor = -1, hovered = -1;
    private static long lastClick, lastNav;
    private static int navDir = 1, lastNavKey = -1;

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    private static final Feature FEATURE = Feature.of("RadialMenus").section("Inventory").when(() -> cfg().radialMenus);

    public static void register() {
        FEATURE.tick(RadialMenus::tick)
                .config("Inventory", b -> {
                    b.label("Radial Menus");
                    b.toggle("Radial Menus", () -> cfg().radialMenus, v -> cfg().radialMenus = v)
                            .desc("Picked menus become a ring of items around the cursor: point at an item and click, or arrows/WASD + Enter. Vanilla button bottom right.")
                            .tags("radial", "ring", "wheel", "menu");
                    List<String> labels = new ArrayList<>();
                    for (Menu m : Menu.values()) labels.add(m.label);
                    b.searchList("  Menus", () -> labels, l -> cfg().radialMenusOn.contains(idOf(l)), l -> {
                        String id = idOf(l);
                        if (!cfg().radialMenusOn.remove(id)) cfg().radialMenusOn.add(id);
                    });
                    b.toggle("  Tooltips Without Shift", () -> cfg().radialMenuTooltips, v -> cfg().radialMenuTooltips = v);
                });
    }

    private static String idOf(String label) {
        for (Menu m : Menu.values()) if (m.label.equals(label)) return m.id;
        return label;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs) || !(cs.getMenu() instanceof ChestMenu chest)) {
            containerId = -1;
            menu = null;
            if (!(mc.screen instanceof AbstractContainerScreen<?>)) vanillaFor = -1;
            return;
        }
        String title = StringUtil.stripTrim(cs.getTitle().getString());
        String lower = title.toLowerCase(Locale.ROOT);
        if (menu == null || !lower.equals(titleLower) || chest.containerId != containerId) {
            menu = null;
            for (Menu m : Menu.values()) {
                if (cfg().radialMenusOn.contains(m.id) && m.matches(lower)) { menu = m; break; }
            }
            if (menu == Menu.STORAGE && StorageOverlay.active()) menu = null;
            if (menu != null && (CustomWardrobe.blocking(cs) || FancyWarpMenu.blocking(cs))) menu = null;
            titleText = title;
            titleLower = lower;
            if (chest.containerId != containerId) hovered = -1;
            containerId = chest.containerId;
        }
        if (menu == null) {
            options = List.of();
            return;
        }
        int size = chest.getRowCount() * 9;
        List<Option> out = new ArrayList<>();
        List<Option> nav = new ArrayList<>();
        for (int i = 0; i < size && i < chest.slots.size(); i++) {
            ItemStack s = chest.slots.get(i).getItem();
            if (s.isEmpty() || !menu.keep(i, s)) continue;
            String name = StringUtil.stripTrim(s.getHoverName().getString());
            Option o = new Option(i, s, name);
            boolean isNav = false;
            for (String n : menu.nav) if (n.equals(name)) { isNav = true; break; }
            (isNav ? nav : out).add(o);
        }
        // navigation items go to the bottom of the ring
        int bottom = Math.ceilDiv(out.size(), 2);
        out.addAll(Math.min(bottom, out.size()), nav);
        options = out;
        if (hovered >= options.size()) hovered = -1;
    }

    private static boolean active(AbstractContainerScreen<?> cs) {
        return menu != null && !options.isEmpty() && cs.getMenu().containerId == containerId
                && vanillaFor != containerId && FEATURE.active();
    }

    public static boolean blocking(AbstractContainerScreen<?> cs) {
        return active(cs);
    }

    private static float arc() {
        return (float) (Math.PI * 2 / options.size());
    }

    private static int[] vanillaButton(int w, int h) {
        return new int[]{w - 60, h - 24, 50, 16};
    }

    private static boolean inVanilla(int w, int h, double mx, double my) {
        int[] b = vanillaButton(w, h);
        return mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3];
    }

    private static void updateHover(int w, int h, double mx, double my) {
        if (inVanilla(w, h, mx, my)) { hovered = -1; return; }
        double dx = mx - w / 2.0, dy = my - h / 2.0;
        if (dx * dx + dy * dy < 16) return;
        double a = Math.atan2(dx, -dy);
        if (a < 0) a += Math.PI * 2;
        hovered = Math.min(options.size() - 1, (int) (a / arc()));
    }

    private static double lastMx = -1, lastMy = -1;

    public static boolean render(AbstractContainerScreen<?> cs, GuiGraphicsExtractor g, int mx, int my) {
        if (!active(cs)) return false;
        Minecraft mc = Minecraft.getInstance();
        int w = g.guiWidth(), h = g.guiHeight(), cx = w / 2, cy = h / 2;
        if (mx != lastMx || my != lastMy) {
            updateHover(w, h, mx, my);
            lastMx = mx;
            lastMy = my;
        }
        g.fill(0, 0, w, h, 0xA0000000);
        List<Option> opts = options;
        float arc = arc();
        float radius = (INNER + OUTER) / 2f;
        for (int i = 0; i < opts.size(); i++) {
            double a = (i + 0.5) * arc;
            boolean hover = i == hovered;
            float r = hover ? radius + 5 : radius;
            int x = Math.round(cx + (float) Math.sin(a) * r), y = Math.round(cy - (float) Math.cos(a) * r);
            int half = hover ? TILE / 2 + 2 : TILE / 2;
            g.fill(x - half, y - half, x + half, y + half, hover ? 0xF0000000 : 0x77000000);
            if (hover) {
                g.fill(x - half, y - half, x + half, y - half + 1, 0xFFFFFFFF);
                g.fill(x - half, y + half - 1, x + half, y + half, 0xFFFFFFFF);
                g.fill(x - half, y - half, x - half + 1, y + half, 0xFFFFFFFF);
                g.fill(x + half - 1, y - half, x + half, y + half, 0xFFFFFFFF);
            }
            g.item(opts.get(i).stack, x - 8, y - 8);
            g.itemDecorations(mc.font, opts.get(i).stack, x - 8, y - 8);
        }
        int tw = mc.font.width(titleText);
        g.centeredText(mc.font, titleText, cx, cy - mc.font.lineHeight, 0xFFFFFFFF);
        g.fill(cx - tw / 2, cy, cx + tw / 2, cy + 1, 0xFFFFFFFF);
        if (hovered >= 0 && hovered < opts.size()) {
            Option o = opts.get(hovered);
            g.centeredText(mc.font, o.stack.getHoverName(), cx, cy + 3, 0xFFFFFFFF);
            if (cfg().radialMenuTooltips || mc.hasShiftDown()) g.setTooltipForNextFrame(mc.font, o.stack, mx, my);
        }
        int[] b = vanillaButton(w, h);
        boolean hv = mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3];
        g.fill(b[0], b[1], b[0] + b[2], b[1] + b[3], hv ? 0xFF505060 : 0xFF2C2C36);
        g.centeredText(mc.font, Component.literal("Vanilla"), b[0] + b[2] / 2, b[1] + 4, 0xFFFFFFFF);
        return true;
    }

    public static boolean click(AbstractContainerScreen<?> cs, MouseButtonEvent e) {
        if (!active(cs)) return false;
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        if (inVanilla(w, h, e.x(), e.y())) {
            vanillaFor = containerId;
            return true;
        }
        if (e.button() > 1) return true;
        updateHover(w, h, e.x(), e.y());
        send(cs, e.button());
        return true;
    }

    public static boolean key(AbstractContainerScreen<?> cs, KeyEvent e) {
        if (!active(cs)) return false;
        Minecraft mc = Minecraft.getInstance();
        int k = e.key();
        if (k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER || k == GLFW.GLFW_KEY_SPACE) {
            send(cs, 0);
            return true;
        }
        int dir;
        if (k == GLFW.GLFW_KEY_UP || mc.options.keyUp.matches(e)) dir = 0;
        else if (k == GLFW.GLFW_KEY_RIGHT || mc.options.keyRight.matches(e)) dir = 1;
        else if (k == GLFW.GLFW_KEY_DOWN || mc.options.keyDown.matches(e)) dir = 2;
        else if (k == GLFW.GLFW_KEY_LEFT || mc.options.keyLeft.matches(e)) dir = 3;
        else return false;
        navigate(dir);
        return true;
    }

    /** 0 up, 1 right, 2 down, 3 left; keeps turning the same way while the key repeats. */
    private static void navigate(int dir) {
        int n = options.size();
        if (hovered == -1) {
            hovered = switch (dir) {
                case 0 -> 0;
                case 1 -> n / 4;
                case 2 -> n / 2;
                default -> (int) (n * 0.75);
            };
            lastNavKey = dir;
            lastNav = System.currentTimeMillis();
            return;
        }
        long now = System.currentTimeMillis();
        if (lastNavKey != dir || now > lastNav + 500) {
            lastNavKey = dir;
            double a = hovered * arc();
            boolean rightHalf = a <= Math.PI;
            boolean bottomHalf = a > Math.PI / 2 && a < Math.PI * 1.5;
            navDir = switch (dir) {
                case 0 -> rightHalf ? -1 : 1;
                case 2 -> rightHalf ? 1 : -1;
                case 3 -> bottomHalf ? 1 : -1;
                default -> bottomHalf ? -1 : 1;
            };
        }
        hovered = Math.floorMod(hovered + navDir, n);
        lastNav = now;
    }

    private static void send(AbstractContainerScreen<?> cs, int button) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null || mc.player == null || hovered < 0 || hovered >= options.size()) return;
        long now = System.currentTimeMillis();
        if (now - lastClick < 150) return;
        lastClick = now;
        int slot = options.get(hovered).slot;
        int target = slot + menu.slotOffset(slot);
        mc.gameMode.handleContainerInput(cs.getMenu().containerId, target, menu.button(button, target, titleLower),
                ContainerInput.PICKUP, mc.player);
    }
}

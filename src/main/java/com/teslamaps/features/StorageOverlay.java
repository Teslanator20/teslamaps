package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.croesus.PriceManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class StorageOverlay {

    private static final int CELL = 18, GRID_COLS = 9, HEADER = 10;
    private static final int BOX_W = GRID_COLS * CELL + 6;
    private static final int GAP = 8;

    // Both scales are FITTED to the current scaled-GUI size every frame so the overlay looks the
    // same at any resolution / GUI scale; the config value just nudges it within a safe range.
    private static final float SIDE_MARGIN = 8f;
    private static final int SCROLLBAR_W = 13;
    private static float fitScl = 1.5f, fitIscl = 2.0f;

    private static float scl() { return fitScl; }
    private static float iscl() { return fitIscl; }

    private static int cols() {
        return Math.max(1, Math.min(10, TeslaMapsConfig.get().storageColumns));
    }

    private static float userScale() {
        return Math.max(0.5f, Math.min(1.5f, TeslaMapsConfig.get().storageOverlayScale));
    }

    // Box-grid scale: fit 3 columns into the width minus margins+scrollbar, capped so it never
    // grows absurdly large on wide screens, then nudged by the user scale but re-clamped to fit.
    private static void layout(int screenW, int screenH) {
        float boxGridW = cols() * BOX_W + (cols() - 1) * GAP;
        float pct = Math.max(40, Math.min(100, TeslaMapsConfig.get().storageWidthPercent)) / 100f;
        float availBoxW = screenW * pct - 2 * SIDE_MARGIN - SCROLLBAR_W;
        float maxBoxScl = availBoxW / boxGridW;
        float boxScl = Math.min(maxBoxScl, 1.15f) * userScale();
        // never exceed the fit width (would clip / hit the scrollbar); floor only as a last resort
        fitScl = Math.min(Math.max(0.4f, boxScl), maxBoxScl);

        float bottomW = SEL_COLS * SEL_CELL + SEL_GAP + 9 * 18;     // selector + gap + inventory
        float availBottomW = screenW - 2 * SIDE_MARGIN;
        float maxInvScl = availBottomW / bottomW;
        // also bound by available bottom height so the inventory never eats the box area
        float invVirtH = 13 + 3 * 18 + 4 + 18 + 13;                 // wall top..hotbar bottom padding
        float maxInvSclH = (screenH * 0.42f) / invVirtH;
        float maxInv = Math.min(maxInvScl, maxInvSclH);
        float invScl = Math.min(maxInv, 1.5f) * userScale();
        fitIscl = Math.min(Math.max(0.4f, invScl), maxInv);
    }

    private static int topPad() { return TeslaMapsConfig.get().storageShowNames ? HEADER : 2; }
    private static int boxH() { return topPad() + 5 * CELL + 2; }
    private static final int HEADER_PX = 34;

    private record Box(String key, String label, boolean ender, int num) {}

    /** Box offsets from (startX, startY) in virtual px, before scrolling. */
    private record Lay(int[] x, int[] y, int[] h, int contentH) {}

    private static int rowsOf(String key) {
        int size = StorageCache.size(key);
        return Math.max(1, (size + GRID_COLS - 1) / GRID_COLS);
    }

    private static Lay lay(List<Box> vis) {
        int n = vis.size(), cols = cols();
        int[] x = new int[n], y = new int[n], h = new int[n];
        if (!TeslaMapsConfig.get().storageCompact) {
            for (int i = 0; i < n; i++) {
                x[i] = (i % cols) * (BOX_W + GAP);
                y[i] = (i / cols) * (boxH() + GAP);
                h[i] = boxH();
            }
            return new Lay(x, y, h, ((n + cols - 1) / cols) * (boxH() + GAP));
        }
        // masonry: each box drops into the shortest column
        int[] colH = new int[cols];
        for (int i = 0; i < n; i++) {
            int c = 0;
            for (int k = 1; k < cols; k++) if (colH[k] < colH[c]) c = k;
            x[i] = c * (BOX_W + GAP);
            y[i] = colH[c];
            h[i] = topPad() + rowsOf(vis.get(i).key) * CELL + 2;
            colH[c] += h[i] + GAP;
        }
        int max = 0;
        for (int v : colH) max = Math.max(max, v);
        return new Lay(x, y, h, max);
    }

    private static String rename = null;
    private static String renameText = "";

    public static String pageName(String key, String fallback) {
        String n = TeslaMapsConfig.get().storagePageNames.get(key);
        return n == null || n.isEmpty() ? fallback : n.replace('&', '§');
    }

    private static long lastRenderMs = 0;

    private static String search = "";
    private static boolean searchFocused = false;
    private static float scroll = 0;
    private static boolean scrollDragging = false;

    private record PageVal(String label, double value) {}
    private record ItemVal(String name, double value) {}

    private static long lastValueCalc = 0;
    private static double cachedTotal = 0;
    private static List<PageVal> cachedPages = new ArrayList<>();
    private static List<ItemVal> cachedTop = new ArrayList<>();

    private static void recalcValue() {
        long now = System.currentTimeMillis();
        if (now - lastValueCalc < 2000) return;
        lastValueCalc = now;
        PriceManager.ensureFresh();
        String live = liveKey();
        double total = 0;
        List<PageVal> pages = new ArrayList<>();
        List<ItemVal> items = new ArrayList<>();
        for (int i = 1; i <= 9; i++) total += accumPage("epage_" + i, "Ender " + i, live, pages, items);
        for (int i = 1; i <= 18; i++) total += accumPage("backpack_" + i, "Backpack " + i, live, pages, items);
        items.sort((a, b) -> Double.compare(b.value, a.value));
        cachedTotal = total;
        cachedPages = pages;
        cachedTop = items.size() > 10 ? new ArrayList<>(items.subList(0, 10)) : items;
    }

    private static double accumPage(String key, String label, String live, List<PageVal> pages, List<ItemVal> items) {
        Map<Integer, ItemStack> m = key.equals(live) ? liveContent() : StorageCache.items(key);
        if (m == null || m.isEmpty()) return 0;
        double pv = 0;
        for (ItemStack st : m.values()) {
            double v = EstimatedValue.compute(st) * st.getCount();
            if (v <= 0) continue;
            pv += v;
            items.add(new ItemVal(strip(st.getHoverName().getString()), v));
        }
        if (pv > 0) pages.add(new PageVal(label, pv));
        return pv;
    }

    private static void drawValuePanel(GuiGraphicsExtractor ctx, Minecraft mc) {
        if (!TeslaMapsConfig.get().containerValue) return;
        recalcValue();
        if (cachedPages.isEmpty()) return;

        List<String[]> lines = new ArrayList<>();
        lines.add(new String[]{"§6§lStorage Value", "§6" + EstimatedValue.fmt(cachedTotal)});
        lines.add(null);
        lines.add(new String[]{"§e§lPer Page", ""});
        for (PageVal p : cachedPages) lines.add(new String[]{"§7" + p.label(), "§6" + EstimatedValue.fmt(p.value())});
        if (!cachedTop.isEmpty()) {
            lines.add(null);
            lines.add(new String[]{"§e§lTop " + cachedTop.size(), ""});
            for (ItemVal it : cachedTop) {
                String name = it.name().length() > 22 ? it.name().substring(0, 21) + "…" : it.name();
                lines.add(new String[]{" §7" + name, "§6" + EstimatedValue.fmt(it.value())});
            }
        }

        int pad = 4, gap = 14;
        int contentW = 0;
        for (String[] l : lines) {
            if (l == null) continue;
            contentW = Math.max(contentW, mc.font.width(l[0]) + gap + mc.font.width(l[1]));
        }
        int panelW = contentW + pad * 2;
        int x = 4, y = HEADER_PX + 4;
        ctx.fill(x, y, x + panelW, y + lines.size() * 10 + pad * 2, 0xF0141118);
        ctx.fill(x, y, x + panelW, y + 1, 0xFF6A4AC0);
        int ly = y + pad;
        for (String[] l : lines) {
            if (l == null) { ly += 6; continue; }
            ctx.text(mc.font, l[0], x + pad, ly, 0xFFFFFFFF);
            if (!l[1].isEmpty()) {
                int rw = mc.font.width(l[1]);
                ctx.text(mc.font, l[1], x + panelW - pad - rw, ly, 0xFFFFFFFF);
            }
            ly += 10;
        }
    }

    public static void register() {
        com.teslamaps.registry.Feature.of("StorageChat").section("Inventory").serverChat(StorageOverlay::onChat);
        com.teslamaps.registry.Feature.of("StorageLastPage").section("Inventory")
                .when(() -> TeslaMapsConfig.get().storageRememberLast).tick(StorageOverlay::trackLast);
        net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents.MODIFY_COMMAND.register(StorageOverlay::rewriteStorage);
    }

    private static String lastPage = null;

    private static void trackLast() {
        String k = liveKey();
        if (k != null) lastPage = k;
    }

    /** /storage straight into the last used page. */
    private static String rewriteStorage(String command) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (lastPage == null || !c.storageRememberLast || !c.section("Inventory")) return command;
        if (command == null || !command.trim().equalsIgnoreCase("storage")) return command;
        if (!com.teslamaps.utils.SkyblockUtils.isInSkyblock()) return command;
        if (Boolean.FALSE.equals(StorageCache.unlocked(lastPage))) return command;
        String cmd = commandForGlobal(globalIndex(lastPage));
        return cmd == null ? command : cmd;
    }

    private static final java.util.regex.Pattern REMOVED_BACKPACK =
            java.util.regex.Pattern.compile("^Removed backpack from slot (\\d+)!$");

    private static boolean vanillaNext = false;
    private static java.lang.ref.WeakReference<net.minecraft.client.gui.screens.Screen> vanillaScreen = new java.lang.ref.WeakReference<>(null);

    private static void onChat(String raw) {
        String t = strip(raw);
        java.util.regex.Matcher m = REMOVED_BACKPACK.matcher(t);
        if (m.matches()) StorageCache.remove("backpack_" + m.group(1));
        if (TeslaMapsConfig.get().storageVanillaAfterSwap
                && (t.startsWith("Placing backpack") || t.startsWith("Removed backpack"))) vanillaNext = true;
    }

    public static boolean active() {
        if (!TeslaMapsConfig.get().customStorageOverlay) return false;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return false;
        if (vanillaScreen.get() == cs) return false;
        String t = strip(cs.getTitle().getString());
        if (!t.equals("Storage") && BackpackPreview.keyFromTitle(t) == null) return false;
        if (vanillaNext && cs != lastScreen.get()) {
            vanillaNext = false;
            vanillaScreen = new java.lang.ref.WeakReference<>(cs);
            return false;
        }
        if (lastScreen.get() != cs) {
            lastScreen = new java.lang.ref.WeakReference<>(cs);
            openedAt = System.currentTimeMillis();
        }
        return true;
    }

    private static java.lang.ref.WeakReference<net.minecraft.client.gui.screens.Screen> lastScreen = new java.lang.ref.WeakReference<>(null);
    private static long openedAt = 0;

    private static int globalIndex(String key) {
        if (key == null) return -1;
        try {
            if (key.startsWith("epage_")) return Integer.parseInt(key.substring(6)) - 1;
            if (key.startsWith("backpack_")) return 9 + Integer.parseInt(key.substring(9)) - 1;
        } catch (NumberFormatException ignored) {}
        return -1;
    }

    private static boolean sameCategory(int a, int b) { return a >= 0 && b >= 0 && (a < 9) == (b < 9); }

    private static String commandForGlobal(int gi) {
        if (gi < 0 || gi > 26) return null;
        return gi < 9 ? "ec " + (gi + 1) : "bp " + (gi - 9 + 1);
    }

    private static final long SETTLE_MS = 175;
    private static int selectedGi = -1;
    private static long lastNavMs = 0;
    private static boolean settleSent = false, scrollToSel = false;

    private static boolean settle() { return TeslaMapsConfig.get().storageSettleSwap; }

    private static void select(int gi) {
        if (gi < 0 || gi > 26) return;
        selectedGi = gi;
        lastNavMs = System.currentTimeMillis();
        settleSent = false;
        scrollToSel = true;
    }

    /** One page command once the local selection has stood still for SETTLE_MS. */
    private static void tickSettle() {
        if (selectedGi < 0) return;
        int live = globalIndex(liveKey());
        long now = System.currentTimeMillis();
        if (live == selectedGi || (settleSent && now - lastNavMs > 3000)) { selectedGi = -1; return; }
        if (settleSent || now - lastNavMs < SETTLE_MS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.player.containerMenu.getCarried().isEmpty()) return;
        settleSent = true;
        lastNavMs = now;
        openGlobal(live, selectedGi);
    }

    private static void openGlobal(int liveGi, int tgi) {
        if (sameCategory(liveGi, tgi) && tgi == liveGi + 1) clickPageArrow("Next Page");
        else if (sameCategory(liveGi, tgi) && tgi == liveGi - 1) clickPageArrow("Previous Page");
        else {
            Minecraft mc = Minecraft.getInstance();
            String cmd = commandForGlobal(tgi);
            if (cmd != null && mc.getConnection() != null) mc.getConnection().sendCommand(cmd);
        }
    }

    private static void navigateDir(boolean next) {
        if (settle()) {
            int base = selectedGi >= 0 ? selectedGi : globalIndex(liveKey());
            if (base >= 0) select(base + (next ? 1 : -1));
            return;
        }
        int gi = globalIndex(liveKey());
        if (gi < 0) return;
        int tgi = next ? gi + 1 : gi - 1;
        if (tgi < 0 || tgi > 26) return;
        if (sameCategory(gi, tgi)) {
            clickPageArrow(next ? "Next Page" : "Previous Page");
        } else {
            Minecraft mc = Minecraft.getInstance();
            com.teslamaps.features.StorageGuiScale.expectReopen();
            if (mc.getConnection() != null) mc.getConnection().sendCommand(commandForGlobal(tgi));
        }
    }

    private static void navigateRow(int delta) {
        if (settle()) {
            int base = selectedGi >= 0 ? selectedGi : globalIndex(liveKey());
            if (base >= 0) select(base + delta);
            return;
        }
        int gi = globalIndex(liveKey());
        if (gi < 0) return;
        int tgi = gi + delta;
        if (tgi < 0 || tgi > 26) return;
        Minecraft mc = Minecraft.getInstance();
        com.teslamaps.features.StorageGuiScale.expectReopen();
        if (mc.getConnection() != null) mc.getConnection().sendCommand(commandForGlobal(tgi));
    }

    private static String liveKey() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return null;
        return BackpackPreview.keyFromTitle(strip(cs.getTitle().getString()));
    }

    private static Map<Integer, ItemStack> liveContent() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return Map.of();
        AbstractContainerMenu menu = mc.player.containerMenu;
        LinkedHashMap<Integer, ItemStack> out = new LinkedHashMap<>();
        for (Slot s : menu.slots) {
            if (s.container instanceof Inventory) continue;
            int cs = s.getContainerSlot();
            if (cs < 9 || s.getItem().isEmpty()) continue;
            out.put(cs - 9, s.getItem());
        }
        return out;
    }

    private static List<Box> visible() {
        List<Box> out = new ArrayList<>();
        String live = liveKey();
        for (int i = 1; i <= 9; i++) addIfVisible(out, "epage_" + i, "Ender Page " + i, true, i, live);
        for (int i = 1; i <= 18; i++) addIfVisible(out, "backpack_" + i, "Backpack " + i, false, i, live);
        return out;
    }

    private static void addIfVisible(List<Box> out, String key, String label, boolean ender, int num, String live) {
        boolean isLive = key.equals(live);
        boolean unopened = false;
        if (TeslaMapsConfig.get().storageShowUnopened) {
            Boolean u = StorageCache.unlocked(key);
            if (Boolean.FALSE.equals(u) && !isLive) return;
            unopened = Boolean.TRUE.equals(u) && StorageCache.size(key) <= 0;
        }
        if (StorageCache.size(key) <= 0 && !isLive && !unopened) return;
        Map<Integer, ItemStack> items = StorageCache.items(key);
        if (!search.isEmpty() && !isLive) {
            boolean match = false;
            if (items != null) for (ItemStack st : items.values()) if (matches(st, search)) { match = true; break; }
            if (!match) return;
        }
        out.add(new Box(key, label, ender, num));
    }

    private static int startX(int screenW) {
        int vw = (int) ((screenW - SCROLLBAR_W) / scl());
        return Math.max((int) (SIDE_MARGIN / scl()), (vw - (cols() * BOX_W + (cols() - 1) * GAP)) / 2);
    }

    private static int startY() { return (int) (HEADER_PX / scl()) + 4; }

    private static int invVW() { return (int) (Minecraft.getInstance().screen.width / iscl()); }
    private static int invVH() { return (int) (Minecraft.getInstance().screen.height / iscl()); }
    // selector + gap + inventory are laid out as one group, centered in the virtual width
    private static int groupX() { return (invVW() - (SEL_COLS * SEL_CELL + SEL_GAP + 9 * 18)) / 2; }
    private static int invX() { return groupX() + SEL_COLS * SEL_CELL + SEL_GAP; }
    private static int hotbarY() { return invVH() - 22; }
    private static int mainY() { return hotbarY() - 58; }

    private static int[] sbRect() {
        int x = (int) (invX() * iscl());
        int w = (int) (9 * 18 * iscl());
        int y = (int) ((mainY() - 13) * iscl()) + 6;
        return new int[]{x, y, w, 14};
    }

    private static int[] invSlotPos(int cs) {
        if (cs < 9) return new int[]{invX() + cs * 18, hotbarY()};
        int idx = cs - 9;
        return new int[]{invX() + (idx % 9) * 18, mainY() + (idx / 9) * 18};
    }

    private static final int SEL_CELL = 20, SEL_COLS = 9, SEL_GAP = 24;
    private static final float SEL_ICON = 1.1f;
    // virtual (iscl-space) coordinates so the selector fits next to the inventory at any scale
    private static int selX() { return groupX(); }
    private static int selY() { return mainY() + 4; }
    private static int[] selIconPos(int i) { return new int[]{selX() + (i % SEL_COLS) * SEL_CELL, selY() + (i / SEL_COLS) * SEL_CELL}; }
    private static String selKey(int i) { return i < 9 ? "epage_" + (i + 1) : "backpack_" + (i - 8); }

    public static void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        int sw = mc.screen.width, sh = mc.screen.height;
        layout(sw, sh);
        AbstractContainerMenu menu = mc.player != null ? mc.player.containerMenu : null;
        ctx.fill(0, 0, sw, sh, 0xE8101010);
        List<Box> vis = visible();
        Lay L = lay(vis);
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        boolean showNames = cfg.storageShowNames;
        boolean vanillaTex = cfg.storageVanillaTextures;
        ctx.text(mc.font, "§6§lStorage §7(" + vis.size() + ")", 8, 6, 0xFFFFFFFF);
        drawButton(ctx, mc, sw - 96, 6, 88, "Vanilla View", 0xFFFF453A, true);
        if (cfg.storageQuickButtons) {
            int qx = sw - 100;
            for (int i = 0; i < QUICK_LABELS.length; i++) {
                qx -= QUICK_W[i] + 4;
                quickButton(ctx, mc, qx, 6, QUICK_W[i], QUICK_LABELS[i]);
            }
        }

        String live = liveKey();
        boolean searching = !search.isEmpty();
        float vmx = mouseX / scl(), vmy = mouseY / scl();
        int sx = startX(sw), sy = startY();
        int wallBottom = (int) ((mainY() - 13) * iscl()) - 2;

        // a search shrinks the box list, so an offset from the unfiltered view can sit past
        // the end and show an empty band that reads as "nothing found"
        long nowMs = System.currentTimeMillis();
        if (nowMs - lastRenderMs > 1000) {
            if (cfg.storageSearchFocus) searchFocused = true;
            if (cfg.storageCenterActive) {
                for (int i = 0; i < vis.size(); i++) {
                    if (!vis.get(i).key.equals(live)) continue;
                    scroll = sy + L.y[i] + L.h[i] / 2f - (HEADER_PX + wallBottom) / (2f * scl());
                    break;
                }
            }
        }
        lastRenderMs = nowMs;
        if (settle()) tickSettle();
        String scrollKey = null;
        if (scrollToSel && selectedGi >= 0) scrollKey = selKey(selectedGi);
        else if (jumpKey != null) scrollKey = jumpKey;
        scrollToSel = false;
        jumpKey = null;
        if (scrollKey != null) {
            for (int i = 0; i < vis.size(); i++) {
                if (!vis.get(i).key.equals(scrollKey)) continue;
                float top = sy + L.y[i] - HEADER_PX / scl(), bottom = sy + L.y[i] + L.h[i] - wallBottom / scl();
                if (scroll > top) scroll = top;
                else if (scroll < bottom) scroll = Math.min(top, bottom);
                break;
            }
        }
        scroll = Math.max(0, Math.min(scroll,
                Math.max(0, L.contentH + startY() + 10 - wallBottom / scl())));
        Map<Integer, ItemStack> dragTop = DRAG_TOP, dragInv = DRAG_INV;
        dragTop.clear();
        dragInv.clear();
        dragPreview(mc, dragTop, dragInv);

        ItemStack hoverNonLive = null;
        ctx.enableScissor(0, HEADER_PX, sw, Math.max(HEADER_PX, wallBottom));
        var pose = ctx.pose();
        pose.pushMatrix();
        pose.scale(scl(), scl());
        for (int i = 0; i < vis.size(); i++) {
            Box b = vis.get(i);
            int bx = sx + L.x[i];
            int by = (int) (sy + L.y[i] - scroll);
            int bh = L.h[i];
            if (by + bh < HEADER_PX / scl() || by > sh / scl()) continue;
            boolean isLive = b.key.equals(live);

            boolean hoverBox = vmx >= bx && vmx <= bx + BOX_W && vmy >= by && vmy <= by + bh;
            if (vanillaTex) {
                chestPanel(ctx, bx, by, BOX_W, bh);
                if (hoverBox) ctx.fill(bx, by, bx + BOX_W, by + bh, 0x20FFFFFF);
            } else {
                ctx.fill(bx, by, bx + BOX_W, by + bh, hoverBox ? 0xFF2A2A2A : 0xF01E1E1E);
            }
            if (isLive) {
                int c = b.ender ? 0xFF55FFFF : 0xFFFFAA00;
                ctx.fill(bx - 1, by - 1, bx + BOX_W + 1, by + 1, c);
                ctx.fill(bx - 1, by + bh - 1, bx + BOX_W + 1, by + bh + 1, c);
                ctx.fill(bx - 1, by - 1, bx + 1, by + bh + 1, c);
                ctx.fill(bx + BOX_W - 1, by - 1, bx + BOX_W + 1, by + bh + 1, c);
            }
            if (selectedGi >= 0 && !isLive && globalIndex(b.key) == selectedGi) {
                int c = 0xFFFFFFFF;
                ctx.fill(bx - 1, by - 1, bx + BOX_W + 1, by + 1, c);
                ctx.fill(bx - 1, by + bh - 1, bx + BOX_W + 1, by + bh + 1, c);
                ctx.fill(bx - 1, by - 1, bx + 1, by + bh + 1, c);
                ctx.fill(bx + BOX_W - 1, by - 1, bx + BOX_W + 1, by + bh + 1, c);
            }
            if (showNames) {
                String title = b.key.equals(rename) ? "§e" + renameText + "_" : pageName(b.key, b.label);
                if (vanillaTex) ctx.text(mc.font, title + (isLive ? " §2●" : ""), bx + 4, by + 2, 0xFF404040, false);
                else ctx.text(mc.font, title + (isLive ? " §a●" : ""), bx + 4, by + 2, 0xFFFFFFFF);
            }

            int size = StorageCache.size(b.key);
            Map<Integer, ItemStack> items = isLive ? liveContent() : StorageCache.items(b.key);
            if (isLive && items.isEmpty() && cfg.storageCachedWhileLoading && nowMs - openedAt < 1000) {
                Map<Integer, ItemStack> cached = StorageCache.items(b.key);
                if (cached != null) items = cached;
            }
            int gx0 = bx + 4, gy0 = by + topPad();
            if (size <= 0 && !isLive) ctx.text(mc.font, "§8not opened yet", gx0 + 2, gy0 + 4, 0xFFFFFFFF);
            int emptyCol = searching ? 0xFF252525 : 0xFF555555;
            for (int s = 0; s < size; s++) {
                int gx = gx0 + (s % GRID_COLS) * CELL, gy = gy0 + (s / GRID_COLS) * CELL;
                if (vanillaTex) {
                    ctx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, CHEST_TEX, gx - 1, gy - 1, 7, 17, 18, 18, 256, 256);
                    if (searching) ctx.fill(gx, gy, gx + 16, gy + 16, 0x80000000);
                } else {
                    ctx.fill(gx, gy, gx + 16, gy + 16, emptyCol);
                }
            }
            if (isLive) for (Map.Entry<Integer, ItemStack> e : dragTop.entrySet()) {
                if (items != null && items.containsKey(e.getKey())) continue;
                int gx = gx0 + (e.getKey() % GRID_COLS) * CELL, gy = gy0 + (e.getKey() / GRID_COLS) * CELL;
                ctx.fill(gx, gy, gx + 16, gy + 16, 0x80FFFFFF);
                ctx.item(e.getValue(), gx, gy);
                ctx.itemDecorations(mc.font, e.getValue(), gx, gy);
            }
            if (items != null) for (Map.Entry<Integer, ItemStack> e : items.entrySet()) {
                int gx = gx0 + (e.getKey() % GRID_COLS) * CELL, gy = gy0 + (e.getKey() / GRID_COLS) * CELL;
                ItemStack st = e.getValue();
                if (isLive) {
                    ItemStack pv = dragTop.get(e.getKey());
                    if (pv != null) st = pv;
                }
                boolean match = !searching || matches(st, search);
                if (searching && match) ctx.fill(gx, gy, gx + 16, gy + 16, 0xFF2E8B2E); // bright green bg = hit
                SearchHighlight.drawAt(ctx, gx, gy, st);
                com.teslamaps.features.RarityBackground.draw(ctx, gx, gy, st);
                ctx.item(st, gx, gy);
                ctx.itemDecorations(mc.font, st, gx, gy);
                EnchantAbbrev.draw(ctx, st, gx, gy);
                if (searching && !match) ctx.fill(gx, gy, gx + 16, gy + 16, 0xC0000000); // dim non-hit
                if (!isLive && vmx >= gx && vmx < gx + 16 && vmy >= gy && vmy < gy + 16 && mouseY < wallBottom) hoverNonLive = st;
            }
            if (b.key.equals(markKey) && markSlot >= 0) {
                int gx = gx0 + (markSlot % GRID_COLS) * CELL, gy = gy0 + (markSlot / GRID_COLS) * CELL, c = 0xFF30FF30;
                ctx.fill(gx - 1, gy - 1, gx + 17, gy, c);
                ctx.fill(gx - 1, gy + 16, gx + 17, gy + 17, c);
                ctx.fill(gx - 1, gy - 1, gx, gy + 17, c);
                ctx.fill(gx + 16, gy - 1, gx + 17, gy + 17, c);
            }
        }
        lastHover = hoverNonLive;
        if (TeslaMapsConfig.get().hoveredSlotOutline && mouseY < wallBottom) {
            for (int i = 0; i < vis.size(); i++) {
                int bx = sx + L.x[i];
                int by = (int) (sy + L.y[i] - scroll);
                if (by + L.h[i] < HEADER_PX / scl() || by > sh / scl()) continue;
                int gx0 = bx + 4, gy0 = by + topPad();
                int cx = (int) Math.floor((vmx - gx0) / CELL), cy = (int) Math.floor((vmy - gy0) / CELL);
                int slot = cy * GRID_COLS + cx;
                if (cx < 0 || cx >= GRID_COLS || cy < 0 || slot >= StorageCache.size(vis.get(i).key)) continue;
                HoveredSlot.draw(ctx, gx0 + cx * CELL, gy0 + cy * CELL);
                break;
            }
        }
        pose.popMatrix();
        ctx.disableScissor();

        float contentVirt = L.contentH + startY() + 10;
        float visVirt = wallBottom / scl() - startY();
        float maxScroll = Math.max(0, contentVirt - wallBottom / scl());
        if (maxScroll > 0) {
            int trackW = 10, trackX = sw - trackW - 3, trackTop = HEADER_PX, trackH = wallBottom - HEADER_PX;
            int thumbH = Math.max(24, (int) (trackH * Math.min(1f, visVirt / (contentVirt - startY()))));
            if (scrollDragging) {
                long w = mc.getWindow().handle();
                if (GLFW.glfwGetMouseButton(w, GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) scrollDragging = false;
                else {
                    float frac = (mouseY - trackTop - thumbH / 2f) / Math.max(1, trackH - thumbH);
                    scroll = Math.max(0, Math.min(maxScroll, frac * maxScroll));
                }
            }
            int thumbY = trackTop + (int) ((trackH - thumbH) * (scroll / maxScroll));
            ctx.fill(trackX, trackTop, trackX + trackW, trackTop + trackH, 0x80000000);
            boolean hoverBar = mouseX >= trackX && mouseX <= trackX + trackW && mouseY >= trackTop && mouseY <= trackTop + trackH;
            ctx.fill(trackX + 1, thumbY, trackX + trackW - 1, thumbY + thumbH, (hoverBar || scrollDragging) ? 0xFFE0E0E0 : 0xFFAAAAAA);
        } else scrollDragging = false;

        if (menu != null) {
            pose.pushMatrix();
            pose.scale(iscl(), iscl());
            int px0 = invX() - 5, py0 = mainY() - 13, px1 = invX() + 9 * 18 + 5, py1 = hotbarY() + 18 + 5;
            ctx.fill(px0, py0, px1, py1, 0xFF1A1A1A);
            ctx.fill(px0, py0, px1, py0 + 1, 0xFF3A3A3A);
            ctx.fill(px0, py1 - 1, px1, py1, 0xFF000000);
            for (Slot s : menu.slots) {
                if (!(s.container instanceof Inventory)) continue;
                int[] p = invSlotPos(s.getContainerSlot());
                ctx.fill(p[0] - 1, p[1] - 1, p[0] + 17, p[1] + 17, 0xFF373737);
                ctx.fill(p[0] - 1, p[1] - 1, p[0] + 16, p[1], 0xFF8B8B8B);
                ctx.fill(p[0] - 1, p[1] - 1, p[0], p[1] + 16, 0xFF8B8B8B);
                ctx.fill(p[0], p[1], p[0] + 16, p[1] + 16, 0xFF565656);
                ItemStack pv = dragInv.get(s.getContainerSlot());
                if (pv != null) ctx.fill(p[0], p[1], p[0] + 16, p[1] + 16, 0x80FFFFFF);
                ItemStack shown = pv != null ? pv : s.getItem();
                if (!shown.isEmpty()) {
                    com.teslamaps.features.RarityBackground.draw(ctx, p[0], p[1], shown);
                    ctx.item(shown, p[0], p[1]);
                    ctx.itemDecorations(mc.font, shown, p[0], p[1]);
                    EnchantAbbrev.draw(ctx, shown, p[0], p[1]);
                }
                float imx = mouseX / iscl(), imy = mouseY / iscl();
                if (TeslaMapsConfig.get().hoveredSlotOutline
                        && imx >= p[0] && imx < p[0] + 16 && imy >= p[1] && imy < p[1] + 16) {
                    HoveredSlot.draw(ctx, p[0], p[1]);
                }
            }
            pose.popMatrix();
            ItemStack carried = menu.getCarried();
            if (!carried.isEmpty()) {
                if (dragRemainder >= 0) carried = dragRemainder == 0 ? ItemStack.EMPTY : carried.copyWithCount(dragRemainder);
                if (!carried.isEmpty()) {
                    ctx.item(carried, mouseX - 8, mouseY - 8);
                    ctx.itemDecorations(mc.font, carried, mouseX - 8, mouseY - 8);
                }
            }
        }

        int[] sb = sbRect();
        ctx.fill(sb[0], sb[1], sb[0] + sb[2], sb[1] + sb[3], 0xFF202020);
        int sbc = searchFocused ? 0xFFFFD040 : 0xFF606060;
        ctx.fill(sb[0], sb[1], sb[0] + sb[2], sb[1] + 1, sbc);
        ctx.fill(sb[0], sb[1] + sb[3] - 1, sb[0] + sb[2], sb[1] + sb[3], sbc);
        ctx.fill(sb[0], sb[1], sb[0] + 1, sb[1] + sb[3], sbc);
        ctx.fill(sb[0] + sb[2] - 1, sb[1], sb[0] + sb[2], sb[1] + sb[3], sbc);
        ctx.text(mc.font, search.isEmpty() ? (searchFocused ? "§7type to search… (name + tooltip)" : "§8click to search")
                : "§f" + search + (searchFocused ? "§e_" : ""), sb[0] + 4, sb[1] + 3, 0xFFFFFFFF);

        String selHover = renderSelector(ctx, mc, mouseX, mouseY);

        if (hoverNonLive != null && (menu == null || menu.getCarried().isEmpty())) {
            ctx.setTooltipForNextFrame(mc.font, hoverNonLive, mouseX, mouseY);
        }
        if (selHover != null) gridPopup(ctx, mc, selHover, mouseX, mouseY);

        drawValuePanel(ctx, mc);
        ItemStack hitHover = drawResults(ctx, mc, mouseX, mouseY, wallBottom);
        if (hitHover != null) {
            List<Component> lines = new ArrayList<>(net.minecraft.client.gui.screens.Screen.getTooltipFromItem(mc, hitHover));
            lines.add(Component.literal("§8in " + hitPage));
            ctx.setComponentTooltipForNextFrame(mc.font, lines, mouseX, mouseY);
        }
    }

    private record Hit(String key, int slot, ItemStack stack, String page) {}
    private static List<Hit> hits = new ArrayList<>();
    private static String hitsFor = null;
    private static long hitsAt = 0;
    private static int hitScroll = 0;
    private static String markKey = null, jumpKey = null, hitPage = "";
    private static int markSlot = -1;
    private static final int RES_W = 150, RES_ROW = 18;

    private static boolean resultsOn() { return TeslaMapsConfig.get().storageSearchResults && !search.isEmpty(); }

    private static int[] resultsRect(int sw, int wallBottom) {
        int x = sw - SCROLLBAR_W - 6 - RES_W, y = HEADER_PX + 4;
        return new int[]{x, y, RES_W, Math.max(40, wallBottom - y - 4)};
    }

    private static void rebuildHits() {
        long now = System.currentTimeMillis();
        if (search.equals(hitsFor) && now - hitsAt < 1000) return;
        if (!search.equals(hitsFor)) hitScroll = 0;
        hitsFor = search;
        hitsAt = now;
        String live = liveKey();
        List<Hit> out = new ArrayList<>();
        for (int gi = 0; gi < 27 && out.size() < 500; gi++) {
            String key = selKey(gi);
            Map<Integer, ItemStack> m = key.equals(live) ? liveContent() : StorageCache.items(key);
            if (m == null) continue;
            String page = com.teslamaps.utils.StringUtil.strip(pageName(key, gi < 9 ? "Ender Page " + (gi + 1) : "Backpack " + (gi - 8)));
            for (Map.Entry<Integer, ItemStack> e : m.entrySet())
                if (!e.getValue().isEmpty() && matches(e.getValue(), search)) out.add(new Hit(key, e.getKey(), e.getValue(), page));
        }
        hits = out;
    }

    private static ItemStack drawResults(GuiGraphicsExtractor ctx, Minecraft mc, int mouseX, int mouseY, int wallBottom) {
        if (!resultsOn()) return null;
        rebuildHits();
        int[] r = resultsRect(mc.screen.width, wallBottom);
        int rows = Math.max(1, (r[3] - 16) / RES_ROW);
        hitScroll = Math.max(0, Math.min(hitScroll, Math.max(0, hits.size() - rows)));
        ctx.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], 0xF0141118);
        ctx.fill(r[0], r[1], r[0] + r[2], r[1] + 1, 0xFF30FF30);
        ctx.text(mc.font, "§a" + hits.size() + " results", r[0] + 4, r[1] + 4, 0xFFFFFFFF);
        ItemStack hover = null;
        for (int i = 0; i < rows && hitScroll + i < hits.size(); i++) {
            Hit h = hits.get(hitScroll + i);
            int y = r[1] + 16 + i * RES_ROW;
            boolean over = mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= y && mouseY < y + RES_ROW;
            if (over) { ctx.fill(r[0] + 1, y, r[0] + r[2] - 1, y + RES_ROW, 0x30FFFFFF); hover = h.stack; hitPage = h.page; }
            ctx.item(h.stack, r[0] + 3, y + 1);
            ctx.itemDecorations(mc.font, h.stack, r[0] + 3, y + 1);
            ctx.text(mc.font, mc.font.plainSubstrByWidth(strip(h.stack.getHoverName().getString()), r[2] - 26), r[0] + 22, y + 5, 0xFFFFFFFF);
        }
        return hover;
    }

    private static boolean resultsClick(double mx, double my, int wallBottom) {
        if (!resultsOn()) return false;
        Minecraft mc = Minecraft.getInstance();
        int[] r = resultsRect(mc.screen.width, wallBottom);
        if (mx < r[0] || mx >= r[0] + r[2] || my < r[1] || my >= r[1] + r[3]) return false;
        int i = (int) ((my - r[1] - 16) / RES_ROW);
        if (my < r[1] + 16 || hitScroll + i >= hits.size()) return true;
        Hit h = hits.get(hitScroll + i);
        markKey = h.key;
        markSlot = h.slot;
        jumpKey = h.key;
        String live = liveKey();
        if (h.key.equals(live) || mc.player == null || !mc.player.containerMenu.getCarried().isEmpty()) return true;
        if (settle()) select(globalIndex(h.key));
        else openGlobal(globalIndex(live), globalIndex(h.key));
        return true;
    }

    private static boolean overResults(double mx, double my) {
        if (!resultsOn()) return false;
        Minecraft mc = Minecraft.getInstance();
        int[] r = resultsRect(mc.screen.width, (int) ((mainY() - 13) * iscl()) - 2);
        return mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }

    private static int dragRemainder = -1;
    private static final Map<Integer, ItemStack> DRAG_TOP = new java.util.HashMap<>(), DRAG_INV = new java.util.HashMap<>();

    /** Vanilla drag-splitting still runs; this only shows the counts it will produce. */
    private static void dragPreview(Minecraft mc, Map<Integer, ItemStack> top, Map<Integer, ItemStack> inv) {
        dragRemainder = -1;
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs) || mc.player == null) return;
        com.teslamaps.mixin.QuickCraftAccessor q = (com.teslamaps.mixin.QuickCraftAccessor) cs;
        java.util.Set<Slot> slots = q.teslamaps$quickCraftSlots();
        ItemStack carried = mc.player.containerMenu.getCarried();
        if (!q.teslamaps$isQuickCrafting() || slots.size() < 2 || carried.isEmpty()) return;
        int type = q.teslamaps$quickCraftingType();
        int place = AbstractContainerMenu.getQuickCraftPlaceCount(slots.size(), type, carried);
        int left = carried.getCount();
        for (Slot s : slots) {
            ItemStack cur = s.getItem();
            int base = cur.isEmpty() ? 0 : cur.getCount();
            int max = Math.min(carried.getMaxStackSize(), s.getMaxStackSize(carried));
            int n = Math.min(max, base + place);
            left -= n - base;
            ItemStack out = carried.copyWithCount(n);
            if (s.container instanceof Inventory) inv.put(s.getContainerSlot(), out);
            else if (s.getContainerSlot() >= 9) top.put(s.getContainerSlot() - 9, out);
        }
        dragRemainder = Math.max(0, left);
    }

    private static String renderSelector(GuiGraphicsExtractor ctx, Minecraft mc, int mouseX, int mouseY) {
        String live = liveKey();
        int sx = selX(), sy = selY();
        float vmx = mouseX / iscl(), vmy = mouseY / iscl();
        var pose = ctx.pose();
        pose.pushMatrix();
        pose.scale(iscl(), iscl());
        ctx.fill(sx - 3, sy - 3, sx + SEL_COLS * SEL_CELL + 3, sy + 3 * SEL_CELL + 3, 0xF01A1A1A);
        String hover = null;
        int box = SEL_CELL - 2; // slot face size
        for (int i = 0; i < 27; i++) {
            String key = selKey(i);
            boolean known = StorageCache.has(key);
            int num = i < 9 ? i + 1 : i - 8;
            int[] p = selIconPos(i);
            boolean isLive = key.equals(live);
            boolean hovered = vmx >= p[0] && vmx < p[0] + box && vmy >= p[1] && vmy < p[1] + box && known;
            int border = isLive ? (i < 9 ? 0xFF55FFFF : 0xFFFFAA00) : hovered ? 0xFFFFFFFF : 0xFF373737;
            ctx.fill(p[0] - 1, p[1] - 1, p[0] + box + 1, p[1] + box + 1, border);
            ctx.fill(p[0], p[1], p[0] + box, p[1] + box, hovered ? 0xFF6E6E6E : 0xFF565656);
            if (SearchHighlight.pageHit(key))
                ctx.fill(p[0], p[1], p[0] + box, p[1] + box, TeslaMapsConfig.parseColor(TeslaMapsConfig.get().colorSearchHighlight));
            ItemStack icon = StorageCache.icon(key);
            if (icon == null || icon.isEmpty()) icon = new ItemStack(i < 9 ? Items.ENDER_CHEST : Items.PLAYER_HEAD);
            float off = (box - 16 * SEL_ICON) / 2f;
            pose.pushMatrix();
            pose.translate(p[0] + off, p[1] + off);
            pose.scale(SEL_ICON, SEL_ICON);
            ctx.item(icon, 0, 0);
            pose.popMatrix();
            if (!known) ctx.fill(p[0], p[1], p[0] + box, p[1] + box, 0xA0000000);
            ctx.text(mc.font, "§f" + num, p[0] + box - (num < 10 ? 7 : 12), p[1] + box - 8, 0xFFFFFFFF);
            if (hovered) hover = key;
        }
        pose.popMatrix();
        return hover;
    }

    private static void gridPopup(GuiGraphicsExtractor ctx, Minecraft mc, String key, int mx, int my) {
        Map<Integer, ItemStack> items = StorageCache.items(key);
        if (items == null) return;
        int size = StorageCache.size(key), cols = 9, rows = Math.max(1, (size + cols - 1) / cols), cell = 18;
        int w = cols * cell + 8, h = rows * cell + 8;
        int px = mx + 12, py = my - 12;
        if (px + w > mc.screen.width) px = mx - w - 12;
        if (py + h > mc.screen.height) py = mc.screen.height - h;
        if (py < 0) py = 0;
        ctx.fill(px, py, px + w, py + h, 0xF0202020);
        ctx.fill(px, py, px + w, py + 1, 0xFF7755FF);
        ctx.fill(px, py + h - 1, px + w, py + h, 0xFF7755FF);
        String custom = TeslaMapsConfig.get().storagePageNames.get(key);
        if (custom != null && !custom.isEmpty()) {
            int tw = mc.font.width(custom.replace('&', '§')) + 6;
            ctx.fill(px, py - 12, px + tw, py, 0xF0202020);
            ctx.text(mc.font, custom.replace('&', '§'), px + 3, py - 10, 0xFFFFFFFF);
        }
        for (int s = 0; s < size; s++) {
            int gx = px + 4 + (s % cols) * cell, gy = py + 4 + (s / cols) * cell;
            ctx.fill(gx, gy, gx + 16, gy + 16, 0xFF555555);
        }
        for (Map.Entry<Integer, ItemStack> e : items.entrySet()) {
            int gx = px + 4 + (e.getKey() % cols) * cell, gy = py + 4 + (e.getKey() / cols) * cell;
            com.teslamaps.features.RarityBackground.draw(ctx, gx, gy, e.getValue());
            ctx.item(e.getValue(), gx, gy);
            ctx.itemDecorations(mc.font, e.getValue(), gx, gy);
            EnchantAbbrev.draw(ctx, e.getValue(), gx, gy);
        }
    }

    static final int BUTTON_H = 16;

    private static final net.minecraft.resources.Identifier CHEST_TEX =
            net.minecraft.resources.Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final String[] QUICK_LABELS = {"Hide", "Home", "Hunting Toolkit", "Farming Toolkit"};
    private static final int[] QUICK_W = {36, 40, 86, 86};

    /** generic_54 panel cut into corners so any box size keeps the pack's border. */
    private static void chestPanel(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
        var p = net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED;
        int e = 4;
        ctx.blit(p, CHEST_TEX, x, y, 0, 0, w - e, h - e, 256, 256);
        ctx.blit(p, CHEST_TEX, x + w - e, y, 176 - e, 0, e, h - e, 256, 256);
        ctx.blit(p, CHEST_TEX, x, y + h - e, 0, 222 - e, w - e, e, 256, 256);
        ctx.blit(p, CHEST_TEX, x + w - e, y + h - e, 176 - e, 222 - e, e, e, 256, 256);
    }

    private static void quickButton(GuiGraphicsExtractor ctx, Minecraft mc, int x, int y, int w, String label) {
        boolean hovered = buttonHovered(mc, x, y, w);
        com.teslamaps.render.gui.TeslaGui.roundedRect(ctx, x, y, w, BUTTON_H, 4f, hovered ? 0xFF3A3A3C : 0xFF2C2C2E);
        com.teslamaps.render.gui.TeslaGui.roundedOutline(ctx, x + 0.5f, y + 0.5f, w - 1, BUTTON_H - 1, 4f, 1f,
                hovered ? 0xFF64D2FF : 0x8064D2FF);
        ctx.centeredText(mc.font, label, x + w / 2, y + 4, 0xFF64D2FF);
    }

    private static boolean handleQuickClick(AbstractContainerScreen<?> cs, double mx, double my) {
        if (!TeslaMapsConfig.get().storageQuickButtons || my < 6 || my > 6 + BUTTON_H) return false;
        Minecraft mc = Minecraft.getInstance();
        int qx = cs.width - 100;
        for (int i = 0; i < QUICK_LABELS.length; i++) {
            qx -= QUICK_W[i] + 4;
            if (mx < qx || mx > qx + QUICK_W[i]) continue;
            if (i == 0) {
                vanillaScreen = new java.lang.ref.WeakReference<>(cs);
                return true;
            }
            if (mc.player == null || !mc.player.containerMenu.getCarried().isEmpty() || mc.getConnection() == null) return true;
            mc.getConnection().sendCommand(i == 1 ? "storage" : i == 2 ? "huntingtoolkit" : "farmingtoolkit");
            return true;
        }
        return false;
    }

    /** Rounded card in the mod's own style, with a drawn chevron/cross instead of a font glyph. */
    private static void drawButton(GuiGraphicsExtractor ctx, Minecraft mc, int x, int y, int w,
                                   String label, int accent, boolean cross) {
        boolean hovered = buttonHovered(mc, x, y, w);

        com.teslamaps.render.gui.TeslaGui.roundedRect(ctx, x, y, w, BUTTON_H, 4f,
                hovered ? 0xFF3A3A3C : 0xFF2C2C2E);
        com.teslamaps.render.gui.TeslaGui.roundedOutline(ctx, x + 0.5f, y + 0.5f, w - 1, BUTTON_H - 1, 4f, 1f,
                hovered ? accent : (accent & 0x00FFFFFF) | 0x80000000);

        ctx.text(mc.font, label, x + 8, y + 4, accent);

        float iconX = x + w - 12, iconY = y + 4;
        if (cross) {
            com.teslamaps.render.gui.TeslaGui.crossIcon(ctx, iconX, iconY, 8, accent);
        } else {
            com.teslamaps.render.gui.TeslaGui.checkIcon(ctx, iconX, iconY, 8, accent);
        }
    }

    private static boolean buttonHovered(Minecraft mc, int x, int y, int w) {
        if (mc.screen == null) return false;
        double sx = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
        double sy = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();
        return sx >= x && sx <= x + w && sy >= y && sy <= y + BUTTON_H;
    }

    public static void renderEnableButton(GuiGraphicsExtractor ctx) {
        if (TeslaMapsConfig.get().customStorageOverlay) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        if (!strip(cs.getTitle().getString()).equals("Storage")) return;
        drawButton(ctx, mc, mc.screen.width - 110, 6, 102, "Custom Storage", 0xFF30D158, false);
    }

    public static boolean handleEnableClick(double mx, double my) {
        if (TeslaMapsConfig.get().customStorageOverlay) return false;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return false;
        if (!strip(cs.getTitle().getString()).equals("Storage")) return false;
        int x = mc.screen.width - 110, y = 6;
        if (mx >= x && mx <= x + 102 && my >= y && my <= y + BUTTON_H) {
            TeslaMapsConfig.get().customStorageOverlay = true;
            TeslaMapsConfig.save();
            return true;
        }
        return false;
    }

    public static boolean isOverSlot(Slot slot, double mx, double my) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return false;
        layout(cs.width, cs.height);
        if (slot.container instanceof Inventory) {
            int[] p = invSlotPos(slot.getContainerSlot());
            float imx = (float) mx / iscl(), imy = (float) my / iscl();
            // ±1 like vanilla: tiles the 18px pitch with no dead gutter between slots
            return imx >= p[0] - 1 && imx < p[0] + 17 && imy >= p[1] - 1 && imy < p[1] + 17;
        }
        String live = liveKey();
        if (live == null) return false;
        int cell = slot.getContainerSlot() - 9;
        if (cell < 0) return false;
        List<Box> vis = visible();
        Lay L = lay(vis);
        int sx = startX(cs.width), sy = startY();
        for (int i = 0; i < vis.size(); i++) {
            if (!vis.get(i).key.equals(live)) continue;
            int bx = sx + L.x[i];
            int by = (int) (sy + L.y[i] - scroll);
            int gx = bx + 4 + (cell % GRID_COLS) * CELL, gy = by + topPad() + (cell / GRID_COLS) * CELL;
            float vmx = (float) mx / scl(), vmy = (float) my / scl();
            return vmx >= gx && vmx < gx + 16 && vmy >= gy && vmy < gy + 16;
        }
        return false;
    }

    public static Slot slotAt(double mx, double my) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs) || mc.player == null) return null;
        layout(cs.width, cs.height);
        AbstractContainerMenu menu = mc.player.containerMenu;

        float imx = (float) mx / iscl(), imy = (float) my / iscl();
        for (Slot s : menu.slots) {
            if (!(s.container instanceof Inventory)) continue;
            int[] p = invSlotPos(s.getContainerSlot());
            if (imx >= p[0] - 1 && imx < p[0] + 17 && imy >= p[1] - 1 && imy < p[1] + 17) return s;
        }
        if (my < HEADER_PX) return null;

        String live = liveKey();
        if (live == null) return null;
        float vmx = (float) mx / scl(), vmy = (float) my / scl();
        int sx = startX(cs.width), sy = startY();
        List<Box> vis = visible();
        Lay L = lay(vis);
        for (int i = 0; i < vis.size(); i++) {
            Box b = vis.get(i);
            if (!b.key.equals(live)) continue;
            int bx = sx + L.x[i];
            int by = (int) (sy + L.y[i] - scroll);
            if (!(vmx >= bx && vmx <= bx + BOX_W && vmy >= by && vmy <= by + L.h[i])) continue;
            int gx0 = bx + 4, gy0 = by + topPad();
            int cx = (int) Math.floor((vmx - gx0) / CELL), cy = (int) Math.floor((vmy - gy0) / CELL);
            if (cx < 0 || cx >= GRID_COLS || cy < 0) return null;
            // 16px hit box within the 18px cell, matching isOverSlot - gutter clicks are no slot
            if (vmx - gx0 - cx * CELL >= 16 || vmy - gy0 - cy * CELL >= 16) return null;
            int target = cy * GRID_COLS + cx + 9;
            for (Slot s : menu.slots)
                if (!(s.container instanceof Inventory) && s.getContainerSlot() == target) return s;
            return null;
        }
        return null;
    }

    private static ItemStack lastHover;

    public static boolean handleClick(double mx, double my, int button) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs) || mc.player == null) return false;
        layout(cs.width, cs.height);

        if (button == 2) {
            Slot s = slotAt(mx, my);
            if (s != null && CopyItemName.copy(s.getItem())) return true;
            if (lastHover != null && CopyItemName.copy(lastHover)) return true;
        }

        if (TeslaMapsConfig.get().debugMode) {
            Slot dbg = slotAt(mx, my);
            com.teslamaps.TeslaMaps.LOGGER.info(
                    "[StorageDbg] click {},{} btn={} scl={} iscl={} screen={}x{} slot={} item={}",
                    (int) mx, (int) my, button, scl(), iscl(), cs.width, cs.height,
                    dbg != null ? dbg.getContainerSlot() : -1,
                    dbg != null ? strip(dbg.getItem().getHoverName().getString()) : "-");
        }

        if (handleQuickClick(cs, mx, my)) return true;
        int dbX = cs.width - 96;
        if (mx >= dbX && mx <= dbX + 88 && my >= 6 && my <= 6 + BUTTON_H) {
            TeslaMapsConfig.get().customStorageOverlay = false;
            TeslaMapsConfig.save();
            return true;
        }
        float selMx = (float) mx / iscl(), selMy = (float) my / iscl();
        for (int i = 0; i < 27; i++) { // left selector column
            String key = selKey(i);
            if (!StorageCache.has(key)) continue;
            int[] p = selIconPos(i);
            if (selMx >= p[0] && selMx < p[0] + SEL_CELL - 2 && selMy >= p[1] && selMy < p[1] + SEL_CELL - 2) {
                if (!mc.player.containerMenu.getCarried().isEmpty()) return true; // no page swap while holding an item
                com.teslamaps.features.StorageGuiScale.expectReopen();
                if (mc.getConnection() != null) mc.getConnection().sendCommand((i < 9 ? "ec " : "bp ") + (i < 9 ? i + 1 : i - 8));
                return true;
            }
        }

        int wallBottom = (int) ((mainY() - 13) * iscl()) - 2;
        int trackX = cs.width - 13;
        if (button == 0 && mx >= trackX && my >= HEADER_PX && my <= wallBottom) { // scrollbar drag
            scrollDragging = true;
            searchFocused = false;
            return true;
        }
        if (inSearchBar(mx, my)) {
            if (button == 1) setSearch(""); else searchFocused = true;
            return true;
        }
        if (resultsClick(mx, my, wallBottom)) { searchFocused = false; return true; }
        searchFocused = false;
        if (rename != null) finishRename(true);

        Slot hit = slotAt(mx, my);
        if (hit != null) {
            if (markKey != null && markKey.equals(liveKey()) && !(hit.container instanceof Inventory)
                    && hit.getContainerSlot() - 9 == markSlot) { markKey = null; markSlot = -1; }
            return false;
        }

        // boxes are scissored to header..wall; clicks outside that band (inventory/selector
        // area, slot gutters) must never hit the virtual box rects hidden behind it
        if (my < HEADER_PX || my > wallBottom) return true;
        float vmx = (float) mx / scl(), vmy = (float) my / scl();
        int sx = startX(cs.width), sy = startY();
        List<Box> vis = visible();
        Lay L = lay(vis);
        for (int i = 0; i < vis.size(); i++) {
            Box b = vis.get(i);
            int bx = sx + L.x[i];
            int by = (int) (sy + L.y[i] - scroll);
            if (vmx >= bx && vmx <= bx + BOX_W && vmy >= by && vmy <= by + L.h[i]) {
                TeslaMapsConfig cfg = TeslaMapsConfig.get();
                if (cfg.storageRenamePages && cfg.storageShowNames && vmy < by + topPad()) {
                    rename = b.key;
                    String cur = cfg.storagePageNames.get(b.key);
                    renameText = cur == null ? "" : cur;
                    searchFocused = false;
                    return true;
                }
                // clicking the already-open box (gutter/padding miss) or carrying an item: don't
                // fire page commands - that reloads the container mid-interaction
                if (b.key.equals(liveKey()) || !mc.player.containerMenu.getCarried().isEmpty()) return true;
                int liveGi = globalIndex(liveKey()), clickedGi = globalIndex(b.key);
                if (settle()) { select(clickedGi); return true; }
                if (sameCategory(liveGi, clickedGi) && clickedGi == liveGi + 1) clickPageArrow("Next Page");
                else if (sameCategory(liveGi, clickedGi) && clickedGi == liveGi - 1) clickPageArrow("Previous Page");
                else if (mc.getConnection() != null) {
                    com.teslamaps.features.StorageGuiScale.expectReopen();
                    mc.getConnection().sendCommand((b.ender ? "ec " : "bp ") + b.num);
                }
                return true;
            }
        }
        return true;
    }

    private static void finishRename(boolean save) {
        if (rename == null) return;
        if (save) {
            String t = renameText.trim();
            if (t.isEmpty()) TeslaMapsConfig.get().storagePageNames.remove(rename);
            else TeslaMapsConfig.get().storagePageNames.put(rename, t);
            TeslaMapsConfig.save();
        }
        rename = null;
        renameText = "";
    }

    public static boolean keyPressed(int key, int scancode) {
        if (rename != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) finishRename(false);
            else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) finishRename(true);
            else if (key == GLFW.GLFW_KEY_BACKSPACE && !renameText.isEmpty()) renameText = renameText.substring(0, renameText.length() - 1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) return false;
        if (!searchFocused) {
            if (key == GLFW.GLFW_KEY_A) { navigateDir(false); return true; }
            if (key == GLFW.GLFW_KEY_D) { navigateDir(true); return true; }
            if (key == GLFW.GLFW_KEY_W) { navigateRow(-cols()); return true; }
            if (key == GLFW.GLFW_KEY_S) { navigateRow(cols()); return true; }
            return false;
        }
        if (TeslaMapsConfig.get().storageSearchResults && searchKeys(key)) return true;
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (allSelected) { allSelected = false; setSearch(""); return true; }
            if (!search.isEmpty()) setSearch(search.substring(0, search.length() - 1));
            return true;
        }
        return true; // text itself arrives via charTyped
    }

    private static boolean allSelected = false;

    /** Ctrl+A/C/V/X in the search box. */
    private static boolean searchKeys(int key) {
        Minecraft mc = Minecraft.getInstance();
        long w = mc.getWindow().handle();
        boolean ctrl = GLFW.glfwGetKey(w, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(w, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
        if (!ctrl) return false;
        switch (key) {
            case GLFW.GLFW_KEY_A -> allSelected = !search.isEmpty();
            case GLFW.GLFW_KEY_C -> mc.keyboardHandler.setClipboard(search);
            case GLFW.GLFW_KEY_X -> { mc.keyboardHandler.setClipboard(search); allSelected = false; setSearch(""); }
            case GLFW.GLFW_KEY_V -> {
                StringBuilder sb = new StringBuilder(allSelected ? "" : search);
                for (char ch : mc.keyboardHandler.getClipboard().toCharArray())
                    if (ch >= 32 && ch != 127 && sb.length() < 64) sb.append(Character.toLowerCase(ch));
                allSelected = false;
                setSearch(sb.toString());
            }
            default -> { return false; }
        }
        return true;
    }

    public static boolean charTyped(int codepoint) {
        if (active() && rename != null) {
            if (codepoint >= 32 && codepoint != 127 && renameText.length() < 32) renameText += Character.toString(codepoint);
            return true;
        }
        if (!active() || !searchFocused) return false;
        if (codepoint < 32 || codepoint == 127) return false;
        String base = allSelected ? "" : search;
        allSelected = false;
        setSearch(base + Character.toString(Character.toLowerCase(codepoint)));
        return true;
    }

    private static void clickPageArrow(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) return;
        AbstractContainerMenu menu = mc.player.containerMenu;
        for (Slot s : menu.slots) {
            if (s.container instanceof Inventory || s.getItem().isEmpty()) continue;
            if (strip(s.getItem().getHoverName().getString()).contains(name)) {
                mc.gameMode.handleContainerInput(menu.containerId, s.index, 0, ContainerInput.PICKUP, mc.player);
                return;
            }
        }
    }

    public static boolean mouseScrolled(double sy) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return false;
        layout(cs.width, cs.height);
        double pmx = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
        double pmy = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();
        if (overResults(pmx, pmy)) {
            hitScroll = Math.max(0, hitScroll - (int) Math.signum(sy) * 2);
            return true;
        }
        int wallBottom = (int) ((mainY() - 13) * iscl()) - 2;
        float maxScroll = Math.max(0, lay(visible()).contentH + startY() + 10 - wallBottom / scl());
        boolean ctrl = GLFW.glfwGetKey(mc.getWindow().handle(), GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(mc.getWindow().handle(), GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
        int speed = Math.max(1, TeslaMapsConfig.get().storageScrollSpeed);
        scroll = Math.max(0, Math.min(maxScroll, scroll - (float) (sy * (ctrl ? speed * 4.5f : speed))));
        return true;
    }

    /** Hits can be anywhere in the list, so every query change goes back to the top. */
    private static void setSearch(String value) {
        if (search.equals(value)) return;
        search = value;
        scroll = 0;
    }

    private static boolean inSearchBar(double x, double y) {
        int[] sb = sbRect();
        return x >= sb[0] && x <= sb[0] + sb[2] && y >= sb[1] && y <= sb[1] + sb[3];
    }

    // name+lore stripping is way too hot to run per item per frame while searching -> memoize per stack
    private static final java.util.IdentityHashMap<ItemStack, String> searchTextCache = new java.util.IdentityHashMap<>();

    private static String searchText(ItemStack st) {
        String cached = searchTextCache.get(st);
        if (cached != null) return cached;
        if (searchTextCache.size() > 4096) searchTextCache.clear();
        StringBuilder sb = new StringBuilder(com.teslamaps.utils.StringUtil.strip(st.getHoverName().getString()).toLowerCase());
        ItemLore lore = st.get(DataComponents.LORE);
        if (lore != null) for (Component line : lore.lines())
            sb.append('\n').append(com.teslamaps.utils.StringUtil.strip(line.getString()).toLowerCase());
        String s = sb.toString();
        searchTextCache.put(st, s);
        return s;
    }

    private static boolean matches(ItemStack st, String q) {
        return searchText(st).contains(q);
    }

    private static String strip(String s) {
        return com.teslamaps.utils.StringUtil.stripTrim(s);
    }
}

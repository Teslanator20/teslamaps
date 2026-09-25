package com.teslamaps.features;

// Ported from SkyHanni's ZorroCapeContestProtection.kt, LogBookStats.kt, GardenVisitorSupercraft.kt, VisitorListener.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.NeuRecipeIndex;
import com.teslamaps.utils.SkyblockItems;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Garden menus: Zorro's Cape claim guard, Visitor's Logbook totals, visitor Supercraft button and accept key. */
public final class GardenMenuHelpers {

    private static final Pattern CONTESTS = Pattern.compile("^(?:\\(\\d+/\\d+\\) )?Your Contests$");
    private static final Pattern LOGBOOK = Pattern.compile("^(?:\\(\\d+/\\d+\\) )?Visitor's Logbook$");
    private static final Pattern VISITED = Pattern.compile("Times Visited: ([0-9,.]+)");
    private static final Pattern ACCEPTED = Pattern.compile("Offers Accepted: ([0-9,.]+)");
    private static final Pattern AMOUNT = Pattern.compile("^(.+?)(?: x([\\d,]+))?$");
    private static final Pattern VISITOR_COUNT = Pattern.compile("Visitors: \\((\\d+)\\)");

    private static final int INFO_SLOT = 13, ACCEPT_SLOT = 29, SUPERCRAFT_SLOT = 31, BULK_SLOT = 50;

    private static Feature feature;
    private static Object lastScreen;
    private static long openedAt;
    private static boolean inContests, inLogbook, inVisitor;
    private static int visitorContainer = -1;
    private static boolean visitorChecked;
    private static String superCraftId;
    private static ItemStack superCraftItem;
    private static long lastClick;
    private static int ticks;
    private static final Map<String, long[]> logged = new HashMap<>();
    private static final EcoPanel logPanel = new EcoPanel(EcoPanel.Side.LEFT);

    private GardenMenuHelpers() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("GardenMenuHelpers").section("Garden")
                .when(() -> cfg().zorroCapeProtection || cfg().visitorLogbookStats
                        || cfg().visitorSupercraft || cfg().visitorAcceptHotkey)
                .tick(GardenMenuHelpers::tick)
                .config("Garden", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Garden Menus");
                    b.toggle("Zorro's Cape Contest Protection", () -> c.zorroCapeProtection, v -> c.zorroCapeProtection = v)
                            .desc("Blocks claiming Jacob's Contest rewards (single or Bulk Claim) while Zorro's Cape is not equipped. Reads your cloak from /equipment, open it once.")
                            .tags("jacob", "contest", "medals", "zorro", "cloak");
                    b.toggle("Visitor's Logbook Stats", () -> c.visitorLogbookStats, v -> c.visitorLogbookStats = v)
                            .desc("Beside the Visitor's Logbook: total times visited, accepted and denied. Flip through every page for the full count.")
                            .tags("logbook", "visitor", "denied");
                    b.toggle("Visitor Supercraft Button", () -> c.visitorSupercraft, v -> c.visitorSupercraft = v)
                            .desc("When your sacks hold the ingredients for an item the visitor wants, slot 31 becomes a Supercraft button that opens /viewrecipe. Uses the sack amounts TeslaMaps knows.")
                            .tags("visitor", "supercraft", "sacks", "recipe");
                    b.toggle("Visitor Accept Key", () -> c.visitorAcceptHotkey, v -> c.visitorAcceptHotkey = v)
                            .desc("A key that clicks Accept Offer in a visitor menu.")
                            .tags("visitor", "accept", "hotkey")
                            .cheat();
                    b.keybind("  Accept Key", () -> c.visitorAcceptKey, v -> c.visitorAcceptKey = v);
                });
        com.teslamaps.utils.SbProfile.onChange(logged::clear);
    }

    private static boolean on() {
        return feature != null && feature.active();
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) {
            if (lastScreen != null) close();
            return;
        }
        if (cs != lastScreen) {
            close();
            lastScreen = cs;
            openedAt = System.currentTimeMillis();
        }
        String title = StringUtil.stripTrim(cs.getTitle().getString());
        List<Slot> slots = cs.getMenu().slots;

        if (cfg().zorroCapeProtection && !inContests && CONTESTS.matcher(title).matches() && slots.size() > BULK_SLOT) {
            List<String> lore = ItemUtil.loreLines(slots.get(BULK_SLOT).getItem());
            if (!lore.isEmpty() && StringUtil.stripTrim(lore.get(0)).startsWith("Claim multiple farming contest")) inContests = true;
        }

        if (cfg().visitorLogbookStats && LOGBOOK.matcher(title).matches()) {
            inLogbook = true;
            if (System.currentTimeMillis() - openedAt > 500 && ticks++ % 10 == 0) readLogbook(cs, slots);
        }

        if ((cfg().visitorSupercraft || cfg().visitorAcceptHotkey) && SkyblockUtils.isInGarden() && slots.size() > SUPERCRAFT_SLOT) {
            if (!visitorChecked || cs.getMenu().containerId != visitorContainer) {
                ItemStack accept = slots.get(ACCEPT_SLOT).getItem();
                if (isVisitorInfo(slots.get(INFO_SLOT).getItem())
                        && StringUtil.stripTrim(accept.getHoverName().getString()).equals("Accept Offer")) {
                    visitorChecked = true;
                    visitorContainer = cs.getMenu().containerId;
                    inVisitor = true;
                    superCraftId = cfg().visitorSupercraft ? findSupercraft(accept) : null;
                }
            }
        }
    }

    private static void close() {
        lastScreen = null;
        inContests = false;
        inLogbook = false;
        inVisitor = false;
        visitorChecked = false;
        visitorContainer = -1;
        superCraftId = null;
        logPanel.clear();
    }

    private static boolean isVisitorInfo(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null || lore.lines().size() != 4) return false;
        return StringUtil.strip(lore.lines().get(3).getString()).startsWith("Offers Accepted: ");
    }

    // ---------------------------------------------------------------- logbook

    private static void readLogbook(AbstractContainerScreen<?> cs, List<Slot> slots) {
        for (Slot s : slots) {
            if (s.container instanceof Inventory) continue;
            ItemStack st = s.getItem();
            if (st.isEmpty()) continue;
            String name = st.getHoverName().getString();
            if (name.isEmpty()) continue;
            long visited = -1, accepted = 0;
            for (String l : ItemUtil.loreLines(st)) {
                String t = StringUtil.stripTrim(l);
                Matcher m = VISITED.matcher(t);
                if (m.find()) { visited = num(m.group(1)); continue; }
                m = ACCEPTED.matcher(t);
                if (m.find()) accepted = num(m.group(1));
            }
            if (visited >= 0) logged.put(name, new long[]{visited, accepted});
        }
        long visited = 0, accepted = 0;
        for (long[] v : logged.values()) {
            visited += v[0];
            accepted += v[1];
        }
        long denied = Math.max(0, visited - accepted - visitingNow());
        logPanel.set(List.of(
                EcoPanel.Row.of("§6Times Visited: §b" + String.format("%,d", visited)),
                EcoPanel.Row.of("§6Times Accepted: §a" + String.format("%,d", accepted)),
                EcoPanel.Row.of("§6Times Denied: §c" + String.format("%,d", denied))));
    }

    private static long num(String s) {
        try {
            return (long) Double.parseDouble(s.replace(",", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static int visitingNow() {
        for (String raw : TabListUtils.getTabListLines()) {
            Matcher m = VISITOR_COUNT.matcher(StringUtil.strip(raw));
            if (m.find()) return Integer.parseInt(m.group(1));
        }
        return 0;
    }

    public static void render(GuiGraphicsExtractor g, int mx, int my) {
        if (!inLogbook || !on() || !cfg().visitorLogbookStats || logPanel.empty()) return;
        if (!(Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> cs) || cs != lastScreen) return;
        logPanel.render(g, cs, 0, mx, my);
    }

    // ---------------------------------------------------------------- supercraft

    private static String findSupercraft(ItemStack accept) {
        Map<String, Integer> shopping = new LinkedHashMap<>();
        boolean inList = false;
        for (String l : ItemUtil.loreLines(accept)) {
            String t = StringUtil.stripTrim(l);
            if (t.equals("Items Required:")) { inList = true; continue; }
            if (!inList) continue;
            if (t.isEmpty()) break;
            Matcher m = AMOUNT.matcher(t);
            if (!m.matches()) continue;
            String id = SkyblockItems.idForName(m.group(1));
            if (id == null) continue;
            int amount = m.group(2) == null ? 1 : Integer.parseInt(m.group(2).replace(",", ""));
            shopping.merge(id, amount, Integer::sum);
        }
        if (shopping.isEmpty()) return null;
        Map<String, NeuRecipeIndex.Entry> recipes = NeuRecipeIndex.recipesOrNull();
        if (recipes == null) return null;
        Map<String, Long> sacks = sackAmounts();
        for (Map.Entry<String, Integer> e : shopping.entrySet()) {
            long have = sacks.getOrDefault(e.getKey(), 0L);
            if (have >= e.getValue()) continue;
            NeuRecipeIndex.Entry r = recipes.get(e.getKey());
            if (r == null || r.ingredients() == null || r.ingredients().isEmpty()) continue;
            long crafts = (e.getValue() - have + Math.max(1, r.count()) - 1) / Math.max(1, r.count());
            boolean ok = true;
            for (Map.Entry<String, Integer> in : r.ingredients().entrySet()) {
                if (sacks.getOrDefault(in.getKey(), 0L) < (long) in.getValue() * crafts) { ok = false; break; }
            }
            if (ok) return e.getKey();
        }
        return null;
    }

    private static Map<String, Long> sackAmounts() {
        Map<String, Long> out = new HashMap<>();
        for (Map.Entry<String, Integer> e : ItemIndex.sackAmounts().entrySet()) {
            String id = SkyblockItems.idForName(e.getKey());
            if (id != null) out.merge(id, (long) e.getValue(), Long::sum);
        }
        return out;
    }

    private static boolean superCraftSlot(Slot slot) {
        return slot != null && superCraftId != null && inVisitor && slot.index == SUPERCRAFT_SLOT
                && !(slot.container instanceof Inventory) && cfg().visitorSupercraft && on();
    }

    /** Stack drawn (and shown as tooltip) instead of the slot's own, or {@code stack} unchanged. */
    public static ItemStack icon(Slot slot, ItemStack stack) {
        if (superCraftId == null || !superCraftSlot(slot)) return stack;
        if (superCraftItem == null) {
            ItemStack st = new ItemStack(Items.GOLDEN_PICKAXE);
            st.set(DataComponents.CUSTOM_NAME, Component.literal("§bSupercraft"));
            st.set(DataComponents.LORE, new ItemLore(List.of(
                    Component.literal("§8(From TeslaMaps)"), Component.empty(),
                    Component.literal("§7You have the items to craft."),
                    Component.literal("§7Click me to open the supercrafter!"))));
            superCraftItem = st;
        }
        return superCraftItem;
    }

    // ---------------------------------------------------------------- clicks

    /** Slot click hook, true cancels the click. */
    public static boolean blockClick(AbstractContainerScreen<?> cs, Slot slot) {
        if (slot == null || !on() || cs != lastScreen) return false;
        if (superCraftSlot(slot)) {
            long now = System.currentTimeMillis();
            Minecraft mc = Minecraft.getInstance();
            if (now - lastClick > 300 && mc.getConnection() != null) {
                lastClick = now;
                mc.getConnection().sendCommand("viewrecipe " + superCraftId);
            }
            return true;
        }
        if (inContests && cfg().zorroCapeProtection && !(slot.container instanceof Inventory)) {
            ItemStack st = slot.getItem();
            if (st.isEmpty()) return false;
            List<String> lore = ItemUtil.loreLines(st);
            boolean claim = !lore.isEmpty() && StringUtil.stripTrim(lore.get(lore.size() - 1)).equals("Click to claim reward!");
            boolean bulk = StringUtil.stripTrim(st.getHoverName().getString()).equals("Bulk Claim");
            if (!claim && !bulk) return false;
            String cloak = EquipmentDisplay.cloakId();
            if ("ZORROS_CAPE".equals(cloak)) return false;
            Minecraft mc = Minecraft.getInstance();
            mc.gui.setTitle(Component.literal("§cNo Zorro's Cape equipped!"));
            if (mc.player != null) {
                mc.player.playSound(SoundEvents.VILLAGER_NO, 1f, 1f);
                mc.player.sendSystemMessage(Component.literal(cloak == null
                        ? "§b[TeslaMaps] §cBlocked Jacob's Contest reward claim! §eOpen §6/equipment §eonce so TeslaMaps knows your cloak."
                        : "§b[TeslaMaps] §cBlocked Jacob's Contest reward claim! §eEquip §6Zorro's Cape §ewhen claiming contest rewards."));
            }
            return true;
        }
        return false;
    }

    /** Key press inside a container, true eats it. */
    public static boolean key(int key) {
        TeslaMapsConfig c = cfg();
        if (!c.visitorAcceptHotkey || c.visitorAcceptKey < 0 || key != c.visitorAcceptKey) return false;
        if (!inVisitor || !on() || LegitMode.blocksCheats()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs) || cs != lastScreen || mc.gameMode == null || mc.player == null) return false;
        long now = System.currentTimeMillis();
        if (now - lastClick < 300) return true;
        lastClick = now;
        if (cs.getMenu().slots.size() <= ACCEPT_SLOT
                || com.teslamaps.features.visitors.VisitorRewardGuard.blockClick(cs.getMenu().slots.get(ACCEPT_SLOT))) return true;
        mc.gameMode.handleContainerInput(cs.getMenu().containerId, ACCEPT_SLOT, 0, ContainerInput.PICKUP, mc.player);
        return true;
    }
}

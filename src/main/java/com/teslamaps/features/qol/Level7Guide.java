package com.teslamaps.features.qol;

// Ported from FloydAddons' FloydSkyBlockLevel7Guide: fresh-profile route to SkyBlock Level 7, passive step detection
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.ScoreboardUtils;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class Level7Guide {

    private static final int W = 300, TEXT_W = W - 16;
    private static final int DONE = Level7Route.MAX + 1;

    private static HudEditElement hud;
    private static List<String> lines = List.of();
    private static int panelH = 40;
    private static int timer;
    private static String lastChat = "";
    private static long lastChatAt;
    private static final Map<String, Integer> skills = new HashMap<>();
    private static Level7Route.Base base;
    private static int baseStep = -1;
    private static long stepStarted = System.currentTimeMillis();
    private static long teleportedUntil;
    private static Object lastLevel;
    private static Vec3 lastPos;
    private static int builtFor = -1;

    private Level7Guide() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("level7Guide", "SkyBlock Level 7 Guide")
                .enabled(() -> cfg().level7Guide)
                .size(() -> W, () -> panelH)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, lines.isEmpty() ? build(Math.max(1, Math.min(step(), Level7Route.MAX))) : lines));
        Feature.of("Level7Guide").when(() -> cfg().level7Guide)
                .tick(Level7Guide::tick)
                .serverChat(Level7Guide::onChat)
                .hud("level7guide", Level7Guide::render)
                .config("QoL & Misc", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("SkyBlock Level 7 Guide");
                    b.toggle("SkyBlock Level 7 Guide", () -> c.level7Guide, v -> c.level7Guide = v)
                            .desc("HUD that walks a fresh profile through 119 steps to SkyBlock Level 7. Steps advance on their own from chat, menus, inventory, scoreboard, skills and nearby NPCs. Position in /tmap gui.");
                    b.buttonRow(new String[]{"Previous Step", "Next Step", "Reset"}, new Runnable[]{
                            () -> setStep(step() - 1), () -> setStep(step() + 1), () -> setStep(1)});
                });
    }

    private static int step() {
        return Math.max(1, Math.min(cfg().level7GuideStep, DONE));
    }

    private static void setStep(int s) {
        int v = Math.max(1, Math.min(s, DONE));
        cfg().level7GuideStep = v;
        TeslaMapsConfig.save();
        baseStep = -1;
        builtFor = -1;
    }

    private static void onChat(String raw) {
        lastChat = Level7Route.normalize(StringUtil.strip(raw));
        lastChatAt = System.currentTimeMillis();
        rememberSkill(lastChat);
    }

    private static void rememberSkill(String normalized) {
        Object[] r = Level7Route.parseSkill(normalized);
        if (r == null) return;
        skills.merge((String) r[0], (Integer) r[1], Math::max);
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        Vec3 pos = mc.player.position();
        if (lastLevel != null && (lastLevel != mc.level || (lastPos != null && lastPos.distanceToSqr(pos) > 2500)))
            teleportedUntil = now + 3000;
        lastLevel = mc.level;
        lastPos = pos;

        int cur = step();
        if (cur != builtFor) {
            builtFor = cur;
            lines = build(cur);
        }
        if (++timer % 10 != 0) return;
        if (cur >= DONE || !SkyblockUtils.isInSkyblock()) return;
        if (cur == 47) PriceManager.ensureFresh();

        List<String> board = board();
        if (baseStep != cur || base == null) {
            base = new Level7Route.Base(inventory(mc), mc.player.experienceLevel, Level7Route.parsePurse(board));
            baseStep = cur;
            stepStarted = now;
        }
        Level7Route.Obs o = observe(mc, now, board);
        for (Level7Route.Item i : o.screenItems()) {
            rememberSkill(i.name());
            for (String l : i.lore()) rememberSkill(l);
        }
        if (Level7Route.isComplete(cur, o, base)) setStep(cur + 1);
    }

    private static List<String> board() {
        List<String> out = new ArrayList<>();
        for (String l : ScoreboardUtils.getScoreboardLines()) out.add(Level7Route.normalize(StringUtil.strip(l)));
        String title = ScoreboardUtils.getScoreboardTitle();
        if (title != null) out.add(Level7Route.normalize(StringUtil.strip(title)));
        for (String l : TabListUtils.getTabListLines()) out.add(Level7Route.normalize(StringUtil.strip(l)));
        return out;
    }

    private static Level7Route.Obs observe(Minecraft mc, long now, List<String> board) {
        String title = "";
        List<Level7Route.Item> screenItems = new ArrayList<>();
        if (mc.screen instanceof AbstractContainerScreen<?> cs) {
            title = Level7Route.normalize(StringUtil.strip(cs.getTitle().getString()));
            Inventory inv = mc.player.getInventory();
            for (Slot s : cs.getMenu().slots) {
                if (s.container == inv) continue;
                Level7Route.Item it = describe(s.getItem());
                if (it != null) screenItems.add(it);
            }
        }
        Map<String, Level7Route.Item> armor = new HashMap<>();
        putArmor(armor, "head", mc.player.getItemBySlot(EquipmentSlot.HEAD));
        putArmor(armor, "chest", mc.player.getItemBySlot(EquipmentSlot.CHEST));
        putArmor(armor, "legs", mc.player.getItemBySlot(EquipmentSlot.LEGS));
        putArmor(armor, "feet", mc.player.getItemBySlot(EquipmentSlot.FEET));
        Double bin = PriceManager.lowestBinOrNull("TREECAPITATOR_AXE");
        return new Level7Route.Obs(now - lastChatAt <= 4000 ? lastChat : "", title, screenItems, inventory(mc),
                nearby(mc), board, Map.copyOf(skills), mc.player.experienceLevel, Level7Route.parsePurse(board),
                describe(mc.player.getMainHandItem()), armor, mc.player.isShiftKeyDown(), now <= teleportedUntil,
                now - stepStarted, bin == null ? null : bin.longValue());
    }

    private static void putArmor(Map<String, Level7Route.Item> m, String k, ItemStack s) {
        Level7Route.Item i = describe(s);
        if (i != null) m.put(k, i);
    }

    private static List<Level7Route.Item> inventory(Minecraft mc) {
        Inventory inv = mc.player.getInventory();
        List<Level7Route.Item> out = new ArrayList<>();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            Level7Route.Item it = describe(inv.getItem(i));
            if (it != null) out.add(it);
        }
        return out;
    }

    private static List<String> nearby(Minecraft mc) {
        Set<String> out = new LinkedHashSet<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e == mc.player || e.distanceToSqr(mc.player) > 625) continue;
            String n = e.getName().getString();
            if (!n.isBlank()) out.add(Level7Route.normalize(StringUtil.strip(n)));
            if (e.getCustomName() != null) {
                String c = e.getCustomName().getString();
                if (!c.isBlank()) out.add(Level7Route.normalize(StringUtil.strip(c)));
            }
            if (out.size() >= 80) break;
        }
        return new ArrayList<>(out);
    }

    private static Level7Route.Item describe(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        String id = ItemUtil.skyblockId(stack);
        if (id == null || id.isEmpty()) id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toUpperCase(Locale.ROOT);
        List<String> lore = new ArrayList<>();
        for (String l : ItemUtil.loreLines(stack)) lore.add(Level7Route.normalize(l));
        return new Level7Route.Item(id, Level7Route.normalize(StringUtil.strip(stack.getHoverName().getString())),
                stack.getCount(), stack.isEnchanted(), lore);
    }

    private static List<String> build(int s) {
        Minecraft mc = Minecraft.getInstance();
        List<String> out = new ArrayList<>();
        boolean done = s >= DONE;
        Level7Route.Step st = Level7Route.step(s);
        String progress = done ? "§a119 / 119" : "§fStep " + s + " / " + Level7Route.MAX;
        out.add("§bSkyBlock LVL 7 Guide  " + progress);
        String label = done ? "Guide complete!" : st.label();
        String instr = done ? "All 119 manual tasks are finished." : instruction(st);
        String det = done ? "Progress is saved" : st.detection();
        for (String l : wrap(mc, label)) out.add("§f§l" + l);
        for (String l : wrap(mc, "Do: " + instr)) out.add("§f" + l);
        for (String l : wrap(mc, "Done when: " + det)) out.add((done ? "§a" : "§7") + l);
        panelH = out.size() * 10 + 6;
        return out;
    }

    private static String instruction(Level7Route.Step st) {
        if (!st.instruction().contains("{treecapTarget}")) return st.instruction();
        Double bin = PriceManager.lowestBinOrNull("TREECAPITATOR_AXE");
        String target = bin == null ? "the current Treecapitator lowest BIN" : String.format(Locale.ROOT, "%,d coins", bin.longValue());
        return st.instruction().replace("{treecapTarget}", target).replace(", the current Treecapitator lowest BIN provided by SkyCofl", "");
    }

    private static List<String> wrap(Minecraft mc, String text) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : text.split(" ")) {
            String cand = cur.isEmpty() ? word : cur + " " + word;
            if (!cur.isEmpty() && mc.font.width(cand) > TEXT_W) {
                out.add(cur.toString());
                cur = new StringBuilder(word);
            } else {
                cur = new StringBuilder(cand);
            }
        }
        if (!cur.isEmpty()) out.add(cur.toString());
        return out;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        List<String> ls = lines;
        if (ls.isEmpty() || hud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), ls);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, List<String> ls) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        HudStyle.panel(g, 0, 0, W, ls.size() * 10 + 6);
        for (int i = 0; i < ls.size(); i++) g.text(mc.font, ls.get(i), 8, 4 + i * 10, 0xFFFFFFFF, true);
        g.pose().popMatrix();
    }
}

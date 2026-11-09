package com.teslamaps.features.theend;

// Special Zealot check from Skyblocker's TheEnd.isSpecialZealot, counter after SkyblockAddons'
// ZEALOT_COUNTER / SHOW_SUMMONING_EYE_COUNT / SHOW_AVERAGE_ZEALOTS_PER_EYE
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.EspStyle;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.NametagMobs;
import com.teslamaps.utils.ScoreboardUtils;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ZealotHelper {

    private static final List<String> SAMPLE = List.of("§5Zealots: §f1,234", "§5Since Last Eye: §f321",
            "§5Summoning Eyes: §f3", "§5Avg Zealots/Eye: §f411");
    private static final double EXPLOSION_R_SQ = 4.6 * 4.6;
    private static final long EXPLOSION_MS = 1500;
    private static final long HIT_MS = 30_000;

    private record Boom(Vec3 pos, long at) {}

    private static final Int2ObjectOpenHashMap<Long> hits = new Int2ObjectOpenHashMap<>();
    private static final IntOpenHashSet counted = new IntOpenHashSet();
    private static final Int2ObjectOpenHashMap<Vec3> arrows = new Int2ObjectOpenHashMap<>();
    private static final List<Boom> booms = new ArrayList<>();
    private static final List<Enderman> specials = new ArrayList<>();

    private static Object lastLevel;
    private static boolean inEnd, inZealotArea;
    private static int unsaved;
    private static long areaCheck;
    private static List<String> lines = List.of();
    private static int width;
    private static HudEditElement hud;

    private ZealotHelper() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("zealotCounter", "Zealot Counter").ctx(HudEditElement.HudCtx.THE_END).section("The End")
                .enabled(() -> cfg().zealotCounter)
                .size(() -> panelWidth(SAMPLE), () -> SAMPLE.size() * 10 + 6)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, SAMPLE, panelWidth(SAMPLE)));
        Feature.of("SpecialZealotHighlight").section("The End").legit().when(() -> cfg().specialZealotHighlight)
                .tick(ZealotHelper::highlightTick)
                .world(ZealotHelper::renderHighlight);
        Feature.of("ZealotCounter").section("The End").when(() -> cfg().zealotCounter)
                .tick(ZealotHelper::counterTick)
                .serverChat(ZealotHelper::onChat)
                .hudHiddenInInventory("zealotcounter", ZealotHelper::render)
                .config("The End", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Zealots");
                    b.toggle("Special Zealot Highlight", () -> c.specialZealotHighlight, v -> c.specialZealotHighlight = v).legit()
                            .desc("Highlights the Special Zealot (carries an end portal frame, drops the Summoning Eye).")
                            .tags("zealot", "special", "summoning eye", "highlight", "glow");
                    b.espStyle("Special Zealot", () -> c.specialZealotEsp);
                    b.toggle("Zealot Counter", () -> c.zealotCounter, v -> c.zealotCounter = v)
                            .desc("Zealots you killed (Explosive Bow kills too), Zealots since the last Summoning Eye, Eyes and the "
                                    + "average Zealots per Eye. Position in /tmap gui")
                            .tags("zealot", "counter", "summoning eye", "pity", "average");
                    b.toggle("  Only In Zealot Areas", () -> c.zealotCounterOnlyInArea, v -> c.zealotCounterOnlyInArea = v)
                            .desc("Dragon's Nest and Zealot Bruiser Hideout only");
                    b.toggle("  Eye / Special Zealot Alerts", () -> c.zealotAlerts, v -> c.zealotAlerts = v)
                            .desc("Big title and sound on RARE DROP! Summoning Eye and A special Zealot has spawned nearby!");
                    b.toggle("  Add Count To Special Zealot Line", () -> c.zealotAlertAppendCount, v -> c.zealotAlertAppendCount = v);
                    b.button("  Reset Zealot Counter", () -> {
                        c.zealotKillsTotal = 0;
                        c.zealotKillsSinceEye = 0;
                        c.zealotEyes = 0;
                        TeslaMapsConfig.save();
                    });
                });
    }

    private static boolean isZealot(Enderman e) {
        ArmorStand tag = NametagMobs.tagForMob(e);
        if (tag != null) {
            String n = NametagMobs.tagName(tag);
            if (n != null && n.contains("Zealot")) return true;
        }
        for (ArmorStand as : NametagMobs.namedStands(e, 1.0)) {
            String n = NametagMobs.tagName(as);
            if (n != null && n.contains("Zealot")) return true;
        }
        return false;
    }

    private static void updateArea() {
        long now = System.currentTimeMillis();
        if (now - areaCheck < 1000) return;
        areaCheck = now;
        inEnd = SkyblockUtils.isInArea("The End");
        inZealotArea = false;
        if (!inEnd) return;
        for (String l : ScoreboardUtils.getScoreboardLines()) {
            String s = StringUtil.strip(l);
            if (s.contains("Dragon's Nest") || s.contains("Zealot Bruiser Hideout")) { inZealotArea = true; break; }
        }
    }

    private static void highlightTick() {
        specials.clear();
        updateArea();
        if (!inEnd) return;
        EspStyle style = EspStyle.of(cfg().specialZealotEsp);
        for (Enderman e : EntityCache.byType(Enderman.class)) {
            BlockState held = e.getCarriedBlock();
            if (held == null || !held.is(Blocks.END_PORTAL_FRAME) || !e.isAlive()) continue;
            if (!isZealot(e)) continue;
            style.tickEntity(e);
            specials.add(e);
        }
    }

    private static void renderHighlight(PoseStack pose, Vec3 cam) {
        if (specials.isEmpty()) return;
        EspStyle style = EspStyle.of(cfg().specialZealotEsp);
        float pt = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
        for (Enderman e : specials) style.drawEntity(pose, e, cam, pt);
    }

    /** ClientPlayerInteractionManager.attack. */
    public static void onAttack(Entity target) {
        if (!cfg().zealotCounter || !inEnd || !(target instanceof Enderman)) return;
        hits.put(target.getId(), (Long) System.currentTimeMillis());
    }

    private static void counterTick() {
        Minecraft mc = Minecraft.getInstance();
        TeslaMapsConfig c = cfg();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            hits.clear();
            counted.clear();
            arrows.clear();
            booms.clear();
        }
        updateArea();
        if (!inEnd) {
            lines = List.of();
            return;
        }
        long now = System.currentTimeMillis();
        trackArrows(mc, now);
        booms.removeIf(b -> now - b.at > EXPLOSION_MS);
        hits.int2ObjectEntrySet().removeIf(en -> now - en.getValue() > HIT_MS);

        for (Enderman e : EntityCache.byType(Enderman.class)) {
            if (!e.isDeadOrDying() || counted.contains(e.getId())) continue;
            counted.add(e.getId());
            boolean mine = hits.remove(e.getId()) != null || nearBoom(e.position());
            if (!mine || !isZealot(e)) continue;
            c.zealotKillsTotal++;
            c.zealotKillsSinceEye++;
            if (++unsaved >= 10) {
                unsaved = 0;
                TeslaMapsConfig.save();
            }
        }
        if (counted.size() > 2000) counted.clear();

        if (c.zealotCounterOnlyInArea && !inZealotArea) {
            lines = List.of();
            return;
        }
        List<String> out = new ArrayList<>(4);
        out.add("§5Zealots: §f" + fmt(c.zealotKillsTotal));
        out.add("§5Since Last Eye: §f" + fmt(c.zealotKillsSinceEye));
        out.add("§5Summoning Eyes: §f" + fmt(c.zealotEyes));
        out.add("§5Avg Zealots/Eye: §f" + (c.zealotEyes > 0 ? fmt(c.zealotKillsTotal / c.zealotEyes) : "-"));
        lines = out;
        width = panelWidth(out);
    }

    private static void trackArrows(Minecraft mc, long now) {
        IntOpenHashSet alive = new IntOpenHashSet();
        boolean explosive = "EXPLOSIVE_BOW".equals(ItemUtil.skyblockId(mc.player.getMainHandItem()));
        for (AbstractArrow a : EntityCache.byType(AbstractArrow.class)) {
            if (a.getOwner() != mc.player) continue;
            alive.add(a.getId());
            if (explosive || arrows.containsKey(a.getId())) arrows.put(a.getId(), a.position());
        }
        var it = arrows.int2ObjectEntrySet().iterator();
        while (it.hasNext()) {
            var en = it.next();
            if (alive.contains(en.getIntKey())) continue;
            booms.add(new Boom(en.getValue(), now));
            it.remove();
        }
    }

    private static boolean nearBoom(Vec3 p) {
        for (Boom b : booms) if (b.pos.distanceToSqr(p) <= EXPLOSION_R_SQ) return true;
        return false;
    }

    private static void onChat(String text) {
        TeslaMapsConfig c = cfg();
        String t = StringUtil.stripTrim(text);
        if (t.startsWith("RARE DROP! Summoning Eye")) {
            c.zealotEyes++;
            c.zealotKillsSinceEye = 0;
            TeslaMapsConfig.save();
            if (c.zealotAlerts) alert("§5§lSUMMONING EYE!");
        } else if (t.equals("A special Zealot has spawned nearby!")) {
            if (c.zealotAlerts) alert("§c§lSPECIAL ZEALOT!");
        }
    }

    /** ChatFilter chain: text to append to the special Zealot line, or null. */
    public static String appendCount(String stripped) {
        TeslaMapsConfig c = cfg();
        if (!c.zealotCounter || !c.zealotAlertAppendCount || !c.section("The End")) return null;
        if (!stripped.trim().equals("A special Zealot has spawned nearby!")) return null;
        return " §7(§e" + fmt(c.zealotKillsSinceEye) + " §7Zealots since last Eye)";
    }

    private static void alert(String text) {
        Minecraft mc = Minecraft.getInstance();
        mc.gui.hud.setTimes(0, 40, 10);
        mc.gui.hud.setTitle(Component.literal(text));
        LoudSound.play(SoundEvents.PLAYER_LEVELUP, 1f, 1f);
    }

    private static String fmt(int n) {
        return String.format(Locale.ROOT, "%,d", n);
    }

    private static int panelWidth(List<String> ls) {
        Minecraft mc = Minecraft.getInstance();
        int w = 0;
        for (String l : ls) w = Math.max(w, mc.font.width(l));
        return w + 8;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        List<String> ls = lines;
        if (ls.isEmpty() || hud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), ls, width);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, List<String> ls, int w) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        HudStyle.panel(g, 0, 0, w, ls.size() * 10 + 6);
        for (int i = 0; i < ls.size(); i++) g.text(mc.font, ls.get(i), 4, 4 + i * 10, 0xFFFFFFFF, true);
        g.pose().popMatrix();
    }
}
